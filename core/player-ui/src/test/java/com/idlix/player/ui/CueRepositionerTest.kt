package com.idlix.player.ui

import androidx.media3.common.text.Cue
import com.idlix.data.model.PlayerAppearance
import com.idlix.data.model.SubtitleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueRepositionerTest {

    @Test
    fun testEmptyCuesReturnEmpty() {
        val result = CueRepositioner.reposition(emptyList(), PlayerAppearance())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDefaultCueIsRepositioned() {
        val cue = Cue.Builder().setText("Hello World").build()
        val app = PlayerAppearance(
            subtitlePosX = 0.50f,
            subtitlePosY = 0.85f,
            subtitleSize = SubtitleSize.MEDIUM
        )
        val result = CueRepositioner.reposition(listOf(cue), app)
        assertEquals(1, result.size)
        val rCue = result[0]
        assertEquals("Hello World", rCue.text.toString())
        assertEquals(0.50f, rCue.position, 0.001f)
        assertEquals(0.85f, rCue.line, 0.001f)
        assertEquals(Cue.ANCHOR_TYPE_MIDDLE, rCue.positionAnchor)
        assertEquals(Cue.ANCHOR_TYPE_END, rCue.lineAnchor)
    }

    @Test
    fun testMultipleDefaultCuesMerged() {
        val cue1 = Cue.Builder().setText("Line 1").build()
        val cue2 = Cue.Builder().setText("Line 2").build()
        val app = PlayerAppearance()
        val result = CueRepositioner.reposition(listOf(cue1, cue2), app)
        assertEquals(1, result.size)
        assertEquals("Line 1\nLine 2", result[0].text.toString())
    }

    @Test
    fun testLineNumberCueIsRepositionedToFraction() {
        val cue = Cue.Builder()
            .setText("VTT Line -1")
            .setLine(-1f, Cue.LINE_TYPE_NUMBER)
            .build()
        val app = PlayerAppearance(
            subtitlePosX = 0.70f,
            subtitlePosY = 0.75f,
            subtitleSize = SubtitleSize.MEDIUM
        )
        val result = CueRepositioner.reposition(listOf(cue), app)
        assertEquals(1, result.size)
        val rCue = result[0]
        assertEquals(0.70f, rCue.position, 0.001f)
        assertEquals(Cue.ANCHOR_TYPE_MIDDLE, rCue.positionAnchor)
        assertEquals(Cue.LINE_TYPE_FRACTION, rCue.lineType)
        assertEquals(0.75f, rCue.line, 0.001f)
        assertEquals(Cue.ANCHOR_TYPE_END, rCue.lineAnchor)
    }
}

