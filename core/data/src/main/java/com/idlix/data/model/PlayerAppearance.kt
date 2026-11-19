package com.idlix.data.model

data class PlayerAppearance(
    val backgroundColor: Int = DEFAULT_BG_COLOR,
    val subtitleTextColor: Int = DEFAULT_SUB_TEXT_COLOR,
    val subtitleBoxColor: Int = DEFAULT_SUB_BOX_COLOR,
    val subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    val subtitlePosX: Float = DEFAULT_POS_X,
    val subtitlePosY: Float = DEFAULT_POS_Y
) {
    companion object {
        const val DEFAULT_BG_COLOR: Int = 0xFF000000.toInt()
        const val DEFAULT_SUB_TEXT_COLOR: Int = 0xFFFFFFFF.toInt()
        const val DEFAULT_SUB_BOX_COLOR: Int = 0x00000000 // Fully transparent
        const val DEFAULT_POS_X: Float = 0.50f
        const val DEFAULT_POS_Y: Float = 0.92f

        val SAFE_X_RANGE = 0.15f..0.85f
        val SAFE_Y_RANGE = 0.08f..0.95f

        fun minYForSize(size: SubtitleSize): Float = when (size) {
            SubtitleSize.SMALL -> 0.08f
            SubtitleSize.MEDIUM -> 0.10f
            SubtitleSize.LARGE -> 0.12f
            SubtitleSize.EXTRA_LARGE -> 0.15f
        }
    }
}

enum class SubtitleSize(val fraction: Float) {
    SMALL(0.040f),
    MEDIUM(0.0533f),
    LARGE(0.065f),
    EXTRA_LARGE(0.080f)
}

object PlayerAppearancePresets {
    val backgroundColors: List<Int> = listOf(
        0xFF000000.toInt(), // Pure Black
        0xFF121212.toInt(), // Dark Charcoal
        0xFF0B192C.toInt(), // Deep Navy
        0xFF0D2818.toInt(), // Deep Forest Green
        0xFF1D1128.toInt(), // Deep Purple
        0xFF1C130D.toInt()  // Deep Espresso
    )

    val subtitleTextColors: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // Crisp White
        0xFFFFEB3B.toInt(), // Netflix Yellow
        0xFF00E5FF.toInt(), // Vivid Cyan
        0xFF69F0AE.toInt(), // Soft Mint Green
        0xFFFFAB40.toInt(), // Warm Amber / Orange
        0xFFE0E0E0.toInt()  // Light Silver
    )

    val subtitleBoxColors: List<Int> = listOf(
        0x00000000,         // Transparent (Default)
        0x80000000.toInt(), // 50% Black
        0xC0000000.toInt(), // 75% Black
        0xFF000000.toInt()  // 100% Solid Black
    )
}

