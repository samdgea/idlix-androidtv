package com.idlix.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.data.local.PlayerAppearanceStore
import com.idlix.player.ui.PositionEditorState
import com.idlix.player.ui.SafeBounds
import com.idlix.player.ui.SubtitleOverlay
import com.idlix.player.ui.rememberPositionEditorState
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitlePositionEditorContent(
    cues: List<Cue> = emptyList(),
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance by PlayerAppearanceStore.appearance.collectAsState()
    val state = rememberPositionEditorState(appearance)

    val sampleCues = remember {
        listOf(
            Cue.Builder()
                .setText("This is an example subtitle.\nAdjust position using your TV remote.")
                .build()
        )
    }
    val activeCues = if (cues.isNotEmpty()) cues else sampleCues

    val surfaceFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }
    val dialogSaveFocusRequester = remember { FocusRequester() }
    var isSurfaceFocused by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun handleDismissRequest() {
        if (state.isDirty) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        try {
            surfaceFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(showDiscardDialog) {
        if (showDiscardDialog) {
            try {
                dialogSaveFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            try {
                surfaceFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(surfaceFocusRequester)
            .onFocusChanged { isSurfaceFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (showDiscardDialog) {
                        if (keyEvent.key == Key.Back) {
                            showDiscardDialog = false
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    }

                    if (keyEvent.key == Key.Back) {
                        handleDismissRequest()
                        return@onKeyEvent true
                    }

                    if (isSurfaceFocused) {
                        val isRepeating = keyEvent.nativeKeyEvent.repeatCount > 0
                        val step = if (isRepeating) 0.035f else 0.01f

                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                state.nudge(-step, 0f, snapCenter = true)
                                return@onKeyEvent true
                            }
                            Key.DirectionRight -> {
                                state.nudge(step, 0f, snapCenter = true)
                                return@onKeyEvent true
                            }
                            Key.DirectionUp -> {
                                state.nudge(0f, -step, snapCenter = true)
                                return@onKeyEvent true
                            }
                            Key.DirectionDown -> {
                                // If at bottom edge, shift focus down to the action buttons
                                if (state.posY >= SafeBounds.maxY() - 0.015f) {
                                    try {
                                        saveButtonFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                } else {
                                    state.nudge(0f, step, snapCenter = true)
                                }
                                return@onKeyEvent true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                state.commit()
                                onSaved()
                                return@onKeyEvent true
                            }
                        }
                    } else if (keyEvent.key == Key.DirectionUp) {
                        try {
                            surfaceFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        // Safe Boundary & Center Guideline Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .border(
                    width = 1.dp,
                    color = if (isSurfaceFocused) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        // Center line (subtle vertical guideline)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }

        // Real-time Subtitle Display
        val draftAppearance = remember(appearance, state.posX, state.posY) {
            appearance.copy(subtitlePosX = state.posX, subtitlePosY = state.posY)
        }
        SubtitleOverlay(
            cues = activeCues,
            appearance = draftAppearance,
            modifier = Modifier.fillMaxSize()
        )

        // Top Info & Hint Badge
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "X: ${(state.posX * 100).toInt()}%   Y: ${(state.posY * 100).toInt()}%",
                    color = NetflixWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.editor_hint_tv),
                color = NetflixLightGray,
                fontSize = 11.sp
            )
        }

        // Bottom Action Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { state.resetToDefault() },
                colors = ButtonDefaults.colors(
                    containerColor = NetflixDarkCard,
                    focusedContainerColor = NetflixWhite,
                    contentColor = NetflixLightGray,
                    focusedContentColor = NetflixBlack
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.reset), fontSize = 12.sp)
                }
            }

            Button(
                onClick = { handleDismissRequest() },
                colors = ButtonDefaults.colors(
                    containerColor = NetflixDarkCard,
                    focusedContainerColor = NetflixWhite,
                    contentColor = NetflixLightGray,
                    focusedContentColor = NetflixBlack
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.cancel), fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    state.commit()
                    onSaved()
                },
                colors = ButtonDefaults.colors(
                    containerColor = NetflixRed,
                    focusedContainerColor = Color(0xFFE50914).copy(alpha = 0.8f),
                    contentColor = NetflixWhite,
                    focusedContentColor = NetflixWhite
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                modifier = Modifier
                    .height(38.dp)
                    .focusRequester(saveButtonFocusRequester)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.save),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (showDiscardDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.discard_changes_title),
                        color = NetflixWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.discard_changes_desc),
                        color = NetflixLightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                showDiscardDialog = false
                                onDismiss()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard,
                                focusedContainerColor = NetflixWhite,
                                contentColor = NetflixLightGray,
                                focusedContentColor = NetflixBlack
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.discard), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showDiscardDialog = false },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard,
                                focusedContainerColor = NetflixWhite,
                                contentColor = NetflixLightGray,
                                focusedContentColor = NetflixBlack
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.keep_editing), fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                state.commit()
                                showDiscardDialog = false
                                onSaved()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixRed,
                                focusedContainerColor = Color(0xFFE50914).copy(alpha = 0.8f),
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixWhite
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(dialogSaveFocusRequester)
                        ) {
                            Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
