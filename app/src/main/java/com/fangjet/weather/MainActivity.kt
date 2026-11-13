package com.fangjet.weather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.analytics.AnalyticsUserPropertiesUpdater
import com.fangjet.weather.analytics.notificationKindForOpenedKey
import com.fangjet.weather.model.UpdatePromptViewModel
import com.fangjet.weather.ui.UpdateRequiredScreen
import com.fangjet.weather.ui.navigation.WeatherNavHost
import com.fangjet.weather.ui.theme.CatsDogsTheme
import com.fangjet.weather.util.UpdatePrompt
import com.fangjet.weather.worker.EXTRA_NOTIFICATION_KIND
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var analyticsRepository: AnalyticsRepository

    @Inject
    lateinit var userProperties: AnalyticsUserPropertiesUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A fresh start from a notification's tap. Recreated (a rotation) or reopened from Recents,
        // the intent still carries the extra, but that isn't another tap.
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromRecents) logNotificationOpened(intent)
        if (BuildConfig.DEBUG) {
            // adb shell am start -n com.fangjet.weather.debug/com.fangjet.weather.MainActivity --es updatePrompt required
            UpdatePromptViewModel.debugOverride = when (intent.getStringExtra("updatePrompt")) {
                "required" -> UpdatePrompt.REQUIRED
                "recommended" -> UpdatePrompt.RECOMMENDED
                else -> null
            }
        }
        setContent {
            CatsDogsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val prompt by hiltViewModel<UpdatePromptViewModel>().prompt.collectAsStateWithLifecycle()
                    if (prompt == UpdatePrompt.REQUIRED) UpdateRequiredScreen() else WeatherNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logNotificationOpened(intent)
    }

    override fun onStart() {
        super.onStart()
        // Back in the foreground: permissions may have changed in system settings.
        runCatching { userProperties.refresh() }
    }

    /** notification_opened for a tap on one of the app's notifications, once: the extra goes after. */
    private fun logNotificationOpened(intent: Intent) {
        val kind = notificationKindForOpenedKey(intent.getStringExtra(EXTRA_NOTIFICATION_KIND)) ?: return
        intent.removeExtra(EXTRA_NOTIFICATION_KIND)
        analyticsRepository.logNotificationOpened(kind)
    }
}
