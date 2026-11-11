package com.idlix.mobile.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.idlix.data.model.WatchHistoryItem
import com.idlix.mobile.R
import com.idlix.mobile.download.DownloadStatus
import com.idlix.mobile.download.OfflineDownloadItem
import com.idlix.mobile.ui.components.ContinueWatchingCard
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.BorderDark
import com.idlix.mobile.ui.theme.GoldStar
import com.idlix.mobile.ui.theme.IdlixAccentCyan
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.SurfaceVariantDark
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextSecondary
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun LibraryScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onHistoryItemClick: (WatchHistoryItem) -> Unit = { item ->
        val slug = item.slug?.takeIf { it.isNotBlank() } ?: item.contentId
        onNavigateToDetail(item.contentType, slug)
    },
    onDownloadItemClick: (OfflineDownloadItem) -> Unit = { item ->
        val slug = item.slug.takeIf { it.isNotBlank() } ?: item.contentId
        onNavigateToDetail(item.contentType, slug)
    },
    onNavigateToSettings: () -> Unit,
    initialTab: String? = null,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearDownloadsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) {
        if (initialTab.equals("downloads", ignoreCase = true)) {
            viewModel.selectTab(LibraryTab.DOWNLOADS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library),
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Row {
                val showTrash = (uiState.selectedTab == LibraryTab.HISTORY && uiState.historyItems.isNotEmpty()) ||
                    (uiState.selectedTab == LibraryTab.DOWNLOADS && uiState.downloadItems.isNotEmpty())
                if (showTrash) {
                    IconButton(
                        onClick = {
                            if (uiState.selectedTab == LibraryTab.HISTORY) {
                                showClearHistoryDialog = true
                            } else {
                                showClearDownloadsDialog = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.clear_all),
                            tint = TextMuted
                        )
                    }
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = TextWhite
                    )
                }
            }
        }

        // Segmented Tab Bar (Watch History vs Offline Downloads)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(SurfaceDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isHistorySelected = uiState.selectedTab == LibraryTab.HISTORY
            val isDownloadsSelected = uiState.selectedTab == LibraryTab.DOWNLOADS

            Surface(
                color = if (isHistorySelected) IdlixRed else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.selectTab(LibraryTab.HISTORY) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = if (isHistorySelected) Color.White else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tab_watch_history),
                        color = if (isHistorySelected) Color.White else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isHistorySelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = if (isDownloadsSelected) IdlixRed else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.selectTab(LibraryTab.DOWNLOADS) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = if (isDownloadsSelected) Color.White else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val countSuffix = if (uiState.downloadItems.isNotEmpty()) {
                        " (${uiState.downloadItems.size})"
                    } else {
                        ""
                    }
                    Text(
                        text = stringResource(R.string.tab_offline_downloads) + countSuffix,
                        color = if (isDownloadsSelected) Color.White else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isDownloadsSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (uiState.selectedTab == LibraryTab.HISTORY) {
            if (uiState.historyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.no_history_title),
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_history_desc),
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.historyItems, key = { it.contentId }) { item ->
                        ContinueWatchingCard(
                            item = item,
                            onClick = { onHistoryItemClick(item) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            // Offline Downloads Tab
            if (uiState.downloadItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = SurfaceDark,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = IdlixRed,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.download_empty_title),
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.download_empty_desc),
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Storage Summary Strip
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.download_storage_summary,
                                    uiState.downloadItems.size,
                                    OfflineDownloadItem.formatBytes(uiState.totalStorageBytes)
                                ),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.downloadItems, key = { it.contentId }) { item ->
                            OfflineDownloadCard(
                                item = item,
                                onClick = { onDownloadItemClick(item) },
                                onPause = { viewModel.pauseDownload(item.contentId) },
                                onResume = { viewModel.resumeDownload(item.contentId) },
                                onDelete = { viewModel.removeDownload(item.contentId) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearHistoryDialog = false
                }) {
                    Text(stringResource(R.string.clear_all), color = IdlixRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextWhite)
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextWhite,
            textContentColor = TextMuted
        )
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text(stringResource(R.string.download_clear_all_title)) },
            text = { Text(stringResource(R.string.download_clear_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllDownloads()
                    showClearDownloadsDialog = false
                }) {
                    Text(stringResource(R.string.clear_all), color = IdlixRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextWhite)
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextWhite,
            textContentColor = TextMuted
        )
    }
}

@Composable
private fun OfflineDownloadCard(
    item: OfflineDownloadItem,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.status == DownloadStatus.COMPLETED) {
                IdlixAccentCyan.copy(alpha = 0.35f)
            } else {
                BorderDark
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Play or Progress Overlay
            Box(
                modifier = Modifier
                    .width(114.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariantDark)
            ) {
                AsyncImage(
                    model = item.effectivePosterModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.status) {
                        DownloadStatus.COMPLETED -> {
                            Surface(
                                color = IdlixRed,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.download_play_offline),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        DownloadStatus.QUEUED,
                        DownloadStatus.RESOLVING -> {
                            CircularProgressIndicator(
                                color = IdlixRed,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DownloadStatus.DOWNLOADING -> {
                            CircularProgressIndicator(
                                progress = { (item.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                color = IdlixRed,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        DownloadStatus.PAUSED -> {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = null,
                                tint = GoldStar,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = IdlixRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                val statusText = when (item.status) {
                    DownloadStatus.QUEUED -> stringResource(R.string.download_status_queued)
                    DownloadStatus.RESOLVING -> stringResource(R.string.download_status_resolving)
                    DownloadStatus.DOWNLOADING -> stringResource(
                        R.string.download_status_downloading,
                        item.progressPercent,
                        item.formattedSize
                    )
                    DownloadStatus.PAUSED -> stringResource(
                        R.string.download_status_paused,
                        item.progressPercent,
                        item.formattedSize
                    )
                    DownloadStatus.COMPLETED -> stringResource(
                        R.string.download_status_completed,
                        "${item.qualityLabel} • ${item.formattedSize}"
                    )
                    DownloadStatus.FAILED -> stringResource(
                        R.string.download_status_failed,
                        item.errorMessage ?: ""
                    )
                }

                Text(
                    text = statusText,
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> IdlixAccentCyan
                        DownloadStatus.FAILED -> IdlixRed
                        DownloadStatus.PAUSED -> GoldStar
                        else -> TextMuted
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.status != DownloadStatus.COMPLETED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                        color = IdlixRed,
                        trackColor = BorderDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isActive) {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.download_pause),
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.download_resume),
                            tint = GoldStar,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (item.status == DownloadStatus.COMPLETED) {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = stringResource(R.string.download_downloaded),
                        tint = IdlixAccentCyan,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.download_delete),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
