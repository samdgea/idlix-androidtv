package com.idlix.data.repository

import com.idlix.data.api.ApiClient
import com.idlix.data.api.IdlixApiService
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.local.PersistentCookieJar
import com.idlix.data.model.ApiErrorResponse
import com.idlix.data.model.AuthLoginResult
import com.idlix.data.model.AuthSessionItem
import com.idlix.data.model.AuthState
import com.idlix.data.model.IdlixUser
import com.idlix.data.model.LoginRequest
import com.idlix.data.model.LogoutRequest
import com.idlix.data.model.RevokeSessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AuthRepository(
    private val idlixApiProvider: () -> IdlixApiService = { ApiClient.idlixApi },
    private val fingerprintProvider: () -> String = { ApiClient.getClientFingerprint() },
    private val cookieJar: PersistentCookieJar = ApiClient.cookieJar,
    private val baseUrlProvider: () -> String = { ApiClient.getBaseUrl() },
    private val onBaseUrlResolved: (String) -> Unit = { resolved -> ApiClient.setBaseUrl(resolved) },
    private val onSessionChanged: () -> Unit = { IdlixRepository.clearPlaybackCache() }
) {
    private val idlixApi: IdlixApiService get() = idlixApiProvider()

    val authState: StateFlow<AuthState> = AuthSessionStore.authState

    companion object {
        private val bootstrapMutex = Mutex()
    }

    suspend fun bootstrapSession() = withContext(Dispatchers.IO) {
        bootstrapMutex.withLock {
            val baseHttpUrl = baseUrlProvider().toHttpUrlOrNull()
            val hasCookies = baseHttpUrl != null && cookieJar.hasAuthCookies(baseHttpUrl)
            if (!AuthSessionStore.isAuthenticated() && !hasCookies) {
                return@withLock
            }
            try {
                val resp = idlixApi.getMe()
                if (resp.isSuccessful) {
                    val user = resp.body()?.user
                    if (user != null) {
                        AuthSessionStore.setAuthenticated(user)
                    }
                } else if (resp.code() == 401) {
                    cookieJar.clearAuthCookies()
                    AuthSessionStore.setGuest()
                    onSessionChanged()
                }
            } catch (_: Exception) {
                // Keep cached AuthSessionStore state on transient network/timeout errors
            }
        }
    }

    suspend fun login(
        identifier: String,
        password: String,
        replaceExistingSessions: Boolean = false
    ): AuthLoginResult = withContext(Dispatchers.IO) {
        val cleanIdentifier = identifier.trim()
        if (cleanIdentifier.isBlank() || password.isEmpty()) {
            return@withContext AuthLoginResult.Error(
                message = "Email/username dan password wajib diisi."
            )
        }

        try {
            var resp = idlixApi.login(
                request = LoginRequest(
                    identifier = cleanIdentifier,
                    password = password,
                    replaceExistingSessions = if (replaceExistingSessions) true else null
                ),
                fingerprint = fingerprintProvider()
            )

            // If a legacy mirror (e.g. z2.idlixku.com) 301-redirected POST->GET into 404 on z3.idlixku.com,
            // update baseUrl to the resolved origin and retry the POST directly once.
            if (resp.code() == 404 && migrateBaseUrlIfRedirected(resp.raw().request.url)) {
                resp = idlixApi.login(
                    request = LoginRequest(
                        identifier = cleanIdentifier,
                        password = password,
                        replaceExistingSessions = if (replaceExistingSessions) true else null
                    ),
                    fingerprint = fingerprintProvider()
                )
            }

            if (resp.isSuccessful) {
                val userFromLogin = resp.body()?.user
                val resolvedUser = userFromLogin ?: run {
                    val meResp = idlixApi.getMe()
                    if (meResp.isSuccessful) meResp.body()?.user else null
                }
                if (resolvedUser != null) {
                    AuthSessionStore.setAuthenticated(resolvedUser)
                    onSessionChanged()
                    return@withContext AuthLoginResult.Success(resolvedUser)
                }
                return@withContext AuthLoginResult.Error(
                    message = "Gagal memuat data akun setelah masuk."
                )
            }

            val err = parseApiError(resp.errorBody()?.string())
            if (err?.code == "AUTH_DEVICE_LIMIT_REACHED") {
                val limit = (err.limit ?: 1).coerceAtLeast(1)
                val currentCount = (err.currentCount ?: limit).coerceAtLeast(limit)
                return@withContext AuthLoginResult.DeviceLimitReached(
                    limit = limit,
                    currentCount = currentCount
                )
            }

            val msg = err?.error?.ifBlank { null }
                ?: when (resp.code()) {
                    401 -> "Email/username atau password salah."
                    429 -> "Terlalu banyak percobaan login. Silakan coba lagi nanti."
                    else -> "Gagal masuk (HTTP ${resp.code()})."
                }

            AuthLoginResult.Error(
                message = msg,
                code = err?.code?.ifBlank { null },
                retryAfter = err?.retryAfter
            )
        } catch (e: Exception) {
            AuthLoginResult.Error(
                message = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Gagal terhubung ke server IDLIX."
            )
        }
    }

    suspend fun refreshUser(): Result<IdlixUser> = withContext(Dispatchers.IO) {
        try {
            val resp = idlixApi.getMe()
            if (resp.isSuccessful) {
                val user = resp.body()?.user
                if (user != null) {
                    AuthSessionStore.setAuthenticated(user)
                    return@withContext Result.success(user)
                }
                return@withContext Result.failure(Exception("Data akun kosong"))
            }
            if (resp.code() == 401) {
                cookieJar.clearAuthCookies()
                AuthSessionStore.setGuest()
                onSessionChanged()
                return@withContext Result.failure(Exception("Sesi akun telah berakhir"))
            }
            val err = parseApiError(resp.errorBody()?.string())
            Result.failure(Exception(err?.error?.ifBlank { null } ?: "HTTP ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(everywhere: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = LogoutRequest(scope = if (everywhere) "everywhere" else "local")
            var resp = idlixApi.logout(req)
            if (resp.code() == 404 && migrateBaseUrlIfRedirected(resp.raw().request.url)) {
                resp = idlixApi.logout(req)
            }
            if (resp.isSuccessful || resp.code() == 401) {
                clearLocalSession()
                Result.success(Unit)
            } else {
                val err = parseApiError(resp.errorBody()?.string())
                if (!everywhere) {
                    clearLocalSession()
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(err?.error?.ifBlank { null } ?: "HTTP ${resp.code()}"))
                }
            }
        } catch (e: Exception) {
            if (!everywhere) {
                clearLocalSession()
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getSessions(): Result<List<AuthSessionItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = idlixApi.getSessions()
            if (resp.isSuccessful) {
                val active = resp.body()?.sessions
                    ?.filter { it.status.equals("active", ignoreCase = true) }
                    ?: emptyList()
                Result.success(active)
            } else {
                val err = parseApiError(resp.errorBody()?.string())
                Result.failure(Exception(err?.error?.ifBlank { null } ?: "HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeSession(sessionHandle: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = RevokeSessionRequest(sessionHandle)
            var resp = idlixApi.revokeSession(req)
            if (resp.code() == 404 && migrateBaseUrlIfRedirected(resp.raw().request.url)) {
                resp = idlixApi.revokeSession(req)
            }
            if (resp.isSuccessful) Result.success(Unit)
            else {
                val err = parseApiError(resp.errorBody()?.string())
                Result.failure(Exception(err?.error?.ifBlank { null } ?: "HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logoutOtherSessions(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var resp = idlixApi.logoutOtherSessions()
            if (resp.code() == 404 && migrateBaseUrlIfRedirected(resp.raw().request.url)) {
                resp = idlixApi.logoutOtherSessions()
            }
            if (resp.isSuccessful) Result.success(Unit)
            else {
                val err = parseApiError(resp.errorBody()?.string())
                Result.failure(Exception(err?.error?.ifBlank { null } ?: "HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun migrateBaseUrlIfRedirected(finalUrl: okhttp3.HttpUrl): Boolean {
        val currentBase = baseUrlProvider().toHttpUrlOrNull() ?: return false
        if (!currentBase.host.contains("idlix", ignoreCase = true) ||
            !finalUrl.host.contains("idlix", ignoreCase = true) ||
            currentBase.host.equals(finalUrl.host, ignoreCase = true)
        ) {
            return false
        }
        val defaultPort = if (finalUrl.scheme == "https") 443 else 80
        val portSuffix = if (finalUrl.port != defaultPort) ":${finalUrl.port}" else ""
        val resolvedBase = "${finalUrl.scheme}://${finalUrl.host}$portSuffix/"
        onBaseUrlResolved(resolvedBase)
        return true
    }

    fun clearLocalSession() {
        cookieJar.clearAuthCookies()
        AuthSessionStore.setGuest()
        onSessionChanged()
    }

    private fun parseApiError(rawBody: String?): ApiErrorResponse? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            ApiClient.json.decodeFromString<ApiErrorResponse>(rawBody)
        } catch (_: Exception) {
            null
        }
    }
}
