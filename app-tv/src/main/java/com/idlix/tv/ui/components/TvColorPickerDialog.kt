package com.idlix.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.player.ui.AlphaBar
import com.idlix.player.ui.HsvPanel
import com.idlix.player.ui.HueBar
import com.idlix.player.ui.rememberColorPickerState
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvColorPickerDialog(
    isOpen: Boolean,
    initialColor: Int,
    supportsAlpha: Boolean = false,
    recentColors: List<Int> = emptyList(),
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val state = rememberColorPickerState(initialColor)
    val keyboardController = LocalSoftwareKeyboardController.current
    val firstFocusRequester = remember { FocusRequester() }

    var isHsvPanelFocused by remember { mutableStateOf(false) }
    var isHsvInteracting by remember { mutableStateOf(false) }

    var isHueBarFocused by remember { mutableStateOf(false) }
    var isAlphaBarFocused by remember { mutableStateOf(false) }

    var hexInput by remember(state.currentHex) { mutableStateOf(state.currentHex) }
    var isHexFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            try {
                firstFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.color_picker_title),
                color = NetflixWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current",
                        color = NetflixLightGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(initialColor), RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New",
                        color = NetflixLightGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(state.currentColor), RoundedCornerShape(6.dp))
                            .border(2.dp, NetflixWhite, RoundedCornerShape(6.dp))
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isHsvInteracting) "Editing Saturation / Value (Press Back to finish)" else "Saturation & Brightness (Press OK to adjust)",
                    color = if (isHsvInteracting) NetflixRed else NetflixLightGray,
                    fontSize = 11.sp
                )
                HsvPanel(
                    hue = state.hue,
                    saturation = state.saturation,
                    value = state.value,
                    isFocused = isHsvPanelFocused,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .focusRequester(firstFocusRequester)
                        .onFocusChanged { isHsvPanelFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (!isHsvInteracting && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                    isHsvInteracting = true
                                    return@onKeyEvent true
                                }
                                if (isHsvInteracting) {
                                    val step = 0.04f
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            state.saturation = (state.saturation - step).coerceIn(0f, 1f)
                                            return@onKeyEvent true
                                        }
                                        Key.DirectionRight -> {
                                            state.saturation = (state.saturation + step).coerceIn(0f, 1f)
                                            return@onKeyEvent true
                                        }
                                        Key.DirectionUp -> {
                                            state.value = (state.value + step).coerceIn(0f, 1f)
                                            return@onKeyEvent true
                                        }
                                        Key.DirectionDown -> {
                                            state.value = (state.value - step).coerceIn(0f, 1f)
                                            return@onKeyEvent true
                                        }
                                        Key.Back, Key.DirectionCenter, Key.Enter -> {
                                            isHsvInteracting = false
                                            return@onKeyEvent true
                                        }
                                    }
                                }
                            }
                            false
                        }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Hue", color = NetflixLightGray, fontSize = 11.sp)
                HueBar(
                    hue = state.hue,
                    isFocused = isHueBarFocused,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .onFocusChanged { isHueBarFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val step = 4f
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        state.hue = ((state.hue - step + 360f) % 360f)
                                        return@onKeyEvent true
                                    }
                                    Key.DirectionRight -> {
                                        state.hue = ((state.hue + step) % 360f)
                                        return@onKeyEvent true
                                    }
                                }
                            }
                            false
                        }
                )
            }

            if (supportsAlpha) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.alpha_label) + ": ${(state.alpha * 100).toInt()}%",
                        color = NetflixLightGray,
                        fontSize = 11.sp
                    )
                    AlphaBar(
                        baseColor = state.currentColor,
                        alpha = state.alpha,
                        isFocused = isAlphaBarFocused,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .onFocusChanged { isAlphaBarFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    val step = 0.05f
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            state.alpha = (state.alpha - step).coerceIn(0f, 1f)
                                            return@onKeyEvent true
                                        }
                                        Key.DirectionRight -> {
                                            state.alpha = (state.alpha + step).coerceIn(0f, 1f)
                                            return@onKeyEvent true
                                        }
                                    }
                                }
                                false
                            }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(NetflixBlack, RoundedCornerShape(6.dp))
                    .border(
                        width = if (isHexFocused) 2.dp else 1.dp,
                        color = if (isHexFocused) NetflixWhite else NetflixDarkCard,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = hexInput,
                    onValueChange = {
                        hexInput = it
                        state.setHex(it)
                    },
                    textStyle = TextStyle(
                        color = NetflixWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(NetflixRed),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isHexFocused = it.isFocused }
                )
            }

            if (recentColors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.color_recent),
                        color = NetflixLightGray,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentColors.forEach { col ->
                            var isSwatchFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(col), CircleShape)
                                    .border(
                                        width = if (isSwatchFocused) 2.dp else 1.dp,
                                        color = if (isSwatchFocused) NetflixWhite else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .onFocusChanged { isSwatchFocused = it.isFocused }
                                    .clickable { state.setColor(col) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixDarkCard,
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.cancel), fontSize = 13.sp)
                }

                Button(
                    onClick = { onColorSelected(state.currentColor) },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixRed,
                        focusedContainerColor = Color(0xFFE50914).copy(alpha = 0.8f),
                        contentColor = NetflixWhite,
                        focusedContentColor = NetflixWhite
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.apply),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

