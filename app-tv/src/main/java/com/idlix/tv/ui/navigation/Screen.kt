package com.idlix.tv.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Movies : Screen("movies")
    object Series : Screen("series")
    object Search : Screen("search")
    object ContinueWatching : Screen("continue_watching")
    object Account : Screen("account")
    object Settings : Screen("settings")
    object SubtitlePositionEditor : Screen("subtitle_position_editor")

    object Detail : Screen("detail/{contentType}/{slug}") {
        fun createRoute(contentType: String, slug: String): String =
            "detail/$contentType/$slug"
    }

    object Player : Screen("player/{contentType}/{contentId}/{title}?poster={poster}&backdrop={backdrop}&slug={slug}&season={season}&episode={episode}") {
        fun createRoute(
            contentType: String,
            contentId: String,
            title: String,
            poster: String? = null,
            backdrop: String? = null,
            slug: String? = null,
            season: Int? = null,
            episode: Int? = null
        ): String {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            val encodedPoster = URLEncoder.encode(poster ?: "", StandardCharsets.UTF_8.toString())
            val encodedBackdrop = URLEncoder.encode(backdrop ?: "", StandardCharsets.UTF_8.toString())
            val encodedSlug = URLEncoder.encode(slug ?: "", StandardCharsets.UTF_8.toString())
            val seasonParam = season?.toString() ?: ""
            val episodeParam = episode?.toString() ?: ""
            return "player/$contentType/$contentId/$encodedTitle?poster=$encodedPoster&backdrop=$encodedBackdrop&slug=$encodedSlug&season=$seasonParam&episode=$episodeParam"
        }
    }
}
