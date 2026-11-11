package com.idlix.mobile.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.data.model.WatchHistoryItem
import com.idlix.mobile.R
import com.idlix.mobile.ui.components.ContinueWatchingSection
import com.idlix.mobile.ui.components.HeroBannerPager
import com.idlix.mobile.ui.components.MovieSwimlane
import com.idlix.mobile.ui.components.SeriesSwimlane
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun HomeScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onContinueWatchingClick: (WatchHistoryItem) -> Unit = { item ->
        val slug = item.slug?.takeIf { it.isNotBlank() } ?: item.contentId
        onNavigateToDetail(item.contentType, slug)
    },
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading && !uiState.isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IdlixRed)
            }
        } else if (uiState.error != null && uiState.bannerMovies.isEmpty() && uiState.continueWatching.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.unable_to_connect),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.error ?: "",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadData() },
                        colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Bar (Logo & Settings Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.idlix_logo),
                        contentDescription = "IDLIX Logo",
                        modifier = Modifier.height(28.dp)
                    )

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = TextWhite
                        )
                    }
                }

                if (uiState.bannerMovies.isNotEmpty()) {
                    HeroBannerPager(
                        movies = uiState.bannerMovies,
                        onItemClick = { slug -> onNavigateToDetail("movie", slug) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uiState.continueWatching.isNotEmpty()) {
                    ContinueWatchingSection(
                        items = uiState.continueWatching,
                        onItemClick = { item -> onContinueWatchingClick(item) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.bannerMovies.isEmpty() && uiState.popularMovies.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.error ?: stringResource(R.string.failed_load_recommendations),
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.loadData() },
                                colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.retry), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uiState.trending.isNotEmpty()) {
                    MovieSwimlane(
                        title = stringResource(R.string.trending_now),
                        movies = uiState.trending,
                        onMovieClick = { slug -> onNavigateToDetail("movie", slug) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.popularSeries.isNotEmpty()) {
                    SeriesSwimlane(
                        title = stringResource(R.string.popular_series),
                        seriesList = uiState.popularSeries,
                        onSeriesClick = { slug -> onNavigateToDetail("series", slug) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.popularMovies.isNotEmpty()) {
                    MovieSwimlane(
                        title = stringResource(R.string.popular_movies),
                        movies = uiState.popularMovies,
                        onMovieClick = { slug -> onNavigateToDetail("movie", slug) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.actionMovies.isNotEmpty()) {
                    MovieSwimlane(
                        title = stringResource(R.string.movies_action),
                        movies = uiState.actionMovies,
                        onMovieClick = { slug -> onNavigateToDetail("movie", slug) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.comedyMovies.isNotEmpty()) {
                    MovieSwimlane(
                        title = stringResource(R.string.movies_comedy),
                        movies = uiState.comedyMovies,
                        onMovieClick = { slug -> onNavigateToDetail("movie", slug) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
