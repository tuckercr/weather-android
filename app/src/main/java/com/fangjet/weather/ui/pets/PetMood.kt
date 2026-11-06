package com.fangjet.weather.ui.pets

import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.util.FeelsLikeBand
import com.fangjet.weather.util.feelsLikeBand

/**
 * How the cat and dog react on the home screen, derived from the current conditions. [FAIR] is a
 * clear day that's chilly or mild, [SUNNY] a clear day that's warm (sunglasses on).
 */
enum class PetMood { FAIR, SUNNY, CLOUDY, RAIN, STORM, SNOW, HOT, COLD, NIGHT }

/**
 * Picks the pet mood. Precipitation and storms win over temperature, temperature extremes win over
 * time of day, and an OpenWeatherMap icon ending in "n" means night. The hot / cold cut-offs come
 * from [thresholds] (Remote Config `pet_hot_threshold_c` / `pet_cold_threshold_c`) and are
 * compared with the feels-like temperature, since humid heat and wind chill are what pets feel. A
 * clear day is warm by the Feels Like tile's bands (above the walk comfort range). Same rules as
 * iOS's `PetMood.forConditions`.
 */
fun petMoodFor(
    conditionMain: String,
    iconCode: String,
    feelsLikeC: Double,
    thresholds: PetWeatherThresholds = PetWeatherThresholds.DEFAULT,
): PetMood =
    when (conditionMain.lowercase()) {
        "thunderstorm" -> PetMood.STORM
        "snow" -> PetMood.SNOW
        "rain", "drizzle" -> PetMood.RAIN
        else -> when {
            feelsLikeC >= thresholds.petHotC -> PetMood.HOT
            feelsLikeC <= thresholds.petColdC -> PetMood.COLD
            iconCode.endsWith("n") -> PetMood.NIGHT
            conditionMain.equals("clear", ignoreCase = true) ->
                if (feelsLikeBand(feelsLikeC, thresholds) == FeelsLikeBand.WARM) PetMood.SUNNY else PetMood.FAIR
            else -> PetMood.CLOUDY
        }
    }
