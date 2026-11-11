package com.idlix.mobile.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.idlix.data.update.AppPlatform
import com.idlix.data.update.AppUpdateManager
import com.idlix.data.update.AppUpdateState
import com.idlix.mobile.BuildConfig
import com.idlix.mobile.ui.components.MobileUpdateDialog
import com.idlix.mobile.ui.theme.BackgroundDark

@Composable
fun MainScaffold(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val updateState by AppUpdateManager.state.collectAsState()
    val showUpdatePrompt by AppUpdateManager.showUpdatePrompt.collectAsState()

    LaunchedEffect(Unit) {
        AppUpdateManager.checkOnStartup(
            context = context,
            platform = AppPlatform.MOBILE,
            currentVersion = BuildConfig.VERSION_NAME
        )
    }

    val readyState = updateState as? AppUpdateState.ReadyToInstall
    if (showUpdatePrompt && readyState != null) {
        MobileUpdateDialog(
            latestVersion = readyState.latestVersion,
            currentVersion = BuildConfig.VERSION_NAME,
            onInstallNow = {
                AppUpdateManager.installApk(context, readyState.apkFile)
            },
            onDismiss = {
                AppUpdateManager.dismissUpdatePrompt()
            }
        )
    }

    // Tampilkan BottomBar hanya di top-level bottom nav routes
    val baseCurrentRoute = currentRoute?.substringBefore('?')
    val showBottomBar = BottomNavItem.items.any { it.route == baseCurrentRoute }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                MobileBottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(
                    bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline
                )
        ) {
            AppMobileNavHost(
                navController = navController
            )
        }
    }
}
