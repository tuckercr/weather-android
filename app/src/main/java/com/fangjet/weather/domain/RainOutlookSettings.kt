package com.fangjet.weather.domain

/**
 * The rain outlook card, from Remote Config: whether it shows, how many hours it charts from the
 * current one, and the chance of rain (%) some hour must reach for it to appear, which is also the
 * chance from which an hour counts as wet. Same keys and defaults as iOS.
 */
data class RainOutlookSettings(
    val enabled: Boolean = true,
    val hours: Int = 10,
    val minChance: Int = 20,
)
