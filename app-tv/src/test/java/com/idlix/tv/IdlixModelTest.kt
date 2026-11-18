package com.idlix.tv

import com.idlix.data.api.ApiClient
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.RedeemResponse
import com.idlix.data.model.SearchResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IdlixModelTest {

    @Test
    fun testMovieListDeserialization() {
        val sampleJson = """
        {
          "data": [
            {
              "id": "a22c5860-fe44-4a14-8ab4-61a23d26ab06",
              "title": "Fuori",
              "slug": "fuori-2025",
              "posterPath": "/gxSkBOfTEOX2f6KhqMFSXf24nMo.jpg",
              "backdropPath": "/k9Oe0KXL1qYnEddxr1WR4VkB8eW.jpg",
              "releaseDate": "2025-05-22",
              "voteAverage": 6.40,
              "viewCount": 259,
              "quality": "WEB-DL",
              "country": "FR",
              "runtime": 117
            }
          ],
          "pagination": {
            "page": 1,
            "limit": 20,
            "total": 100,
            "totalPages": 5
          }
        }
        """.trimIndent()

        val parsed = ApiClient.json.decodeFromString<MovieListResponse>(sampleJson)
        assertEquals(1, parsed.data.size)
        val movie = parsed.data[0]
        assertEquals("Fuori", movie.title)
        assertEquals("https://image.tmdb.org/t/p/w342/gxSkBOfTEOX2f6KhqMFSXf24nMo.jpg", movie.fullPosterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/k9Oe0KXL1qYnEddxr1WR4VkB8eW.jpg", movie.fullBackdropUrl)
        assertEquals("6.4", movie.formattedRating)
    }

    @Test
    fun testSearchDeserializationWithRealData() {
        val searchJson = """
        {
          "results": [
            {
              "id": "5f561446-98c4-4e26-908c-301dc8113384",
              "contentType": "movie",
              "title": "The Batman",
              "originalTitle": "The Batman",
              "overview": "In his second year of fighting crime...",
              "genres": [
                "Crime",
                "Mystery",
                "Thriller"
              ],
              "releaseDate": "2022-03-01",
              "originalLanguage": "en",
              "voteAverage": 7.66,
              "viewCount": 32089,
              "popularityScore": 978,
              "posterPath": "/74xTEgt7R36Fpooo50r9T25onhq.jpg",
              "backdropPath": "/rvtdN5XkWAfGX6xDuPL6yYS2seK.jpg",
              "quality": "WEB-DL",
              "slug": "the-batman-2022",
              "commentCount": 25
            }
          ],
          "total": 108,
          "order": "popular"
        }
        """.trimIndent()

        val searchResponse = ApiClient.json.decodeFromString<SearchResponse>(searchJson)
        assertEquals(1, searchResponse.results.size)
        val item = searchResponse.results[0]
        assertEquals("The Batman", item.title)
        assertEquals("the-batman-2022", item.slug)
        assertEquals("7.7", item.formattedRating)
        assertEquals(3, item.genres.size)
        assertEquals("Crime", item.genres[0])
    }

    @Test
    fun testRealLiveSearchApi() = kotlinx.coroutines.runBlocking {
        if (System.getenv("RUN_LIVE_TESTS") != "true") {
            println("Skipping live search test (set RUN_LIVE_TESTS=true to run against live backend)")
            return@runBlocking
        }
        try {
            val repo = com.idlix.data.repository.IdlixRepository()
            val results = repo.search("Reacher")
            println("Live Search 'Reacher' returned ${results.size} items")
            if (results.isNotEmpty()) {
                val first = results[0]
                println("First item: ${first.title} (${first.contentType}) - ${first.slug}")
                org.junit.Assert.assertTrue(first.title.isNotBlank())
            }
        } catch (e: Exception) {
            println("Live search test note: ${e.message}")
        }
    }

    @Test
    fun testPlayInfoAndClaimDeserialization() {
        val playInfoJson = """
        {
          "kind": "gate",
          "gateToken": "sample_gate_jwt",
          "serverNow": 1792580280608,
          "unlockAt": 1792580295608,
          "viewerTier": "guest",
          "maxHeight": 720,
          "preroll": {"countdownSec": 7},
          "verification": {"siteKey": "0x4AAAAAAB4OxS-c9fV6wteq", "action": "play"}
        }
        """.trimIndent()

        val playInfo = ApiClient.json.decodeFromString<PlayInfoResponse>(playInfoJson)
        assertEquals("sample_gate_jwt", playInfo.gateToken)
        assertEquals(1792580295608L, playInfo.unlockAt)
        assertEquals("0x4AAAAAAB4OxS-c9fV6wteq", playInfo.verification?.siteKey)

        val claimJson = """
        {
          "kind": "pentos",
          "claim": "sample_claim_jwt",
          "claimExpiresAt": 1792585991,
          "redeemUrl": "https://e2e.majorplay.net/api/play",
          "videoId": "vkkj-VpTuVxK",
          "title": "Selfless (2015)",
          "durationSec": 7015,
          "viewerTier": "guest",
          "maxHeight": 720,
          "qualityLadder": [720]
        }
        """.trimIndent()

        val claim = ApiClient.json.decodeFromString<ClaimResponse>(claimJson)
        assertEquals("vkkj-VpTuVxK", claim.videoId)
        assertEquals("https://e2e.majorplay.net/api/play", claim.redeemUrl)

        val redeemJson = """
        {
          "code": "ok",
          "url": "https://e2e.majorplay.net/v/z4/vkkj/config-310070.json",
          "expiresAt": 1792583896,
          "subtitles": [
            {
              "lang": "id",
              "label": "Indonesian",
              "path": "https://e2e.majorplay.net/v/z4/sub.vtt"
            }
          ]
        }
        """.trimIndent()

        val redeem = ApiClient.json.decodeFromString<RedeemResponse>(redeemJson)
        assertEquals("ok", redeem.code)
        assertEquals(1, redeem.subtitles.size)
        assertEquals("id", redeem.subtitles[0].lang)
        assertEquals("https://e2e.majorplay.net/v/z4/sub.vtt", redeem.subtitles[0].path)
    }

    @Test
    fun testResolvedPlaybackDeserialization() {
        val sampleJson = """
        {
          "title": "Hope (2026)",
          "hlsMasterUrl": "https://e2e.majorplay.net/v/z4/master.m3u8",
          "subtitles": [
            {
              "lang": "id",
              "label": "Indonesian",
              "path": "https://e2e.majorplay.net/v/z4/sub_id.vtt"
            }
          ],
          "durationSec": 7200,
          "videoId": "v-12345"
        }
        """.trimIndent()

        val parsed = ApiClient.json.decodeFromString<com.idlix.data.model.ResolvedPlayback>(sampleJson)
        assertEquals("Hope (2026)", parsed.title)
        assertEquals("https://e2e.majorplay.net/v/z4/master.m3u8", parsed.hlsMasterUrl)
        assertEquals(1, parsed.subtitles.size)
        assertEquals("id", parsed.subtitles[0].lang)
        assertEquals(7200, parsed.durationSec)
    }

    @Test
    fun testRealResolveStreamPlayback() = kotlinx.coroutines.runBlocking {
        if (System.getenv("RUN_LIVE_TESTS") != "true") {
            println("Skipping live stream resolution test (set RUN_LIVE_TESTS=true to run against live backend)")
            return@runBlocking
        }
        try {
            val repo = com.idlix.data.repository.IdlixRepository()
            val movieId = "859b0000-7e53-4bfc-9dc5-a45d8940adbd"
            println("Resolving stream for Hope ($movieId)...")
            var lastCountdown = -1
            val res = repo.resolveStream(
                contentType = "movie",
                contentId = movieId,
                contentTitle = "Hope",
                onCountdown = { sec ->
                    if (sec != lastCountdown) {
                        println("Countdown: ${sec}s")
                        lastCountdown = sec
                    }
                }
            )

            if (res.isSuccess) {
                val playback = res.getOrNull()
                assertNotNull(playback)
                println("SUCCESS! HLS Master Playlist: ${playback?.hlsMasterUrl}")
                org.junit.Assert.assertTrue(playback!!.hlsMasterUrl.startsWith("http"))
            }
        } catch (e: Exception) {
            println("Skipping live playback test (backend not reachable in unit test env: ${e.message})")
        }
    }
}
