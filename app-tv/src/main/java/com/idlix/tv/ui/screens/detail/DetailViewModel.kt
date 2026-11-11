package com.idlix.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.Season
import com.idlix.data.model.SeriesDetail
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val movie: MovieDetail? = null,
    val series: SeriesDetail? = null,
    val selectedSeason: Season? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val error: String? = null
)

class DetailViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(contentType: String, slug: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (contentType == "tv_series" || contentType == "series" || contentType == "episode") {
                    val rawSeries = repository.getSeriesDetail(slug)
                    val series = rawSeries?.copy(slug = rawSeries.slug.ifBlank { slug })
                    val defaultSeason = series?.defaultSeason
                    val defaultSeasonNum = defaultSeason?.seasonNumber
                    // Match selected season with defaultSeason, or fallback to first season
                    val initialSeason = series?.seasons?.firstOrNull { it.seasonNumber == defaultSeasonNum }
                        ?: defaultSeason?.let { Season(id = it.id, seasonNumber = it.seasonNumber, name = it.name) }
                        ?: series?.seasons?.firstOrNull()
                    val episodes = defaultSeason?.episodes ?: emptyList()

                    _uiState.value = DetailUiState(
                        isLoading = false,
                        series = series,
                        selectedSeason = initialSeason,
                        episodes = episodes
                    )
                } else {
                    val rawMovie = repository.getMovieDetail(slug)
                    val movie = rawMovie?.copy(slug = rawMovie.slug.ifBlank { slug })
                    _uiState.value = DetailUiState(
                        isLoading = false,
                        movie = movie
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Gagal memuat detail"
                )
            }
        }
    }

    fun selectSeason(seriesSlug: String, season: Season) {
        if (_uiState.value.selectedSeason?.id == season.id && _uiState.value.episodes.isNotEmpty() && !_uiState.value.isLoadingEpisodes) {
            return
        }
        viewModelScope.launch {
            val series = _uiState.value.series
            val defaultSeason = series?.defaultSeason
            // If selecting the default season and its episodes are already cached, use them directly
            if (defaultSeason != null && defaultSeason.seasonNumber == season.seasonNumber && defaultSeason.episodes.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    selectedSeason = season,
                    episodes = defaultSeason.episodes,
                    isLoadingEpisodes = false
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                selectedSeason = season,
                isLoadingEpisodes = true
            )
            try {
                // Fetch specific season episodes via episode 1 detail
                val epRes = repository.getEpisodeDetail(seriesSlug, season.seasonNumber, 1)
                val newEpisodes = epRes?.season?.episodes ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    isLoadingEpisodes = false,
                    episodes = if (newEpisodes.isNotEmpty()) newEpisodes else _uiState.value.episodes
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingEpisodes = false)
            }
        }
    }
}
