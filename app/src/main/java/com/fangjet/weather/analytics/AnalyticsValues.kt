package com.fangjet.weather.analytics

import com.fangjet.weather.worker.NotificationKind

/*
 * The fixed values analytics parameters take: anonymous categories, never places, coordinates,
 * city names or typed text. The same names and values as iOS.
 */

/** `onboarding_step`'s `step`: the onboarding screen shown. */
enum class OnboardingStepShown(
    val key: String,
) {
    WELCOME("welcome"),
    DOG("dog"),
    NOTIFICATIONS("notifications"),
    LOCATION("location"),
}

/** `onboarding_complete`'s `notifications` and `location`: how onboarding's permission question went. */
enum class OnboardingPermissionResult(
    val key: String,
) {
    /** The permission is held at the end of onboarding. */
    GRANTED("granted"),

    /** Asked during onboarding and refused. */
    DENIED("denied"),

    /** Never asked: "Not now", or "Enter a town or city manually". */
    SKIPPED("skipped"),
    ;

    companion object {
        /**
         * Held is [GRANTED] whatever was answered; otherwise [DENIED] when it was asked, and [SKIPPED]
         * when it wasn't or the answer is unknown (the app was restarted mid-onboarding).
         */
        fun of(
            asked: Boolean?,
            held: Boolean,
        ): OnboardingPermissionResult =
            when {
                held -> GRANTED
                asked == true -> DENIED
                else -> SKIPPED
            }
    }
}

/** `permission_changed`'s `permission`. */
enum class AnalyticsPermission(
    val key: String,
) {
    /** POST_NOTIFICATIONS, or notifications being enabled for the app below Android 13. */
    NOTIFICATIONS("notifications"),

    /** "While using the app" location, precise or approximate. */
    LOCATION("location"),

    /** Background location, "Allow all the time". */
    LOCATION_ALWAYS("location_always"),
}

/** `permission_changed`'s `from`: where the app asked. */
enum class PermissionAskedFrom(
    val key: String,
) {
    /** The Settings and Notifications screens' rows for a permission that's off. */
    SETTINGS("settings"),

    /** Add My Location on Add location. */
    ADD_MY_LOCATION("add_my_location"),

    /** The Follow my location switch. */
    FOLLOW_MY_LOCATION("follow_my_location"),
}

/** `notification_opened`'s `kind`, which also rides on each notification's tap as an extra. */
internal val NotificationKind.openedKey: String
    get() = when (this) {
        NotificationKind.MORNING_BRIEFING -> "briefing"
        NotificationKind.WALK_TIME -> "walk"
        NotificationKind.RAIN -> "rain"
    }

/** The notification kind a tap's extra names, or null for anything else. */
internal fun notificationKindForOpenedKey(key: String?): NotificationKind? = NotificationKind.entries.firstOrNull { it.openedKey == key }

/** `city_added`'s `source`: how a location was added. Same as iOS's `CityAddedSource`. */
enum class CityAddedSource(
    val key: String,
) {
    /** A row in the search list was tapped. */
    SUGGESTION("suggestion"),

    /** Add (or the keyboard's Search key) took the row named what was typed. */
    TYPED_MATCH("typed_match"),

    /** Saved by the typed name only, with no list (offline, say). */
    TYPED_NAME("typed_name"),
    MY_LOCATION("my_location"),
}

/** Where city search suggestions come from. Android's search is OpenWeather's only for now. */
enum class CitySearchProvider(
    val key: String,
) {
    OPEN_METEO("open_meteo"),
    OPENWEATHER("openweather"),
    ;

    companion object {
        /** The provider Android's search uses: for a typed name or My Location, the one search would use. */
        val CURRENT = OPENWEATHER
    }
}

/** `city_added`'s parameters. Same as iOS's `CityAdded`. */
data class CityAdded(
    val source: CityAddedSource,
    // The provider of the row added; for a typed name or My Location, the one the search would use.
    val provider: CitySearchProvider = CitySearchProvider.CURRENT,
)
