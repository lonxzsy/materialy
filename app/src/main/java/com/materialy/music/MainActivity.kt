package com.materialy.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.navigation.AppNavHost
import com.materialy.music.ui.theme.DynamicThemeManager
import com.materialy.music.ui.theme.MaterialyTheme
import com.materialy.music.updater.AppUpdateDialog
import com.materialy.music.updater.AppUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var dynamicThemeManager: DynamicThemeManager

    @Inject
    lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerManager.init()
        enableEdgeToEdge()

        // Background update check on app launch
        lifecycleScope.launch {
            updateManager.checkForUpdates(silent = true)
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            val darkScheme by dynamicThemeManager.dynamicDarkScheme.collectAsState()
            val lightScheme by dynamicThemeManager.dynamicLightScheme.collectAsState()
            val activeCoverScheme = if (isDark) darkScheme else lightScheme
            val updateState by updateManager.updateState.collectAsState()

            MaterialyTheme(
                darkTheme = isDark,
                coverColorScheme = activeCoverScheme
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()

                    AppUpdateDialog(
                        updateState = updateState,
                        updateManager = updateManager,
                        onDismiss = { updateManager.dismissUpdate() }
                    )
                }
            }
        }
    }
}
