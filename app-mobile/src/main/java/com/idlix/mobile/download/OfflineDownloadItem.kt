package com.idlix.mobile.download

import com.idlix.data.model.SubtitleTrack
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

@Serializable
enum class DownloadStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Serializable
data class OfflineDownloadItem(
    val contentId: String,
    val contentType: String,
    val slug: String,
    val title: String,
    val seriesTitle: String? = null,
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val overview: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val runtimeMin: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val localPosterPath: String? = null,
    val localPlaylistPath: String? = null,
    val localSubtitles: List<SubtitleTrack> = emptyList(),
    val qualityLabel: String = "HD",
    val durationSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Int = 0,
    val downloadedSegments: Int = 0,
    val totalSegments: Int = 0,
    val downloadedBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.RESOLVING ||
            status == DownloadStatus.DOWNLOADING

    val isPlayableOffline: Boolean
        get() = status == DownloadStatus.COMPLETED &&
            !localPlaylistPath.isNullOrBlank() &&
            File(localPlaylistPath).exists()

    val effectivePosterModel: Any?
        get() {
            val localFile = localPosterPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                return localFile
            }
            return posterUrl?.takeIf { it.isNotBlank() } ?: backdropUrl?.takeIf { it.isNotBlank() }
        }

    val formattedSize: String
        get() = formatBytes(downloadedBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else if (mb >= 10.0) {
                String.format(Locale.US, "%.0f MB", mb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
    }
}
