package com.fangjet.weather.domain

/** Which Today tiles show, from Remote Config. UV starts off. Same switches as iOS. */
data class TodayTileSwitches(
    val feelsLike: Boolean = true,
    val wind: Boolean = true,
    val humidity: Boolean = true,
    val pawCheck: Boolean = true,
    val uv: Boolean = false,
    val daylight: Boolean = true,
    val sky: Boolean = true,
)
