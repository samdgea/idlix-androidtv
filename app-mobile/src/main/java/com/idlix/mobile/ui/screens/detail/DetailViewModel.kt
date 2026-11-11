package com.idlix.mobile.ui.screens.detail

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.Season
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SubdlItem
import com.idlix.data.model.SubdlQuery
import com.idlix.data.model.SubdlSearchResult
import com.idlix.data.model.SubdlSubtitle
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.repository.IdlixRepository
import com.idlix.data.repository.SubdlRepository
import com.idlix.data.util.SubdlTitleCleaner
import com.idlix.mobile.download.OfflineDownloadItem
import com.idlix.mobile.download.OfflineDownloadManager
import com.idlix.mobile.download.OfflineDownloadStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class NextEpisodeInfo(
    val episode: EpisodeItem,
    val seasonNumber: Int,
    val seriesTitle: String,
    val seriesSlug: String,
    val posterUrl: String?,
    val backdropUrl: String?
)

data class DetailUiState(
    val isLoadingDetail: Boolean = true,
    val detailError: String? = null,
    val movie: MovieDetail? = null,
    val series: SeriesDetail? = null,
    val selectedSeason: Season? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val selectedEpisode: EpisodeItem? = null,

    // Active playback info
    val playingEpisode: EpisodeItem? = null,
    val playingSeasonNumber: Int? = null,

    // Playback state
    val isStreamLoading: Boolean = false,
    val countdownSeconds: Int = 0,
    val streamError: String? = null,
    val resolvedStream: ResolvedPlayback? = null,
    val initialPositionMs: Long = 0L,

    // Subtitles
    val selectedSubtitle: SubtitleTrack? = null,
    val isSubtitleEnabled: Boolean = true,
    val isSubtitlePickerVisible: Boolean = false,
    val subdlSubtitles: List<SubdlSubtitle> = emptyList(),
    val isSearchingSubdl: Boolean = false,
    val subdlCandidates: List<SubdlItem> = emptyList(),
    val subdlError: SubdlSearchResult.Error? = null,
    val subdlActiveQuery: SubdlQuery? = null,
    val isManualSearchOpen: Boolean = false,
    val manualTitle: String = "",
    val manualYear: String = "",
    val manualSeason: String = "",
    val manualEpisode: String = "",
    val isUsingSessionOverride: Boolean = false,
    val isAppearancePanelOpen: Boolean = false,

    // Next Episode Auto-Prompt
    val nextEpisode: NextEpisodeInfo? = null,
    val isNextEpisodeDismissed: Boolean = false,

    // Offline Downloads (Mobile Only)
    val downloadsByContentId: Map<String, OfflineDownloadItem> = emptyMap()
)

class DetailViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = IdlixRepository()
    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)
    private val downloadStore = OfflineDownloadStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadStore.downloadsFlow.collect { items ->
                _uiState.value = _uiState.value.copy(
                    downloadsByContentId = items.associateBy { it.contentId }
                )
            }
        }
    }

    private var currentContentId: String = ""
    private var currentContentType: String = ""
    private var currentContentTitle: String = ""
    private var currentPosterUrl: String? = null
    private var currentBackdropUrl: String? = null
    private var currentSlug: String? = null
    private var currentEpisodeTitle: String? = null
    private var currentSeasonNumber: Int? = null
    private var currentEpisodeNumber: Int? = null
    private var loadedRequestKey: String? = null

    private var playbackJob: Job? = null
    private var resolveNextEpisodeJob: Job? = null

    fun loadDetail(
        contentType: String,
        slug: String,
        autoPlay: Boolean = false,
        resumeContentId: String? = null,
        resumeSeasonNumber: Int? = null,
        resumeEpisodeNumber: Int? = null
    ) {
        val requestKey = "$contentType|$slug|${resumeContentId.orEmpty()}|${resumeSeasonNumber ?: ""}|${resumeEpisodeNumber ?: ""}"
        val isFirstLoadForRequest = loadedRequestKey != requestKey
        if (!isFirstLoadForRequest && (_uiState.value.movie != null || _uiState.value.series != null) && _uiState.value.detailError == null) {
            return
        }
        loadedRequestKey = requestKey
        val shouldTriggerAutoPlay = autoPlay && isFirstLoadForRequest

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingDetail = true,
                detailError = null
            )

            try {
                val allHistory = historyStore.getAllHistory()
                val historyItem = allHistory.firstOrNull { item ->
                    (!resumeContentId.isNullOrBlank() && item.contentId == resumeContentId) ||
                        (slug.isNotBlank() && (item.slug == slug || item.contentId == slug))
                }

                val allDownloads = downloadStore.getAll()
                val matchedDownload = allDownloads.firstOrNull { dl ->
                    (!resumeContentId.isNullOrBlank() && dl.contentId == resumeContentId) ||
                        (slug.isNotBlank() && (dl.slug.equals(slug, ignoreCase = true) || dl.contentId == slug))
                }

                val targetContentId = resumeContentId?.takeIf { it.isNotBlank() }
                    ?: historyItem?.contentId
                    ?: matchedDownload?.contentId
                val targetSeason = resumeSeasonNumber ?: historyItem?.seasonNumber ?: matchedDownload?.seasonNumber
                val targetEpisode = resumeEpisodeNumber ?: historyItem?.episodeNumber ?: matchedDownload?.episodeNumber

                var effectiveSlug = historyItem?.slug?.takeIf { it.isNotBlank() }
                    ?: matchedDownload?.slug?.takeIf { it.isNotBlank() }
                    ?: slug.takeIf { it.isNotBlank() && it != targetContentId }
                    ?: slug

                currentSlug = effectiveSlug

                var autoPlaybackStarted = false
                if (shouldTriggerAutoPlay && historyItem != null && historyItem.contentId.isNotBlank() &&
                    (historyItem.contentType == "movie" || historyItem.contentType == "episode")
                ) {
                    autoPlaybackStarted = true
                    startStreamPlayback(
                        contentType = historyItem.contentType,
                        contentId = historyItem.contentId,
                        title = historyItem.title,
                        posterUrl = historyItem.posterUrl,
                        backdropUrl = historyItem.backdropUrl,
                        slug = effectiveSlug.takeIf { it.isNotBlank() && it != historyItem.contentId },
                        episodeTitle = historyItem.episodeTitle,
                        seasonNumber = targetSeason,
                        episodeNumber = targetEpisode
                    )
                } else if (shouldTriggerAutoPlay && matchedDownload != null && matchedDownload.contentId.isNotBlank()) {
                    autoPlaybackStarted = true
                    startStreamPlayback(
                        contentType = matchedDownload.contentType,
                        contentId = matchedDownload.contentId,
                        title = matchedDownload.title,
                        posterUrl = matchedDownload.localPosterPath ?: matchedDownload.posterUrl,
                        backdropUrl = matchedDownload.localPosterPath ?: matchedDownload.backdropUrl,
                        slug = effectiveSlug.takeIf { it.isNotBlank() && it != matchedDownload.contentId },
                        episodeTitle = matchedDownload.episodeTitle,
                        seasonNumber = targetSeason,
                        episodeNumber = targetEpisode
                    )
                }

                val isSeries = contentType.equals("tv_series", ignoreCase = true) ||
                    contentType.equals("series", ignoreCase = true) ||
                    contentType.equals("tv", ignoreCase = true) ||
                    contentType.equals("episode", ignoreCase = true) ||
                    historyItem?.contentType.equals("episode", ignoreCase = true) ||
                    historyItem?.contentType.equals("tv_series", ignoreCase = true) ||
                    historyItem?.contentType.equals("series", ignoreCase = true) ||
                    matchedDownload?.contentType.equals("episode", ignoreCase = true) ||
                    targetSeason != null ||
                    targetEpisode != null

                if (isSeries) {
                    var rawSeries = if (effectiveSlug.isNotBlank() && effectiveSlug != targetContentId) {
                        repository.getSeriesDetail(effectiveSlug)
                    } else null

                    if (rawSeries == null && effectiveSlug.isNotBlank()) {
                        rawSeries = repository.getSeriesDetail(effectiveSlug)
                    }

                    if (rawSeries == null && historyItem != null && historyItem.title.isNotBlank()) {
                        try {
                            val baseSeriesTitle = historyItem.title.substringBefore(" -").trim()
                            if (baseSeriesTitle.isNotBlank()) {
                                val searchResults = repository.search(baseSeriesTitle)
                                val matched = searchResults.firstOrNull {
                                    (it.contentType.contains("series", ignoreCase = true) || it.contentType.contains("tv", ignoreCase = true)) &&
                                        it.title.equals(baseSeriesTitle, ignoreCase = true) &&
                                        it.slug.isNotBlank()
                                } ?: searchResults.firstOrNull {
                                    (it.contentType.contains("series", ignoreCase = true) || it.contentType.contains("tv", ignoreCase = true)) &&
                                        it.slug.isNotBlank()
                                } ?: searchResults.firstOrNull { it.slug.isNotBlank() }

                                if (matched != null) {
                                    effectiveSlug = matched.slug
                                    rawSeries = repository.getSeriesDetail(effectiveSlug)
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                    val offlineSeriesDownloads = allDownloads.filter { dl ->
                        dl.contentType == "episode" && (
                            (effectiveSlug.isNotBlank() && dl.slug.equals(effectiveSlug, ignoreCase = true)) ||
                                (!targetContentId.isNullOrBlank() && dl.contentId == targetContentId)
                            )
                    }

                    if (rawSeries != null) {
                        val series = rawSeries.copy(slug = rawSeries.slug.ifBlank { effectiveSlug })
                        currentSlug = series.slug

                        val allSeasons = series.seasons
                        val defaultSeason = series.defaultSeason
                        val desiredSeasonNum = targetSeason ?: defaultSeason?.seasonNumber
                        val initialSeason = allSeasons.firstOrNull { it.seasonNumber == desiredSeasonNum }
                            ?: defaultSeason?.let {
                                if (desiredSeasonNum == null || it.seasonNumber == desiredSeasonNum) {
                                    Season(id = it.id, seasonNumber = it.seasonNumber, name = it.name)
                                } else null
                            }
                            ?: allSeasons.firstOrNull()
                            ?: Season(
                                seasonNumber = desiredSeasonNum ?: 1,
                                name = if (desiredSeasonNum != null) "Season $desiredSeasonNum" else (defaultSeason?.name ?: "Season 1")
                            )

                        val initialEpisodes = if (defaultSeason != null && defaultSeason.seasonNumber == initialSeason.seasonNumber && defaultSeason.episodes.isNotEmpty()) {
                            defaultSeason.episodes
                        } else {
                            try {
                                repository.getEpisodeDetail(series.slug, initialSeason.seasonNumber, 1)?.season?.episodes
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: defaultSeason?.episodes
                                    ?: emptyList()
                            } catch (_: Exception) {
                                defaultSeason?.episodes ?: emptyList()
                            }
                        }

                        val matchedEp = initialEpisodes.firstOrNull {
                            (!targetContentId.isNullOrBlank() && it.id == targetContentId) ||
                                (targetEpisode != null && it.episodeNumber == targetEpisode)
                        } ?: initialEpisodes.firstOrNull()

                        _uiState.value = _uiState.value.copy(
                            isLoadingDetail = false,
                            detailError = null,
                            series = series,
                            movie = null,
                            selectedSeason = initialSeason,
                            episodes = initialEpisodes,
                            selectedEpisode = matchedEp,
                            playingEpisode = if (shouldTriggerAutoPlay || autoPlaybackStarted) (matchedEp ?: _uiState.value.playingEpisode) else _uiState.value.playingEpisode,
                            playingSeasonNumber = if (shouldTriggerAutoPlay || autoPlaybackStarted) initialSeason.seasonNumber else _uiState.value.playingSeasonNumber
                        )

                        if (shouldTriggerAutoPlay && !autoPlaybackStarted && matchedEp != null) {
                            playEpisode(series, initialSeason.seasonNumber, matchedEp)
                        } else if (autoPlaybackStarted && series.slug.isNotBlank()) {
                            historyStore.recordStartPlayback(
                                contentId = currentContentId,
                                contentType = currentContentType,
                                title = currentContentTitle,
                                posterUrl = currentPosterUrl,
                                backdropUrl = currentBackdropUrl,
                                slug = series.slug,
                                episodeTitle = currentEpisodeTitle,
                                seasonNumber = currentSeasonNumber,
                                episodeNumber = currentEpisodeNumber
                            )
                            if (resolveNextEpisodeJob == null) {
                                resolveNextEpisodeJob = resolveNextEpisode(
                                    seriesSlug = series.slug,
                                    currentEpisodeId = currentContentId,
                                    seasonNumber = currentSeasonNumber,
                                    episodeNumber = currentEpisodeNumber,
                                    fallbackTitle = currentContentTitle,
                                    posterUrl = currentPosterUrl,
                                    backdropUrl = currentBackdropUrl
                                )
                            }
                        }
                    } else if (offlineSeriesDownloads.isNotEmpty() || historyItem != null) {
                        val primaryDl = offlineSeriesDownloads.firstOrNull { it.contentId == targetContentId }
                            ?: offlineSeriesDownloads.firstOrNull()
                        val seasonNumbers = offlineSeriesDownloads.mapNotNull { it.seasonNumber }
                            .distinct()
                            .sorted()
                            .ifEmpty { listOf(targetSeason ?: 1) }
                        val fallbackSeasons = seasonNumbers.map { sNum ->
                            Season(seasonNumber = sNum, name = "Season $sNum")
                        }
                        val activeSeasonNum = targetSeason ?: seasonNumbers.first()
                        val activeSeason = fallbackSeasons.firstOrNull { it.seasonNumber == activeSeasonNum }
                            ?: fallbackSeasons.first()

                        val offlineEpsForSeason = offlineSeriesDownloads
                            .filter { (it.seasonNumber ?: 1) == activeSeason.seasonNumber }
                            .sortedBy { it.episodeNumber ?: 1 }
                            .map { dl ->
                                EpisodeItem(
                                    id = dl.contentId,
                                    episodeNumber = dl.episodeNumber ?: 1,
                                    name = dl.episodeTitle?.substringAfter(": ")
                                        ?: dl.title.substringAfter(": ", "Episode ${dl.episodeNumber ?: 1}"),
                                    overview = dl.overview,
                                    stillPath = dl.localPosterPath ?: dl.posterUrl,
                                    runtime = dl.runtimeMin
                                )
                            }

                        val fallbackEps = offlineEpsForSeason.ifEmpty {
                            val fallbackEpNum = targetEpisode ?: 1
                            listOf(
                                EpisodeItem(
                                    id = historyItem?.contentId ?: primaryDl?.contentId.orEmpty(),
                                    episodeNumber = fallbackEpNum,
                                    name = historyItem?.episodeTitle
                                        ?: primaryDl?.episodeTitle
                                        ?: historyItem?.title?.substringAfter(": ", "Episode $fallbackEpNum")
                                        ?: "Episode $fallbackEpNum",
                                    stillPath = primaryDl?.localPosterPath ?: historyItem?.posterUrl ?: primaryDl?.posterUrl
                                )
                            )
                        }

                        val selectedEp = fallbackEps.firstOrNull {
                            (!targetContentId.isNullOrBlank() && it.id == targetContentId) ||
                                (targetEpisode != null && it.episodeNumber == targetEpisode)
                        } ?: fallbackEps.first()

                        val seriesTitle = primaryDl?.seriesTitle?.takeIf { it.isNotBlank() }
                            ?: primaryDl?.title?.substringBefore(" -")?.takeIf { it.isNotBlank() }
                            ?: historyItem?.title?.substringBefore(" -")?.ifBlank { historyItem.title }
                            ?: "Series"

                        val fallbackSeries = SeriesDetail(
                            id = primaryDl?.contentId ?: historyItem?.contentId.orEmpty(),
                            title = seriesTitle,
                            slug = effectiveSlug,
                            overview = primaryDl?.overview,
                            firstAirDate = primaryDl?.year,
                            voteAverage = primaryDl?.rating,
                            posterPath = primaryDl?.localPosterPath ?: historyItem?.posterUrl ?: primaryDl?.posterUrl,
                            backdropPath = primaryDl?.localPosterPath ?: historyItem?.backdropUrl ?: primaryDl?.backdropUrl,
                            seasons = fallbackSeasons
                        )
                        _uiState.value = _uiState.value.copy(
                            isLoadingDetail = false,
                            detailError = null,
                            series = fallbackSeries,
                            movie = null,
                            selectedSeason = activeSeason,
                            episodes = fallbackEps,
                            selectedEpisode = selectedEp,
                            playingEpisode = if (shouldTriggerAutoPlay || autoPlaybackStarted) selectedEp else _uiState.value.playingEpisode,
                            playingSeasonNumber = if (shouldTriggerAutoPlay || autoPlaybackStarted) activeSeason.seasonNumber else _uiState.value.playingSeasonNumber
                        )
                        if (shouldTriggerAutoPlay && !autoPlaybackStarted) {
                            playEpisode(fallbackSeries, activeSeason.seasonNumber, selectedEp)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingDetail = false,
                            detailError = "Failed to load TV series details"
                        )
                    }
                } else {
                    var rawMovie = if (effectiveSlug.isNotBlank() && effectiveSlug != targetContentId) {
                        repository.getMovieDetail(effectiveSlug)
                    } else null

                    if (rawMovie == null && effectiveSlug.isNotBlank()) {
                        rawMovie = repository.getMovieDetail(effectiveSlug)
                    }

                    if (rawMovie == null && historyItem != null && historyItem.title.isNotBlank()) {
                        try {
                            val cleanTitle = SubdlTitleCleaner.clean(historyItem.title).title.ifBlank { historyItem.title }
                            val searchResults = repository.search(cleanTitle)
                            val matched = searchResults.firstOrNull {
                                !targetContentId.isNullOrBlank() && it.id == targetContentId && it.slug.isNotBlank()
                            } ?: searchResults.firstOrNull {
                                it.title.equals(historyItem.title, ignoreCase = true) && it.slug.isNotBlank()
                            } ?: searchResults.firstOrNull {
                                it.title.equals(cleanTitle, ignoreCase = true) && it.slug.isNotBlank()
                            } ?: searchResults.firstOrNull { it.slug.isNotBlank() }

                            if (matched != null) {
                                effectiveSlug = matched.slug
                                rawMovie = repository.getMovieDetail(effectiveSlug)
                            }
                        } catch (_: Exception) {
                        }
                    }

                    if (rawMovie != null) {
                        val movie = rawMovie.copy(slug = rawMovie.slug.ifBlank { effectiveSlug })
                        currentSlug = movie.slug

                        _uiState.value = _uiState.value.copy(
                            isLoadingDetail = false,
                            detailError = null,
                            movie = movie,
                            series = null,
                            episodes = emptyList()
                        )

                        if (shouldTriggerAutoPlay && !autoPlaybackStarted) {
                            playMovie(movie)
                        } else if (autoPlaybackStarted && movie.slug.isNotBlank()) {
                            historyStore.recordStartPlayback(
                                contentId = currentContentId,
                                contentType = currentContentType,
                                title = currentContentTitle,
                                posterUrl = currentPosterUrl,
                                backdropUrl = currentBackdropUrl,
                                slug = movie.slug,
                                seasonNumber = null,
                                episodeNumber = null
                            )
                        }
                    } else if (matchedDownload != null || historyItem != null) {
                        val fallbackMovie = MovieDetail(
                            id = matchedDownload?.contentId ?: historyItem?.contentId.orEmpty(),
                            title = matchedDownload?.title ?: historyItem?.title.orEmpty(),
                            slug = effectiveSlug,
                            overview = matchedDownload?.overview,
                            releaseDate = matchedDownload?.year,
                            voteAverage = matchedDownload?.rating,
                            runtime = matchedDownload?.runtimeMin,
                            posterPath = matchedDownload?.localPosterPath ?: historyItem?.posterUrl ?: matchedDownload?.posterUrl,
                            backdropPath = matchedDownload?.localPosterPath ?: historyItem?.backdropUrl ?: matchedDownload?.backdropUrl
                        )
                        _uiState.value = _uiState.value.copy(
                            isLoadingDetail = false,
                            detailError = null,
                            movie = fallbackMovie,
                            series = null,
                            episodes = emptyList()
                        )
                        if (shouldTriggerAutoPlay && !autoPlaybackStarted) {
                            playMovie(fallbackMovie)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingDetail = false,
                            detailError = "Failed to load movie details"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error loading detail: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingDetail = false,
                    detailError = e.localizedMessage ?: "Network error loading details"
                )
            }
        }
    }

    fun selectSeason(season: Season) {
        val series = _uiState.value.series ?: return
        if (_uiState.value.selectedSeason?.seasonNumber == season.seasonNumber && _uiState.value.episodes.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            val defaultSeason = series.defaultSeason
            if (defaultSeason != null && defaultSeason.seasonNumber == season.seasonNumber && defaultSeason.episodes.isNotEmpty()) {
                val cachedEpisodes = defaultSeason.episodes
                _uiState.value = _uiState.value.copy(
                    selectedSeason = season,
                    episodes = cachedEpisodes,
                    selectedEpisode = cachedEpisodes.firstOrNull()
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(selectedSeason = season)
            val seriesSlug = series.slug.ifBlank { currentSlug ?: "" }
            try {
                val epRes = repository.getEpisodeDetail(seriesSlug, season.seasonNumber, 1)
                val newEpisodes = epRes?.season?.episodes ?: emptyList()
                if (newEpisodes.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        episodes = newEpisodes,
                        selectedEpisode = newEpisodes.firstOrNull()
                    )
                    return@launch
                }
            } catch (e: Exception) {
                Log.w("DetailViewModel", "Failed to fetch episodes for season ${season.seasonNumber}: ${e.message}")
            }

            // Offline fallback for selected season
            val offlineEps = downloadStore.getBySlug(seriesSlug)
                .filter { it.contentType == "episode" && (it.seasonNumber ?: 1) == season.seasonNumber }
                .sortedBy { it.episodeNumber ?: 1 }
                .map { dl ->
                    EpisodeItem(
                        id = dl.contentId,
                        episodeNumber = dl.episodeNumber ?: 1,
                        name = dl.episodeTitle?.substringAfter(": ")
                            ?: dl.title.substringAfter(": ", "Episode ${dl.episodeNumber ?: 1}"),
                        overview = dl.overview,
                        stillPath = dl.localPosterPath ?: dl.posterUrl,
                        runtime = dl.runtimeMin
                    )
                }
            if (offlineEps.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    episodes = offlineEps,
                    selectedEpisode = offlineEps.firstOrNull()
                )
            }
        }
    }

    fun selectEpisode(episode: EpisodeItem) {
        val series = _uiState.value.series ?: return
        val seasonNum = _uiState.value.selectedSeason?.seasonNumber ?: 1

        // If this exact episode is already playing and active without error, keep playing
        if (_uiState.value.playingEpisode?.id == episode.id &&
            (_uiState.value.resolvedStream != null || _uiState.value.isStreamLoading) &&
            _uiState.value.streamError == null
        ) {
            return
        }

        _uiState.value = _uiState.value.copy(
            selectedEpisode = episode,
            playingEpisode = episode,
            playingSeasonNumber = seasonNum
        )
        playEpisode(series, seasonNum, episode)
    }

    fun playCurrentMedia() {
        val state = _uiState.value
        val movie = state.movie
        val series = state.series
        if (movie != null) {
            playMovie(movie)
        } else if (series != null) {
            val seasonNum = state.selectedSeason?.seasonNumber ?: 1
            val ep = state.selectedEpisode ?: state.episodes.firstOrNull()
            if (ep != null) {
                _uiState.value = _uiState.value.copy(
                    selectedEpisode = ep,
                    playingEpisode = ep,
                    playingSeasonNumber = seasonNum
                )
                playEpisode(series, seasonNum, ep)
            }
        } else if (currentContentId.isNotBlank()) {
            retryPlayback()
        }
    }

    private fun playMovie(movie: MovieDetail) {
        val effectiveSlug = movie.slug.ifBlank { currentSlug ?: "" }
        startStreamPlayback(
            contentType = "movie",
            contentId = movie.id,
            title = movie.title,
            posterUrl = movie.fullPosterUrl,
            backdropUrl = movie.fullBackdropUrl,
            slug = effectiveSlug.takeIf { it.isNotBlank() },
            seasonNumber = null,
            episodeNumber = null
        )
    }

    private fun playEpisode(series: SeriesDetail, seasonNumber: Int, episode: EpisodeItem) {
        val effectiveSlug = series.slug.ifBlank { currentSlug ?: "" }
        val epLabel = "S${seasonNumber}E${episode.episodeNumber}: ${episode.name}"
        val episodeTitle = "${series.title} - $epLabel"
        startStreamPlayback(
            contentType = "episode",
            contentId = episode.id,
            title = episodeTitle,
            posterUrl = episode.fullStillUrl.ifBlank { series.fullPosterUrl },
            backdropUrl = series.fullBackdropUrl,
            slug = effectiveSlug.takeIf { it.isNotBlank() },
            episodeTitle = epLabel,
            seasonNumber = seasonNumber,
            episodeNumber = episode.episodeNumber
        )
    }

    fun retryPlayback() {
        if (currentContentId.isNotBlank()) {
            startStreamPlayback(
                contentType = currentContentType,
                contentId = currentContentId,
                title = currentContentTitle,
                posterUrl = currentPosterUrl,
                backdropUrl = currentBackdropUrl,
                slug = currentSlug,
                episodeTitle = currentEpisodeTitle,
                seasonNumber = currentSeasonNumber,
                episodeNumber = currentEpisodeNumber
            )
        }
    }

    private fun startStreamPlayback(
        contentType: String,
        contentId: String,
        title: String,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        slug: String? = null,
        episodeTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        // Cancel in-flight playback and resolve jobs to prevent race conditions and double play
        playbackJob?.cancel()
        resolveNextEpisodeJob?.cancel()
        resolveNextEpisodeJob = null

        currentContentId = contentId
        currentContentType = contentType
        currentContentTitle = title
        currentPosterUrl = posterUrl
        currentBackdropUrl = backdropUrl
        currentSlug = slug
        currentEpisodeTitle = episodeTitle
        currentSeasonNumber = seasonNumber
        currentEpisodeNumber = episodeNumber

        val savedPos = historyStore.getSavedPosition(contentId)

        historyStore.recordStartPlayback(
            contentId = contentId,
            contentType = contentType,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            slug = slug,
            episodeTitle = episodeTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )

        playbackJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isStreamLoading = true,
                countdownSeconds = 0,
                streamError = null,
                resolvedStream = null,
                initialPositionMs = savedPos,
                nextEpisode = null,
                isNextEpisodeDismissed = false
            )

            // Asynchronously resolve next episode for Series
            if (contentType == "episode" && !slug.isNullOrBlank()) {
                resolveNextEpisodeJob = resolveNextEpisode(
                    seriesSlug = slug,
                    currentEpisodeId = contentId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    fallbackTitle = title,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl
                )
            }

            // Offline-first playback: if downloaded and local playlist exists, play immediately without countdown or network
            val offlineItem = downloadStore.getById(contentId)
            if (offlineItem != null && offlineItem.isPlayableOffline) {
                val localPlaylistFile = File(offlineItem.localPlaylistPath!!)
                val localMasterUri = Uri.fromFile(localPlaylistFile).toString()
                val localResolved = ResolvedPlayback(
                    title = offlineItem.title.ifBlank { title },
                    hlsMasterUrl = localMasterUri,
                    subtitles = offlineItem.localSubtitles,
                    durationSec = offlineItem.durationSec,
                    videoId = offlineItem.contentId
                )
                val prefLang = com.idlix.data.util.LocaleHelper.getPreferredLanguage()
                val fallbackLang = if (prefLang == "id") "en" else "id"
                val defaultSub = localResolved.subtitles.firstOrNull { it.lang.equals(prefLang, ignoreCase = true) }
                    ?: localResolved.subtitles.firstOrNull { it.lang.equals(fallbackLang, ignoreCase = true) }
                    ?: localResolved.subtitles.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    isStreamLoading = false,
                    countdownSeconds = 0,
                    streamError = null,
                    resolvedStream = localResolved,
                    selectedSubtitle = defaultSub,
                    isSubtitleEnabled = defaultSub != null
                )
                return@launch
            }

            val result = repository.resolveStream(
                contentType = contentType,
                contentId = contentId,
                contentTitle = title,
                onCountdown = { sec ->
                    _uiState.value = _uiState.value.copy(countdownSeconds = sec)
                }
            )

            result.onSuccess { resolved ->
                val prefLang = com.idlix.data.util.LocaleHelper.getPreferredLanguage()
                val fallbackLang = if (prefLang == "id") "en" else "id"
                val defaultSub = resolved.subtitles.firstOrNull { it.lang.equals(prefLang, ignoreCase = true) }
                    ?: resolved.subtitles.firstOrNull { it.lang.equals(fallbackLang, ignoreCase = true) }
                    ?: resolved.subtitles.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    isStreamLoading = false,
                    resolvedStream = resolved,
                    selectedSubtitle = defaultSub,
                    isSubtitleEnabled = defaultSub != null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isStreamLoading = false,
                    streamError = err.localizedMessage ?: "Failed to play stream"
                )
            }
        }
    }

    private fun resolveNextEpisode(
        seriesSlug: String,
        currentEpisodeId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        fallbackTitle: String,
        posterUrl: String?,
        backdropUrl: String?
    ): Job {
        return viewModelScope.launch {
            try {
                val series = try {
                    repository.getSeriesDetail(seriesSlug)
                } catch (_: Exception) {
                    null
                }
                if (series == null) {
                    val offlineEps = downloadStore.getBySlug(seriesSlug)
                        .filter { it.contentType == "episode" && it.isPlayableOffline }
                        .sortedWith(compareBy<OfflineDownloadItem> { it.seasonNumber ?: 1 }.thenBy { it.episodeNumber ?: 1 })
                    val curIdx = offlineEps.indexOfFirst {
                        it.contentId == currentEpisodeId ||
                            ((seasonNumber != null && it.seasonNumber == seasonNumber) &&
                                (episodeNumber != null && it.episodeNumber == episodeNumber))
                    }
                    if (curIdx >= 0 && curIdx + 1 < offlineEps.size) {
                        val nextDl = offlineEps[curIdx + 1]
                        val nextEp = EpisodeItem(
                            id = nextDl.contentId,
                            episodeNumber = nextDl.episodeNumber ?: 1,
                            name = nextDl.episodeTitle?.substringAfter(": ")
                                ?: nextDl.title.substringAfter(": ", "Episode ${nextDl.episodeNumber ?: 1}"),
                            overview = nextDl.overview,
                            stillPath = nextDl.localPosterPath ?: nextDl.posterUrl,
                            runtime = nextDl.runtimeMin
                        )
                        _uiState.value = _uiState.value.copy(
                            nextEpisode = NextEpisodeInfo(
                                episode = nextEp,
                                seasonNumber = nextDl.seasonNumber ?: 1,
                                seriesTitle = nextDl.seriesTitle ?: fallbackTitle.substringBefore(" -"),
                                seriesSlug = seriesSlug,
                                posterUrl = nextDl.localPosterPath ?: nextDl.posterUrl ?: posterUrl,
                                backdropUrl = nextDl.localPosterPath ?: nextDl.backdropUrl ?: backdropUrl
                            )
                        )
                    }
                    return@launch
                }
                val defaultSeason = series.defaultSeason
                val seriesTitle = series.title.ifBlank { fallbackTitle.substringBefore(" -") }
                val rawSeasons = series.seasons.ifEmpty {
                    if (defaultSeason != null) {
                        listOf(Season(seasonNumber = defaultSeason.seasonNumber, name = defaultSeason.name))
                    } else emptyList()
                }
                val allSeasons = rawSeasons.sortedBy { it.seasonNumber }

                var currentSeasonNum = seasonNumber
                var currentEpNum = episodeNumber
                var seasonEpisodes: List<EpisodeItem> = emptyList()

                // Strategy 1: Check if currentEpisodeId is in defaultSeason
                if (defaultSeason?.episodes?.any { it.id == currentEpisodeId } == true) {
                    currentSeasonNum = defaultSeason.seasonNumber
                    seasonEpisodes = defaultSeason.episodes
                } else if (currentSeasonNum != null && defaultSeason?.seasonNumber == currentSeasonNum && !defaultSeason.episodes.isNullOrEmpty()) {
                    seasonEpisodes = defaultSeason.episodes
                } else if (currentSeasonNum != null) {
                    val epDetail = try {
                        repository.getEpisodeDetail(seriesSlug, currentSeasonNum, 1)
                    } catch (_: Exception) {
                        null
                    }
                    seasonEpisodes = epDetail?.season?.episodes ?: emptyList()
                }

                // Strategy 2: If seasonEpisodes is still empty or currentEpisodeId not found, scan all seasons
                if (seasonEpisodes.isEmpty() || (currentEpisodeId.isNotBlank() && seasonEpisodes.none { it.id == currentEpisodeId } && currentEpNum == null)) {
                    for (s in allSeasons) {
                        if (s.seasonNumber == defaultSeason?.seasonNumber && !defaultSeason.episodes.isNullOrEmpty()) {
                            if (defaultSeason.episodes.any { it.id == currentEpisodeId }) {
                                currentSeasonNum = s.seasonNumber
                                seasonEpisodes = defaultSeason.episodes
                                break
                            }
                        } else {
                            val epDetail = try {
                                repository.getEpisodeDetail(seriesSlug, s.seasonNumber, 1)
                            } catch (_: Exception) {
                                null
                            }
                            val eps = epDetail?.season?.episodes ?: emptyList()
                            if (eps.any { it.id == currentEpisodeId }) {
                                currentSeasonNum = s.seasonNumber
                                seasonEpisodes = eps
                                break
                            }
                        }
                    }
                }

                if (seasonEpisodes.isEmpty()) return@launch

                // Find index of current episode in this season
                var currentIndex = seasonEpisodes.indexOfFirst { it.id == currentEpisodeId }
                if (currentIndex == -1 && currentEpNum != null) {
                    currentIndex = seasonEpisodes.indexOfFirst { it.episodeNumber == currentEpNum }
                }

                if (currentIndex != -1 && currentEpNum == null) {
                    currentEpNum = seasonEpisodes[currentIndex].episodeNumber
                }

                if (currentIndex != -1 && currentIndex + 1 < seasonEpisodes.size) {
                    // Next episode is in the same season!
                    val nextEp = seasonEpisodes[currentIndex + 1]
                    _uiState.value = _uiState.value.copy(
                        nextEpisode = NextEpisodeInfo(
                            episode = nextEp,
                            seasonNumber = currentSeasonNum ?: 1,
                            seriesTitle = seriesTitle,
                            seriesSlug = seriesSlug,
                            posterUrl = nextEp.fullStillUrl.ifBlank { posterUrl },
                            backdropUrl = backdropUrl ?: series.fullBackdropUrl
                        )
                    )
                } else if (currentSeasonNum != null) {
                    // Check if there is a next season
                    val nextSeason = allSeasons.firstOrNull { it.seasonNumber > currentSeasonNum }
                    if (nextSeason != null) {
                        val nextSeasonDetail = try {
                            repository.getEpisodeDetail(seriesSlug, nextSeason.seasonNumber, 1)
                        } catch (_: Exception) {
                            null
                        }
                        val nextEp = nextSeasonDetail?.season?.episodes?.firstOrNull()
                            ?: nextSeasonDetail?.episode
                        if (nextEp != null) {
                            _uiState.value = _uiState.value.copy(
                                nextEpisode = NextEpisodeInfo(
                                    episode = nextEp,
                                    seasonNumber = nextSeason.seasonNumber,
                                    seriesTitle = seriesTitle,
                                    seriesSlug = seriesSlug,
                                    posterUrl = nextEp.fullStillUrl.ifBlank { posterUrl },
                                    backdropUrl = backdropUrl ?: series.fullBackdropUrl
                                )
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(nextEpisode = null)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(nextEpisode = null)
                    }
                }
            } catch (e: Exception) {
                Log.w("DetailViewModel", "Failed to resolve next episode: ${e.message}")
            }
        }
    }

    fun dismissNextEpisode() {
        _uiState.value = _uiState.value.copy(isNextEpisodeDismissed = true)
    }

    fun playNextEpisode() {
        val next = _uiState.value.nextEpisode ?: return
        val series = _uiState.value.series ?: return
        _uiState.value = _uiState.value.copy(
            selectedEpisode = next.episode,
            playingEpisode = next.episode,
            playingSeasonNumber = next.seasonNumber
        )
        playEpisode(series, next.seasonNumber, next.episode)
    }

    private var sessionQueryOverride: SubdlQuery? = null
    private var subdlSearchJob: Job? = null

    fun openSubtitlePicker() {
        _uiState.value = _uiState.value.copy(
            isSubtitlePickerVisible = true,
            isManualSearchOpen = false,
            isAppearancePanelOpen = false
        )
        if (_uiState.value.subdlSubtitles.isEmpty() && !_uiState.value.isSearchingSubdl) {
            searchSubdlSubtitles()
        }
    }

    fun closeSubtitlePicker() {
        _uiState.value = _uiState.value.copy(
            isSubtitlePickerVisible = false,
            isManualSearchOpen = false,
            isAppearancePanelOpen = false
        )
    }

    fun searchSubdlSubtitles(queryOverride: SubdlQuery? = null) {
        val targetQuery = queryOverride ?: sessionQueryOverride ?: run {
            val title = currentContentTitle
            if (title.isBlank()) return
            val cleaned = SubdlTitleCleaner.clean(title)
            SubdlQuery(
                filmName = cleaned.title,
                year = cleaned.year,
                type = if (currentContentType == "episode") "tv" else "movie",
                season = currentSeasonNumber,
                episode = currentEpisodeNumber
            )
        }

        subdlSearchJob?.cancel()
        subdlSearchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingSubdl = true,
                subdlActiveQuery = targetQuery,
                subdlError = null,
                isUsingSessionOverride = sessionQueryOverride != null
            )
            when (val result = SubdlRepository.search(targetQuery)) {
                is SubdlSearchResult.Found -> {
                    if (queryOverride != null) {
                        sessionQueryOverride = queryOverride
                    }
                    _uiState.value = _uiState.value.copy(
                        isSearchingSubdl = false,
                        subdlSubtitles = result.subtitles,
                        subdlCandidates = result.candidates,
                        subdlError = null,
                        isUsingSessionOverride = sessionQueryOverride != null
                    )
                }
                is SubdlSearchResult.NotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isSearchingSubdl = false,
                        subdlSubtitles = emptyList(),
                        subdlCandidates = result.candidates,
                        subdlError = null,
                        isUsingSessionOverride = sessionQueryOverride != null
                    )
                }
                is SubdlSearchResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSearchingSubdl = false,
                        subdlSubtitles = emptyList(),
                        subdlCandidates = emptyList(),
                        subdlError = result,
                        isUsingSessionOverride = sessionQueryOverride != null
                    )
                }
            }
        }
    }

    fun openManualSearch() {
        val defaultCleaned = SubdlTitleCleaner.clean(currentContentTitle)
        val initialTitle = sessionQueryOverride?.filmName ?: defaultCleaned.title
        val initialYear = (sessionQueryOverride?.year ?: defaultCleaned.year)?.toString() ?: ""
        val initialSeason = (sessionQueryOverride?.season ?: currentSeasonNumber)?.toString() ?: ""
        val initialEpisode = (sessionQueryOverride?.episode ?: currentEpisodeNumber)?.toString() ?: ""

        _uiState.value = _uiState.value.copy(
            isManualSearchOpen = true,
            isAppearancePanelOpen = false,
            manualTitle = initialTitle,
            manualYear = initialYear,
            manualSeason = initialSeason,
            manualEpisode = initialEpisode
        )
    }

    fun closeManualSearch() {
        _uiState.value = _uiState.value.copy(isManualSearchOpen = false)
    }

    fun updateManualTitle(title: String) {
        _uiState.value = _uiState.value.copy(manualTitle = title)
    }

    fun updateManualYear(year: String) {
        _uiState.value = _uiState.value.copy(manualYear = year)
    }

    fun updateManualSeason(season: String) {
        _uiState.value = _uiState.value.copy(manualSeason = season)
    }

    fun updateManualEpisode(episode: String) {
        _uiState.value = _uiState.value.copy(manualEpisode = episode)
    }

    fun submitManualSearch() {
        val title = _uiState.value.manualTitle.trim()
        if (title.length < 2) return

        val yearInt = _uiState.value.manualYear.trim().toIntOrNull()
        val seasonInt = _uiState.value.manualSeason.trim().toIntOrNull()
        val episodeInt = _uiState.value.manualEpisode.trim().toIntOrNull()

        val query = SubdlQuery(
            filmName = title,
            year = yearInt,
            type = if (currentContentType == "episode" || seasonInt != null) "tv" else "movie",
            season = seasonInt,
            episode = episodeInt
        )

        _uiState.value = _uiState.value.copy(isManualSearchOpen = false)
        searchSubdlSubtitles(queryOverride = query)
    }

    fun selectSubdlCandidate(candidate: SubdlItem) {
        val query = SubdlQuery(
            sdId = candidate.sd_id,
            filmName = candidate.name,
            year = candidate.year,
            type = candidate.type ?: if (currentContentType == "episode") "tv" else "movie",
            season = currentSeasonNumber,
            episode = currentEpisodeNumber
        )
        _uiState.value = _uiState.value.copy(isManualSearchOpen = false)
        searchSubdlSubtitles(queryOverride = query)
    }

    fun restoreOriginalTitle() {
        sessionQueryOverride = null
        _uiState.value = _uiState.value.copy(
            isUsingSessionOverride = false,
            isManualSearchOpen = false
        )
        searchSubdlSubtitles(queryOverride = null)
    }

    fun openAppearancePanel() {
        _uiState.value = _uiState.value.copy(
            isAppearancePanelOpen = true,
            isManualSearchOpen = false
        )
    }

    fun closeAppearancePanel() {
        _uiState.value = _uiState.value.copy(isAppearancePanelOpen = false)
    }

    fun selectSubtitleTrack(track: SubtitleTrack?) {
        _uiState.value = _uiState.value.copy(
            selectedSubtitle = track,
            isSubtitleEnabled = track != null,
            isSubtitlePickerVisible = false
        )
    }

    fun selectSubdlSubtitle(
        sub: SubdlSubtitle,
        onSubtitleReady: (SubtitleTrack) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingSubdl = true)
            val downloaded = SubdlRepository.downloadSubtitle(
                sub = sub,
                context = getApplication<Application>().applicationContext
            )
            _uiState.value = _uiState.value.copy(isSearchingSubdl = false)

            if (downloaded != null) {
                val currentStream = _uiState.value.resolvedStream
                if (currentStream != null) {
                    val updatedSubs = currentStream.subtitles.toMutableList()
                    updatedSubs.removeAll { it.path == downloaded.path }
                    updatedSubs.add(0, downloaded)
                    _uiState.value = _uiState.value.copy(
                        resolvedStream = currentStream.copy(subtitles = updatedSubs),
                        selectedSubtitle = downloaded,
                        isSubtitleEnabled = true,
                        isSubtitlePickerVisible = false
                    )
                }
                onSubtitleReady(downloaded)
            }
        }
    }

    fun toggleSubtitle() {
        val current = _uiState.value.isSubtitleEnabled
        _uiState.value = _uiState.value.copy(isSubtitleEnabled = !current)
    }

    fun saveProgress(currentPosMs: Long, durationMs: Long) {
        if (currentContentId.isNotBlank() && durationMs > 0) {
            historyStore.updateProgress(currentContentId, currentPosMs, durationMs)
        }
    }

    fun downloadMovie(movie: MovieDetail) {
        val effectiveSlug = movie.slug.ifBlank { currentSlug ?: movie.id }
        OfflineDownloadManager.enqueueDownload(
            context = getApplication(),
            contentId = movie.id,
            contentType = "movie",
            slug = effectiveSlug,
            title = movie.title,
            overview = movie.overview,
            year = movie.releaseDate?.take(4),
            rating = movie.voteAverage,
            runtimeMin = movie.runtime,
            posterUrl = movie.fullPosterUrl,
            backdropUrl = movie.fullBackdropUrl
        )
    }

    fun downloadEpisode(episode: EpisodeItem) {
        val series = _uiState.value.series ?: return
        val seasonNum = _uiState.value.selectedSeason?.seasonNumber ?: 1
        val effectiveSlug = series.slug.ifBlank { currentSlug ?: series.id }
        val epLabel = "S${seasonNum}E${episode.episodeNumber}: ${episode.name}"
        val fullTitle = "${series.title} - $epLabel"

        OfflineDownloadManager.enqueueDownload(
            context = getApplication(),
            contentId = episode.id,
            contentType = "episode",
            slug = effectiveSlug,
            title = fullTitle,
            seriesTitle = series.title,
            episodeTitle = epLabel,
            seasonNumber = seasonNum,
            episodeNumber = episode.episodeNumber,
            overview = episode.overview?.takeIf { it.isNotBlank() } ?: series.overview,
            year = series.firstAirDate?.take(4),
            rating = series.voteAverage,
            runtimeMin = episode.runtime,
            posterUrl = episode.fullStillUrl.ifBlank { series.fullPosterUrl },
            backdropUrl = series.fullBackdropUrl
        )
    }

    fun pauseDownload(contentId: String) {
        OfflineDownloadManager.pauseDownload(getApplication(), contentId)
    }

    fun resumeDownload(contentId: String) {
        OfflineDownloadManager.resumeDownload(getApplication(), contentId)
    }

    fun cancelOrRemoveDownload(contentId: String) {
        OfflineDownloadManager.cancelAndRemoveDownload(getApplication(), contentId)
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        resolveNextEpisodeJob?.cancel()
    }
}
