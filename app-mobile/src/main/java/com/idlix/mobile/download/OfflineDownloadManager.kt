package com.idlix.mobile.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.idlix.data.api.ApiClient
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object OfflineDownloadManager {

    private const val TAG = "OfflineDownloadMgr"
    private const val CONCURRENT_SEGMENT_WORKERS = 4
    private const val MAX_SEGMENT_RETRIES = 3
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
    private const val REFERER = "https://z2.idlixku.com/"
    private const val ORIGIN = "https://z2.idlixku.com"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val repository = IdlixRepository()

    private val downloadHttpClient by lazy {
        ApiClient.okHttpClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun enqueueDownload(
        context: Context,
        contentId: String,
        contentType: String,
        slug: String,
        title: String,
        seriesTitle: String? = null,
        episodeTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        overview: String? = null,
        year: String? = null,
        rating: String? = null,
        runtimeMin: Int? = null,
        posterUrl: String? = null,
        backdropUrl: String? = null
    ) {
        val appContext = context.applicationContext
        val store = OfflineDownloadStore.getInstance(appContext)

        if (activeJobs[contentId]?.isActive == true) {
            return
        }

        val existing = store.getById(contentId)
        val initialItem = if (existing != null) {
            existing.copy(
                contentType = contentType,
                slug = slug.ifBlank { existing.slug },
                title = title.ifBlank { existing.title },
                seriesTitle = seriesTitle ?: existing.seriesTitle,
                episodeTitle = episodeTitle ?: existing.episodeTitle,
                seasonNumber = seasonNumber ?: existing.seasonNumber,
                episodeNumber = episodeNumber ?: existing.episodeNumber,
                overview = overview ?: existing.overview,
                year = year ?: existing.year,
                rating = rating ?: existing.rating,
                runtimeMin = runtimeMin ?: existing.runtimeMin,
                posterUrl = posterUrl ?: existing.posterUrl,
                backdropUrl = backdropUrl ?: existing.backdropUrl,
                status = DownloadStatus.QUEUED,
                errorMessage = null
            )
        } else {
            OfflineDownloadItem(
                contentId = contentId,
                contentType = contentType,
                slug = slug,
                title = title,
                seriesTitle = seriesTitle,
                episodeTitle = episodeTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                overview = overview,
                year = year,
                rating = rating,
                runtimeMin = runtimeMin,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                status = DownloadStatus.QUEUED
            )
        }

        store.upsert(initialItem)
        OfflineDownloadService.start(appContext)
        startJobForItem(appContext, contentId)
    }

    fun pauseDownload(context: Context, contentId: String) {
        val appContext = context.applicationContext
        activeJobs.remove(contentId)?.cancel()
        val store = OfflineDownloadStore.getInstance(appContext)
        store.update(contentId) { item ->
            if (item.status == DownloadStatus.COMPLETED) {
                item
            } else {
                item.copy(status = DownloadStatus.PAUSED, errorMessage = null)
            }
        }
    }

    fun resumeDownload(context: Context, contentId: String) {
        val appContext = context.applicationContext
        val store = OfflineDownloadStore.getInstance(appContext)
        val item = store.getById(contentId) ?: return
        if (item.status == DownloadStatus.COMPLETED && item.isPlayableOffline) return
        if (activeJobs[contentId]?.isActive == true) return

        store.update(contentId) {
            it.copy(status = DownloadStatus.QUEUED, errorMessage = null)
        }
        OfflineDownloadService.start(appContext)
        startJobForItem(appContext, contentId)
    }

    fun cancelAndRemoveDownload(context: Context, contentId: String) {
        val appContext = context.applicationContext
        activeJobs.remove(contentId)?.cancel()
        val store = OfflineDownloadStore.getInstance(appContext)
        store.removeAndDeleteFiles(contentId)
    }

    fun clearAllDownloads(context: Context) {
        val appContext = context.applicationContext
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        val store = OfflineDownloadStore.getInstance(appContext)
        store.clearAllAndDeleteFiles()
    }

    fun hasActiveJobs(): Boolean {
        return activeJobs.values.any { it.isActive }
    }

    private fun startJobForItem(appContext: Context, contentId: String) {
        val store = OfflineDownloadStore.getInstance(appContext)
        val job = scope.launch {
            try {
                executeDownload(appContext, store, contentId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $contentId: ${e.message}", e)
                store.update(contentId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Gagal mengunduh stream"
                    )
                }
            } finally {
                activeJobs.remove(contentId)
            }
        }
        activeJobs[contentId] = job
    }

    private suspend fun executeDownload(
        appContext: Context,
        store: OfflineDownloadStore,
        contentId: String
    ) {
        val item = store.getById(contentId) ?: return
        val contentDir = store.getContentDir(contentId)
        val segmentsDir = File(contentDir, "segments").apply { mkdirs() }
        val subtitlesDir = File(contentDir, "subtitles").apply { mkdirs() }

        store.update(contentId) {
            it.copy(status = DownloadStatus.RESOLVING, errorMessage = null)
        }

        // 1. Download poster image for offline display if not yet cached locally
        val imageUrl = item.posterUrl?.takeIf { it.isNotBlank() }
            ?: item.backdropUrl?.takeIf { it.isNotBlank() }
        if (!imageUrl.isNullOrBlank()) {
            val posterFile = File(contentDir, "poster.jpg")
            if (!posterFile.exists() || posterFile.length() == 0L) {
                try {
                    downloadSimpleFile(imageUrl, posterFile, includeCdnHeaders = false)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache poster offline: ${e.message}")
                }
            }
            if (posterFile.exists() && posterFile.length() > 0L) {
                store.update(contentId) {
                    it.copy(localPosterPath = posterFile.absolutePath)
                }
            }
        }

        // 2. Resolve HLS stream via IdlixRepository
        val resolveRes = repository.resolveStream(
            contentType = item.contentType,
            contentId = item.contentId,
            contentTitle = item.title,
            onCountdown = null
        )

        val resolved = resolveRes.getOrElse { err ->
            throw IllegalStateException(
                err.localizedMessage ?: "Gagal mendapatkan tautan unduhan dari server"
            )
        }

        val masterUrl = resolved.hlsMasterUrl
        if (masterUrl.isBlank()) {
            throw IllegalStateException("URL master playlist kosong")
        }

        val tokenQuery = HlsPlaylistParser.extractTokenQuery(masterUrl)

        // 3. Download subtitles locally
        val localSubtitles = mutableListOf<SubtitleTrack>()
        resolved.subtitles.forEachIndexed { idx, sub ->
            if (sub.path.isNotBlank()) {
                try {
                    val isSrt = sub.path.endsWith(".srt", ignoreCase = true)
                    val ext = if (isSrt) "srt" else "vtt"
                    val safeLang = sub.lang.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "sub" }
                    val destSubFile = File(subtitlesDir, "sub_${safeLang}_$idx.$ext")

                    val parsedUri = Uri.parse(sub.path)
                    if (parsedUri.scheme == "file" || parsedUri.scheme == null) {
                        val srcFile = File(parsedUri.path ?: sub.path)
                        if (srcFile.exists() && srcFile.length() > 0L) {
                            srcFile.copyTo(destSubFile, overwrite = true)
                        }
                    } else {
                        val subUrl = HlsPlaylistParser.resolveUriWithToken(masterUrl, sub.path, tokenQuery)
                        downloadSimpleFile(subUrl, destSubFile, includeCdnHeaders = true)
                    }

                    if (destSubFile.exists() && destSubFile.length() > 0L) {
                        localSubtitles.add(
                            SubtitleTrack(
                                lang = sub.lang,
                                label = sub.label,
                                path = Uri.fromFile(destSubFile).toString()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Subtitle download skipped (${sub.lang}): ${e.message}")
                }
            }
        }

        // 4. Fetch Master Playlist & select best quality variant automatically
        val masterContent = fetchStringContent(masterUrl)
        val selection = HlsPlaylistParser.selectBestVariant(masterContent, masterUrl, tokenQuery)

        // 5. Fetch Video Media Playlist (and Audio Media Playlist if separate)
        val videoPlaylistContent = if (selection.videoPlaylistUrl == masterUrl) {
            masterContent
        } else {
            fetchStringContent(selection.videoPlaylistUrl)
        }

        val videoParsed = HlsPlaylistParser.parseMediaPlaylist(
            playlistContent = videoPlaylistContent,
            playlistUrl = selection.videoPlaylistUrl,
            tokenQuery = tokenQuery,
            filePrefix = "v"
        )

        val audioParsed = selection.audioPlaylistUrl?.let { audioUrl ->
            val audioContent = fetchStringContent(audioUrl)
            HlsPlaylistParser.parseMediaPlaylist(
                playlistContent = audioContent,
                playlistUrl = audioUrl,
                tokenQuery = tokenQuery,
                filePrefix = "a"
            )
        }

        val allSegments = buildList {
            addAll(videoParsed.segments)
            if (audioParsed != null) {
                addAll(audioParsed.segments)
            }
        }

        if (allSegments.isEmpty()) {
            throw IllegalStateException("Tidak ditemukan segmen video di dalam playlist")
        }

        // 6. Write local .m3u8 playlist(s)
        val mainPlaylistFile = File(contentDir, "playlist.m3u8")
        if (audioParsed != null) {
            val videoM3u8File = File(contentDir, "video.m3u8")
            val audioM3u8File = File(contentDir, "audio.m3u8")
            videoM3u8File.writeText(videoParsed.rewrittenPlaylistContent, Charsets.UTF_8)
            audioM3u8File.writeText(audioParsed.rewrittenPlaylistContent, Charsets.UTF_8)
            val localMaster = HlsPlaylistParser.buildLocalMasterPlaylist(
                videoPlaylistFileName = "video.m3u8",
                audioPlaylistFileName = "audio.m3u8",
                bandwidth = selection.bandwidth,
                height = selection.height
            )
            mainPlaylistFile.writeText(localMaster, Charsets.UTF_8)
        } else {
            mainPlaylistFile.writeText(videoParsed.rewrittenPlaylistContent, Charsets.UTF_8)
        }

        val totalSegments = allSegments.size
        val completedCounter = AtomicInteger(0)
        val bytesCounter = AtomicLong(0L)

        // Pre-count already downloaded segments for seamless resume
        val pendingTasks = mutableListOf<HlsSegmentTask>()
        for (task in allSegments) {
            val segFile = File(contentDir, task.localRelativePath)
            if (segFile.exists() && segFile.length() > 0L) {
                completedCounter.incrementAndGet()
                bytesCounter.addAndGet(segFile.length())
            } else {
                pendingTasks.add(task)
            }
        }

        val initialCompleted = completedCounter.get()
        val initialPercent = ((initialCompleted * 100) / totalSegments).coerceIn(0, 99)

        store.update(contentId) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                qualityLabel = selection.qualityLabel,
                durationSec = if (resolved.durationSec > 0L) resolved.durationSec else it.durationSec,
                localSubtitles = if (localSubtitles.isNotEmpty()) localSubtitles else it.localSubtitles,
                totalSegments = totalSegments,
                downloadedSegments = initialCompleted,
                downloadedBytes = bytesCounter.get(),
                progressPercent = initialPercent,
                errorMessage = null
            )
        }

        // 7. Download remaining segments with bounded concurrency
        val semaphore = Semaphore(CONCURRENT_SEGMENT_WORKERS)
        val lastProgressUpdateMs = AtomicLong(0L)

        coroutineScope {
            val jobs = pendingTasks.map { task ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (!isActive) return@withPermit
                        val targetFile = File(contentDir, task.localRelativePath)
                        targetFile.parentFile?.mkdirs()

                        var attempt = 0
                        var lastErr: Exception? = null
                        var downloadedLen = 0L

                        while (attempt < MAX_SEGMENT_RETRIES && isActive) {
                            attempt++
                            try {
                                downloadedLen = downloadSimpleFile(
                                    url = task.remoteUrl,
                                    destFile = targetFile,
                                    includeCdnHeaders = true
                                )
                                lastErr = null
                                break
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                lastErr = e
                                if (attempt < MAX_SEGMENT_RETRIES) {
                                    delay(500L * attempt)
                                }
                            }
                        }

                        if (lastErr != null) {
                            throw lastErr
                        }

                        val doneCount = completedCounter.incrementAndGet()
                        val totalBytes = bytesCounter.addAndGet(downloadedLen)
                        val now = System.currentTimeMillis()
                        val prevUpdate = lastProgressUpdateMs.get()

                        if (doneCount == totalSegments || now - prevUpdate >= 350L) {
                            lastProgressUpdateMs.set(now)
                            val pct = ((doneCount * 100) / totalSegments).coerceIn(0, 99)
                            store.update(contentId) { current ->
                                if (current.status == DownloadStatus.PAUSED) {
                                    current
                                } else {
                                    current.copy(
                                        status = DownloadStatus.DOWNLOADING,
                                        downloadedSegments = doneCount,
                                        totalSegments = totalSegments,
                                        downloadedBytes = totalBytes,
                                        progressPercent = pct
                                    )
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        // 8. Mark completed
        val finalBytes = bytesCounter.get()
        store.update(contentId) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                progressPercent = 100,
                downloadedSegments = totalSegments,
                totalSegments = totalSegments,
                downloadedBytes = finalBytes,
                localPlaylistPath = mainPlaylistFile.absolutePath,
                localSubtitles = if (localSubtitles.isNotEmpty()) localSubtitles else it.localSubtitles,
                completedAt = System.currentTimeMillis(),
                errorMessage = null
            )
        }
    }

    private fun fetchStringContent(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .get()
            .build()

        downloadHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} saat mengambil playlist")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw IllegalStateException("Playlist kosong dari server")
            }
            return body
        }
    }

    private fun downloadSimpleFile(
        url: String,
        destFile: File,
        includeCdnHeaders: Boolean
    ): Long {
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()

        if (includeCdnHeaders) {
            reqBuilder.header("Referer", REFERER)
            reqBuilder.header("Origin", ORIGIN)
        }

        downloadHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} saat mengunduh segmen")
            }
            val body = response.body ?: throw IllegalStateException("Response body kosong")
            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var totalRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                    }
                    output.flush()
                }
            }
        }

        if (tmpFile.length() <= 0L) {
            tmpFile.delete()
            throw IllegalStateException("Segmen yang diunduh kosong (0 byte)")
        }

        if (destFile.exists()) {
            destFile.delete()
        }
        if (!tmpFile.renameTo(destFile)) {
            tmpFile.copyTo(destFile, overwrite = true)
            tmpFile.delete()
        }
        return destFile.length()
    }
}
