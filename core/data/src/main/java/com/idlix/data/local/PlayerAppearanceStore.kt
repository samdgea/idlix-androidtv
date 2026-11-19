package com.idlix.data.local

import android.content.Context
import android.content.SharedPreferences
import com.idlix.data.model.PlayerAppearance
import com.idlix.data.model.SubtitleSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerAppearanceStore {

    private const val PREFS_NAME = "idlix_player_appearance"

    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_SUB_TEXT_COLOR = "sub_text_color"
    private const val KEY_SUB_BOX_COLOR = "sub_box_color"
    private const val KEY_SUB_SIZE = "sub_size"
    private const val KEY_SUB_POS_X = "sub_pos_x"
    private const val KEY_SUB_POS_Y = "sub_pos_y"
    private const val KEY_RECENT_CUSTOM_COLORS = "recent_custom_colors"

    private const val MAX_RECENT_COLORS = 6

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _appearanceFlow = MutableStateFlow(PlayerAppearance())
    val appearance: StateFlow<PlayerAppearance> = _appearanceFlow.asStateFlow()

    private val _recentColorsFlow = MutableStateFlow<List<Int>>(emptyList())
    val recentCustomColors: StateFlow<List<Int>> = _recentColorsFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs = p
                    _appearanceFlow.value = loadAppearance(p)
                    _recentColorsFlow.value = loadRecentColors(p)
                }
            }
        }
    }

    internal fun initWithPreferencesForTest(sharedPreferences: SharedPreferences) {
        synchronized(this) {
            prefs = sharedPreferences
            _appearanceFlow.value = loadAppearance(sharedPreferences)
            _recentColorsFlow.value = loadRecentColors(sharedPreferences)
        }
    }

    internal fun resetInMemoryForTest() {
        synchronized(this) {
            prefs = null
            _appearanceFlow.value = PlayerAppearance()
            _recentColorsFlow.value = emptyList()
        }
    }

    private fun loadAppearance(p: SharedPreferences): PlayerAppearance {
        val bgColor = p.getInt(KEY_BG_COLOR, PlayerAppearance.DEFAULT_BG_COLOR)
        val subTextColor = p.getInt(KEY_SUB_TEXT_COLOR, PlayerAppearance.DEFAULT_SUB_TEXT_COLOR)
        val subBoxColor = p.getInt(KEY_SUB_BOX_COLOR, PlayerAppearance.DEFAULT_SUB_BOX_COLOR)

        val sizeName = p.getString(KEY_SUB_SIZE, SubtitleSize.MEDIUM.name) ?: SubtitleSize.MEDIUM.name
        val subSize = try {
            SubtitleSize.valueOf(sizeName)
        } catch (_: Exception) {
            SubtitleSize.MEDIUM
        }

        val rawX = p.getFloat(KEY_SUB_POS_X, PlayerAppearance.DEFAULT_POS_X)
        val rawY = p.getFloat(KEY_SUB_POS_Y, PlayerAppearance.DEFAULT_POS_Y)

        val posX = rawX.coerceIn(PlayerAppearance.SAFE_X_RANGE)
        val posY = rawY.coerceIn(PlayerAppearance.minYForSize(subSize), PlayerAppearance.SAFE_Y_RANGE.endInclusive)

        return PlayerAppearance(
            backgroundColor = bgColor,
            subtitleTextColor = subTextColor,
            subtitleBoxColor = subBoxColor,
            subtitleSize = subSize,
            subtitlePosX = posX,
            subtitlePosY = posY
        )
    }

    private fun loadRecentColors(p: SharedPreferences): List<Int> {
        val raw = p.getString(KEY_RECENT_CUSTOM_COLORS, null) ?: return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull(16)?.toInt() }
            .take(MAX_RECENT_COLORS)
    }

    private fun persistAppearance(p: SharedPreferences, appearance: PlayerAppearance) {
        p.edit()
            .putInt(KEY_BG_COLOR, appearance.backgroundColor)
            .putInt(KEY_SUB_TEXT_COLOR, appearance.subtitleTextColor)
            .putInt(KEY_SUB_BOX_COLOR, appearance.subtitleBoxColor)
            .putString(KEY_SUB_SIZE, appearance.subtitleSize.name)
            .putFloat(KEY_SUB_POS_X, appearance.subtitlePosX)
            .putFloat(KEY_SUB_POS_Y, appearance.subtitlePosY)
            // Use commit() so customizations flush to disk immediately even if the user closes/force-stops the app right away
            .commit()
    }

    fun update(transform: (PlayerAppearance) -> PlayerAppearance) {
        synchronized(this) {
            val current = _appearanceFlow.value
            val updated = transform(current)

            val clamped = updated.copy(
                subtitlePosX = updated.subtitlePosX.coerceIn(PlayerAppearance.SAFE_X_RANGE),
                subtitlePosY = updated.subtitlePosY.coerceIn(
                    PlayerAppearance.minYForSize(updated.subtitleSize),
                    PlayerAppearance.SAFE_Y_RANGE.endInclusive
                )
            )

            _appearanceFlow.value = clamped
            prefs?.let { persistAppearance(it, clamped) }
        }
    }

    fun addRecentCustomColor(color: Int) {
        synchronized(this) {
            val currentList = _recentColorsFlow.value.toMutableList()
            currentList.remove(color)
            currentList.add(0, color)
            val trimmed = currentList.take(MAX_RECENT_COLORS)
            _recentColorsFlow.value = trimmed

            val raw = trimmed.joinToString(",") { String.format("%08X", it) }
            prefs?.edit()?.putString(KEY_RECENT_CUSTOM_COLORS, raw)?.commit()
        }
    }

    fun resetPosition() {
        update {
            it.copy(
                subtitlePosX = PlayerAppearance.DEFAULT_POS_X,
                subtitlePosY = PlayerAppearance.DEFAULT_POS_Y
            )
        }
    }

    fun resetAll() {
        synchronized(this) {
            val defaultAppearance = PlayerAppearance()
            _appearanceFlow.value = defaultAppearance
            prefs?.let { persistAppearance(it, defaultAppearance) }
        }
    }
}

