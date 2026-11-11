package com.idlix.data

import com.idlix.data.model.SubdlQuery
import com.idlix.data.repository.SubdlRepository
import com.idlix.data.util.SubdlTitleCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubdlSearchTest {

    @Test
    fun testTitleCleanerRemovesYearAndTags() {
        val r1 = SubdlTitleCleaner.clean("Avatar: The Way of Water (2022)")
        assertEquals("Avatar: The Way of Water", r1.title)
        assertEquals(2022, r1.year)

        val r2 = SubdlTitleCleaner.clean("Loki Season 2 (2023) Sub Indo")
        assertEquals("Loki", r2.title)
        assertEquals(2023, r2.year)

        val r3 = SubdlTitleCleaner.clean("Breaking Bad S01E02 [1080p]")
        assertEquals("Breaking Bad", r3.title)
        assertNull(r3.year)

        val r4 = SubdlTitleCleaner.clean("Interstellar 2014")
        assertEquals("Interstellar", r4.title)
        assertEquals(2014, r4.year)

        val r5 = SubdlTitleCleaner.clean("Game of Thrones - Episode 5")
        assertEquals("Game of Thrones", r5.title)
        assertNull(r5.year)
    }

    @Test
    fun testBuildSearchUrlParameters() {
        val q1 = SubdlQuery(filmName = "Avatar", year = 2009, type = "movie")
        val url1 = SubdlRepository.buildSearchUrl(q1, "test_api_key")
        assertTrue(url1.contains("api_key=test_api_key"))
        assertTrue(url1.contains("film_name=Avatar"))
        assertTrue(url1.contains("year=2009"))
        assertTrue(url1.contains("type=movie"))
        assertTrue(url1.contains("languages=ID,EN"))

        val q2 = SubdlQuery(sdId = 12345L, type = "tv", season = 2, episode = 4)
        val url2 = SubdlRepository.buildSearchUrl(q2, "my_key")
        assertTrue(url2.contains("sd_id=12345"))
        assertTrue(url2.contains("type=tv"))
        assertTrue(url2.contains("season_number=2"))
        assertTrue(url2.contains("episode_number=4"))
    }

    @Test
    fun testBuildRedeemCandidateUrls() {
        val repo = com.idlix.data.repository.IdlixRepository()
        val urls1 = repo.buildRedeemCandidateUrls("/watch/session/redeem", "https://z2.idlixku.com/")
        assertEquals("https://z2.idlixku.com/api/watch/session/redeem", urls1.first())

        val urls2 = repo.buildRedeemCandidateUrls("/api/watch/session/redeem", "https://z2.idlixku.com/")
        assertEquals("https://z2.idlixku.com/api/watch/session/redeem", urls2.first())

        val urls3 = repo.buildRedeemCandidateUrls("", "https://z2.idlixku.com/")
        assertEquals("https://z2.idlixku.com/api/watch/session/redeem", urls3.first())
    }
}

