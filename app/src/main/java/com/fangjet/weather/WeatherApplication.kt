package com.fangjet.weather

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import com.fangjet.weather.analytics.AnalyticsCollectionController
import com.fangjet.weather.worker.NotificationChannels
import com.fangjet.weather.worker.WorkManagerScheduler
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WeatherApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManagerScheduler: WorkManagerScheduler

    @Inject
    lateinit var coilImageLoader: ImageLoader

    @Inject
    lateinit var analyticsCollectionController: AnalyticsCollectionController

    @Inject
    lateinit var notificationChannels: NotificationChannels

    /** Lives as long as the process; for app-wide observers such as [analyticsCollectionController] and the channels. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration
            .Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        coil.Coil.setImageLoader(coilImageLoader)
        workManagerScheduler.schedule()
        runCatching {
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
        // Applies "Share usage statistics" + the analytics_enabled kill switch to the SDK now,
        // and again whenever either changes.
        runCatching { analyticsCollectionController.start(appScope) }
        notificationChannels.start(appScope)
    }
}
