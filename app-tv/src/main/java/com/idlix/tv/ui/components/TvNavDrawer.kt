package com.idlix.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

enum class NavItem(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Default.Home),
    SEARCH(R.string.search, Icons.Default.Search),
    CONTINUE_WATCHING(R.string.continue_watching, Icons.Default.History),
    MOVIES(R.string.movies, Icons.Default.Movie),
    SERIES(R.string.series, Icons.Default.Tv),
    ACCOUNT(R.string.account, Icons.Default.Person),
    SETTINGS(R.string.settings, Icons.Default.Settings)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavDrawer(
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val homeFocusRequester = remember { FocusRequester() }
    val drawerWidth by animateDpAsState(
        targetValue = if (isExpanded) 210.dp else 64.dp,
        label = "drawerWidth"
    )

    LaunchedEffect(Unit) {
        if (selectedItem == NavItem.HOME) {
            try {
                homeFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize().background(NetflixBlack)) {
        // Fixed start padding ensures content does not resize during drawer animations.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 64.dp)
        ) {
            content()
        }

        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .background(NetflixBlack.copy(alpha = if (isExpanded) 0.98f else 0.92f))
                .padding(vertical = 24.dp, horizontal = 10.dp)
                .onFocusChanged { isExpanded = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Official IDLIX Logo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.idlix_logo),
                    contentDescription = "IDLIX",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(if (isExpanded) 28.dp else 16.dp)
                        .then(if (isExpanded) Modifier.padding(start = 8.dp) else Modifier)
                )
            }

            // Navigation items
            NavItem.values().forEach { item ->
                val isSelected = item == selectedItem
                var itemFocused by remember { mutableStateOf(false) }

                Card(
                    onClick = { onItemSelected(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .then(if (item == NavItem.HOME) Modifier.focusRequester(homeFocusRequester) else Modifier)
                        .onFocusChanged { itemFocused = it.isFocused },
                    scale = CardDefaults.scale(focusedScale = 1.0f),
                    glow = CardDefaults.glow(focusedGlow = Glow.None),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = CardDefaults.colors(
                        containerColor = if (itemFocused) NetflixDarkCard else Color.Transparent,
                        focusedContainerColor = NetflixRed
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (itemFocused || isSelected) NetflixWhite else NetflixLightGray,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isExpanded) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(item.labelRes),
                                color = if (itemFocused || isSelected) NetflixWhite else NetflixLightGray,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
