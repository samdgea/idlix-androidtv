package com.idlix.data.api

import android.content.Context
import android.os.Build
import com.idlix.data.BuildConfig
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.local.PersistentCookieJar
import com.idlix.data.model.ApiErrorResponse
import com.idlix.data.model.MovieListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val PREF_KEY_SERVER_URL = "custom_backend_url"
    private const val PREF_KEY_DEVICE_ID = "device_session_id"
    private const val PREF_KEY_REFRESH_RECOVERY = "auth_refresh_recovery"
    private const val CLIENT_VERSION_HEADER_VALUE = "1.9.0"
    private const val DEFAULT_URL = BuildConfig.BACKEND_API_URL

    val cookieJar = PersistentCookieJar()
    private val refreshLock = Any()

    @Volatile
    var applicationContext: Context? = null
        internal set

    @Volatile
    private var userAgentHeader: String = "Idlix-Client/1.0"

    @Volatile
    private var cachedDeviceId: String? = null

    @Volatile
    private var cachedRefreshRecovery: String? = null

    fun init(context: Context, userAgent: String? = null) {
        val appContext = context.applicationContext
        this.applicationContext = appContext
        if (!userAgent.isNullOrBlank()) {
            this.userAgentHeader = userAgent
        }
        cookieJar.init(appContext)
        AuthSessionStore.init(appContext)
    }

    fun getDeviceId(): String {
        cachedDeviceId?.let { return it }
        val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
        val saved = prefs?.getString(PREF_KEY_DEVICE_ID, null)?.trim()
        if (!saved.isNullOrBlank()) {
            cachedDeviceId = saved
            return saved
        }
        val generated = java.util.UUID.randomUUID().toString().replace("-", "")
        cachedDeviceId = generated
        try {
            prefs?.edit()?.putString(PREF_KEY_DEVICE_ID, generated)?.apply()
        } catch (_: Exception) {}
        return generated
    }

    fun setDeviceId(newDeviceId: String) {
        val trimmed = newDeviceId.trim()
        if (trimmed.isNotBlank()) {
            cachedDeviceId = trimmed
            try {
                val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
                prefs?.edit()?.putString(PREF_KEY_DEVICE_ID, trimmed)?.apply()
            } catch (_: Exception) {}
        }
    }

    fun getClientFingerprint(): String {
        val brand = try { Build.BRAND.orEmpty() } catch (_: Throwable) { "" }
        val model = try { Build.MODEL.orEmpty() } catch (_: Throwable) { "" }
        val device = try { Build.DEVICE.orEmpty() } catch (_: Throwable) { "" }
        val raw = "${getDeviceId()}|$brand|$model|$device"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            getDeviceId().padEnd(64, '0').take(64)
        }
    }

    fun getRefreshRecoveryToken(): String {
        cachedRefreshRecovery?.let { return it }
        val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
        val saved = prefs?.getString(PREF_KEY_REFRESH_RECOVERY, null)?.trim()
        if (!saved.isNullOrBlank() && saved.length in 32..128) {
            cachedRefreshRecovery = saved
            return saved
        }
        val generated = java.util.UUID.randomUUID().toString().replace("-", "") +
            java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        cachedRefreshRecovery = generated
        try {
            prefs?.edit()?.putString(PREF_KEY_REFRESH_RECOVERY, generated)?.apply()
        } catch (_: Exception) {}
        return generated
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Volatile
    private var currentBaseUrl: String? = null

    fun getDefaultUrl(): String = if (DEFAULT_URL.endsWith("/")) DEFAULT_URL else "$DEFAULT_URL/"

    fun getOrigin(): String {
        return try {
            val httpUrl = getBaseUrl().toHttpUrl()
            val defaultPort = if (httpUrl.scheme == "https") 443 else 80
            val portSuffix = if (httpUrl.port != defaultPort) ":${httpUrl.port}" else ""
            "${httpUrl.scheme}://${httpUrl.host}$portSuffix"
        } catch (_: Exception) {
            "https://z3.idlixku.com"
        }
    }

    fun getBaseUrl(): String {
        currentBaseUrl?.let { return it }
        val saved = try {
            val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
            prefs?.getString(PREF_KEY_SERVER_URL, null)?.trim()
        } catch (_: Exception) {
            null
        }

        val url = if (!saved.isNullOrBlank()) {
            if (saved.endsWith("/")) saved else "$saved/"
        } else {
            getDefaultUrl()
        }
        currentBaseUrl = url
        return url
    }

    fun setBaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        currentBaseUrl = formatted
        try {
            val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
            prefs?.edit()?.putString(PREF_KEY_SERVER_URL, formatted)?.apply()
        } catch (_: Exception) {}

        synchronized(this) {
            _idlixApi = null
            _majorplayApi = null
        }
    }

    suspend fun testBackendConnection(targetUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL cannot be empty"))
        }
        val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val testEndpoint = "${formatted}api/movies?page=1&limit=1"
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(testEndpoint)
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val parsed = try {
                        json.decodeFromString<MovieListResponse>(body)
                    } catch (_: Exception) { null }
                    val count = parsed?.data?.size ?: 0
                    Result.success("Connected successfully (${duration}ms, $count items)")
                } else {
                    Result.failure(Exception("HTTP error: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val headerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val defaultPort = if (originalRequest.url.scheme == "https") 443 else 80
        val portSuffix = if (originalRequest.url.port != defaultPort) ":${originalRequest.url.port}" else ""
        val hostOrigin = "${originalRequest.url.scheme}://${originalRequest.url.host}$portSuffix"

        val builder = originalRequest.newBuilder()
            .header("Accept", "application/json, text/plain, */*")

        if (originalRequest.header("User-Agent") == null) {
            builder.header("User-Agent", userAgentHeader)
        }

        if (originalRequest.header("Referer") == null) {
            builder.header("Referer", "$hostOrigin/")
        }
        if (originalRequest.header("Origin") == null) {
            builder.header("Origin", hostOrigin)
        }

        val host = originalRequest.url.host
        val path = originalRequest.url.encodedPath
        if (path.contains("/api/auth/") && originalRequest.header("X-Client-Version") == null) {
            builder.header("X-Client-Version", CLIENT_VERSION_HEADER_VALUE)
        }
        if ((path.endsWith("/api/auth/login") || path.endsWith("/api/auth/register")) &&
            originalRequest.header("X-Client-Fp") == null
        ) {
            builder.header("X-Client-Fp", getClientFingerprint())
        }

        // Seed persistent device session cookie (did) into CookieJar so BridgeInterceptor merges it with auth cookies
        if (host.contains("idlix") || path.contains("/api/watch/") || path.contains("/api/playback/") || path.contains("/api/auth/")) {
            cookieJar.ensureCookieIfMissing(originalRequest.url, "did", getDeviceId())
        }

        val response = chain.proceed(builder.build())

        // Synchronize did if server provides a new Set-Cookie: did=...
        val setCookieHeaders = response.headers("Set-Cookie")
        for (header in setCookieHeaders) {
            val match = """did=([a-fA-F0-9]{20,64})""".toRegex().find(header)
            if (match != null) {
                val serverDid = match.groupValues[1]
                if (serverDid.isNotBlank()) {
                    setDeviceId(serverDid)
                    cookieJar.upsertCookie(originalRequest.url, "did", serverDid)
                }
            }
        }

        response
    }

    private val authRefreshHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dynamicDns)
            .cookieJar(cookieJar)
            .addInterceptor(headerInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        val req = response.request
        val path = req.url.encodedPath
        if (req.header("X-Auth-Retried") == "1" ||
            path.endsWith("/api/auth/login") ||
            path.endsWith("/api/auth/register") ||
            path.endsWith("/api/auth/refresh") ||
            path.endsWith("/api/auth/logout")
        ) {
            return@Authenticator null
        }

        if (!cookieJar.hasAuthCookies(req.url) && !AuthSessionStore.isAuthenticated()) {
            return@Authenticator null
        }

        val beforeSnapshot = cookieJar.cookieSnapshotForUrl(req.url)
        synchronized(refreshLock) {
            val currentSnapshot = cookieJar.cookieSnapshotForUrl(req.url)
            if (currentSnapshot != beforeSnapshot && cookieJar.hasAuthCookies(req.url)) {
                return@synchronized req.newBuilder()
                    .header("X-Auth-Retried", "1")
                    .build()
            }

            val defaultPort = if (req.url.scheme == "https") 443 else 80
            val portSuffix = if (req.url.port != defaultPort) ":${req.url.port}" else ""
            val targetOrigin = "${req.url.scheme}://${req.url.host}$portSuffix"
            val refreshUrl = "$targetOrigin/api/auth/refresh?bootstrap=0"

            val refreshReq = Request.Builder()
                .url(refreshUrl)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .header("X-Client-Version", CLIENT_VERSION_HEADER_VALUE)
                .header("X-Refresh-Recovery", getRefreshRecoveryToken())
                .build()

            try {
                authRefreshHttpClient.newCall(refreshReq).execute().use { refreshResp ->
                    val rawBody = refreshResp.body?.string().orEmpty()
                    if (refreshResp.isSuccessful) {
                        return@synchronized req.newBuilder()
                            .header("X-Auth-Retried", "1")
                            .build()
                    }
                    val err = try {
                        json.decodeFromString<ApiErrorResponse>(rawBody)
                    } catch (_: Exception) {
                        null
                    }
                    if (refreshResp.code == 409 && err?.code == "REFRESH_RACE") {
                        try {
                            Thread.sleep(150L)
                        } catch (_: InterruptedException) {}
                        val meReq = Request.Builder()
                            .url("$targetOrigin/api/auth/me")
                            .get()
                            .header("X-Client-Version", CLIENT_VERSION_HEADER_VALUE)
                            .build()
                        authRefreshHttpClient.newCall(meReq).execute().use { meResp ->
                            if (meResp.isSuccessful) {
                                return@synchronized req.newBuilder()
                                    .header("X-Auth-Retried", "1")
                                    .build()
                            }
                        }
                    }
                    if (refreshResp.code == 401) {
                        cookieJar.clearAuthCookies()
                        AuthSessionStore.setGuest()
                    }
                }
            } catch (_: Exception) {
            }
            null
        }
    }

    const val DNS_PROVIDER_GOOGLE = "google"
    const val DNS_PROVIDER_CLOUDFLARE = "cloudflare"
    const val DNS_PROVIDER_ADGUARD = "adguard"
    const val DNS_PROVIDER_SYSTEM = "system"

    private const val PREF_KEY_DOH_PROVIDER = "custom_doh_provider"
    const val DEFAULT_DNS_PROVIDER = DNS_PROVIDER_GOOGLE

    @Volatile
    private var currentDnsProvider: String? = null

    fun getDnsProvider(): String {
        currentDnsProvider?.let { return it }
        val saved = try {
            val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
            prefs?.getString(PREF_KEY_DOH_PROVIDER, null)?.trim()
        } catch (_: Exception) {
            null
        }
        val provider = if (!saved.isNullOrBlank()) saved else DEFAULT_DNS_PROVIDER
        currentDnsProvider = provider
        return provider
    }

    fun setDnsProvider(newProvider: String) {
        val trimmed = newProvider.trim().lowercase()
        currentDnsProvider = trimmed
        try {
            val prefs = applicationContext?.getSharedPreferences("idlix_config", Context.MODE_PRIVATE)
            prefs?.edit()?.putString(PREF_KEY_DOH_PROVIDER, trimmed)?.apply()
        } catch (_: Exception) {}
        clearDnsCache()
    }

    private data class CachedDnsRecord(
        val addresses: List<InetAddress>,
        val expiresAt: Long
    )

    private val dnsCache = ConcurrentHashMap<String, CachedDnsRecord>()
    private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    private const val CIRCUIT_BREAKER_COOLDOWN_MS = 2 * 60 * 1000L // 2 minutes

    @Volatile
    private var circuitBreakerTrippedUntil: Long = 0L

    @Volatile
    private var consecutiveFailures: Int = 0

    fun clearDnsCache() {
        dnsCache.clear()
        circuitBreakerTrippedUntil = 0L
        consecutiveFailures = 0
    }

    private val bootstrapClient: OkHttpClient by lazy {
        val appCache = applicationContext?.let { ctx ->
            try {
                Cache(File(ctx.cacheDir, "doh_cache"), 10L * 1024 * 1024)
            } catch (_: Exception) {
                null
            }
        }

        OkHttpClient.Builder()
            .apply {
                if (appCache != null) cache(appCache)
            }
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .writeTimeout(2500, TimeUnit.MILLISECONDS)
            .callTimeout(3000, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildDnsOverHttps(url: String, bootstrapIps: List<String>): DnsOverHttps {
        val hosts = bootstrapIps.mapNotNull { ip ->
            try {
                InetAddress.getByName(ip)
            } catch (_: Exception) {
                null
            }
        }
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(hosts)
            .includeIPv6(false)
            .build()
    }

    private val googleDoh: DnsOverHttps by lazy {
        buildDnsOverHttps(
            url = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
        )
    }

    private val cloudflareDoh: DnsOverHttps by lazy {
        buildDnsOverHttps(
            url = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        )
    }

    private val adguardDoh: DnsOverHttps by lazy {
        buildDnsOverHttps(
            url = "https://dns.adguard-dns.com/dns-query",
            bootstrapIps = listOf("94.140.14.14", "94.140.15.15")
        )
    }

    private fun getDohInstance(provider: String): DnsOverHttps? {
        return when (provider.lowercase()) {
            DNS_PROVIDER_GOOGLE -> googleDoh
            DNS_PROVIDER_CLOUDFLARE -> cloudflareDoh
            DNS_PROVIDER_ADGUARD -> adguardDoh
            else -> null
        }
    }

    private fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> {
        val (ipv4, ipv6) = addresses.partition { it is Inet4Address }
        return if (ipv4.isNotEmpty()) ipv4 + ipv6 else addresses
    }

    private val dynamicDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()

            // In-memory DNS cache check
            val cached = dnsCache[hostname]
            if (cached != null && now < cached.expiresAt) {
                return cached.addresses
            }

            val provider = getDnsProvider()

            // If user explicitly configured System DNS (DoH Off)
            if (provider == DNS_PROVIDER_SYSTEM) {
                val resolved = prioritizeIpv4(Dns.SYSTEM.lookup(hostname))
                dnsCache[hostname] = CachedDnsRecord(resolved, now + CACHE_TTL_MS)
                return resolved
            }

            // Fallback to system DNS when DoH circuit breaker is tripped
            if (now < circuitBreakerTrippedUntil) {
                return try {
                    val resolved = prioritizeIpv4(Dns.SYSTEM.lookup(hostname))
                    dnsCache[hostname] = CachedDnsRecord(resolved, now + CACHE_TTL_MS)
                    resolved
                } catch (e: Exception) {
                    throw UnknownHostException("System DNS resolution failed for $hostname: ${e.message}")
                }
            }

            // Resolve via configured DoH provider
            val doh = getDohInstance(provider)
            if (doh != null) {
                try {
                    val results = doh.lookup(hostname)
                    if (results.isNotEmpty()) {
                        consecutiveFailures = 0
                        val prioritized = prioritizeIpv4(results)
                        dnsCache[hostname] = CachedDnsRecord(prioritized, now + CACHE_TTL_MS)
                        return prioritized
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        circuitBreakerTrippedUntil = now + CIRCUIT_BREAKER_COOLDOWN_MS
                    }
                    // Fallback to system DNS
                    return try {
                        val resolved = prioritizeIpv4(Dns.SYSTEM.lookup(hostname))
                        dnsCache[hostname] = CachedDnsRecord(resolved, now + CACHE_TTL_MS)
                        resolved
                    } catch (sysEx: Exception) {
                        throw UnknownHostException("DNS resolution failed for $hostname: DoH failed (${e.message}) / System failed (${sysEx.message})")
                    }
                }
            }

            // Fallback for unknown provider
            val resolved = prioritizeIpv4(Dns.SYSTEM.lookup(hostname))
            dnsCache[hostname] = CachedDnsRecord(resolved, now + CACHE_TTL_MS)
            return resolved
        }
    }

    suspend fun testDns(provider: String = getDnsProvider()): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val testHost = try {
            getBaseUrl().toHttpUrl().host
        } catch (_: Exception) {
            "z3.idlixku.com"
        }
        try {
            val addresses = when (provider.lowercase()) {
                DNS_PROVIDER_SYSTEM -> Dns.SYSTEM.lookup(testHost)
                else -> {
                    val doh = getDohInstance(provider)
                        ?: throw IllegalStateException("Unknown DNS provider: $provider")
                    doh.lookup(testHost)
                }
            }
            val duration = System.currentTimeMillis() - start
            if (addresses.isEmpty()) {
                Result.failure(Exception("No IP address returned"))
            } else {
                val ipString = addresses.first().hostAddress
                Result.success("Resolved in ${duration}ms ($ipString)")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dynamicDns)
            .cookieJar(cookieJar)
            .authenticator(tokenAuthenticator)
            .addInterceptor(headerInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val updateHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dynamicDns)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", userAgentHeader)
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Volatile
    private var _idlixApi: IdlixApiService? = null
    val idlixApi: IdlixApiService
        get() = _idlixApi ?: synchronized(this) {
            _idlixApi ?: buildRetrofit(getBaseUrl()).create(IdlixApiService::class.java).also { _idlixApi = it }
        }

    @Volatile
    private var _majorplayApi: MajorplayApiService? = null
    val majorplayApi: MajorplayApiService
        get() = _majorplayApi ?: synchronized(this) {
            _majorplayApi ?: buildRetrofit(getBaseUrl()).create(MajorplayApiService::class.java).also { _majorplayApi = it }
        }
}
