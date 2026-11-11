package com.idlix.mobile.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.WatchHistoryItem
import com.idlix.mobile.download.OfflineDownloadItem
import com.idlix.mobile.download.OfflineDownloadManager
import com.idlix.mobile.download.OfflineDownloadStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab {
    HISTORY,
    DOWNLOADS
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.HISTORY,
    val historyItems: List<WatchHistoryItem> = emptyList(),
    val downloadItems: List<OfflineDownloadItem> = emptyList(),
    val totalStorageBytes: Long = 0L,
    val isLoading: Boolean = false
)

class LibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)
    private val downloadStore = OfflineDownloadStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyStore.historyFlow.collect { items ->
                _uiState.value = _uiState.value.copy(historyItems = items)
            }
        }
        viewModelScope.launch {
            downloadStore.downloadsFlow.collect { items ->
                val totalBytes = items.sumOf { it.downloadedBytes }
                _uiState.value = _uiState.value.copy(
                    downloadItems = items,
                    totalStorageBytes = totalBytes
                )
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun removeItem(contentId: String) {
        historyStore.removeItem(contentId)
    }

    fun clearAll() {
        historyStore.clearAll()
    }

    fun pauseDownload(contentId: String) {
        OfflineDownloadManager.pauseDownload(getApplication(), contentId)
    }

    fun resumeDownload(contentId: String) {
        OfflineDownloadManager.resumeDownload(getApplication(), contentId)
    }

    fun removeDownload(contentId: String) {
        OfflineDownloadManager.cancelAndRemoveDownload(getApplication(), contentId)
    }

    fun clearAllDownloads() {
        OfflineDownloadManager.clearAllDownloads(getApplication())
    }
}
