package com.idlix.mobile.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.idlix.mobile.ui.screens.detail.DetailScreen
import com.idlix.mobile.ui.screens.home.HomeScreen
import com.idlix.mobile.ui.screens.library.LibraryScreen
import com.idlix.mobile.ui.screens.movies.MoviesScreen
import com.idlix.mobile.ui.screens.search.SearchScreen
import com.idlix.mobile.ui.screens.series.SeriesScreen
import com.idlix.mobile.ui.screens.settings.SettingsScreen
import com.idlix.mobile.ui.screens.settings.SubtitlePositionEditorScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppMobileNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MobileScreen.Home.route,
        modifier = modifier
    ) {
        composable(
            route = MobileScreen.Home.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            HomeScreen(
                onNavigateToDetail = { contentType, slug ->
                    navController.navigate(MobileScreen.Detail.createRoute(contentType, slug))
                },
                onContinueWatchingClick = { item ->
                    val slug = item.slug?.takeIf { it.isNotBlank() } ?: item.contentId
                    navController.navigate(
                        MobileScreen.Detail.createRoute(
                            contentType = item.contentType,
                            slug = slug,
                            autoPlay = true,
                            contentId = item.contentId,
                            season = item.seasonNumber,
                            episode = item.episodeNumber
                        )
                    )
                },
                onNavigateToSettings = {
                    navController.navigate(MobileScreen.Settings.route)
                }
            )
        }

        composable(
            route = MobileScreen.Movies.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            MoviesScreen(
                onNavigateToDetail = { contentType, slug ->
                    navController.navigate(MobileScreen.Detail.createRoute(contentType, slug))
                }
            )
        }

        composable(
            route = MobileScreen.Series.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            SeriesScreen(
                onNavigateToDetail = { contentType, slug ->
                    navController.navigate(MobileScreen.Detail.createRoute(contentType, slug))
                }
            )
        }

        composable(
            route = MobileScreen.Search.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            SearchScreen(
                onNavigateToDetail = { contentType, slug ->
                    navController.navigate(MobileScreen.Detail.createRoute(contentType, slug))
                }
            )
        }

        composable(
            route = MobileScreen.Library.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            LibraryScreen(
                onNavigateToDetail = { contentType, slug ->
                    navController.navigate(MobileScreen.Detail.createRoute(contentType, slug))
                },
                onHistoryItemClick = { item ->
                    val slug = item.slug?.takeIf { it.isNotBlank() } ?: item.contentId
                    navController.navigate(
                        MobileScreen.Detail.createRoute(
                            contentType = item.contentType,
                            slug = slug,
                            autoPlay = true,
                            contentId = item.contentId,
                            season = item.seasonNumber,
                            episode = item.episodeNumber
                        )
                    )
                },
                onNavigateToSettings = {
                    navController.navigate(MobileScreen.Settings.route)
                }
            )
        }

        composable(
            route = MobileScreen.Detail.route,
            arguments = listOf(
                navArgument("contentType") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType },
                navArgument("autoPlay") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("contentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("season") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("episode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            ),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(350)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(350)
                )
            }
        ) { backStackEntry ->
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "movie"
            val rawSlug = backStackEntry.arguments?.getString("slug") ?: ""
            val slug = try {
                URLDecoder.decode(rawSlug, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawSlug
            }
            val autoPlay = backStackEntry.arguments?.getBoolean("autoPlay") ?: false
            val rawContentId = backStackEntry.arguments?.getString("contentId") ?: ""
            val contentId = try {
                URLDecoder.decode(rawContentId, StandardCharsets.UTF_8.toString()).takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                rawContentId.takeIf { it.isNotBlank() }
            }
            val season = backStackEntry.arguments?.getString("season")?.toIntOrNull()
            val episode = backStackEntry.arguments?.getString("episode")?.toIntOrNull()

            DetailScreen(
                contentType = contentType,
                slug = slug,
                autoPlay = autoPlay,
                resumeContentId = contentId,
                resumeSeasonNumber = season,
                resumeEpisodeNumber = episode,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = MobileScreen.Settings.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(350)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(350)
                )
            }
        ) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenSubtitlePositionEditor = {
                    navController.navigate(MobileScreen.SubtitlePositionEditor.route)
                }
            )
        }

        composable(
            route = MobileScreen.SubtitlePositionEditor.route,
            enterTransition = { fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(250)) }
        ) {
            SubtitlePositionEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
