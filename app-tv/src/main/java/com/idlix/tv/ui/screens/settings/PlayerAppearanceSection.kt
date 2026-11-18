package com.idlix.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
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
import com.idlix.data.model.PlayerAppearance
import com.idlix.data.model.PlayerAppearancePresets
import com.idlix.data.model.SubtitleSize
import com.idlix.player.ui.SubtitleOverlay
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvColorPickerDialog
import com.idlix.tv.ui.screens.player.TvColorSwatch
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

private enum class ActivePicker {
    NONE,
    BACKGROUND,
    TEXT,
    BOX
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerAppearanceSection(
    onOpenPositionEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance by PlayerAppearanceStore.appearance.collectAsState()
    val recentColors by PlayerAppearanceStore.recentCustomColors.collectAsState()
    var activePicker by remember { mutableStateOf(ActivePicker.NONE) }

    val sampleCues = remember {
        listOf(
            Cue.Builder()
                .setText("Contoh Teks Subtitle IDLIX TV\nSesuaikan tampilan sesuai keinginan Anda")
                .build()
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NetflixDarkCard, RoundedCornerShape(10.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = NetflixWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.player_appearance_header),
                    color = NetflixWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.player_appearance_desc),
                    color = NetflixLightGray,
                    fontSize = 12.sp
                )
            }
        }

        // Live Mini-Preview + Quick Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 16:9 Mini Screen
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .aspectRatio(16f / 9f)
                    .background(Color(appearance.backgroundColor), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Simulated Letterbox viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.39f)
                        .background(Color(0xFF141414))
                )
                SubtitleOverlay(
                    cues = sampleCues,
                    appearance = appearance,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Position summary & Launch Editor Button
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.subtitle_position),
                    color = NetflixLightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                val isDefaultPos = kotlin.math.abs(appearance.subtitlePosX - PlayerAppearance.DEFAULT_POS_X) < 0.005f &&
                    kotlin.math.abs(appearance.subtitlePosY - PlayerAppearance.DEFAULT_POS_Y) < 0.005f
                val posSummary = if (isDefaultPos) {
                    stringResource(R.string.position_summary_default)
                } else {
                    stringResource(
                        R.string.position_summary_custom,
                        (appearance.subtitlePosX * 100).toInt(),
                        (appearance.subtitlePosY * 100).toInt()
                    )
                }
                Text(
                    text = posSummary,
                    color = NetflixWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = onOpenPositionEditor,
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixRed,
                        focusedContainerColor = Color(0xFFE50914).copy(alpha = 0.8f),
                        contentColor = NetflixWhite,
                        focusedContentColor = NetflixWhite
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.adjust_subtitle_position),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

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
                    onClick = { activePicker = ActivePicker.BACKGROUND },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.color_custom), fontSize = 11.sp)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.subtitle_text_size),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            containerColor = if (isSelected) NetflixRed else Color.Black.copy(alpha = 0.5f),
                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                            contentColor = if (isSelected) NetflixWhite else NetflixLightGray,
                            focusedContentColor = if (isSelected) NetflixWhite else Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

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
                    onClick = { activePicker = ActivePicker.TEXT },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.color_custom), fontSize = 11.sp)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.subtitle_box_color),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            containerColor = if (isSelected) NetflixRed else Color.Black.copy(alpha = 0.5f),
                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                            contentColor = if (isSelected) NetflixWhite else NetflixLightGray,
                            focusedContentColor = if (isSelected) NetflixWhite else Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Button(
                    onClick = { activePicker = ActivePicker.BOX },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.color_custom), fontSize = 11.sp)
                    }
                }
            }
        }

        // Reset All Appearance Button
        Button(
            onClick = { PlayerAppearanceStore.resetAll() },
            colors = ButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                focusedContainerColor = NetflixWhite,
                contentColor = NetflixLightGray,
                focusedContentColor = Color.Black
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
            modifier = Modifier.height(36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.reset_all_appearance), fontSize = 12.sp)
            }
        }
    }

    // Color Picker Dialog
    TvColorPickerDialog(
        isOpen = activePicker != ActivePicker.NONE,
        initialColor = when (activePicker) {
            ActivePicker.BACKGROUND -> appearance.backgroundColor
            ActivePicker.TEXT -> appearance.subtitleTextColor
            ActivePicker.BOX -> appearance.subtitleBoxColor
            ActivePicker.NONE -> 0
        },
        supportsAlpha = activePicker == ActivePicker.BOX,
        recentColors = recentColors,
        onColorSelected = { selected ->
            val storedColor = when (activePicker) {
                ActivePicker.BACKGROUND -> {
                    val opaque = selected or 0xFF000000.toInt()
                    PlayerAppearanceStore.update { it.copy(backgroundColor = opaque) }
                    opaque
                }
                ActivePicker.TEXT -> {
                    val opaque = selected or 0xFF000000.toInt()
                    PlayerAppearanceStore.update { it.copy(subtitleTextColor = opaque) }
                    opaque
                }
                ActivePicker.BOX -> {
                    PlayerAppearanceStore.update { it.copy(subtitleBoxColor = selected) }
                    selected
                }
                ActivePicker.NONE -> selected
            }
            PlayerAppearanceStore.addRecentCustomColor(storedColor)
            activePicker = ActivePicker.NONE
        },
        onDismiss = { activePicker = ActivePicker.NONE }
    )
}

