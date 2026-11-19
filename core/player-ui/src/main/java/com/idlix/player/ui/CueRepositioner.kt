package com.idlix.player.ui

import android.text.Layout
import androidx.media3.common.text.Cue
import com.idlix.data.model.PlayerAppearance

object CueRepositioner {

    fun reposition(
        cues: List<Cue>,
        appearance: PlayerAppearance
    ): List<Cue> {
        if (cues.isEmpty()) return emptyList()

        val (clampedX, clampedY) = SafeBounds.clamp(
            appearance.subtitlePosX,
            appearance.subtitlePosY,
            appearance.subtitleSize
        )

        val defaultTextCues = mutableListOf<Cue>()
        val result = mutableListOf<Cue>()

        for (cue in cues) {
            if (cue.bitmap != null) {
                // Keep image-based subtitles (PGS/VobSub) intact
                result.add(cue)
            } else if (cue.line == Cue.DIMEN_UNSET) {
                // Standard default subtitle (SRT, VTT without explicit line)
                defaultTextCues.add(cue)
            } else {
                // Subtitle with explicit positioning tag (e.g. ASS {\an8} top position or WebVTT line)
                val deltaX = clampedX - PlayerAppearance.DEFAULT_POS_X
                val deltaY = clampedY - PlayerAppearance.DEFAULT_POS_Y
                val minY = SafeBounds.minYForSize(appearance.subtitleSize)
                val maxY = SafeBounds.maxY()

                val (newPos, newPosAnchor) = if (cue.position != Cue.DIMEN_UNSET) {
                    val shifted = (cue.position + deltaX).coerceIn(SafeBounds.minX(), SafeBounds.maxX())
                    val anchor = if (cue.positionAnchor != Cue.TYPE_UNSET) cue.positionAnchor else Cue.ANCHOR_TYPE_MIDDLE
                    shifted to anchor
                } else {
                    clampedX to Cue.ANCHOR_TYPE_MIDDLE
                }

                val newCue = cue.buildUpon()
                    .setPosition(newPos)
                    .setPositionAnchor(newPosAnchor)
                    .apply {
                        when (cue.lineType) {
                            Cue.LINE_TYPE_FRACTION -> {
                                val newLine = (cue.line + deltaY).coerceIn(minY, maxY)
                                setLine(newLine, Cue.LINE_TYPE_FRACTION)
                            }
                            Cue.LINE_TYPE_NUMBER -> {
                                val baseFraction = if (cue.line < 0f) {
                                    // Negative line numbers (e.g. WebVTT line:-1) anchor from the bottom
                                    (PlayerAppearance.DEFAULT_POS_Y + (cue.line + 1f) * 0.055f).coerceIn(minY, maxY)
                                } else {
                                    (0.08f + cue.line * 0.055f).coerceIn(minY, maxY)
                                }
                                val newLine = (baseFraction + deltaY).coerceIn(minY, maxY)
                                setLine(newLine, Cue.LINE_TYPE_FRACTION)
                                if (cue.line < 0f) {
                                    setLineAnchor(Cue.ANCHOR_TYPE_END)
                                }
                            }
                        }
                    }
                    .build()
                result.add(newCue)
            }
        }

        if (defaultTextCues.isNotEmpty()) {
            // Merge all concurrent default cues to avoid overlapping text collisions
            val mergedText = defaultTextCues
                .mapNotNull { it.text }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            if (mergedText.isNotBlank()) {
                val mergedCue = Cue.Builder()
                    .setText(mergedText)
                    .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setPosition(clampedX)
                    .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                    .setLine(clampedY, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_END)
                    .build()
                result.add(mergedCue)
            }
        }

        return result
    }
}

