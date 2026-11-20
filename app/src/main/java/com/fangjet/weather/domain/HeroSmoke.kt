package com.fangjet.weather.domain

/**
 * Smoke drawn over the hero, like fog: the pets keep the mood's look under it. Haze when smoke
 * (fine particles) makes the air bad enough for potty breaks only, falling ash at the worst band,
 * where the walk card says to stay in. OpenWeather's own "Smoke" and "Ash" conditions count too,
 * for cities without air quality. Same as iOS's `HeroSmoke`.
 */
enum class HeroSmoke {
    HAZE,
    ASH,
}

/** [walk] is the verdict on the hour under way; null without a forecast. */
fun heroSmoke(
    walk: WalkCheck?,
    conditionMain: String,
): HeroSmoke? {
    val smoke = walk?.factors?.firstOrNull { (it.reason as? WalkReason.Air)?.pollutant == AirQuality.Pollutant.FINE_PARTICLES }
    val condition = conditionMain.lowercase()
    return when {
        smoke != null && smoke.level >= WalkLevel.STAY_IN -> HeroSmoke.ASH
        condition == "ash" -> HeroSmoke.ASH
        smoke != null && smoke.level >= WalkLevel.POTTY_ONLY -> HeroSmoke.HAZE
        condition == "smoke" -> HeroSmoke.HAZE
        else -> null
    }
}
