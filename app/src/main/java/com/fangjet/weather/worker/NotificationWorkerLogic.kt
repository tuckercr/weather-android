package com.fangjet.weather.worker

import androidx.work.ListenableWorker.Result
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.domain.notificationLocation
import com.fangjet.weather.ui.displayedRainChance
import com.fangjet.weather.ui.formatEpochTime
import com.fangjet.weather.ui.pets.PetMood
import com.fangjet.weather.ui.pets.WalkAdvice
import com.fangjet.weather.ui.pets.WalkRating
import com.fangjet.weather.ui.pets.hasBadAirForWalking
import com.fangjet.weather.ui.pets.isPrecipitating
import com.fangjet.weather.ui.pets.petMoodFor
import com.fangjet.weather.ui.pets.walkAdviceFor
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.fromCurrentSlot
import com.fangjet.weather.util.nextRain
import com.fangjet.weather.util.skyCondition
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which notification it is: each has its own channel, id and Settings switch. */
internal enum class NotificationKind { MORNING_BRIEFING, WALK_TIME, RAIN }

/** A notification: its text, and the pets' mood for its picture. */
internal data class WeatherNotification(
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val mood: PetMood,
    val southernHemisphere: Boolean,
)

/** A walk time closer than this to the morning run isn't worth a separate nudge. */
private const val MIN_WALK_LEAD_SECONDS = 30 * 60L

/**
 * The 8:30 AM run, extracted from [WeatherNotificationWorker] so it can be unit-tested without
 * Android framework dependencies (Context, NotificationManager, etc.). It posts the morning
 * briefing, and plans the walk-time nudge for the day's best walk time ([scheduleWalkTime]),
 * whether or not the briefing is on.
 */
internal class NotificationWorkerLogic(
    private val getSavedLocations: suspend () -> List<SavedLocation>,
    private val getUnits: suspend () -> WeatherUnits,
    private val fetchCurrentWeather: suspend (
        units: WeatherUnits,
        label: String,
        latitude: Double?,
        longitude: Double?,
    ) -> kotlin.Result<CurrentWeather>,
    private val fetchForecast: suspend (
        units: WeatherUnits,
        label: String,
        latitude: Double?,
        longitude: Double?,
    ) -> kotlin.Result<List<DayForecast>>,
    private val hasNotificationPermission: () -> Boolean,
    private val postNotification: (WeatherNotification) -> Unit,
    // Remote Config daily_weather_notifications_enabled: switches the briefing off for everyone.
    private val isDailyWeatherNotificationEnabled: () -> Boolean = { true },
    // Remote Config walk_time_notifications_enabled: switches the walk nudge off for everyone.
    private val isWalkTimeRemotelyEnabled: () -> Boolean = { true },
    // Remote Config pet-mood and walk thresholds: the same mood and walk advice as the app.
    private val petWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    // Same label format as ForecastAggregator produces, in the city's local date (its UTC offset)
    // so it matches the city-local forecast labels. Overridable so tests are date-independent.
    private val todayLabel: (utcOffsetSeconds: Int) -> String = { offset ->
        OffsetDateTime.now(ZoneOffset.ofTotalSeconds(offset)).format(DAY_LABEL_FORMATTER)
    },
    // The user's switches in Settings.
    private val isBriefingEnabled: suspend () -> Boolean = { true },
    private val isWalkTimeEnabled: suspend () -> Boolean = { false },
    // A dog at home ("I have a dog"): the briefing then mentions the walk, and Walk time can nudge.
    private val hasDog: suspend () -> Boolean = { false },
    private val scheduleWalkTime: (epochSeconds: Long) -> Unit = {},
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    suspend fun doWork(): Result {
        val location = getSavedLocations().notificationLocation() ?: return Result.success()

        val units = getUnits()

        val weather = fetchCurrentWeather(
            units,
            // "My Location" has no city name of its own: an empty label lets the API's name for
            // the place show ("57° in Mountain View"), as on the app's own screen.
            if (location.isCurrentLocation) "" else location.label,
            location.latitude,
            location.longitude,
        ).getOrNull() ?: return Result.retry()

        val forecast = fetchForecast(
            units,
            location.label,
            location.latitude,
            location.longitude,
        ).getOrNull()
        // Only an entry that is actually today may supply the high/low.
        val todayForecast = forecast?.firstOrNull { it.dateLabel == todayLabel(weather.offsetSeconds) }
        val thresholds = runCatching { petWeatherThresholds() }.getOrDefault(PetWeatherThresholds.DEFAULT)
        val now = nowEpochSeconds()
        val walk = forecast?.let { walkAdviceNow(weather, it, thresholds, now) }

        val briefingOn = hasNotificationPermission() &&
            runCatching { isDailyWeatherNotificationEnabled() }.getOrDefault(true) &&
            isBriefingEnabled()

        // Walk time only counts with a dog: Settings hides it otherwise, keeping its value.
        val walkTimeOn = runCatching { isWalkTimeRemotelyEnabled() }.getOrDefault(true) && isWalkTimeEnabled() && hasDog()
        if (walk != null && walkTimeOn) {
            when {
                // Now is the time: the briefing says so (it mentions the walk with a dog, which the
                // nudge needs too), or, without one, the nudge goes now.
                walk.rating == WalkRating.GREAT -> if (!briefingOn) scheduleWalkTime(now)
                !walk.bestTimeIsTomorrow ->
                    walk.bestTimeEpochSeconds
                        ?.takeIf { it - now >= MIN_WALK_LEAD_SECONDS }
                        ?.let(scheduleWalkTime)
            }
        }

        if (!briefingOn) return Result.success()
        val badAir = forecast?.let { badAirPhrase(it, weather.offsetSeconds, now, thresholds) }
        val (title, body) = buildBriefingContent(weather, todayForecast, walk.takeIf { hasDog() }, badAir)
        postNotification(
            WeatherNotification(
                NotificationKind.MORNING_BRIEFING,
                title,
                body,
                moodFor(weather, thresholds),
                southernHemisphere = (location.latitude ?: 0.0) < 0,
            ),
        )
        return Result.success()
    }

    private companion object {
        val DAY_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    }
}

/**
 * Runs at the walk time the morning run picked, extracted from [WalkTimeWorker] for tests. The
 * forecast can change in a few hours, so it only nudges if now is still a great time for a walk.
 */
internal class WalkTimeLogic(
    private val getSavedLocations: suspend () -> List<SavedLocation>,
    private val getUnits: suspend () -> WeatherUnits,
    private val fetchCurrentWeather: suspend (
        units: WeatherUnits,
        label: String,
        latitude: Double?,
        longitude: Double?,
    ) -> kotlin.Result<CurrentWeather>,
    private val fetchForecast: suspend (
        units: WeatherUnits,
        label: String,
        latitude: Double?,
        longitude: Double?,
    ) -> kotlin.Result<List<DayForecast>>,
    private val hasNotificationPermission: () -> Boolean,
    private val postNotification: (WeatherNotification) -> Unit,
    private val isWalkTimeEnabled: suspend () -> Boolean,
    private val petWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    suspend fun doWork(): Result {
        if (!isWalkTimeEnabled() || !hasNotificationPermission()) return Result.success()
        val location = getSavedLocations().notificationLocation() ?: return Result.success()
        val units = getUnits()
        val weather = fetchCurrentWeather(
            units,
            if (location.isCurrentLocation) "" else location.label,
            location.latitude,
            location.longitude,
        ).getOrNull() ?: return Result.retry()
        val forecast = fetchForecast(units, location.label, location.latitude, location.longitude).getOrNull()
            ?: return Result.retry()
        val thresholds = runCatching { petWeatherThresholds() }.getOrDefault(PetWeatherThresholds.DEFAULT)
        val walk = walkAdviceNow(weather, forecast, thresholds, nowEpochSeconds())
        if (walk?.rating != WalkRating.GREAT) return Result.success()
        val (title, body) = buildWalkTimeContent(weather, badAirPhrase(forecast, weather.offsetSeconds, nowEpochSeconds(), thresholds))
        postNotification(
            WeatherNotification(
                NotificationKind.WALK_TIME,
                title,
                body,
                moodFor(weather, thresholds),
                southernHemisphere = (location.latitude ?: 0.0) < 0,
            ),
        )
        return Result.success()
    }
}

/** The walk card's advice for now, from the current reading and the hours from now on. */
private fun walkAdviceNow(
    weather: CurrentWeather,
    forecast: List<DayForecast>,
    thresholds: PetWeatherThresholds,
    now: Long,
): WalkAdvice? =
    walkAdviceFor(
        weather.units.toCelsius(weather.feelsLike),
        forecast.flatMap { it.hourlySlots }.fromCurrentSlot(now),
        thresholds,
        utcOffsetSeconds = weather.offsetSeconds,
        nowEpochSeconds = now,
        precipitatingNow = isPrecipitating(weather.conditionMain),
    )

private fun moodFor(
    weather: CurrentWeather,
    thresholds: PetWeatherThresholds,
): PetMood = petMoodFor(weather.conditionMain, weather.iconCode, weather.units.toCelsius(weather.feelsLike), thresholds)

/** Below this, the day's rain chance isn't worth a mention in the briefing. */
private const val BRIEFING_RAIN_CHANCE = 40

/**
 * "Smoky air today" when bad air rules out walking hours from the one under way to the end of the
 * city's day, named by the worst hour's main pollutant: particles read as smoke, ozone by name,
 * anything else as poor air. Null on a clean day.
 */
internal fun badAirPhrase(
    forecast: List<DayForecast>,
    utcOffsetSeconds: Int,
    nowEpochSeconds: Long,
    thresholds: PetWeatherThresholds,
): String? {
    fun localDay(epochSeconds: Long) = Math.floorDiv(epochSeconds + utcOffsetSeconds, 86_400L)
    val worst = forecast
        .flatMap { it.hourlySlots }
        .filter { slot: HourlySlot ->
            val start = slot.epochSeconds ?: return@filter false
            start + 3600 > nowEpochSeconds && localDay(start) == localDay(nowEpochSeconds) && slot.hasBadAirForWalking(thresholds)
        }.maxByOrNull { it.airQuality?.index ?: 0 } ?: return null
    return when (worst.airQuality?.mainPollutant) {
        AirQuality.Pollutant.FINE_PARTICLES, AirQuality.Pollutant.COARSE_PARTICLES -> "Smoky air today"
        AirQuality.Pollutant.OZONE -> "High ozone today"
        else -> "Poor air today"
    }
}

/**
 * "52° and partly cloudy in Knoxville" / "High 70°, low 45°, 60% chance of rain. Sunny's picked
 * 1 PM for a walk." The walk sentence only comes with [walk], which is null without a dog. Bad-air
 * hours are left out of the walk silently, so [badAir] ("Smoky air today") says why.
 */
internal fun buildBriefingContent(
    weather: CurrentWeather,
    todayForecast: DayForecast?,
    walk: WalkAdvice?,
    badAir: String? = null,
): Pair<String, String> {
    val high = (todayForecast?.tempMax ?: weather.tempMax).roundToInt()
    val low = (todayForecast?.tempMin ?: weather.tempMin).roundToInt()
    val title = "${weather.temperature.roundToInt()}° and ${conditionPhrase(weather)} in ${weather.cityName}"
    val body = buildString {
        append("High $high°, low $low°")
        displayedRainChance(todayForecast?.precipitationChance ?: 0)
            ?.takeIf { it >= BRIEFING_RAIN_CHANCE }
            ?.let { append(", $it% chance of rain") }
        append(".")
        walkSentence(walk, badAir)?.let { append(" ").append(it) }
    }
    return title to body
}

private fun walkSentence(
    walk: WalkAdvice?,
    badAir: String?,
): String? {
    val shortOne = badAir?.let { " $it, so keep it short." }.orEmpty()
    return when {
        walk == null -> null
        walk.rating == WalkRating.GREAT -> "Sunny says now's a great time for a walk.$shortOne"
        walk.bestTimeLabel != null && !walk.bestTimeIsTomorrow -> "Sunny's picked ${walk.bestTimeLabel} for a walk.$shortOne"
        else -> badAir?.let { "$it, so potty breaks only." } ?: "Not a good day for walkies."
    }
}

/** "Time for a walk" / "64° and clear in Knoxville. Sunny's waiting by the door. Smoky air today, so a short one." */
internal fun buildWalkTimeContent(
    weather: CurrentWeather,
    badAir: String? = null,
): Pair<String, String> =
    "Time for a walk" to
        "${weather.temperature.roundToInt()}° and ${conditionPhrase(weather)} in ${weather.cityName}. Sunny's waiting by the door." +
        badAir?.let { " $it, so a short one." }.orEmpty()

/** Rain, drizzle, snow and storms by their own description ("light rain"); otherwise the sky ("partly cloudy"). */
internal fun conditionPhrase(weather: CurrentWeather): String {
    if (isPrecipitating(weather.conditionMain)) return weather.description.lowercase()
    return when (skyCondition(weather.conditionMain, weather.cloudPercent, weather.iconCode)) {
        SkyCondition.CLEAR -> "clear"
        SkyCondition.PARTLY_CLOUDY -> "partly cloudy"
        SkyCondition.MOSTLY_CLOUDY -> "mostly cloudy"
        SkyCondition.OVERCAST -> "overcast"
        SkyCondition.MIST -> "misty"
        SkyCondition.HAZE -> "hazy"
        SkyCondition.FOG -> "foggy"
        SkyCondition.SMOKE -> "smoky"
        SkyCondition.DUST -> "dusty"
    }
}

/** Alert this far ahead of the rain at most. */
private const val RAIN_ALERT_LEAD_SECONDS = 60 * 60L

/** No alerts before 7 AM or from 9 PM, the city's local time. */
private val RAIN_ALERT_HOURS = 7 until 21

/** A shower starting within this of the last one alerted about is the same rain. */
private const val RAIN_ALERT_REPEAT_SECONDS = 3 * 60 * 60L

/**
 * The rain or snow to alert about now: starting within the hour, in the daytime, and not the one
 * already alerted about ([lastAlertStart]). Null when there's nothing to say.
 */
internal fun rainAlertFor(
    forecast: List<DayForecast>,
    nowEpochSeconds: Long,
    utcOffsetSeconds: Int,
    lastAlertStart: Long?,
): NextRain.Starting? {
    val next = nextRain(forecast.firstOrNull()?.quarterHours.orEmpty(), nowEpochSeconds) as? NextRain.Starting ?: return null
    if (next.epochSeconds - nowEpochSeconds > RAIN_ALERT_LEAD_SECONDS) return null
    val hour = Instant.ofEpochSecond(nowEpochSeconds).atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds)).hour
    if (hour !in RAIN_ALERT_HOURS) return null
    if (lastAlertStart != null && abs(next.epochSeconds - lastAlertStart) < RAIN_ALERT_REPEAT_SECONDS) return null
    return next
}

/**
 * "Rain starting around 3:15 PM" / "Rain on the way in Knoxville. Walk now or wait it out.", or
 * without a dog, "Misty's staying in."
 */
internal fun buildRainAlertContent(
    rain: NextRain.Starting,
    cityName: String,
    utcOffsetSeconds: Int,
    hasDog: Boolean,
): Pair<String, String> {
    val what = if (rain.snow) "Snow" else "Rain"
    val time = formatEpochTime(rain.epochSeconds, utcOffsetSeconds)
    val advice = if (hasDog) "Walk now or wait it out." else "Misty's staying in."
    return "$what starting around $time" to "$what on the way in $cityName. $advice"
}
