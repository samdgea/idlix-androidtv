package com.idlix.data.local

import android.content.Context
import android.content.SharedPreferences
import com.idlix.data.model.WatchHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WatchHistoryStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("idlix_tv_watch_history", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    private var memoryHistory: MutableList<WatchHistoryItem>? = null

    private val _historyFlow = MutableStateFlow<List<WatchHistoryItem>>(loadHistoryFromPrefs())
    val historyFlow: StateFlow<List<WatchHistoryItem>> = _historyFlow.asStateFlow()

    private fun loadHistoryFromPrefs(): List<WatchHistoryItem> {
        memoryHistory?.let { return it }
        val raw = prefs.getString("history_items_json", null) ?: return emptyList()
        return try {
            val list = json.decodeFromString<List<WatchHistoryItem>>(raw).toMutableList()
            memoryHistory = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistoryToPrefs(items: List<WatchHistoryItem>) {
        memoryHistory = items.toMutableList()
        val raw = try {
            json.encodeToString(items)
        } catch (e: Exception) {
            null
        }
        prefs.edit().putString("history_items_json", raw).apply()
        _historyFlow.value = items
    }

    /**
     * Called when a title starts playback so it is registered in history with its poster and title metadata.
     */
    fun recordStartPlayback(
        contentId: String,
        contentType: String,
        title: String,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        slug: String? = null,
        episodeTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        val currentList = loadHistoryFromPrefs().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.contentId == contentId }
        val savedPos = getSavedPosition(contentId)
        val savedDur = prefs.getLong("${contentId}_dur", 0L)
        val cleanPoster = posterUrl?.takeIf { it.isNotBlank() }
        val cleanBackdrop = backdropUrl?.takeIf { it.isNotBlank() }
        val cleanSlug = slug?.takeIf { it.isNotBlank() }
        val cleanEpisodeTitle = episodeTitle?.takeIf { it.isNotBlank() }

        val updatedItem = if (existingIndex >= 0) {
            val old = currentList[existingIndex]
            old.copy(
                contentType = contentType,
                title = title,
                posterUrl = cleanPoster ?: old.posterUrl?.takeIf { it.isNotBlank() },
                backdropUrl = cleanBackdrop ?: old.backdropUrl?.takeIf { it.isNotBlank() },
                slug = cleanSlug ?: old.slug?.takeIf { it.isNotBlank() },
                episodeTitle = cleanEpisodeTitle ?: old.episodeTitle?.takeIf { it.isNotBlank() },
                seasonNumber = seasonNumber ?: old.seasonNumber,
                episodeNumber = episodeNumber ?: old.episodeNumber,
                positionMs = if (old.positionMs > 0L) old.positionMs else savedPos,
                durationMs = if (old.durationMs > 0L) old.durationMs else savedDur,
                lastWatchedTime = System.currentTimeMillis()
            )
        } else {
            WatchHistoryItem(
                contentId = contentId,
                contentType = contentType,
                title = title,
                posterUrl = cleanPoster,
                backdropUrl = cleanBackdrop,
                slug = cleanSlug,
                episodeTitle = cleanEpisodeTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                positionMs = savedPos,
                durationMs = savedDur,
                lastWatchedTime = System.currentTimeMillis()
            )
        }

        if (existingIndex >= 0) {
            currentList.removeAt(existingIndex)
        }
        currentList.add(0, updatedItem)
        saveHistoryToPrefs(currentList.take(60))
    }

    /**
     * Called periodically during playback to save current progress.
     */
    fun updateProgress(contentId: String, positionMs: Long, durationMs: Long) {
        prefs.edit()
            .putLong("${contentId}_pos", positionMs)
            .putLong("${contentId}_dur", durationMs)
            .putLong("${contentId}_time", System.currentTimeMillis())
            .apply()

        val currentList = loadHistoryFromPrefs().toMutableList()
        val index = currentList.indexOfFirst { it.contentId == contentId }
        if (index >= 0) {
            val old = currentList[index]
            currentList[index] = old.copy(
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatchedTime = System.currentTimeMillis()
            )
            saveHistoryToPrefs(currentList)
        }
    }

    /**
     * Legacy backward-compatible signature.
     */
    fun savePosition(contentId: String, positionMs: Long, durationMs: Long) {
        updateProgress(contentId, positionMs, durationMs)
    }

    fun getUnfinishedItems(): List<WatchHistoryItem> {
        return loadHistoryFromPrefs().filter { it.isUnfinished }
    }

    fun getAllHistory(): List<WatchHistoryItem> {
        return loadHistoryFromPrefs()
    }

    fun removeItem(contentId: String) {
        prefs.edit()
            .remove("${contentId}_pos")
            .remove("${contentId}_dur")
            .remove("${contentId}_time")
            .apply()

        val currentList = loadHistoryFromPrefs().toMutableList()
        currentList.removeAll { it.contentId == contentId }
        saveHistoryToPrefs(currentList)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _historyFlow.value = emptyList()
    }

    fun getSavedPosition(contentId: String): Long {
        val pos = prefs.getLong("${contentId}_pos", 0L)
        val dur = prefs.getLong("${contentId}_dur", 0L)
        // If near end (e.g. >95% watched), start over from beginning
        if (dur > 0 && pos > (dur * 0.95)) {
            return 0L
        }
        return pos
    }

    fun clearPosition(contentId: String) {
        removeItem(contentId)
    }

    companion object {
        @Volatile
        private var INSTANCE: WatchHistoryStore? = null

        fun getInstance(context: Context): WatchHistoryStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WatchHistoryStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
