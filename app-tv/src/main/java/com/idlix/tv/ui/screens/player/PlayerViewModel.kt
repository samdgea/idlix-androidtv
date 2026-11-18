package com.idlix.tv.ui.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.PlaybackVerificationChallenge
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.Season
import com.idlix.data.model.SubdlItem
import com.idlix.data.model.SubdlQuery
import com.idlix.data.model.SubdlSearchResult
import com.idlix.data.model.SubdlSubtitle
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.repository.IdlixRepository
import com.idlix.data.repository.SubdlRepository
import com.idlix.data.util.SubdlTitleCleaner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NextEpisodeInfo(
    val episode: EpisodeItem,
    val seasonNumber: Int,
    val seriesTitle: String,
    val seriesSlug: String,
    val posterUrl: String?,
    val backdropUrl: String?
)

data class PlayerUiState(
    val isLoading: Boolean = true,
    val countdownSeconds: Int = 0,
    val resolvedStream: ResolvedPlayback? = null,
    val selectedSubtitle: SubtitleTrack? = null,
    val isSubtitleEnabled: Boolean = true,
    val initialPositionMs: Long = 0L,
    val error: String? = null,
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
    val nextEpisode: NextEpisodeInfo? = null,
    val isNextEpisodeDismissed: Boolean = false,
    val verificationChallenge: PlaybackVerificationChallenge? = null
)

class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = IdlixRepository()
    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentContentId: String = ""
    private var currentContentType: String = ""
    private var currentContentTitle: String = ""
    private var currentPosterUrl: String? = null
    private var currentBackdropUrl: String? = null
    private var currentSlug: String? = null
    private var currentSeasonNumber: Int? = null
    private var currentEpisodeNumber: Int? = null
    private var loadStreamJob: Job? = null
    private var pendingVerificationDeferred: CompletableDeferred<String?>? = null

    fun submitVerificationToken(token: String) {
        _uiState.value = _uiState.value.copy(verificationChallenge = null)
        pendingVerificationDeferred?.complete(token)
        pendingVerificationDeferred = null
    }

    fun cancelVerification() {
        _uiState.value = _uiState.value.copy(verificationChallenge = null)
        pendingVerificationDeferred?.complete(null)
        pendingVerificationDeferred = null
    }

    override fun onCleared() {
        pendingVerificationDeferred?.cancel()
        pendingVerificationDeferred = null
        super.onCleared()
    }

    fun loadStream(
        contentType: String,
        contentId: String,
        title: String,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        slug: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        loadStreamJob?.cancel()
        pendingVerificationDeferred?.cancel()
        pendingVerificationDeferred = null

        currentContentId = contentId
        currentContentType = contentType
        currentContentTitle = title
        currentPosterUrl = posterUrl
        currentBackdropUrl = backdropUrl
        currentSlug = slug
        currentSeasonNumber = seasonNumber
        currentEpisodeNumber = episodeNumber

        val savedPos = historyStore.getSavedPosition(contentId)

        // Immediately record in history so it tracks the title and images
        historyStore.recordStartPlayback(
            contentId = contentId,
            contentType = contentType,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            slug = slug,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )

        loadStreamJob = viewModelScope.launch {
            _uiState.value = PlayerUiState(
                isLoading = true,
                initialPositionMs = savedPos,
                nextEpisode = null,
                isNextEpisodeDismissed = false,
                verificationChallenge = null
            )

            // Asynchronously resolve the next episode in background for TV Series
            if (contentType == "episode" && !slug.isNullOrBlank()) {
                resolveNextEpisode(
                    seriesSlug = slug,
                    currentEpisodeId = contentId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    fallbackTitle = title,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl
                )
            }

            val result = repository.resolveStream(
                contentType = contentType,
                contentId = contentId,
                contentTitle = title,
                onCountdown = { sec ->
                    _uiState.value = _uiState.value.copy(countdownSeconds = sec)
                },
                onVerificationRequired = { challenge ->
                    val deferred = CompletableDeferred<String?>()
                    pendingVerificationDeferred = deferred
                    _uiState.value = _uiState.value.copy(verificationChallenge = challenge)
                    try {
                        deferred.await()
                    } finally {
                        if (pendingVerificationDeferred === deferred) {
                            pendingVerificationDeferred = null
                        }
                        _uiState.value = _uiState.value.copy(verificationChallenge = null)
                    }
                }
            )

            result.onSuccess { resolved ->
                val prefLang = com.idlix.data.util.LocaleHelper.getPreferredLanguage()
                val fallbackLang = if (prefLang == "id") "en" else "id"
                val defaultSub = resolved.subtitles.firstOrNull { it.lang.equals(prefLang, ignoreCase = true) }
                    ?: resolved.subtitles.firstOrNull { it.lang.equals(fallbackLang, ignoreCase = true) }
                    ?: resolved.subtitles.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resolvedStream = resolved,
                    selectedSubtitle = defaultSub,
                    isSubtitleEnabled = defaultSub != null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = err.localizedMessage ?: "Failed to play video"
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
    ) {
        viewModelScope.launch {
            try {
                val series = repository.getSeriesDetail(seriesSlug) ?: return@launch
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
                        // Load episode 1 of next season
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
                        // Final episode of final season: stop completely
                        _uiState.value = _uiState.value.copy(nextEpisode = null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PlayerViewModel", "Failed to resolve next episode: ${e.message}")
            }
        }
    }

    fun dismissNextEpisode() {
        _uiState.value = _uiState.value.copy(isNextEpisodeDismissed = true)
    }

    fun playNextEpisode(
        onReadyToPlay: ((NextEpisodeInfo) -> Unit)? = null
    ) {
        val next = _uiState.value.nextEpisode ?: return
        val nextTitle = "${next.seriesTitle} - ${next.episode.name}"
        loadStream(
            contentType = "episode",
            contentId = next.episode.id,
            title = nextTitle,
            posterUrl = next.posterUrl,
            backdropUrl = next.backdropUrl,
            slug = next.seriesSlug,
            seasonNumber = next.seasonNumber,
            episodeNumber = next.episode.episodeNumber
        )
        onReadyToPlay?.invoke(next)
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
}
