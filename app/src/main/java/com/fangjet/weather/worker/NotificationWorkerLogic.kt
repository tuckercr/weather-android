package com.fangjet.weather.worker

import androidx.work.ListenableWorker.Result
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WalkAdvice
import com.fangjet.weather.domain.WalkRating
import com.fangjet.weather.domain.hasBadAirForWalking
import com.fangjet.weather.domain.isPrecipitating
import com.fangjet.weather.domain.notificationLocation
import com.fangjet.weather.domain.petMoodAt
import com.fangjet.weather.domain.walkAdviceForReading
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.airNameOrPoorRes
import com.fangjet.weather.util.dayOf
import com.fangjet.weather.util.displayedRainChance
import com.fangjet.weather.util.formatEpochTime
import com.fangjet.weather.util.nextRain
import com.fangjet.weather.util.skyCondition
import com.fangjet.weather.util.todayHighLow
import java.time.Instant
import java.time.ZoneOffset
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
    // Fetches and caches a location's weather: LocationWeatherRepository.refreshWeather and refreshForecast.
    private val refreshWeather: suspend (location: SavedLocation) -> kotlin.Result<CurrentWeather>,
    private val refreshForecast: suspend (location: SavedLocation) -> kotlin.Result<List<DayForecast>>,
    private val hasNotificationPermission: () -> Boolean,
    private val postNotification: (WeatherNotification) -> Unit,
    // Bad air's name from strings.xml ("Smoky air"), as the hero says it: airNameOrPoorRes.
    private val airName: (AirQuality.Pollutant?) -> String,
    // Remote Config daily_weather_notifications_enabled: switches the briefing off for everyone.
    private val isDailyWeatherNotificationEnabled: () -> Boolean = { true },
    // Remote Config walk_time_notifications_enabled: switches the walk nudge off for everyone.
    private val isWalkTimeRemotelyEnabled: () -> Boolean = { true },
    // Remote Config pet-mood and walk thresholds: the same mood and walk advice as the app.
    private val petWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
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

        // My Location is named by the phone ("57° in Mountain View"), as on the app's own screen.
        val weather = refreshWeather(location).getOrNull() ?: return Result.retry()
        val forecast = refreshForecast(location).getOrNull()
        val now = nowEpochSeconds()
        // Today by the city's date, as on the screen.
        val todayForecast = forecast?.dayOf(now, weather.offsetSeconds)
        val thresholds = runCatching { petWeatherThresholds() }.getOrDefault(PetWeatherThresholds.DEFAULT)
        val walk = forecast?.let { walkAdviceForReading(weather, it, thresholds, now) }

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
        val badAir = forecast?.let { badAirPhrase(it, weather.offsetSeconds, now, thresholds, airName) }
        val (title, body) = buildBriefingContent(weather, todayForecast, walk.takeIf { hasDog() }, badAir)
        postNotification(
            WeatherNotification(
                NotificationKind.MORNING_BRIEFING,
                title,
                body,
                weather.petMoodAt(now, thresholds),
                southernHemisphere = (location.latitude ?: 0.0) < 0,
            ),
        )
        return Result.success()
    }
}

/**
 * Runs at the walk time the morning run picked, extracted from [WalkTimeWorker] for tests. The
 * forecast can change in a few hours, so it only nudges if now is still a great time for a walk.
 */
internal class WalkTimeLogic(
    private val getSavedLocations: suspend () -> List<SavedLocation>,
    // Fetches and caches a location's weather: LocationWeatherRepository.refreshWeather and refreshForecast.
    private val refreshWeather: suspend (location: SavedLocation) -> kotlin.Result<CurrentWeather>,
    private val refreshForecast: suspend (location: SavedLocation) -> kotlin.Result<List<DayForecast>>,
    private val hasNotificationPermission: () -> Boolean,
    private val postNotification: (WeatherNotification) -> Unit,
    // Bad air's name from strings.xml ("Smoky air"), as the hero says it: airNameOrPoorRes.
    private val airName: (AirQuality.Pollutant?) -> String,
    private val isWalkTimeEnabled: suspend () -> Boolean,
    private val petWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    suspend fun doWork(): Result {
        if (!isWalkTimeEnabled() || !hasNotificationPermission()) return Result.success()
        val location = getSavedLocations().notificationLocation() ?: return Result.success()
        val weather = refreshWeather(location).getOrNull() ?: return Result.retry()
        val forecast = refreshForecast(location).getOrNull() ?: return Result.retry()
        val thresholds = runCatching { petWeatherThresholds() }.getOrDefault(PetWeatherThresholds.DEFAULT)
        val now = nowEpochSeconds()
        val walk = walkAdviceForReading(weather, forecast, thresholds, now)
        if (walk?.rating != WalkRating.GREAT) return Result.success()
        val (title, body) = buildWalkTimeContent(weather, badAirPhrase(forecast, weather.offsetSeconds, now, thresholds, airName))
        postNotification(
            WeatherNotification(
                NotificationKind.WALK_TIME,
                title,
                body,
                weather.petMoodAt(now, thresholds),
                southernHemisphere = (location.latitude ?: 0.0) < 0,
            ),
        )
        return Result.success()
    }
}

/** Below this, the day's rain chance isn't worth a mention in the briefing. */
private const val BRIEFING_RAIN_CHANCE = 40

/**
 * "Smoky air today" when bad air rules out walking hours from the one under way to the end of the
 * city's day, named by the worst hour's main pollutant with [airName], the hero caption's names
 * from strings.xml ([airNameOrPoorRes]): fine particles read as smoke, coarse ones as dust, ozone
 * by name, anything else as poor air. Null on a clean day.
 */
internal fun badAirPhrase(
    forecast: List<DayForecast>,
    utcOffsetSeconds: Int,
    nowEpochSeconds: Long,
    thresholds: PetWeatherThresholds,
    airName: (AirQuality.Pollutant?) -> String,
): String? {
    fun localDay(epochSeconds: Long) = Math.floorDiv(epochSeconds + utcOffsetSeconds, 86_400L)
    val worst = forecast
        .flatMap { it.hourlySlots }
        .filter { slot: HourlySlot ->
            val start = slot.epochSeconds ?: return@filter false
            start + 3600 > nowEpochSeconds && localDay(start) == localDay(nowEpochSeconds) && slot.hasBadAirForWalking(thresholds)
        }.maxByOrNull { it.airQuality?.index ?: 0 } ?: return null
    return "${airName(worst.airQuality?.mainPollutant)} today"
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
    // As on the hero: today's forecast high and low, taking in the temperature now.
    val (high, low) = todayHighLow(weather, todayForecast).let { (h, l) -> h.roundToInt() to l.roundToInt() }
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

/**
 * After a background refresh of the notification city, alerts about rain or snow starting within
 * the hour ([rainAlertFor]), once per shower. Extracted from [WeatherUpdateWorker] for tests.
 */
internal class RainAlertLogic(
    // Remote Config rain_notifications_enabled, the user's Rain switch and notification permission.
    private val isEnabled: suspend () -> Boolean,
    private val hasDog: suspend () -> Boolean,
    private val lastAlertStart: suspend () -> Long?,
    private val setLastAlertStart: suspend (epochSeconds: Long) -> Unit,
    private val postNotification: (WeatherNotification) -> Unit,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    suspend fun check(
        location: SavedLocation,
        weather: CurrentWeather?,
        forecast: List<DayForecast>?,
    ) {
        if (weather == null || forecast == null || !isEnabled()) return
        val rain = rainAlertFor(forecast, nowEpochSeconds(), weather.offsetSeconds, lastAlertStart()) ?: return
        val (title, body) = buildRainAlertContent(rain, weather.cityName, weather.offsetSeconds, hasDog())
        val mood = if (rain.snow) PetMood.SNOW else PetMood.RAIN
        postNotification(WeatherNotification(NotificationKind.RAIN, title, body, mood, (location.latitude ?: 0.0) < 0))
        setLastAlertStart(rain.epochSeconds)
    }
}
