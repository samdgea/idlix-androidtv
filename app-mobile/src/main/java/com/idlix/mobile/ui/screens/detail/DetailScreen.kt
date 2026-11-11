package com.idlix.mobile.ui.screens.detail

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.idlix.data.model.CastMember
import com.idlix.data.model.EpisodeItem
import com.idlix.mobile.R
import com.idlix.mobile.ui.screens.detail.components.MobilePlayerView
import com.idlix.mobile.ui.screens.detail.components.SubtitleModalBottomSheet
import com.idlix.mobile.ui.screens.detail.components.findActivity
import com.idlix.mobile.ui.screens.settings.SubtitlePositionEditorScreen
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.BackgroundElevated
import com.idlix.mobile.ui.theme.GoldStar
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextSecondary
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun DetailScreen(
    contentType: String,
    slug: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    resumeContentId: String? = null,
    resumeSeasonNumber: Int? = null,
    resumeEpisodeNumber: Int? = null,
    viewModel: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var isOverviewExpanded by remember { mutableStateOf(false) }
    var isBookmarked by rememberSaveable { mutableStateOf(false) }
    var isEditingPosition by remember { mutableStateOf(false) }

    val activity = remember(context) { context.findActivity() }

    // Load media detail
    LaunchedEffect(contentType, slug, resumeContentId) {
        viewModel.loadDetail(
            contentType = contentType,
            slug = slug,
            autoPlay = autoPlay,
            resumeContentId = resumeContentId,
            resumeSeasonNumber = resumeSeasonNumber,
            resumeEpisodeNumber = resumeEpisodeNumber
        )
    }

    // Handle orientation & system bars for Fullscreen Mode
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Cleanup orientation and system bars on exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Intercept Back Press: If editing position, close editor; if fullscreen, exit fullscreen; if inline, pop back
    BackHandler(enabled = isEditingPosition || isFullscreen) {
        if (isEditingPosition) {
            isEditingPosition = false
        } else {
            isFullscreen = false
        }
    }

    val title = uiState.movie?.title ?: uiState.series?.title ?: ""
    val overview = uiState.movie?.overview ?: uiState.series?.overview ?: ""
    val rawVoteAverage = uiState.movie?.voteAverage ?: uiState.series?.voteAverage
    val rating = rawVoteAverage?.toDoubleOrNull()?.let { String.format("%.1f", it) } ?: "N/A"
    val year = (uiState.movie?.releaseDate ?: uiState.series?.firstAirDate)?.take(4) ?: ""
    val durationText = uiState.movie?.runtime?.let { "$it min" }
        ?: uiState.series?.numberOfSeasons?.let { "$it Seasons" }
        ?: ""
    val genres = (uiState.movie?.genres ?: uiState.series?.genres ?: emptyList()).map { it.name }
    val castList = uiState.movie?.cast ?: uiState.series?.cast ?: emptyList()

    val currentSubtitle = if (uiState.series != null) {
        val playingEp = uiState.playingEpisode
        val playingSNum = uiState.playingSeasonNumber
        if (playingEp != null) {
            "S${playingSNum ?: (uiState.selectedSeason?.seasonNumber ?: 1)}E${playingEp.episodeNumber}: ${playingEp.name}"
        } else if (uiState.selectedEpisode != null) {
            val sNum = uiState.selectedSeason?.seasonNumber ?: 1
            "S${sNum}E${uiState.selectedEpisode!!.episodeNumber}: ${uiState.selectedEpisode!!.name}"
        } else {
            null
        }
    } else {
        null
    }

    val previewImageUrl = if (uiState.series != null) {
        uiState.selectedEpisode?.fullStillUrl?.takeIf { it.isNotBlank() }
            ?: uiState.series?.fullBackdropUrl
            ?: uiState.series?.fullPosterUrl
    } else {
        uiState.movie?.fullBackdropUrl ?: uiState.movie?.fullPosterUrl
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Player Box (Inline 16:9 or Fullscreen Landscape)
            Box(
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                }
            ) {
                MobilePlayerView(
                    title = title,
                    subtitle = currentSubtitle,
                    resolvedStream = uiState.resolvedStream,
                    isStreamLoading = uiState.isStreamLoading,
                    countdownSeconds = uiState.countdownSeconds,
                    streamError = uiState.streamError,
                    selectedSubtitle = uiState.selectedSubtitle,
                    isSubtitleEnabled = uiState.isSubtitleEnabled,
                    initialPositionMs = uiState.initialPositionMs,
                    isFullscreen = isFullscreen,
                    previewImageUrl = previewImageUrl,
                    onPlayClick = { viewModel.playCurrentMedia() },
                    nextEpisode = uiState.nextEpisode,
                    isNextEpisodeDismissed = uiState.isNextEpisodeDismissed,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onOpenSubtitlePicker = { viewModel.openSubtitlePicker() },
                    onBackClick = {
                        if (isFullscreen) {
                            isFullscreen = false
                        } else {
                            onBackClick()
                        }
                    },
                    onPlayNextEpisode = { viewModel.playNextEpisode() },
                    onDismissNextEpisode = { viewModel.dismissNextEpisode() },
                    onRetryPlayback = { viewModel.retryPlayback() },
                    onSaveProgress = { pos, dur -> viewModel.saveProgress(pos, dur) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Scrollable Content (hidden when in fullscreen)
            if (!isFullscreen) {
                if (uiState.isLoadingDetail) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = IdlixRed, strokeWidth = 2.dp)
                    }
                } else if (uiState.detailError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.detailError ?: stringResource(R.string.loading_error),
                            color = TextWhite,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    // Scrollable Media Metadata, Episodes, and Cast
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Title
                        item {
                            Text(
                                text = title,
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Badges Row (Year, Rating, Duration, Quality)
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (year.isNotBlank()) {
                                    Text(text = year, color = TextMuted, fontSize = 13.sp)
                                }

                                if (rating != "N/A") {
                                    Surface(
                                        color = GoldStar.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = GoldStar,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = rating,
                                                color = GoldStar,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (durationText.isNotBlank()) {
                                    Text(text = durationText, color = TextMuted, fontSize = 13.sp)
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "HD",
                                        color = TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Primary Play / Watch Now CTA Button
                        item {
                            Button(
                                onClick = { viewModel.playCurrentMedia() },
                                colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val buttonText = if (uiState.isStreamLoading) {
                                    stringResource(R.string.connecting_to_media)
                                } else if (uiState.series != null) {
                                    val playingEp = uiState.playingEpisode
                                    val selectedEp = uiState.selectedEpisode ?: uiState.episodes.firstOrNull()
                                    if (uiState.resolvedStream != null && playingEp != null) {
                                        "Sedang Diputar: S${uiState.playingSeasonNumber ?: (uiState.selectedSeason?.seasonNumber ?: 1)}E${playingEp.episodeNumber}"
                                    } else if (selectedEp != null) {
                                        "Putar S${uiState.selectedSeason?.seasonNumber ?: 1}E${selectedEp.episodeNumber}: ${selectedEp.name}"
                                    } else {
                                        stringResource(R.string.play_now)
                                    }
                                } else {
                                    if (uiState.resolvedStream != null) {
                                        "Sedang Diputar"
                                    } else {
                                        stringResource(R.string.play_now)
                                    }
                                }
                                Text(
                                    text = buttonText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Actions Row (Watchlist, Share)
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isBookmarked = !isBookmarked }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = if (isBookmarked) IdlixRed else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isBookmarked) stringResource(R.string.in_my_list) else stringResource(R.string.my_list),
                                            color = TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val shareTitle = context.getString(R.string.share)
                                            val shareType = if (uiState.series != null) "series" else "movie"
                                            val shareSlug = uiState.series?.slug?.ifBlank { slug }
                                                ?: uiState.movie?.slug?.ifBlank { slug }
                                                ?: slug
                                            val shareText = context.getString(R.string.share_movie_text, title, "https://idlixku.com/$shareType/$shareSlug")
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, shareTitle))
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.share),
                                            color = TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Genres Chips
                        if (genres.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    genres.forEach { genre ->
                                        Surface(
                                            color = Color.White.copy(alpha = 0.06f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Overview (Expandable)
                        if (overview.isNotBlank()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .clickable { isOverviewExpanded = !isOverviewExpanded }
                                ) {
                                    Text(
                                        text = overview,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isOverviewExpanded) "Tutup" else "Selengkapnya",
                                        color = IdlixRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // TV Series: Season Selector & Episodes List
                        if (uiState.series != null) {
                            val seasons = uiState.series!!.seasons
                            if (seasons.isNotEmpty()) {
                                item {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color.White.copy(alpha = 0.1f)
                                    )

                                    Text(
                                        text = stringResource(R.string.episodes),
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Season Chips Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        seasons.forEach { season ->
                                            val isSelected = uiState.selectedSeason?.seasonNumber == season.seasonNumber
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.selectSeason(season) },
                                                label = {
                                                    Text(
                                                        text = season.name.ifBlank { "Season ${season.seasonNumber}" },
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = IdlixRed,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color.White.copy(alpha = 0.08f),
                                                    labelColor = TextSecondary
                                                ),
                                                border = null
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Episode Cards
                            items(uiState.episodes) { episode ->
                                val isSelected = (uiState.playingEpisode?.id ?: uiState.selectedEpisode?.id) == episode.id
                                EpisodeCardItem(
                                    episode = episode,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectEpisode(episode) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Cast Section
                        if (castList.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = Color.White.copy(alpha = 0.1f)
                                )

                                Text(
                                    text = stringResource(R.string.cast),
                                    color = TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(castList) { cast ->
                                        CastItem(cast = cast)
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // Subtitle Picker Modal Bottom Sheet
        SubtitleModalBottomSheet(
            isVisible = uiState.isSubtitlePickerVisible,
            availableSubtitles = uiState.resolvedStream?.subtitles ?: emptyList(),
            selectedSubtitle = uiState.selectedSubtitle,
            isSubtitleEnabled = uiState.isSubtitleEnabled,
            subdlSubtitles = uiState.subdlSubtitles,
            isSearchingSubdl = uiState.isSearchingSubdl,
            subdlCandidates = uiState.subdlCandidates,
            subdlError = uiState.subdlError,
            subdlActiveQuery = uiState.subdlActiveQuery,
            isManualSearchOpen = uiState.isManualSearchOpen,
            manualTitle = uiState.manualTitle,
            manualYear = uiState.manualYear,
            manualSeason = uiState.manualSeason,
            manualEpisode = uiState.manualEpisode,
            isUsingSessionOverride = uiState.isUsingSessionOverride,
            isSeries = uiState.series != null,
            onToggleSubtitle = { viewModel.toggleSubtitle() },
            onSelectTrack = { viewModel.selectSubtitleTrack(it) },
            onSelectSubdl = { viewModel.selectSubdlSubtitle(it) {} },
            onSearchSubdl = { viewModel.searchSubdlSubtitles() },
            onOpenManualSearch = { viewModel.openManualSearch() },
            onCloseManualSearch = { viewModel.closeManualSearch() },
            onManualTitleChange = { viewModel.updateManualTitle(it) },
            onManualYearChange = { viewModel.updateManualYear(it) },
            onManualSeasonChange = { viewModel.updateManualSeason(it) },
            onManualEpisodeChange = { viewModel.updateManualEpisode(it) },
            onSubmitManualSearch = { viewModel.submitManualSearch() },
            onSelectCandidate = { viewModel.selectSubdlCandidate(it) },
            onRestoreOriginalTitle = { viewModel.restoreOriginalTitle() },
            onOpenPositionEditor = {
                viewModel.closeSubtitlePicker()
                isEditingPosition = true
            },
            onDismiss = { viewModel.closeSubtitlePicker() }
        )

        // Fullscreen in-player position editor overlay
        if (isEditingPosition) {
            SubtitlePositionEditorScreen(
                onBack = { isEditingPosition = false }
            )
        }
    }
}

@Composable
private fun EpisodeCardItem(
    episode: EpisodeItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) IdlixRed.copy(alpha = 0.15f) else BackgroundElevated,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.5.dp, IdlixRed) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Still Image
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = episode.fullStillUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = IdlixRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    color = if (isSelected) IdlixRed else TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val epOverview = episode.overview
                if (!epOverview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = epOverview,
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val epRuntime = episode.runtime
                if (epRuntime != null && epRuntime > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$epRuntime min",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CastItem(cast: CastMember) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = cast.fullProfileUrl,
            contentDescription = cast.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cast.name,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val castChar = cast.character
        if (!castChar.isNullOrBlank()) {
            Text(
                text = castChar,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
