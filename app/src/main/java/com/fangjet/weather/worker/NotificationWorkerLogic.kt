package com.fangjet.weather.worker

import androidx.work.ListenableWorker.Result
import com.fangjet.weather.R
import com.fangjet.weather.data.CachedLocationWeather
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WalkAdvice
import com.fangjet.weather.domain.WalkRating
import com.fangjet.weather.domain.WalkWarning
import com.fangjet.weather.domain.hasBadAirForWalking
import com.fangjet.weather.domain.hasLikelyStorms
import com.fangjet.weather.domain.isHurricaneForce
import com.fangjet.weather.domain.isPrecipitating
import com.fangjet.weather.domain.notificationLocation
import com.fangjet.weather.domain.petMoodAt
import com.fangjet.weather.domain.walkAdviceForReading
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.Strings
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.airNameOrPoorRes
import com.fangjet.weather.util.dayOf
import com.fangjet.weather.util.displayedRainChance
import com.fangjet.weather.util.formatEpochTime
import com.fangjet.weather.util.nextRain
import com.fangjet.weather.util.skyCondition
import com.fangjet.weather.util.todayHighLow
import com.fangjet.weather.util.windForce
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    // The cached weather when both parts were fetched in the last 30 minutes (LocationWeatherRepository.freshCache):
    // used as it is instead of fetching again.
    private val freshCache: suspend (location: SavedLocation) -> CachedLocationWeather? = { null },
    private val hasNotificationPermission: () -> Boolean,
    private val postNotification: (WeatherNotification) -> Unit,
    // The notifications' text, from strings.xml.
    private val strings: Strings,
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
        val fresh = freshCache(location)
        val weather = fresh?.weather ?: refreshWeather(location).getOrNull() ?: return Result.retry()
        val forecast = fresh?.forecast ?: refreshForecast(location).getOrNull()
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
        val badAir = forecast?.let { badAirPhrase(strings, it, weather.offsetSeconds, now, thresholds) }
        val warning = briefingWarning(weather, forecast.orEmpty(), now, thresholds)
        val (title, body) = buildBriefingContent(strings, weather, todayForecast, walk.takeIf { hasDog() }, badAir, warning)
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
 * Runs at the walk time the morning run (or [WalkTimeTodayLogic]) picked, extracted from
 * [WalkTimeWorker] for tests. The forecast can change in a few hours, so it only nudges if now is
 * still a great time for a walk, and not on a day that has already had one.
 */
internal class WalkTimeLogic(
    private val getSavedLocations: suspend () -> List<SavedLocation>,
    // Fetches and caches a location's weather: LocationWeatherRepository.refreshWeather and refreshForecast.
    private val refreshWeather: suspend (location: SavedLocation) -> kotlin.Result<CurrentWeather>,
    private val refreshForecast: suspend (location: SavedLocation) -> kotlin.Result<List<DayForecast>>,
    // The cached weather when both parts were fetched in the last 30 minutes (LocationWeatherRepository.freshCache):
    // used as it is instead of fetching again.
    private val freshCache: suspend (location: SavedLocation) -> CachedLocationWeather? = { null },
    private val hasNotificationPermission: () -> Boolean,
    private val postNotification: (WeatherNotification) -> Unit,
    // The notifications' text, from strings.xml.
    private val strings: Strings,
    private val isWalkTimeEnabled: suspend () -> Boolean,
    private val petWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    // When the last walk nudge went, and recording this one: one a day at most.
    private val lastWalkNudgeAt: suspend () -> Long? = { null },
    private val setLastWalkNudgeAt: suspend (epochSeconds: Long) -> Unit = {},
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun doWork(): Result {
        if (!isWalkTimeEnabled() || !hasNotificationPermission()) return Result.success()
        if (isSameDeviceDay(lastWalkNudgeAt(), nowEpochSeconds(), zone)) return Result.success()
        val location = getSavedLocations().notificationLocation() ?: return Result.success()
        val fresh = freshCache(location)
        val weather = fresh?.weather ?: refreshWeather(location).getOrNull() ?: return Result.retry()
        val forecast = fresh?.forecast ?: refreshForecast(location).getOrNull() ?: return Result.retry()
        val thresholds = runCatching { petWeatherThresholds() }.getOrDefault(PetWeatherThresholds.DEFAULT)
        val now = nowEpochSeconds()
        val walk = walkAdviceForReading(weather, forecast, thresholds, now)
        if (walk?.rating != WalkRating.GREAT) return Result.success()
        val slots = forecast.flatMap { it.hourlySlots }
        // The walk hour is the forecast hour under way now, as the walk advice reads it.
        val walkHourStart = slots.mapNotNull { it.epochSeconds }.filter { it <= now }.maxOrNull() ?: now
        val (title, body) = buildWalkTimeContent(
            strings,
            weather,
            badAirPhrase(strings, forecast, weather.offsetSeconds, now, thresholds),
            walkNudgeWarning(walkHourStart, slots, weather.offsetSeconds, thresholds),
        )
        postNotification(
            WeatherNotification(
                NotificationKind.WALK_TIME,
                title,
                body,
                weather.petMoodAt(now, thresholds),
                southernHemisphere = (location.latitude ?: 0.0) < 0,
            ),
        )
        setLastWalkNudgeAt(now)
        return Result.success()
    }
}

/** Whether [epochSeconds] falls on the same day as [nowEpochSeconds] by the device's clock, as the 8:30 run goes by. */
private fun isSameDeviceDay(
    epochSeconds: Long?,
    nowEpochSeconds: Long,
    zone: ZoneId,
): Boolean {
    fun date(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
    return epochSeconds != null && date(epochSeconds) == date(nowEpochSeconds)
}

/**
 * Plans today's walk nudge as soon as Walk time or the dog is turned on, which otherwise waits for
 * the next 8:30 run. It goes by the cached weather (the nudge checks again when it's due) and the
 * same switches as the 8:30 run, and plans the same one-off [WalkTimeWorker], replacing any already
 * planned. It plans nothing:
 * - before today's 8:30 run, which plans the day's nudge itself;
 * - on a day that has already had a nudge;
 * - when now is a great time: the walk card on the screen they're on already says so;
 * - when the best time is tomorrow, or not ahead.
 * Same as iOS, which plans the whole queue again on any setting change.
 */
internal class WalkTimeTodayLogic(
    private val getSavedLocations: suspend () -> List<SavedLocation>,
    // The cached reading and forecast (LocationWeatherRepository.cachedWeather and cachedForecast): no fetch.
    private val cachedWeather: suspend (location: SavedLocation) -> CurrentWeather?,
    private val cachedForecast: suspend (location: SavedLocation) -> List<DayForecast>?,
    private val hasNotificationPermission: () -> Boolean,
    // Remote Config walk_time_notifications_enabled: switches the walk nudge off for everyone.
    private val isWalkTimeRemotelyEnabled: () -> Boolean = { true },
    private val isWalkTimeEnabled: suspend () -> Boolean,
    private val hasDog: suspend () -> Boolean,
    private val lastWalkNudgeAt: suspend () -> Long? = { null },
    private val petWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    private val scheduleWalkTime: (epochSeconds: Long) -> Unit,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun plan() {
        val walkTimeOn = runCatching { isWalkTimeRemotelyEnabled() }.getOrDefault(true) && isWalkTimeEnabled() && hasDog()
        if (!walkTimeOn || !hasNotificationPermission()) return
        val now = nowEpochSeconds()
        val local = Instant.ofEpochSecond(now).atZone(zone).toLocalTime()
        if (local < LocalTime.of(WorkManagerScheduler.BRIEFING_HOUR, WorkManagerScheduler.BRIEFING_MINUTE)) return
        if (isSameDeviceDay(lastWalkNudgeAt(), now, zone)) return
        val location = getSavedLocations().notificationLocation() ?: return
        val weather = cachedWeather(location) ?: return
        val forecast = cachedForecast(location) ?: return
        val thresholds = runCatching { petWeatherThresholds() }.getOrDefault(PetWeatherThresholds.DEFAULT)
        val walk = walkAdviceForReading(weather, forecast, thresholds, now) ?: return
        if (walk.rating == WalkRating.GREAT || walk.bestTimeIsTomorrow) return
        walk.bestTimeEpochSeconds?.takeIf { it > now }?.let(scheduleWalkTime)
    }
}

/** Below this, the day's rain chance isn't worth a mention in the briefing. */
private const val BRIEFING_RAIN_CHANCE = 40

/**
 * The hours from the one under way at [nowEpochSeconds] to the end of the city's day: what the
 * briefing's bad-air phrase and warnings look at.
 */
private fun restOfDay(
    forecast: List<DayForecast>,
    utcOffsetSeconds: Int,
    nowEpochSeconds: Long,
): List<HourlySlot> {
    fun localDay(epochSeconds: Long) = Math.floorDiv(epochSeconds + utcOffsetSeconds, 86_400L)
    return forecast.flatMap { it.hourlySlots }.filter { slot ->
        val start = slot.epochSeconds ?: return@filter false
        start + 3600 > nowEpochSeconds && localDay(start) == localDay(nowEpochSeconds)
    }
}

/** What the briefing warns about today, ahead of the walk. Same rules as iOS's `NotificationPlanner`. */
internal sealed interface BriefingWarning {
    /** Hurricane force wind in the hour under way. */
    data object HurricaneNow : BriefingWarning

    /** Hurricane force wind from a later hour today ([timeLabel], such as "2 PM"). */
    data class HurricaneFrom(
        val timeLabel: String,
    ) : BriefingWarning

    /** Likely thunderstorms from a later hour today. */
    data class StormsFrom(
        val timeLabel: String,
    ) : BriefingWarning
}

/**
 * Hurricane force wind now (the hour under way, or the reading's own wind or gusts, which the walk
 * card goes by), else hurricane force wind later today, else likely storms later today. Storms in the
 * hour under way aren't mentioned: the title already gives its weather.
 */
internal fun briefingWarning(
    weather: CurrentWeather,
    forecast: List<DayForecast>,
    nowEpochSeconds: Long,
    thresholds: PetWeatherThresholds,
): BriefingWarning? {
    val hours = restOfDay(forecast, weather.offsetSeconds, nowEpochSeconds)
    val (underWay, later) = hours.partition { (it.epochSeconds ?: Long.MAX_VALUE) <= nowEpochSeconds }
    val readingHurricane = windForce(weather.windSpeed, weather.units) >= WindForce.HURRICANE ||
        weather.windGust?.let { windForce(it, weather.units) >= WindForce.HURRICANE } == true
    if (readingHurricane || underWay.any(::isHurricaneForce)) return BriefingWarning.HurricaneNow
    later.firstOrNull(::isHurricaneForce)?.let { return BriefingWarning.HurricaneFrom(it.timeLabel) }
    later.firstOrNull { hasLikelyStorms(it, thresholds) }?.let { return BriefingWarning.StormsFrom(it.timeLabel) }
    return null
}

/**
 * "Smoky air today" when bad air rules out walking hours from the one under way to the end of the
 * city's day, named by the worst hour's main pollutant with the hero caption's names
 * ([airNameOrPoorRes]): fine particles read as smoke, coarse ones as dust, ozone by name, anything
 * else as poor air. Null on a clean day.
 */
internal fun badAirPhrase(
    strings: Strings,
    forecast: List<DayForecast>,
    utcOffsetSeconds: Int,
    nowEpochSeconds: Long,
    thresholds: PetWeatherThresholds,
): String? {
    val worst = restOfDay(forecast, utcOffsetSeconds, nowEpochSeconds)
        .filter { it.hasBadAirForWalking(thresholds) }
        .maxByOrNull { it.airQuality?.index ?: 0 } ?: return null
    return strings.get(R.string.notif_bad_air_today, strings.get(airNameOrPoorRes(worst.airQuality?.mainPollutant)))
}

/**
 * "52° and partly cloudy in Knoxville" / "High 70°, low 45°, 60% chance of rain. Sunny's picked
 * 1 PM for a walk." The walk sentence only comes with [walk], which is null without a dog. Bad-air
 * hours are left out of the walk silently, so [badAir] ("Smoky air today") says why. Hurricane force
 * wind ([warning]) takes the walk sentence's place, and is said with or without a dog; storms later
 * come before the walk sentence ("Storms from 4 PM.").
 */
internal fun buildBriefingContent(
    strings: Strings,
    weather: CurrentWeather,
    todayForecast: DayForecast?,
    walk: WalkAdvice?,
    badAir: String? = null,
    warning: BriefingWarning? = null,
): Pair<String, String> {
    // As on the hero: today's forecast high and low, taking in the temperature now.
    val (high, low) = todayHighLow(weather, todayForecast).let { (h, l) -> h.roundToInt() to l.roundToInt() }
    val title = strings.get(
        R.string.notif_briefing_title,
        weather.temperature.roundToInt(),
        conditionPhrase(strings, weather),
        weather.cityName,
    )
    val rainChance = displayedRainChance(todayForecast?.precipitationChance ?: 0)?.takeIf { it >= BRIEFING_RAIN_CHANCE }
    val highLow = if (rainChance != null) {
        strings.get(R.string.notif_briefing_high_low_rain, high, low, rainChance)
    } else {
        strings.get(R.string.notif_briefing_high_low, high, low)
    }
    val rest = when (warning) {
        BriefingWarning.HurricaneNow -> listOf(strings.get(R.string.notif_briefing_hurricane_now))
        is BriefingWarning.HurricaneFrom -> listOf(strings.get(R.string.notif_briefing_hurricane_from, warning.timeLabel))
        is BriefingWarning.StormsFrom ->
            listOfNotNull(strings.get(R.string.walk_storms_from, warning.timeLabel), walkSentence(strings, walk, badAir))
        null -> listOfNotNull(walkSentence(strings, walk, badAir))
    }
    val body = (listOf(highLow) + rest).joinToString(" ")
    return title to body
}

private fun walkSentence(
    strings: Strings,
    walk: WalkAdvice?,
    badAir: String?,
): String? {
    val shortOne = badAir?.let { strings.get(R.string.notif_briefing_bad_air_short, it) }
    val walkNow = when {
        walk == null -> return null
        walk.rating == WalkRating.GREAT -> strings.get(R.string.notif_briefing_walk_now)
        walk.bestTimeLabel != null && !walk.bestTimeIsTomorrow -> strings.get(R.string.notif_briefing_walk_at, walk.bestTimeLabel)
        else -> return badAir?.let { strings.get(R.string.notif_briefing_bad_air_potty, it) }
            ?: strings.get(R.string.notif_briefing_no_walk)
    }
    return listOfNotNull(walkNow, shortOne).joinToString(" ")
}

/** How far after the walk hour's start the walk nudge looks for hurricane force wind and storms. */
private const val WALK_NUDGE_AHEAD_SECONDS = 3 * 60 * 60L

/**
 * Hurricane force wind, or else likely storms, in the forecast hours after the walk hour (starting
 * at [walkHourStart]) that start within [WALK_NUDGE_AHEAD_SECONDS] of it, on the same city day.
 * An hour without a time doesn't count. Same rule as iOS's `NotificationPlanner.walkNudgeWarning`.
 */
internal fun walkNudgeWarning(
    walkHourStart: Long,
    slots: List<HourlySlot>,
    utcOffsetSeconds: Int,
    thresholds: PetWeatherThresholds,
): WalkWarning? {
    fun localDay(epochSeconds: Long) = Math.floorDiv(epochSeconds + utcOffsetSeconds, 86_400L)
    val after = slots
        .filter { slot ->
            val start = slot.epochSeconds ?: return@filter false
            start > walkHourStart && start - walkHourStart <= WALK_NUDGE_AHEAD_SECONDS && localDay(start) == localDay(walkHourStart)
        }.sortedBy { it.epochSeconds }
    after.firstOrNull(::isHurricaneForce)?.let { return WalkWarning.Hurricane(it.timeLabel) }
    after.firstOrNull { hasLikelyStorms(it, thresholds) }?.let { return WalkWarning.Storms(it.timeLabel) }
    return null
}

/**
 * "Time for a walk" / "64° and clear in Knoxville. Sunny's waiting by the door. Smoky air today, so a
 * short one." Hurricane force wind or storms soon after ([warning], from [walkNudgeWarning]) take the
 * bad-air sentence's place: "Storms from 4 PM, so be back by then."
 */
internal fun buildWalkTimeContent(
    strings: Strings,
    weather: CurrentWeather,
    badAir: String? = null,
    warning: WalkWarning? = null,
): Pair<String, String> {
    val body = strings.get(R.string.notif_walk_body, weather.temperature.roundToInt(), conditionPhrase(strings, weather), weather.cityName)
    val trailing = when (warning) {
        is WalkWarning.Hurricane -> strings.get(R.string.notif_walk_hurricane_from, warning.timeLabel)
        is WalkWarning.Storms -> strings.get(R.string.notif_walk_storms_from, warning.timeLabel)
        null -> badAir?.let { strings.get(R.string.notif_walk_bad_air, it) }
    }
    return strings.get(R.string.notif_walk_title) to listOfNotNull(body, trailing).joinToString(" ")
}

/** Rain, drizzle, snow and storms by their own description ("light rain"); otherwise the sky ("partly cloudy"). */
internal fun conditionPhrase(
    strings: Strings,
    weather: CurrentWeather,
): String {
    if (isPrecipitating(weather.conditionMain)) return weather.description.lowercase()
    return strings.get(
        when (skyCondition(weather.conditionMain, weather.cloudPercent, weather.iconCode)) {
            SkyCondition.CLEAR -> R.string.notif_sky_clear
            SkyCondition.PARTLY_CLOUDY -> R.string.notif_sky_partly_cloudy
            SkyCondition.MOSTLY_CLOUDY -> R.string.notif_sky_mostly_cloudy
            SkyCondition.OVERCAST -> R.string.notif_sky_overcast
            SkyCondition.MIST -> R.string.notif_sky_mist
            SkyCondition.HAZE -> R.string.notif_sky_haze
            SkyCondition.FOG -> R.string.notif_sky_fog
            SkyCondition.SMOKE -> R.string.notif_sky_smoke
            SkyCondition.DUST -> R.string.notif_sky_dust
        },
    )
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
    strings: Strings,
    rain: NextRain.Starting,
    cityName: String,
    utcOffsetSeconds: Int,
    hasDog: Boolean,
): Pair<String, String> {
    val time = formatEpochTime(rain.epochSeconds, utcOffsetSeconds)
    // The title is the screen's next-rain line.
    val title = strings.get(if (rain.snow) R.string.next_snow_starting else R.string.next_rain_starting, time)
    val body = when {
        rain.snow && hasDog -> R.string.notif_snow_body_dog
        rain.snow -> R.string.notif_snow_body_no_dog
        hasDog -> R.string.notif_rain_body_dog
        else -> R.string.notif_rain_body_no_dog
    }
    return title to strings.get(body, cityName)
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
    // The notifications' text, from strings.xml.
    private val strings: Strings,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    suspend fun check(
        location: SavedLocation,
        weather: CurrentWeather?,
        forecast: List<DayForecast>?,
    ) {
        if (weather == null || forecast == null || !isEnabled()) return
        val rain = rainAlertFor(forecast, nowEpochSeconds(), weather.offsetSeconds, lastAlertStart()) ?: return
        val (title, body) = buildRainAlertContent(strings, rain, weather.cityName, weather.offsetSeconds, hasDog())
        val mood = if (rain.snow) PetMood.SNOW else PetMood.RAIN
        postNotification(WeatherNotification(NotificationKind.RAIN, title, body, mood, (location.latitude ?: 0.0) < 0))
        setLastAlertStart(rain.epochSeconds)
    }
}
