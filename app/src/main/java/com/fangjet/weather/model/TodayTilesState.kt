package com.fangjet.weather.model

import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WalkLevel
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.domain.airWalkLevel
import com.fangjet.weather.util.Daylight
import com.fangjet.weather.util.FeelsLikeBand
import com.fangjet.weather.util.FluffLevel
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.UvLine
import com.fangjet.weather.util.VisibilityBand
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.daylight
import com.fangjet.weather.util.dewPointC
import com.fangjet.weather.util.feelsLikeBand
import com.fangjet.weather.util.feelsLikeBarPosition
import com.fangjet.weather.util.feelsLikeGap
import com.fangjet.weather.util.fluffLevel
import com.fangjet.weather.util.gustWorthMentioning
import com.fangjet.weather.util.isCalmWind
import com.fangjet.weather.util.pavementBand
import com.fangjet.weather.util.skyCondition
import com.fangjet.weather.util.uvLine
import com.fangjet.weather.util.visibilityBand
import com.fangjet.weather.util.windDirectionIndex
import com.fangjet.weather.util.windForce
import com.fangjet.weather.util.windsockDroopDegrees

/**
 * The Today tiles with everything they show worked out: which tiles there are and in what order,
 * and each one's values, bands and lines. The words come from string resources, so the tiles hold
 * what to say and `TodayTiles` says it and colours it. Built with the screen state. Same as iOS's
 * `TodayTilesState`.
 */
data class TodayTilesState(
    /** Feels like, wind, Fluff Factor, Paw Check, UV and air quality, as shown; an odd one out takes the full width. */
    val small: List<TodayTile>,
    val daylight: DaylightTile?,
    val sky: SkyTile?,
)

sealed interface TodayTile {
    /** The feels-like temperature with its band, where it sits on the bar, and how far it is from the actual temperature. */
    data class FeelsLike(
        val feelsLike: Double,
        val units: WeatherUnits,
        val band: FeelsLikeBand,
        val barPosition: Double,
        /** Degrees warmer (positive) or cooler than it is. */
        val gap: Int,
    ) : TodayTile

    /** The speed, the windsock's droop, and where it comes from with any gusts that stand out. */
    data class Wind(
        val speed: Double,
        val units: WeatherUnits,
        val droopDegrees: Double,
        /** Too light to show a direction. */
        val calm: Boolean,
        val force: WindForce,
        /** 0 (N) to 7 (NW). */
        val directionIndex: Int,
        val gust: Double?,
    ) : TodayTile

    /** Fluff Factor: the humidity, the dew point in the reading's units, and how fluffed up Misty is. */
    data class Fluff(
        val humidityPercent: Int,
        val dewPoint: Double?,
        val units: WeatherUnits,
        val level: FluffLevel,
        /** Muggy says the pup is panting, with a dog. */
        val hasDog: Boolean,
    ) : TodayTile

    /** Paw Check: the pavement estimate and its band, which picks the paw and the line. */
    data class PawCheck(
        val pavement: Double,
        val units: WeatherUnits,
        val band: PavementBand,
    ) : TodayTile

    /** The UV index and the peak, or a line for pale-nosed dogs. */
    data class Uv(
        val uvIndex: Double,
        val line: UvLine?,
    ) : TodayTile

    /** The air quality, which of its lines to show, and how it reads. */
    data class Air(
        val air: AirQuality,
        /** Into `air_quality_lines_dog` with a dog, otherwise `air_quality_lines` by band. */
        val lineIndex: Int,
        val hasDog: Boolean,
        val tone: AirTone,
    ) : TodayTile
}

/**
 * Bad where the walk rules say potty breaks only, or without a dog (whose line goes by band) from
 * Unhealthy up; good in the first band.
 */
enum class AirTone { GOOD, SO_SO, BAD }

/** Daylight: where the day is between sunrise and sunset. */
data class DaylightTile(
    val daylight: Daylight,
    val sunrise: Long,
    val sunset: Long,
    val utcOffsetSeconds: Int,
    /** The UV index, carried here when UV has no tile of its own. */
    val uvIndex: Double?,
)

/** Misty's View: the sky, and how far she can see when the reading says. */
data class SkyTile(
    val sky: SkyCondition,
    val visibilityMeters: Int?,
    val visibilityBand: VisibilityBand?,
)

@Suppress("LongParameterList")
fun todayTilesState(
    weather: CurrentWeather,
    // The cached forecast's hour standing in for an old reading: no cloud cover or visibility.
    showingForecast: Boolean,
    pavementNow: Double?,
    groundWet: Boolean,
    uvIndex: Double?,
    uvPeak: HourlySlot?,
    airQuality: AirQuality?,
    hasDog: Boolean,
    nowEpochSeconds: Long,
    thresholds: PetWeatherThresholds,
    switches: TodayTileSwitches,
): TodayTilesState {
    val units = weather.units
    val sunrise = weather.sunriseEpoch
    val sunset = weather.sunsetEpoch
    val showsDaylight = switches.daylight && sunrise != null && sunset != null
    // UV gets its own tile when switched on, or when there's no Daylight tile to carry it.
    val uvTile = uvIndex != null && (switches.uv || !showsDaylight)
    val small = buildList {
        if (switches.feelsLike) {
            val feelsLikeC = units.toCelsius(weather.feelsLike)
            add(
                TodayTile.FeelsLike(
                    feelsLike = weather.feelsLike,
                    units = units,
                    band = feelsLikeBand(feelsLikeC, thresholds),
                    barPosition = feelsLikeBarPosition(feelsLikeC, thresholds),
                    gap = feelsLikeGap(weather.feelsLike, weather.temperature),
                ),
            )
        }
        if (switches.wind) {
            val force = windForce(weather.windSpeed, units)
            add(
                TodayTile.Wind(
                    speed = weather.windSpeed,
                    units = units,
                    droopDegrees = windsockDroopDegrees(weather.windSpeed, units),
                    calm = force == WindForce.CALM || isCalmWind(weather.windSpeed, units),
                    force = force,
                    directionIndex = windDirectionIndex(weather.windDeg),
                    gust = gustWorthMentioning(weather.windSpeed, weather.windGust, units),
                ),
            )
        }
        if (switches.humidity) {
            val dewC = dewPointC(units.toCelsius(weather.temperature), weather.humidityPercent)
            add(
                TodayTile.Fluff(
                    humidityPercent = weather.humidityPercent,
                    dewPoint = dewC?.let(units::fromCelsius),
                    units = units,
                    level = fluffLevel(dewC, weather.humidityPercent),
                    hasDog = hasDog,
                ),
            )
        }
        if (switches.pawCheck && pavementNow != null) {
            val band = pavementBand(units.toCelsius(pavementNow), groundWet, units.toCelsius(weather.feelsLike), thresholds)
            add(TodayTile.PawCheck(pavementNow, units, band))
        }
        if (uvTile) add(TodayTile.Uv(uvIndex, uvLine(uvPeak, nowEpochSeconds, hasDog)))
        if (airQuality != null) {
            val band = airQuality.band
            val bad = if (hasDog) airWalkLevel(band, thresholds) >= WalkLevel.POTTY_ONLY else band >= 3
            add(
                TodayTile.Air(
                    air = airQuality,
                    lineIndex = if (hasDog) airQualityDogLine(band, thresholds) else band,
                    hasDog = hasDog,
                    tone = when {
                        bad -> AirTone.BAD
                        band == 0 -> AirTone.GOOD
                        else -> AirTone.SO_SO
                    },
                ),
            )
        }
    }
    val daylight = if (showsDaylight) {
        DaylightTile(daylight(nowEpochSeconds, sunrise, sunset), sunrise, sunset, weather.offsetSeconds, uvIndex.takeUnless { uvTile })
    } else {
        null
    }
    val sky = if (switches.sky) {
        val visibility = weather.visibilityMeters.takeUnless { showingForecast }
        SkyTile(
            sky = skyCondition(weather.conditionMain, weather.cloudPercent.takeUnless { showingForecast }, weather.iconCode),
            visibilityMeters = visibility,
            visibilityBand = visibility?.let(::visibilityBand),
        )
    } else {
        null
    }
    return TodayTilesState(small, daylight, sky)
}

/**
 * Which of `air_quality_lines_dog` the Air Quality tile shows: it follows the walk rules
 * ([airWalkLevel]), so it moves with Remote Config's `walk_bad_air_band` and never contradicts the
 * walk card. Same as iOS's `AirQuality.line(hasDog:thresholds:)`.
 */
internal fun airQualityDogLine(
    band: Int,
    thresholds: PetWeatherThresholds,
): Int =
    when (airWalkLevel(band, thresholds)) {
        WalkLevel.STAY_IN, WalkLevel.DANGER -> 5
        WalkLevel.POTTY_ONLY -> 3
        WalkLevel.FINE, WalkLevel.WAIT -> 2
        WalkLevel.GREAT -> if (band == 0) 0 else 1
    }
