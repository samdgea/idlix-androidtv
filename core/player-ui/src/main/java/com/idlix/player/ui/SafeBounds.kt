package com.idlix.player.ui

import com.idlix.data.model.PlayerAppearance
import com.idlix.data.model.SubtitleSize

object SafeBounds {

    const val SAFE_MARGIN_HORIZONTAL = 0.05f
    const val SAFE_MARGIN_BOTTOM = 0.05f
    const val CENTER_X = 0.50f
    const val SNAP_THRESHOLD_X = 0.018f

    fun minYForSize(size: SubtitleSize): Float = PlayerAppearance.minYForSize(size)

    fun maxY(): Float = PlayerAppearance.SAFE_Y_RANGE.endInclusive

    fun minX(): Float = PlayerAppearance.SAFE_X_RANGE.start
    fun maxX(): Float = PlayerAppearance.SAFE_X_RANGE.endInclusive

    fun clamp(
        x: Float,
        y: Float,
        size: SubtitleSize = SubtitleSize.MEDIUM
    ): Pair<Float, Float> {
        val clampedX = x.coerceIn(minX(), maxX())
        val clampedY = y.coerceIn(minYForSize(size), maxY())
        return Pair(clampedX, clampedY)
    }

    fun snapX(x: Float): Float {
        return if (kotlin.math.abs(x - CENTER_X) <= SNAP_THRESHOLD_X) {
            CENTER_X
        } else {
            x
        }
    }
}

