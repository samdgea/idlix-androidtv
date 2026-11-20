package com.idlix.data

import com.idlix.data.api.ApiClient
import com.idlix.data.api.IdlixApiService
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.local.PersistentCookieJar
import com.idlix.data.model.AuthLoginResult
import com.idlix.data.model.AuthMeResponse
import com.idlix.data.model.AuthSessionsResponse
import com.idlix.data.model.AuthState
import com.idlix.data.model.ClaimRequest
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.GenreListResponse
import com.idlix.data.model.IdlixUser
import com.idlix.data.model.LoginRequest
import com.idlix.data.model.LogoutRequest
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.RevokeSessionRequest
import com.idlix.data.model.SearchResponse
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesListResponse
import com.idlix.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthContractTest {

    private lateinit var cookieJar: PersistentCookieJar

    @Before
    fun setUp() {
        cookieJar = PersistentCookieJar()
        AuthSessionStore.setGuest()
    }

    @Test
    fun testLoginRequestOmitsNullReplaceExistingSessions() {
        val initial = ApiClient.json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(identifier = "user@example.com", password = "secret")
        )
        assertTrue(initial.contains(""""identifier":"user@example.com""""))
        assertFalse(initial.contains("replaceExistingSessions"))

        val retry = ApiClient.json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(
                identifier = "user@example.com",
                password = "secret",
                replaceExistingSessions = true
            )
        )
        assertTrue(retry.contains(""""replaceExistingSessions":true"""))
    }

    @Test
    fun testAuthMeDeserialization() {
        val json = """
        {
          "user": {
            "id": "usr_123",
            "username": "abdi",
            "email": "abdi@example.com",
            "displayName": "Abdi",
            "role": "user",
            "emailVerified": true,
            "subscription": {
              "tier": "plus",
              "status": "active"
            }
          }
        }
        """.trimIndent()
        val parsed = ApiClient.json.decodeFromString<AuthMeResponse>(json)
        assertEquals("usr_123", parsed.user?.id)
        assertEquals("Abdi", parsed.user?.effectiveName)
        assertTrue(parsed.user?.isSubscriberOrPremium == true)
    }

    @Test
    fun testPersistentCookieJarPreservesDidAndClearsAuthCookies() {
        val url = "https://z3.idlixku.com/api/auth/login".toHttpUrl()
        val didCookie = Cookie.Builder()
            .hostOnlyDomain("z3.idlixku.com")
            .path("/")
            .name("did")
            .value("device123")
            .secure()
            .build()
        val accessCookie = Cookie.Builder()
            .hostOnlyDomain("z3.idlixku.com")
            .path("/")
            .name("access_token")
            .value("jwt-abc")
            .httpOnly()
            .secure()
            .build()
        val signalCookie = Cookie.Builder()
            .hostOnlyDomain("z3.idlixku.com")
            .path("/")
            .name("has_session")
            .value("1")
            .secure()
            .build()

        cookieJar.saveFromResponse(url, listOf(didCookie, accessCookie, signalCookie))
        val loadedBefore = cookieJar.loadForRequest(url)
        assertEquals(3, loadedBefore.size)
        assertTrue(cookieJar.hasAuthCookies(url))

        cookieJar.clearAuthCookies()
        val loadedAfter = cookieJar.loadForRequest(url)
        assertEquals(1, loadedAfter.size)
        assertEquals("did", loadedAfter.first().name)
        assertFalse(cookieJar.hasAuthCookies(url))
    }

    @Test
    fun testLoginDeviceLimitReachedAndRetrySucceeds() = runBlocking {
        var calls = 0
        val capturedRequests = mutableListOf<LoginRequest>()

        val fakeApi = object : StubAuthApiService() {
            override suspend fun login(request: LoginRequest, fingerprint: String): Response<AuthMeResponse> {
                calls++
                capturedRequests.add(request)
                return if (request.replaceExistingSessions != true) {
                    Response.error(
                        409,
                        """{"error":"Device limit reached","code":"AUTH_DEVICE_LIMIT_REACHED","limit":3,"currentCount":3}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                } else {
                    Response.success(
                        AuthMeResponse(
                            user = IdlixUser(id = "u1", username = "tester", displayName = "Tester")
                        )
                    )
                }
            }
        }

        val repo = AuthRepository(
            idlixApiProvider = { fakeApi },
            fingerprintProvider = { "a".repeat(64) },
            cookieJar = cookieJar,
            baseUrlProvider = { "https://z3.idlixku.com/" }
        )

        val first = repo.login("tester", "pass123")
        assertTrue(first is AuthLoginResult.DeviceLimitReached)
        val limitReached = first as AuthLoginResult.DeviceLimitReached
        assertEquals(3, limitReached.limit)
        assertEquals(3, limitReached.currentCount)

        val second = repo.login("tester", "pass123", replaceExistingSessions = true)
        assertTrue(second is AuthLoginResult.Success)
        assertEquals(2, calls)
        assertEquals(null, capturedRequests[0].replaceExistingSessions)
        assertEquals(true, capturedRequests[1].replaceExistingSessions)
        assertTrue(AuthSessionStore.authState.value is AuthState.Authenticated)
    }

    @Test
    fun testBootstrapClearsSessionOn401() = runBlocking {
        AuthSessionStore.setAuthenticated(IdlixUser(id = "u1", username = "tester"))
        val fakeApi = object : StubAuthApiService() {
            override suspend fun getMe(): Response<AuthMeResponse> {
                return Response.error(
                    401,
                    """{"error":"Unauthorized"}""".toResponseBody("application/json".toMediaType())
                )
            }
        }

        val repo = AuthRepository(
            idlixApiProvider = { fakeApi },
            fingerprintProvider = { "a".repeat(64) },
            cookieJar = cookieJar,
            baseUrlProvider = { "https://z3.idlixku.com/" }
        )

        repo.bootstrapSession()
        assertTrue(AuthSessionStore.authState.value is AuthState.Guest)
    }

    @Test
    fun testLoginAutoMigratesRedirectedMirrorOriginAndRetries() = runBlocking {
        var currentBaseUrl = "https://z2.idlixku.com/"
        var loginAttempts = 0

        val fakeApi = object : StubAuthApiService() {
            override suspend fun login(request: LoginRequest, fingerprint: String): Response<AuthMeResponse> {
                loginAttempts++
                return if (currentBaseUrl.contains("z2.idlixku.com")) {
                    val raw = okhttp3.Response.Builder()
                        .request(
                            okhttp3.Request.Builder()
                                .url("https://z3.idlixku.com/api/auth/login")
                                .get()
                                .build()
                        )
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .build()
                    Response.error(
                        """{"error":"Not Found"}""".toResponseBody("application/json".toMediaType()),
                        raw
                    )
                } else {
                    Response.success(
                        AuthMeResponse(
                            user = IdlixUser(id = "u1", username = "tester", displayName = "Tester")
                        )
                    )
                }
            }
        }

        val repo = AuthRepository(
            idlixApiProvider = { fakeApi },
            fingerprintProvider = { "a".repeat(64) },
            cookieJar = cookieJar,
            baseUrlProvider = { currentBaseUrl },
            onBaseUrlResolved = { resolved -> currentBaseUrl = resolved }
        )

        val result = repo.login("tester", "pass123")
        assertTrue(result is AuthLoginResult.Success)
        assertEquals(2, loginAttempts)
        assertEquals("https://z3.idlixku.com/", currentBaseUrl)
    }

    private open class StubAuthApiService : IdlixApiService {
        override suspend fun getMovies(page: Int, limit: Int, sort: String?, genre: String?): MovieListResponse =
            throw NotImplementedError()
        override suspend fun getSeries(page: Int, limit: Int, sort: String?, genre: String?): SeriesListResponse =
            throw NotImplementedError()
        override suspend fun getMovieDetail(slug: String): MovieDetail = throw NotImplementedError()
        override suspend fun getSeriesDetail(slug: String): SeriesDetail = throw NotImplementedError()
        override suspend fun getEpisodeDetail(slug: String, season: Int, episode: Int): EpisodeResponse =
            throw NotImplementedError()
        override suspend fun getGenres(): GenreListResponse = throw NotImplementedError()
        override suspend fun search(query: String): SearchResponse = throw NotImplementedError()
        override suspend fun getMoviePlayInfo(movieId: String): Response<PlayInfoResponse> = throw NotImplementedError()
        override suspend fun getEpisodePlayInfo(episodeId: String): Response<PlayInfoResponse> = throw NotImplementedError()
        override suspend fun claimSession(url: String, request: ClaimRequest, origin: String, referer: String): Response<ClaimResponse> =
            throw NotImplementedError()
        override suspend fun resolvePlayback(contentType: String, contentId: String, contentTitle: String?): ResolvedPlayback =
            throw NotImplementedError()
        override suspend fun login(request: LoginRequest, fingerprint: String): Response<AuthMeResponse> =
            throw NotImplementedError()
        override suspend fun getMe(): Response<AuthMeResponse> = throw NotImplementedError()
        override suspend fun refreshSession(bootstrap: Int): Response<AuthMeResponse> = throw NotImplementedError()
        override suspend fun logout(request: LogoutRequest): Response<Unit> = throw NotImplementedError()
        override suspend fun getSessions(): Response<AuthSessionsResponse> = throw NotImplementedError()
        override suspend fun revokeSession(request: RevokeSessionRequest): Response<Unit> = throw NotImplementedError()
        override suspend fun logoutOtherSessions(): Response<Unit> = throw NotImplementedError()
    }
}
