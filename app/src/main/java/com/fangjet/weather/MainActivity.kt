package com.fangjet.weather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.analytics.AnalyticsUserPropertiesUpdater
import com.fangjet.weather.analytics.notificationKindForOpenedKey
import com.fangjet.weather.model.ReviewPromptViewModel
import com.fangjet.weather.model.UpdatePromptViewModel
import com.fangjet.weather.ui.UpdateRequiredScreen
import com.fangjet.weather.ui.navigation.WeatherNavHost
import com.fangjet.weather.ui.theme.CatsDogsTheme
import com.fangjet.weather.util.UpdatePrompt
import com.fangjet.weather.worker.EXTRA_NOTIFICATION_KIND
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var analyticsRepository: AnalyticsRepository

    @Inject
    lateinit var userProperties: AnalyticsUserPropertiesUpdater

    // The same instance the weather screen tells what it shows.
    private val reviewPrompt: ReviewPromptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A fresh start from a notification's tap. Recreated (a rotation) or reopened from Recents,
        // the intent still carries the extra, but that isn't another tap.
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (BuildConfig.DEBUG) {
            // adb shell am start -n com.fangjet.weather.debug/com.fangjet.weather.MainActivity --es updatePrompt required
            UpdatePromptViewModel.debugOverride = when (intent.getStringExtra("updatePrompt")) {
                "required" -> UpdatePrompt.REQUIRED
                "recommended" -> UpdatePrompt.RECOMMENDED
                else -> null
            }
            // adb shell am start -S -n com.fangjet.weather.debug/com.fangjet.weather.MainActivity --ez reviewPromptForce true
            if (savedInstanceState == null) ReviewPromptViewModel.debugForce = intent.getBooleanExtra("reviewPromptForce", false)
        }
        if (savedInstanceState == null && !fromRecents) logNotificationOpened(intent)
        observeReviewPrompt()
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
        reviewPrompt.notificationOpened()
    }

    /**
     * Tells the ratings prompt the app's comings and goings (iOS's scene phases), and launches Play's
     * review flow when it says to. A rotation isn't leaving the app.
     */
    private fun observeReviewPrompt() {
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> reviewPrompt.becameActive()
                    Lifecycle.Event.ON_PAUSE -> if (!isChangingConfigurations) reviewPrompt.becameInactive()
                    Lifecycle.Event.ON_STOP -> if (!isChangingConfigurations) reviewPrompt.enteredBackground()
                    else -> Unit
                }
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reviewPrompt.requests.collect { launchReviewFlow() }
            }
        }
    }

    /**
     * Play decides whether the sheet shows (a quota, and only for apps installed from Play) and never
     * says what happened, so there's nothing to do after.
     */
    private suspend fun launchReviewFlow() {
        try {
            val manager = ReviewManagerFactory.create(this)
            manager.launchReview(this, manager.requestReview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Play review flow not launched")
        }
    }
}
