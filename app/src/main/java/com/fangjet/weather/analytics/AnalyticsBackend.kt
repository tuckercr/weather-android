package com.fangjet.weather.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** The analytics SDK, behind an interface so the gates in front of it can be unit tested. */
interface AnalyticsBackend {
    fun logEvent(
        name: String,
        params: Map<String, String>,
    )

    /** A user property, attached to the install's later events for filtering reports. */
    fun setUserProperty(
        name: String,
        value: String,
    )

    /** SDK-level collection switch: when false, Firebase's automatic events stop too. */
    fun setCollectionEnabled(enabled: Boolean)
}

/**
 * [AnalyticsBackend] backed by Firebase Analytics. Inert when Firebase isn't initialised
 * (no `google-services.json`), and every call is wrapped so analytics can never crash the app.
 */
@Singleton
class FirebaseAnalyticsBackend @Inject constructor() : AnalyticsBackend {

    private val analytics: FirebaseAnalytics? by lazy {
        runCatching { Firebase.analytics }
            .onFailure { Timber.i("Firebase Analytics unavailable; analytics is a no-op") }
            .getOrNull()
    }

    override fun logEvent(
        name: String,
        params: Map<String, String>,
    ) {
        runCatching {
            val bundle = if (params.isEmpty()) null else Bundle().apply { params.forEach { (k, v) -> putString(k, v) } }
            analytics?.logEvent(name, bundle)
        }.onFailure { Timber.w(it, "Analytics event %s not logged", name) }
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        runCatching { analytics?.setUserProperty(name, value) }
            .onFailure { Timber.w(it, "User property %s not set", name) }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        runCatching { analytics?.setAnalyticsCollectionEnabled(enabled) }
            .onFailure { Timber.w(it, "Analytics collection flag not applied") }
    }
}
