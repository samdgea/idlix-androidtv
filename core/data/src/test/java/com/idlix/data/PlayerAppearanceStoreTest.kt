package com.idlix.data

import android.content.SharedPreferences
import com.idlix.data.local.PlayerAppearanceStore
import com.idlix.data.model.PlayerAppearance
import com.idlix.data.model.SubtitleSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerAppearanceStoreTest {

    private lateinit var backingMap: MutableMap<String, Any?>
    private lateinit var fakePrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        backingMap = mutableMapOf()
        fakePrefs = InMemorySharedPreferences(backingMap)
        PlayerAppearanceStore.initWithPreferencesForTest(fakePrefs)
    }

    @After
    fun tearDown() {
        PlayerAppearanceStore.resetInMemoryForTest()
    }

    @Test
    fun customizationsSurviveAppCloseAndReopen() {
        PlayerAppearanceStore.update {
            it.copy(
                backgroundColor = 0xFF0B192C.toInt(),
                subtitleTextColor = 0xFFFFEB3B.toInt(),
                subtitleBoxColor = 0x80000000.toInt(),
                subtitleSize = SubtitleSize.SMALL,
                subtitlePosX = 0.32f,
                subtitlePosY = 0.08f
            )
        }
        PlayerAppearanceStore.addRecentCustomColor(0xFF112233.toInt())

        assertTrue(fakePrefs.commitCount >= 2)

        // Simulate process death / app restart while disk SharedPreferences retain committed values
        PlayerAppearanceStore.resetInMemoryForTest()
        val reopenedPrefs = InMemorySharedPreferences(backingMap)
        PlayerAppearanceStore.initWithPreferencesForTest(reopenedPrefs)

        val restored = PlayerAppearanceStore.appearance.value
        assertEquals(0xFF0B192C.toInt(), restored.backgroundColor)
        assertEquals(0xFFFFEB3B.toInt(), restored.subtitleTextColor)
        assertEquals(0x80000000.toInt(), restored.subtitleBoxColor)
        assertEquals(SubtitleSize.SMALL, restored.subtitleSize)
        assertEquals(0.32f, restored.subtitlePosX, 0.0001f)
        assertEquals(0.08f, restored.subtitlePosY, 0.0001f)
        assertEquals(listOf(0xFF112233.toInt()), PlayerAppearanceStore.recentCustomColors.value)
    }

    @Test
    fun resetPositionAndResetAllPersistAcrossRestart() {
        PlayerAppearanceStore.update {
            it.copy(
                backgroundColor = 0xFF121212.toInt(),
                subtitlePosX = 0.70f,
                subtitlePosY = 0.55f
            )
        }
        PlayerAppearanceStore.resetPosition()

        PlayerAppearanceStore.resetInMemoryForTest()
        PlayerAppearanceStore.initWithPreferencesForTest(InMemorySharedPreferences(backingMap))

        val afterPosReset = PlayerAppearanceStore.appearance.value
        assertEquals(0xFF121212.toInt(), afterPosReset.backgroundColor)
        assertEquals(PlayerAppearance.DEFAULT_POS_X, afterPosReset.subtitlePosX, 0.0001f)
        assertEquals(PlayerAppearance.DEFAULT_POS_Y, afterPosReset.subtitlePosY, 0.0001f)

        PlayerAppearanceStore.resetAll()
        PlayerAppearanceStore.resetInMemoryForTest()
        PlayerAppearanceStore.initWithPreferencesForTest(InMemorySharedPreferences(backingMap))

        assertEquals(PlayerAppearance(), PlayerAppearanceStore.appearance.value)
    }

    private class InMemorySharedPreferences(
        private val disk: MutableMap<String, Any?>
    ) : SharedPreferences {
        var commitCount: Int = 0
            private set

        override fun getAll(): MutableMap<String, *> = disk.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (disk[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (disk[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            (disk[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            (disk[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            (disk[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (disk[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = disk.containsKey(key)

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val staged = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                flush()
                commitCount++
                return true
            }

            override fun apply() {
                flush()
            }

            private fun flush() {
                if (clearAll) disk.clear()
                for ((k, v) in staged) {
                    if (v == null) disk.remove(k) else disk[k] = v
                }
            }
        }
    }
}
