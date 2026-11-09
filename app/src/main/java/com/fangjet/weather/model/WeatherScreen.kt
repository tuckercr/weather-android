package com.fangjet.weather.model

import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.SeasonalTheme
import com.fangjet.weather.domain.SeasonalThemes
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WalkAdvice
import com.fangjet.weather.domain.isPrecipitating
import com.fangjet.weather.domain.petMoodAt
import com.fangjet.weather.domain.walkAdviceForReading
import com.fangjet.weather.domain.walkCardShown
import com.fangjet.weather.util.HourlyStripItem
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.util.RainOutlook
import com.fangjet.weather.util.forecastAsCurrent
import com.fangjet.weather.util.fromCurrentSlot
import com.fangjet.weather.util.hourlyStrip
import com.fangjet.weather.util.iconCodeAt
import com.fangjet.weather.util.isDayOf
import com.fangjet.weather.util.isTooOldToShowAsNow
import com.fangjet.weather.util.nextRain
import com.fangjet.weather.util.rainOutlook
import com.fangjet.weather.util.sunEvents
import com.fangjet.weather.util.todayHighLow
import com.fangjet.weather.util.withSunTimesToday
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** The Remote Config switches and settings the weather screen goes by. Same as iOS's `WeatherScreenConfig`. */
data class WeatherScreenConfig(
    val thresholds: PetWeatherThresholds = PetWeatherThresholds.DEFAULT,
    val seasonalThemes: List<SeasonalTheme> = emptyList(),
    val nextRainEnabled: Boolean = true,
    val rainOutlookSettings: RainOutlookSettings = RainOutlookSettings(),
    val airQualityEnabled: Boolean = true,
    val hourlyForecastEnabled: Boolean = true,
    val radarEnabled: Boolean = true,
    val weekForecastEnabled: Boolean = true,
    val walkCardEnabled: Boolean = true,
    val todayTiles: TodayTileSwitches = TodayTileSwitches(),
)

/**
 * What the weather screen shows for the active city, worked out by [WeatherForecastViewModel] from
 * the reading, the forecast, its clock and the settings. The screen only draws it. Same as iOS's
 * `WeatherScreen`.
 */
sealed interface WeatherScreen {
    data object Loading : WeatherScreen

    /**
     * Nothing to show as now: no reading at all, or the cached one too old to show with no forecast
     * hour to stand in for it. [oldReading] is that reading, for saying when it was taken.
     */
    data class Error(
        val errorKey: String,
        val canRetry: Boolean,
        val oldReading: CurrentWeather? = null,
        val nowEpochSeconds: Long = 0,
    ) : WeatherScreen

    data class Weather(
        val state: WeatherScreenState,
    ) : WeatherScreen
}

/** The old reading behind the "Showing weather from..." banner, and why the refresh failed. */
data class StaleReading(
    val reading: CurrentWeather,
    val errorKey: String,
)

/** What the Today tiles are given beside the reading. */
data class TodayTileInputs(
    /** The hour in progress's pavement estimate, in the reading's units; null without a dog. */
    val pavementNow: Double?,
    /** Rain or snow lately, or now: below freezing the pavement may be icy. */
    val groundWet: Boolean,
    /** The hour's UV index, only in the daytime (it's always 0 after dark). */
    val uvIndex: Double?,
    /** Today's hour with the highest UV, for the UV tile's peak. */
    val uvPeak: HourlySlot?,
    /** The hour's air quality; null while air quality is switched off. */
    val airQuality: AirQuality?,
)

/** The rest of the week below the radar: its days, or the forecast loading or failed. */
sealed interface WeekSection {
    data class Days(
        val days: List<DayForecast>,
    ) : WeekSection

    data object Loading : WeekSection

    data class Error(
        val errorKey: String,
        val canRetry: Boolean,
    ) : WeekSection
}

/**
 * Everything the weather screen shows for a reading, worked out in one place. The words come from
 * string resources, so the hero caption's choice is here and the screen picks its words. Same as
 * iOS's `WeatherScreenState`.
 */
data class WeatherScreenState(
    /** The screen's clock when this was worked out. */
    val nowEpochSeconds: Long,
    /** The reading shown as now: the latest, or the cached forecast's hour standing in for one too old to show. */
    val weather: CurrentWeather,
    /** True when [weather] is that forecast hour: there's no cloud cover or visibility to show. */
    val showingForecast: Boolean,
    /** Set while refreshes are failing and the cached reading is on screen. */
    val stale: StaleReading?,
    val hasDog: Boolean,
    /**
     * [weather] with today's sunrise and sunset, for a reading cached on an earlier day, so the
     * Daylight tile and the walk card follow the clock as the hero does.
     */
    val weatherToday: CurrentWeather,
    /** Day or night by the clock, so a reading cached before sunset doesn't show the daytime hero. */
    val iconCodeNow: String,
    val mood: PetMood,
    /** Today's seasonal theme by the city's own date, so it ends by itself when its day does. */
    val seasonalTheme: SeasonalTheme?,
    /**
     * Dark at the city by the clock ([iconCodeNow], as the hero's night mood goes by), whatever the
     * weather: the seasonal theme's night look follows this, so a rainy Halloween evening is still
     * a Halloween night. The pets' weather still follows [mood].
     */
    val dark: Boolean,
    /** Which caption the hero shows: the walk's reason, the theme's, or the mood's. */
    val heroCaption: HeroCaption,
    /** Today's forecast, by the city's own date; null before the forecast has today. */
    val today: DayForecast?,
    /** Today's high and low in whole degrees, taking in the temperature now. */
    val high: Int,
    val low: Int,
    /** The hourly strip's hours and sunrise and sunset; empty when it's switched off or there are no hours left. */
    val hourlyStrip: List<HourlyStripItem>,
    /** When rain or snow starts or stops in the next 2 hours. */
    val nextRain: NextRain?,
    /** The chance of rain over the next few hours; it takes over the next-rain line. */
    val rainOutlook: RainOutlook?,
    /** Null while the forecast is loading or failed, which hides the card rather than advising on nothing. */
    val walkAdvice: WalkAdvice?,
    val showsWalkCard: Boolean,
    /** What the walk card says; null when it doesn't show. */
    val walkCard: WalkCardContent?,
    val tiles: TodayTileInputs,
    /** The Today tiles, worked out from [tiles] and the reading. */
    val todayTiles: TodayTilesState,
    val showsRadar: Boolean,
    /** Null when the rest of the week is switched off, or there's nothing to show for it. */
    val week: WeekSection?,
) {
    val night: Boolean get() = mood == PetMood.NIGHT
}

private fun localDateTime(
    epochSeconds: Long,
    offsetSeconds: Int,
): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.ofTotalSeconds(offsetSeconds))

/** The hourly strip covers the next 24 hours. */
const val HOURLY_STRIP_COUNT = 24

/**
 * The screen for [current] at [nowEpochSeconds]. Past the cutoff, an old reading isn't shown as now:
 * the cached forecast's current hour stands in for it, or the error if the forecast doesn't cover now.
 */
fun weatherScreen(
    current: LoadingState<CurrentWeather>,
    forecast: LoadingState<List<DayForecast>>,
    staleErrorKey: String?,
    nowEpochSeconds: Long,
    hasDog: Boolean,
    config: WeatherScreenConfig,
): WeatherScreen =
    when (current) {
        LoadingState.Idle, LoadingState.Loading -> WeatherScreen.Loading
        is LoadingState.Error -> WeatherScreen.Error(current.errorKey, current.canRetry)
        is LoadingState.Success -> {
            val reading = current.data
            val tooOld = staleErrorKey != null && reading.isTooOldToShowAsNow(nowEpochSeconds)
            val forecastNow = if (tooOld) {
                (forecast as? LoadingState.Success)?.data?.let { forecastAsCurrent(reading, it, nowEpochSeconds) }
            } else {
                null
            }
            if (tooOld && forecastNow == null) {
                WeatherScreen.Error(staleErrorKey.orEmpty(), canRetry = true, oldReading = reading, nowEpochSeconds = nowEpochSeconds)
            } else {
                WeatherScreen.Weather(
                    weatherScreenState(
                        weather = forecastNow ?: reading,
                        showingForecast = forecastNow != null,
                        stale = staleErrorKey?.let { StaleReading(reading, it) },
                        forecast = forecast,
                        nowEpochSeconds = nowEpochSeconds,
                        hasDog = hasDog,
                        config = config,
                    ),
                )
            }
        }
    }

private fun weatherScreenState(
    weather: CurrentWeather,
    showingForecast: Boolean,
    stale: StaleReading?,
    forecast: LoadingState<List<DayForecast>>,
    nowEpochSeconds: Long,
    hasDog: Boolean,
    config: WeatherScreenConfig,
): WeatherScreenState {
    val days = (forecast as? LoadingState.Success)?.data
    val forecastDays = days.orEmpty()
    val offset = weather.offsetSeconds
    val weatherToday = weather.withSunTimesToday(nowEpochSeconds)
    val iconCodeNow = weather.iconCodeAt(nowEpochSeconds)
    val mood = weather.petMoodAt(nowEpochSeconds, config.thresholds)
    val dark = iconCodeNow.endsWith("n")
    val seasonalTheme = SeasonalThemes.themeOn(nowEpochSeconds, offset, config.seasonalThemes)
    val today = forecastDays.firstOrNull()?.takeIf { it.isDayOf(nowEpochSeconds, offset) }
    val (high, low) = todayHighLow(weather, today)

    // From the hour in progress on, however old the forecast on screen is.
    val upcomingSlots = forecastDays.flatMap { it.hourlySlots }.fromCurrentSlot(nowEpochSeconds)
    val stripSlots = upcomingSlots.take(HOURLY_STRIP_COUNT)
    val nextRain = if (config.nextRainEnabled) nextRain(forecastDays.firstOrNull()?.quarterHours.orEmpty(), nowEpochSeconds) else null
    val walkAdvice = days?.let { walkAdviceForReading(weather, it, config.thresholds, nowEpochSeconds, hasDog) }
    val hourNow = upcomingSlots.firstOrNull()
    val upcomingDays = if (today != null) forecastDays.drop(1) else forecastDays
    // A danger warning shows for everyone, dog or not.
    val showsWalkCard = walkAdvice?.let { walkCardShown(it, config.walkCardEnabled && hasDog) } ?: false
    val tiles = TodayTileInputs(
        // Paw check goes with the walk card: Settings' Dog walking switch hides both.
        pavementNow = hourNow?.pavementTemperature?.takeIf { hasDog },
        groundWet = hourNow?.groundWet == true || isPrecipitating(weather.conditionMain),
        uvIndex = hourNow?.uvIndex?.takeUnless { iconCodeNow.endsWith("n") },
        uvPeak = today?.hourlySlots?.filter { it.uvIndex != null }?.maxByOrNull { it.uvIndex ?: 0.0 },
        // The forecast's hour in progress, so it shows offline too. A forecast cached before the
        // switch went off can still have it, hence the check here as well.
        airQuality = hourNow?.airQuality?.takeIf { config.airQualityEnabled },
    )

    return WeatherScreenState(
        nowEpochSeconds = nowEpochSeconds,
        weather = weather,
        showingForecast = showingForecast,
        stale = stale,
        hasDog = hasDog,
        weatherToday = weatherToday,
        iconCodeNow = iconCodeNow,
        mood = mood,
        seasonalTheme = seasonalTheme,
        dark = dark,
        heroCaption = heroCaption(
            walk = walkAdvice?.now,
            themeCaption = seasonalTheme?.captionAt(dark, localDateTime(nowEpochSeconds, offset)),
            mood = mood,
            nowEpochSeconds = nowEpochSeconds,
            utcOffsetSeconds = offset,
            hasDog = hasDog,
            // The sky as Misty's View reads it, without a cloud cover when the forecast stands in.
            partlyCloudy = partlyCloudyCaptions(
                weather.conditionMain,
                weather.cloudPercent.takeUnless { showingForecast },
                weather.iconCode,
            ),
        ),
        today = today,
        high = high.roundToInt(),
        low = low.roundToInt(),
        hourlyStrip = if (config.hourlyForecastEnabled && stripSlots.isNotEmpty()) {
            hourlyStrip(stripSlots, sunEvents(forecastDays, weatherToday), nowEpochSeconds)
        } else {
            emptyList()
        },
        nextRain = nextRain,
        rainOutlook = rainOutlook(upcomingSlots, config.rainOutlookSettings, weather.units, nextRain),
        walkAdvice = walkAdvice,
        showsWalkCard = showsWalkCard,
        walkCard = walkAdvice?.takeIf { showsWalkCard }?.let(::walkCardContent),
        tiles = tiles,
        todayTiles = todayTilesState(
            weather = weatherToday,
            showingForecast = showingForecast,
            pavementNow = tiles.pavementNow,
            groundWet = tiles.groundWet,
            uvIndex = tiles.uvIndex,
            uvPeak = tiles.uvPeak,
            airQuality = tiles.airQuality,
            hasDog = hasDog,
            nowEpochSeconds = nowEpochSeconds,
            thresholds = config.thresholds,
            switches = config.todayTiles,
        ),
        showsRadar = config.radarEnabled,
        week = when {
            !config.weekForecastEnabled -> null
            upcomingDays.isNotEmpty() -> WeekSection.Days(upcomingDays)
            forecast is LoadingState.Loading -> WeekSection.Loading
            forecast is LoadingState.Error -> WeekSection.Error(forecast.errorKey, forecast.canRetry)
            else -> null
        },
    )
}
