package com.idlix.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    @Test
    fun `selectBestVariant picks highest resolution and appends tokenQuery`() {
        val masterUrl = "https://cdn.example.com/hls/movie123/master.m3u8?t=secret_token&e=999999"
        val masterContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
            480p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            720p/index.m3u8
        """.trimIndent()

        val selection = HlsPlaylistParser.selectBestVariant(masterContent, masterUrl)

        assertEquals("1080p", selection.qualityLabel)
        assertEquals(1080, selection.height)
        assertEquals(
            "https://cdn.example.com/hls/movie123/1080p/index.m3u8?t=secret_token&e=999999",
            selection.videoPlaylistUrl
        )
        assertNull(selection.audioPlaylistUrl)
    }

    @Test
    fun `selectBestVariant resolves separate audio rendition when present`() {
        val masterUrl = "https://cdn.example.com/hls/ep1/master.m3u8?t=tok123"
        val masterContent = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud-main",NAME="English",DEFAULT=YES,URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,AUDIO="aud-main"
            video/720p.m3u8
        """.trimIndent()

        val selection = HlsPlaylistParser.selectBestVariant(masterContent, masterUrl)

        assertEquals("720p", selection.qualityLabel)
        assertEquals("https://cdn.example.com/hls/ep1/video/720p.m3u8?t=tok123", selection.videoPlaylistUrl)
        assertNotNull(selection.audioPlaylistUrl)
        assertEquals("https://cdn.example.com/hls/ep1/audio/en.m3u8?t=tok123", selection.audioPlaylistUrl)
    }

    @Test
    fun `parseMediaPlaylist rewrites init map, key, and segments to local paths`() {
        val playlistUrl = "https://cdn.example.com/hls/movie123/1080p/index.m3u8?t=secret_token"
        val mediaContent = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=AES-128,URI="../key.bin"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.000,
            seg-001.m4s
            #EXTINF:6.000,
            seg-002.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parseMediaPlaylist(
            playlistContent = mediaContent,
            playlistUrl = playlistUrl,
            filePrefix = "v"
        )

        assertEquals(4, parsed.segments.size)
        assertEquals(
            "https://cdn.example.com/hls/movie123/key.bin?t=secret_token",
            parsed.segments[0].remoteUrl
        )
        assertEquals("segments/v_key_0.bin", parsed.segments[0].localRelativePath)

        assertEquals(
            "https://cdn.example.com/hls/movie123/1080p/init.mp4?t=secret_token",
            parsed.segments[1].remoteUrl
        )
        assertEquals("segments/v_init_0.mp4", parsed.segments[1].localRelativePath)

        assertEquals(
            "https://cdn.example.com/hls/movie123/1080p/seg-001.m4s?t=secret_token",
            parsed.segments[2].remoteUrl
        )
        assertEquals("segments/v_00000.m4s", parsed.segments[2].localRelativePath)

        assertEquals(
            "https://cdn.example.com/hls/movie123/1080p/seg-002.m4s?t=secret_token",
            parsed.segments[3].remoteUrl
        )
        assertEquals("segments/v_00001.m4s", parsed.segments[3].localRelativePath)

        assertTrue(parsed.rewrittenPlaylistContent.contains("""URI="segments/v_key_0.bin""""))
        assertTrue(parsed.rewrittenPlaylistContent.contains("""URI="segments/v_init_0.mp4""""))
        assertTrue(parsed.rewrittenPlaylistContent.contains("segments/v_00000.m4s"))
        assertTrue(parsed.rewrittenPlaylistContent.contains("segments/v_00001.m4s"))
        assertTrue(parsed.rewrittenPlaylistContent.contains("#EXT-X-ENDLIST"))
    }
}
