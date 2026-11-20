package com.idlix.tv.ui.screens.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerControls(
    title: String,
    isVisible: Boolean,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    isSubtitleEnabled: Boolean,
    selectedSubtitleLabel: String? = null,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSubtitlePicker: () -> Unit,
    onScrubbingChanged: ((Boolean) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier
) {
    val subtitleFocusRequester = remember { FocusRequester() }
    val internalPlayFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }

    var isSeekFocused by remember { mutableStateOf(false) }
    var scrubPosMs by remember { mutableLongStateOf(currentPosMs) }
    var lastScrubTime by remember { mutableLongStateOf(0L) }
    var scrubStreak by remember { mutableIntStateOf(0) }
    var justCommittedTarget by remember { mutableStateOf<String?>(null) }

    // Sync with real playback position when not actively scrubbing
    LaunchedEffect(currentPosMs, isSeekFocused) {
        if (!isSeekFocused) {
            scrubPosMs = currentPosMs
        }
    }

    LaunchedEffect(justCommittedTarget) {
        if (justCommittedTarget != null) {
            delay(1200)
            justCommittedTarget = null
        }
    }

    fun calculateStep(): Long {
        val now = System.currentTimeMillis()
        if (now - lastScrubTime < 450) {
            scrubStreak++
        } else {
            scrubStreak = 0
        }
        lastScrubTime = now

        return when {
            scrubStreak >= 15 -> 300_000L // 5 menit
            scrubStreak >= 8  -> 120_000L // 2 menit
            scrubStreak >= 3  -> 60_000L  // 1 menit
            durationMs > 1_800_000L -> 30_000L // 30 detik untuk tayangan panjang
            else -> 10_000L // 10 detik
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC000000),
                            Color(0x00000000),
                            Color(0x26000000),
                            Color(0xE6000000)
                        )
                    )
                )
        ) {
            // Top Bar: Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = NetflixWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom Bar: Interactive Seeker Bar, Playback Controls, Subtitle & Timestamp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 36.dp, vertical = 24.dp)
            ) {
                val targetPos = if (isSeekFocused) scrubPosMs else currentPosMs
                val progress = if (durationMs > 0) (targetPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                val animatedBarHeight by animateDpAsState(
                    targetValue = if (isSeekFocused) 10.dp else 6.dp,
                    label = "barHeight"
                )

                // Interactive Seeker Bar with D-Pad navigation
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(seekBarFocusRequester)
                        .focusProperties {
                            down = internalPlayFocusRequester
                        }
                        .onFocusChanged { state ->
                            val wasFocused = isSeekFocused
                            isSeekFocused = state.isFocused
                            if (state.isFocused) {
                                scrubPosMs = currentPosMs
                                scrubStreak = 0
                                onScrubbingChanged?.invoke(true)
                            } else if (wasFocused) {
                                onScrubbingChanged?.invoke(false)
                            }
                        }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (durationMs <= 0L) return@onPreviewKeyEvent false
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        val step = calculateStep()
                                        scrubPosMs = (scrubPosMs - step).coerceAtLeast(0L)
                                        onUserInteraction?.invoke()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        val step = calculateStep()
                                        scrubPosMs = (scrubPosMs + step).coerceAtMost(durationMs)
                                        onUserInteraction?.invoke()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        val formatted = formatTime(scrubPosMs)
                                        justCommittedTarget = formatted
                                        onSeekTo(scrubPosMs)
                                        onUserInteraction?.invoke()
                                        true
                                    }
                                    KeyEvent.KEYCODE_BACK -> {
                                        if (scrubPosMs != currentPosMs) {
                                            scrubPosMs = currentPosMs
                                            internalPlayFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_RIGHT,
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> true
                                    else -> false
                                }
                            } else false
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSeekTo(scrubPosMs)
                            onUserInteraction?.invoke()
                        }
                ) {
                    val totalWidth = maxWidth
                    val thumbSize = 22.dp
                    val maxOffset = (totalWidth - thumbSize).coerceAtLeast(0.dp)
                    val thumbOffset = (maxOffset * progress).coerceIn(0.dp, maxOffset)
                    val tooltipWidth = 180.dp
                    val maxTooltipOffset = (totalWidth - tooltipWidth).coerceAtLeast(0.dp)
                    val tooltipOffset = (thumbOffset + (thumbSize / 2) - (tooltipWidth / 2)).coerceIn(0.dp, maxTooltipOffset)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Floating Tooltip Preview above the Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isSeekFocused || justCommittedTarget != null) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = tooltipOffset)
                                        .width(tooltipWidth)
                                        .background(NetflixDarkCard.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.5.dp, NetflixRed), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (justCommittedTarget != null) {
                                        Text(
                                            text = stringResource(R.string.jumping_to, justCommittedTarget!!),
                                            color = NetflixRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val deltaMs = scrubPosMs - currentPosMs
                                            val deltaStr = when {
                                                deltaMs >= 1000L -> " (+${formatTime(deltaMs)})"
                                                deltaMs <= -1000L -> " (-${formatTime(-deltaMs)})"
                                                else -> ""
                                            }
                                            Text(
                                                text = "${formatTime(scrubPosMs)}$deltaStr",
                                                color = NetflixWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.press_ok_to_play),
                                                color = NetflixLightGray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Progress Track & Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Inactive / Base Track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedBarHeight)
                                    .background(
                                        if (isSeekFocused) Color(0x66FFFFFF) else Color(0x4DFFFFFF),
                                        RoundedCornerShape(animatedBarHeight / 2)
                                    )
                            )

                            // Active / Filled Progress Track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(animatedBarHeight)
                                    .background(NetflixRed, RoundedCornerShape(animatedBarHeight / 2))
                            )

                            // Draggable / Focusable Thumb Dot
                            if (isSeekFocused) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = thumbOffset)
                                        .size(thumbSize)
                                        .background(NetflixWhite, CircleShape)
                                        .border(BorderStroke(3.dp, NetflixRed), CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Controls: Play/Pause, Rewind 10s, Forward 10s, Subtitle Picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Play / Pause Button
                        Button(
                            onClick = {
                                onPlayPauseClick()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixWhite,
                                contentColor = Color.Black,
                                focusedContainerColor = NetflixRed,
                                focusedContentColor = NetflixWhite
                            ),
                            shape = ButtonDefaults.shape(shape = CircleShape),
                            scale = ButtonDefaults.scale(focusedScale = 1.12f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = playButtonModifier
                                .size(44.dp)
                                .focusRequester(internalPlayFocusRequester)
                                .focusProperties { up = seekBarFocusRequester }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .then(if (!isPlaying) Modifier.offset(x = 1.dp) else Modifier)
                                )
                            }
                        }

                        // Rewind 10s Button
                        Button(
                            onClick = {
                                onSeekBackward()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard.copy(alpha = 0.85f),
                                focusedContainerColor = NetflixRed,
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixWhite
                            ),
                            shape = ButtonDefaults.shape(shape = CircleShape),
                            scale = ButtonDefaults.scale(focusedScale = 1.12f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(44.dp)
                                .focusProperties { up = seekBarFocusRequester }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = stringResource(R.string.rewind_10s),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Forward 10s Button
                        Button(
                            onClick = {
                                onSeekForward()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard.copy(alpha = 0.85f),
                                focusedContainerColor = NetflixRed,
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixWhite
                            ),
                            shape = ButtonDefaults.shape(shape = CircleShape),
                            scale = ButtonDefaults.scale(focusedScale = 1.12f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(44.dp)
                                .focusProperties { up = seekBarFocusRequester }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = stringResource(R.string.forward_10s),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Subtitle Picker Button
                        Button(
                            onClick = {
                                onOpenSubtitlePicker()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSubtitleEnabled) NetflixRed else NetflixDarkCard,
                                contentColor = NetflixWhite,
                                focusedContainerColor = if (isSubtitleEnabled) Color(0xFFB81D24) else NetflixWhite,
                                focusedContentColor = if (isSubtitleEnabled) NetflixWhite else Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .focusRequester(subtitleFocusRequester)
                                .focusProperties { up = seekBarFocusRequester }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSubtitleEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                                    contentDescription = stringResource(R.string.subtitles),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val displayLabel = if (!isSubtitleEnabled) {
                                    stringResource(R.string.subtitle_off)
                                } else {
                                    selectedSubtitleLabel ?: stringResource(R.string.subtitle_on)
                                }
                                Text(
                                    text = "Sub: $displayLabel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right Info: Seek Mode Badge & Timestamp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isSeekFocused) {
                            Box(
                                modifier = Modifier
                                    .background(NetflixRed, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.seek_mode),
                                    color = NetflixWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "${formatTime(targetPos)} / ${formatTime(durationMs)}",
                            color = if (isSeekFocused) NetflixWhite else NetflixLightGray,
                            fontSize = 13.sp,
                            fontWeight = if (isSeekFocused) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
