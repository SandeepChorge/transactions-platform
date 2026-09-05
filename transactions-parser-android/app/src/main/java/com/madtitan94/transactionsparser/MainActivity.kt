package com.madtitan94.transactionsparser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.theme.TransactionsParserTheme
import com.madtitan94.transactionsparser.feature.settings.domain.ThemePreference
import com.madtitan94.transactionsparser.feature.settings.domain.ThemeStorage
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val themeStorage: ThemeStorage by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // SYSTEM as the initial value means the very first frame follows the device rather
            // than guessing dark, so a light-mode user does not see a dark flash before DataStore
            // has answered.
            val preference by themeStorage.observeTheme()
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)

            val darkTheme = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            // enableEdgeToEdge picks the system bars' icon colour from the device's dark-mode
            // setting, which is the wrong answer whenever the user has overridden it here — light
            // app, dark phone would leave white status-bar icons on a paper background.
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            TransactionsParserTheme(darkTheme = darkTheme) {
                AppRoot()
            }
        }
    }
}
