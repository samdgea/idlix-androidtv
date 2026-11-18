package com.idlix.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.idlix.data.local.PlayerAppearanceStore
import com.idlix.data.update.AppUpdateManager
import com.idlix.tv.ui.navigation.AppNavigation
import com.idlix.tv.ui.theme.IdlixTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerAppearanceStore.init(applicationContext)
        // Keep screen on while TV app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            IdlixTvTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstallIfAllowed(this)
    }
}
