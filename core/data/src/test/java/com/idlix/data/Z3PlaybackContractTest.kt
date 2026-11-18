package com.idlix.data

import com.idlix.data.api.ApiClient
import com.idlix.data.api.IdlixApiService
import com.idlix.data.api.MajorplayApiService
import com.idlix.data.model.ApiErrorResponse
import com.idlix.data.model.ClaimRequest
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.GenreListResponse
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.PlaybackVerificationChallenge
import com.idlix.data.model.RedeemRequest
import com.idlix.data.model.RedeemResponse
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SearchResponse
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesListResponse
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class Z3PlaybackContractTest {

    @Test
    fun testPlayInfoVerificationAndPrerollDeserialization() {
        val json = """
        {
          "kind": "gate",
          "gateToken": "eyJ2IjoyfQ.sig",
          "serverNow": 1795011369043,
          "unlockAt": 1795011384043,
          "viewerTier": "guest",
          "maxHeight": 720,
          "preroll": {
            "ad": {"id": "ad1", "name": "Banner"},
            "countdownSec": 7
          },
          "verification": {
            "siteKey": "0x4AAAAAAB4OxS-c9fV6wteq",
            "action": "play"
          }
        }
        """.trimIndent()

        val parsed = ApiClient.json.decodeFromString<PlayInfoResponse>(json)
        assertEquals("gate", parsed.kind)
        assertEquals("eyJ2IjoyfQ.sig", parsed.gateToken)
        assertEquals(7, parsed.preroll?.countdownSec)
        assertNotNull(parsed.verification)
        assertEquals("0x4AAAAAAB4OxS-c9fV6wteq", parsed.verification?.siteKey)
        assertEquals("play", parsed.verification?.action)
    }

    @Test
    fun testClaimRequestOmitsNullVerificationToken() {
        val withoutToken = ApiClient.json.encodeToString(
            ClaimRequest.serializer(),
            ClaimRequest(gateToken = "gate-123")
        )
        assertTrue(withoutToken.contains(""""gateToken":"gate-123""""))
        assertFalse(withoutToken.contains("verificationToken"))

        val withToken = ApiClient.json.encodeToString(
            ClaimRequest.serializer(),
            ClaimRequest(gateToken = "gate-123", verificationToken = "turnstile-tok")
        )
        assertTrue(withToken.contains(""""verificationToken":"turnstile-tok""""))
    }

    @Test
    fun testPendingClaimAndVerificationErrorDeserialization() {
        val pendingJson = """
        {
          "kind": "pending",
          "serverNow": 1795010748546,
          "unlockAt": 1795010762739,
          "remainingMs": 450
        }
        """.trimIndent()
        val pending = ApiClient.json.decodeFromString<ClaimResponse>(pendingJson)
        assertEquals("pending", pending.kind)
        assertEquals(450L, pending.remainingMs)

        val errJson = """
        {
          "error": "Playback verification required",
          "code": "VERIFICATION_REQUIRED"
        }
        """.trimIndent()
        val err = ApiClient.json.decodeFromString<ApiErrorResponse>(errJson)
        assertEquals("VERIFICATION_REQUIRED", err.code)
        assertEquals("Playback verification required", err.error)
    }

    @Test
    fun testResolveStreamUsesResolvedZ3OriginAndVerificationToken() = runBlocking {
        var capturedClaimUrl: String? = null
        var capturedClaimOrigin: String? = null
        var capturedClaimToken: String? = null
        var capturedRedeemOrigin: String? = null
        var claimCalls = 0

        val fakeIdlixApi = object : FakeIdlixApiService() {
            override suspend fun getMoviePlayInfo(movieId: String): Response<PlayInfoResponse> {
                val body = ApiClient.json.decodeFromString<PlayInfoResponse>(
                    """
                    {
                      "kind": "gate",
                      "gateToken": "eyJ2IjoyfQ.sig",
                      "serverNow": 1000,
                      "unlockAt": 1000,
                      "verification": {
                        "siteKey": "0x4AAAAAAB4OxS-c9fV6wteq",
                        "action": "play"
                      }
                    }
                    """.trimIndent()
                )
                val raw = okhttp3.Response.Builder()
                    .request(Request.Builder().url("https://z3.idlixku.com/api/watch/play-info/movie/$movieId").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build()
                return Response.success(body, raw)
            }

            override suspend fun claimSession(
                url: String,
                request: ClaimRequest,
                cookie: String?,
                origin: String,
                referer: String
            ): Response<ClaimResponse> {
                claimCalls++
                capturedClaimUrl = url
                capturedClaimOrigin = origin
                capturedClaimToken = request.verificationToken
                if (claimCalls == 1) {
                    return Response.success(
                        ClaimResponse(kind = "pending", remainingMs = 100L)
                    )
                }
                return Response.success(
                    ClaimResponse(
                        kind = "pentos",
                        claim = "claim-jwt",
                        redeemUrl = "/api/watch/session/redeem",
                        title = "Test Movie",
                        videoId = "vid-1",
                        durationSec = 1200L
                    )
                )
            }
        }

        val fakeMajorplayApi = object : MajorplayApiService {
            override suspend fun redeemPlay(
                url: String,
                cookie: String?,
                referer: String,
                origin: String,
                request: RedeemRequest
            ): RedeemResponse {
                capturedRedeemOrigin = origin
                return RedeemResponse(
                    code = "ok",
                    url = "https://e2e.majorplay.net/v/master.m3u8",
                    subtitles = listOf(SubtitleTrack(lang = "id", label = "Indonesian", path = "https://e2e.majorplay.net/sub.vtt"))
                )
            }
        }

        val repo = IdlixRepository(fakeIdlixApi, fakeMajorplayApi)
        var capturedChallenge: PlaybackVerificationChallenge? = null
        val result = repo.resolveStream(
            contentType = "movie",
            contentId = "movie-z3-1",
            contentTitle = "Test Movie",
            onCountdown = {},
            onVerificationRequired = { challenge ->
                capturedChallenge = challenge
                "turnstile-token-ok"
            }
        )

        assertTrue(result.isSuccess)
        assertEquals("https://z3.idlixku.com/", capturedChallenge?.originUrl)
        assertEquals("0x4AAAAAAB4OxS-c9fV6wteq", capturedChallenge?.siteKey)
        assertEquals("https://z3.idlixku.com/api/watch/session/claim", capturedClaimUrl)
        assertEquals("https://z3.idlixku.com", capturedClaimOrigin)
        assertEquals("turnstile-token-ok", capturedClaimToken)
        assertEquals("https://z3.idlixku.com", capturedRedeemOrigin)
        assertEquals("https://e2e.majorplay.net/v/master.m3u8", result.getOrNull()?.hlsMasterUrl)
    }

    @Test
    fun testClaim403VerificationRequiredFailsImmediatelyWithoutRetryLoop() = runBlocking {
        var claimCalls = 0
        val fakeIdlixApi = object : FakeIdlixApiService() {
            override suspend fun getMoviePlayInfo(movieId: String): Response<PlayInfoResponse> {
                val body = PlayInfoResponse(
                    kind = "gate",
                    gateToken = "eyJ2IjoyfQ.sig",
                    serverNow = 1000,
                    unlockAt = 1000
                )
                val raw = okhttp3.Response.Builder()
                    .request(Request.Builder().url("https://z3.idlixku.com/api/watch/play-info/movie/$movieId").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build()
                return Response.success(body, raw)
            }

            override suspend fun claimSession(
                url: String,
                request: ClaimRequest,
                cookie: String?,
                origin: String,
                referer: String
            ): Response<ClaimResponse> {
                claimCalls++
                return Response.error(
                    403,
                    """{"error":"Playback verification required","code":"VERIFICATION_REQUIRED"}"""
                        .toResponseBody("application/json".toMediaType())
                )
            }
        }

        val fakeMajorplayApi = object : MajorplayApiService {
            override suspend fun redeemPlay(
                url: String,
                cookie: String?,
                referer: String,
                origin: String,
                request: RedeemRequest
            ): RedeemResponse = RedeemResponse()
        }

        val repo = IdlixRepository(fakeIdlixApi, fakeMajorplayApi)
        val result = repo.resolveStream(
            contentType = "movie",
            contentId = "movie-z3-403",
            contentTitle = "Test Movie",
            onCountdown = {},
            onVerificationRequired = null
        )

        assertTrue(result.isFailure)
        assertEquals(1, claimCalls)
        assertEquals("Playback verification required", result.exceptionOrNull()?.message)
    }

    private open class FakeIdlixApiService : IdlixApiService {
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
        override suspend fun getMoviePlayInfo(movieId: String): Response<PlayInfoResponse> =
            throw NotImplementedError()

        override suspend fun getEpisodePlayInfo(episodeId: String): Response<PlayInfoResponse> =
            throw NotImplementedError()

        override suspend fun claimSession(
            url: String,
            request: ClaimRequest,
            cookie: String?,
            origin: String,
            referer: String
        ): Response<ClaimResponse> = throw NotImplementedError()

        override suspend fun resolvePlayback(
            contentType: String,
            contentId: String,
            contentTitle: String?
        ): ResolvedPlayback = throw NotImplementedError()
    }
}
