package com.idlix.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.idlix.mobile.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class MobileScreen(val route: String) {
    object Home : MobileScreen("home")
    object Movies : MobileScreen("movies")
    object Series : MobileScreen("series")
    object Search : MobileScreen("search")
    object Library : MobileScreen("library?tab={tab}") {
        const val BASE_ROUTE = "library"
        fun createRoute(tab: String = "history"): String = "library?tab=$tab"
    }
    object Settings : MobileScreen("settings")
    object SubtitlePositionEditor : MobileScreen("settings/subtitle_position")

    object Detail : MobileScreen(
        "detail/{contentType}/{slug}?autoPlay={autoPlay}&contentId={contentId}&season={season}&episode={episode}"
    ) {
        fun createRoute(
            contentType: String,
            slug: String,
            autoPlay: Boolean = false,
            contentId: String? = null,
            season: Int? = null,
            episode: Int? = null
        ): String {
            val safeSlug = slug.takeIf { it.isNotBlank() } ?: contentId?.takeIf { it.isNotBlank() } ?: "unknown"
            val encodedSlug = URLEncoder.encode(safeSlug, StandardCharsets.UTF_8.toString())
            val encodedContentId = URLEncoder.encode(contentId ?: "", StandardCharsets.UTF_8.toString())
            val seasonParam = season?.toString() ?: ""
            val episodeParam = episode?.toString() ?: ""
            return "detail/$contentType/$encodedSlug?autoPlay=$autoPlay&contentId=$encodedContentId&season=$seasonParam&episode=$episodeParam"
        }
    }
}

sealed class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    object Home : BottomNavItem(MobileScreen.Home.route, R.string.home, Icons.Filled.Home)
    object Movies : BottomNavItem(MobileScreen.Movies.route, R.string.movies, Icons.Filled.Movie)
    object Series : BottomNavItem(MobileScreen.Series.route, R.string.series, Icons.Filled.Tv)
    object Search : BottomNavItem(MobileScreen.Search.route, R.string.search, Icons.Filled.Search)
    object Library : BottomNavItem(MobileScreen.Library.BASE_ROUTE, R.string.library, Icons.Filled.VideoLibrary)

    companion object {
        val items = listOf(Home, Movies, Series, Search, Library)
    }
}
