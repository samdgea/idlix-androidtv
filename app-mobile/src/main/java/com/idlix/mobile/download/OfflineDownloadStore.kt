package com.idlix.mobile.download

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class OfflineDownloadStore private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val lock = Any()

    @Volatile
    private var memoryItems: MutableList<OfflineDownloadItem>? = null

    private val _downloadsFlow = MutableStateFlow<List<OfflineDownloadItem>>(emptyList())
    val downloadsFlow: StateFlow<List<OfflineDownloadItem>> = _downloadsFlow.asStateFlow()

    init {
        val loaded = loadFromPrefs().map { item ->
            if (item.isActive) {
                item.copy(status = DownloadStatus.PAUSED)
            } else {
                item
            }
        }
        saveToPrefs(loaded)
    }

    fun getRootDownloadsDir(): File {
        val dir = File(appContext.filesDir, ROOT_FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getContentDir(contentId: String): File {
        val safeName = contentId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = File(getRootDownloadsDir(), safeName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun loadFromPrefs(): List<OfflineDownloadItem> {
        synchronized(lock) {
            memoryItems?.let { return it.toList() }
            val raw = prefs.getString(KEY_ITEMS_JSON, null) ?: return emptyList()
            return try {
                val list = json.decodeFromString<List<OfflineDownloadItem>>(raw).toMutableList()
                memoryItems = list
                list.toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun saveToPrefs(items: List<OfflineDownloadItem>) {
        synchronized(lock) {
            memoryItems = items.toMutableList()
            val raw = try {
                json.encodeToString(items)
            } catch (_: Exception) {
                null
            }
            prefs.edit().putString(KEY_ITEMS_JSON, raw).apply()
            _downloadsFlow.value = items
        }
    }

    fun getAll(): List<OfflineDownloadItem> = loadFromPrefs()

    fun getById(contentId: String): OfflineDownloadItem? {
        return loadFromPrefs().firstOrNull { it.contentId == contentId }
    }

    fun getBySlug(slug: String): List<OfflineDownloadItem> {
        if (slug.isBlank()) return emptyList()
        return loadFromPrefs().filter {
            it.slug.equals(slug, ignoreCase = true) || it.contentId.equals(slug, ignoreCase = true)
        }
    }

    fun upsert(item: OfflineDownloadItem) {
        synchronized(lock) {
            val current = loadFromPrefs().toMutableList()
            val idx = current.indexOfFirst { it.contentId == item.contentId }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(0, item)
            }
            saveToPrefs(current)
        }
    }

    fun update(
        contentId: String,
        transform: (OfflineDownloadItem) -> OfflineDownloadItem
    ): OfflineDownloadItem? {
        synchronized(lock) {
            val current = loadFromPrefs().toMutableList()
            val idx = current.indexOfFirst { it.contentId == contentId }
            if (idx < 0) return null
            val updated = transform(current[idx])
            current[idx] = updated
            saveToPrefs(current)
            return updated
        }
    }

    fun removeAndDeleteFiles(contentId: String) {
        synchronized(lock) {
            val current = loadFromPrefs().toMutableList()
            current.removeAll { it.contentId == contentId }
            saveToPrefs(current)
        }
        try {
            val dir = getContentDir(contentId)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }

    fun clearAllAndDeleteFiles() {
        synchronized(lock) {
            saveToPrefs(emptyList())
        }
        try {
            val root = getRootDownloadsDir()
            if (root.exists()) {
                root.deleteRecursively()
                root.mkdirs()
            }
        } catch (_: Exception) {
        }
    }

    fun getTotalStorageBytes(): Long {
        return loadFromPrefs().sumOf { it.downloadedBytes }
    }

    companion object {
        private const val PREFS_NAME = "idlix_mobile_offline_downloads"
        private const val KEY_ITEMS_JSON = "offline_download_items_v1"
        private const val ROOT_FOLDER_NAME = "offline_downloads"

        @Volatile
        private var INSTANCE: OfflineDownloadStore? = null

        fun getInstance(context: Context): OfflineDownloadStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineDownloadStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
