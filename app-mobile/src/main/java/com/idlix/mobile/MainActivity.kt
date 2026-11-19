package com.idlix.mobile

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.idlix.data.local.PlayerAppearanceStore
import com.idlix.data.update.AppUpdateManager
import com.idlix.mobile.player.PipController
import com.idlix.mobile.ui.navigation.MainScaffold
import com.idlix.mobile.ui.theme.IdlixMobileTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerAppearanceStore.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            IdlixMobileTheme {
                MainScaffold()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstallIfAllowed(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipController.isPlaybackActive) {
            PipController.enterPip(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.setInPipMode(isInPictureInPictureMode)
    }
}
