package com.idlix.tv.ui.screens.player

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.idlix.data.local.PlayerAppearanceStore
import com.idlix.data.model.PlayerAppearancePresets
import com.idlix.data.model.SubtitleSize
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvColorPickerDialog
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

private enum class ActiveColorPickerTarget {
    NONE,
    BACKGROUND,
    TEXT,
    BOX
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubtitleAppearanceQuickPanel(
    onOpenPositionEditor: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance by PlayerAppearanceStore.appearance.collectAsState()
    val recentColors by PlayerAppearanceStore.recentCustomColors.collectAsState()
    var activePicker by remember { mutableStateOf(ActiveColorPickerTarget.NONE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = NetflixWhite,
                    contentColor = NetflixLightGray,
                    focusedContentColor = NetflixBlack
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.back_to_list), fontSize = 12.sp)
                }
            }
        }

        // Action: Adjust Position on Video
        Button(
            onClick = onOpenPositionEditor,
            colors = ButtonDefaults.colors(
                containerColor = NetflixRed,
                focusedContainerColor = Color(0xFFE50914).copy(alpha = 0.8f),
                contentColor = NetflixWhite,
                focusedContentColor = NetflixWhite
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.adjust_position_on_video),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Subtitle Size Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.subtitle_text_size),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleSize.entries.forEach { size ->
                    val isSelected = appearance.subtitleSize == size
                    val labelRes = when (size) {
                        SubtitleSize.SMALL -> R.string.size_small
                        SubtitleSize.MEDIUM -> R.string.size_medium
                        SubtitleSize.LARGE -> R.string.size_large
                        SubtitleSize.EXTRA_LARGE -> R.string.size_extra_large
                    }
                    Button(
                        onClick = {
                            PlayerAppearanceStore.update { it.copy(subtitleSize = size) }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) NetflixRed else NetflixDarkCard,
                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                            contentColor = if (isSelected) NetflixWhite else NetflixLightGray,
                            focusedContentColor = if (isSelected) NetflixWhite else NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Subtitle Text Color
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.subtitle_text_color),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerAppearancePresets.subtitleTextColors.forEach { col ->
                    val isSelected = appearance.subtitleTextColor == col
                    TvColorSwatch(
                        color = col,
                        isSelected = isSelected,
                        onClick = {
                            PlayerAppearanceStore.update { it.copy(subtitleTextColor = col) }
                        }
                    )
                }
                Button(
                    onClick = { activePicker = ActiveColorPickerTarget.TEXT },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixDarkCard,
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Subtitle Box Background Color
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.subtitle_box_color),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerAppearancePresets.subtitleBoxColors.forEach { boxColor ->
                    val isSelected = appearance.subtitleBoxColor == boxColor
                    val label = when (boxColor) {
                        0x00000000 -> stringResource(R.string.box_transparent)
                        0x80000000.toInt() -> stringResource(R.string.box_black_50)
                        0xC0000000.toInt() -> stringResource(R.string.box_black_75)
                        else -> stringResource(R.string.box_black_solid)
                    }
                    Button(
                        onClick = {
                            PlayerAppearanceStore.update { it.copy(subtitleBoxColor = boxColor) }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) NetflixRed else NetflixDarkCard,
                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                            contentColor = if (isSelected) NetflixWhite else NetflixLightGray,
                            focusedContentColor = if (isSelected) NetflixWhite else NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Button(
                    onClick = { activePicker = ActiveColorPickerTarget.BOX },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixDarkCard,
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Player Background Color
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.player_bg_color),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerAppearancePresets.backgroundColors.forEach { col ->
                    val isSelected = appearance.backgroundColor == col
                    TvColorSwatch(
                        color = col,
                        isSelected = isSelected,
                        onClick = {
                            PlayerAppearanceStore.update { it.copy(backgroundColor = col) }
                        }
                    )
                }
                Button(
                    onClick = { activePicker = ActiveColorPickerTarget.BACKGROUND },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixDarkCard,
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset to Default button
        Button(
            onClick = { PlayerAppearanceStore.resetAll() },
            colors = ButtonDefaults.colors(
                containerColor = NetflixDarkCard,
                focusedContainerColor = NetflixWhite,
                contentColor = NetflixLightGray,
                focusedContentColor = NetflixBlack
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.reset_all_appearance), fontSize = 12.sp)
            }
        }
    }

    // Color Picker Dialog
    TvColorPickerDialog(
        isOpen = activePicker != ActiveColorPickerTarget.NONE,
        initialColor = when (activePicker) {
            ActiveColorPickerTarget.BACKGROUND -> appearance.backgroundColor
            ActiveColorPickerTarget.TEXT -> appearance.subtitleTextColor
            ActiveColorPickerTarget.BOX -> appearance.subtitleBoxColor
            ActiveColorPickerTarget.NONE -> 0
        },
        supportsAlpha = activePicker == ActiveColorPickerTarget.BOX,
        recentColors = recentColors,
        onColorSelected = { selected ->
            val storedColor = when (activePicker) {
                ActiveColorPickerTarget.BACKGROUND -> {
                    val opaque = selected or 0xFF000000.toInt()
                    PlayerAppearanceStore.update { it.copy(backgroundColor = opaque) }
                    opaque
                }
                ActiveColorPickerTarget.TEXT -> {
                    val opaque = selected or 0xFF000000.toInt()
                    PlayerAppearanceStore.update { it.copy(subtitleTextColor = opaque) }
                    opaque
                }
                ActiveColorPickerTarget.BOX -> {
                    PlayerAppearanceStore.update { it.copy(subtitleBoxColor = selected) }
                    selected
                }
                ActiveColorPickerTarget.NONE -> selected
            }
            PlayerAppearanceStore.addRecentCustomColor(storedColor)
            activePicker = ActiveColorPickerTarget.NONE
        },
        onDismiss = { activePicker = ActiveColorPickerTarget.NONE }
    )
}

@Composable
fun TvColorSwatch(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(color), CircleShape)
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 2.dp else 1.dp,
                color = if (isFocused) NetflixWhite else if (isSelected) NetflixRed else Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            // Keep clickable as the single focus target so D-pad OK/Enter invokes onClick
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (color == 0xFFFFFFFF.toInt()) NetflixBlack else NetflixWhite,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
