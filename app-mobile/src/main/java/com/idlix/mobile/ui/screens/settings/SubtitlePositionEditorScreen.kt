package com.idlix.mobile.ui.screens.settings

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import com.idlix.data.local.PlayerAppearanceStore
import com.idlix.mobile.R
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextDim
import com.idlix.mobile.ui.theme.TextWhite
import com.idlix.player.ui.SafeBounds
import com.idlix.player.ui.SubtitleOverlay
import com.idlix.player.ui.rememberPositionEditorState

@Composable
fun SubtitlePositionEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Lock orientation to Landscape while inside this screen
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val appearance by PlayerAppearanceStore.appearance.collectAsState()
    val state = rememberPositionEditorState(appearance)

    val sampleText1 = stringResource(R.string.subtitle_preview_sample_1)
    val sampleText2 = stringResource(R.string.subtitle_preview_sample_2)
    val sampleCues = remember(sampleText1, sampleText2) {
        listOf(
            Cue.Builder()
                .setText("$sampleText1\n$sampleText2")
                .build()
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(appearance.backgroundColor))
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.39f)
                .align(Alignment.Center)
                .background(Color(0xFF14171E))
        ) {
            Text(
                text = "Area Video (2.39:1)",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }

        val draftAppearance = remember(appearance, state.posX, state.posY) {
            appearance.copy(subtitlePosX = state.posX, subtitlePosY = state.posY)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        state.dragBy(
                            deltaPxX = dragAmount.x,
                            deltaPxY = dragAmount.y,
                            containerWidthPx = size.width.toFloat(),
                            containerHeightPx = size.height.toFloat(),
                            snapCenter = true
                        )
                    }
                }
        ) {
            SubtitleOverlay(
                cues = sampleCues,
                appearance = draftAppearance,
                modifier = Modifier.fillMaxSize()
            )
        }

        val isSnappedToCenter = kotlin.math.abs(state.posX - 0.50f) < 0.005f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = TextWhite
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = stringResource(R.string.subtitle_position),
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "X: ${(state.posX * 100).toInt()}% • Y: ${(state.posY * 100).toInt()}%",
                        color = if (isSnappedToCenter) Color(0xFF4CAF50) else TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { state.nudge(0.50f - state.posX, 0f) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextWhite
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Center", color = TextWhite, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { state.resetToDefault() },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextWhite
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.reset), color = TextWhite, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        state.commit()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.save), color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Nudge:",
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = { state.nudge(-0.01f, 0f, snapCenter = true) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", tint = TextWhite)
                }

                IconButton(
                    onClick = { state.nudge(0f, -0.01f, snapCenter = true) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = TextWhite)
                }

                IconButton(
                    onClick = { state.nudge(0f, 0.01f, snapCenter = true) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = TextWhite)
                }

                IconButton(
                    onClick = { state.nudge(0.01f, 0f, snapCenter = true) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", tint = TextWhite)
                }
            }

            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.editor_hint_mobile),
                    color = TextWhite.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
