package com.fangjet.weather.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fangjet.weather.R

/**
 * A weather condition icon, bundled with the app and drawn in the pet scene's style. Every shape
 * has the pets' navy outline, so the white clouds hold up on the light cards, and the fills carry
 * them on the dark ones. Night icons show the moon's phase at [epochSeconds] (when null, the screen's
 * clock, [LocalScreenNow]), lit from the left in the southern hemisphere ([LocalSouthernHemisphere]).
 */
@Composable
fun WeatherIcon(
    iconCode: String,
    contentDescription: String,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    epochSeconds: Long? = null,
) {
    val phase = moonPhaseAt(epochSeconds ?: screenNowEpochSeconds())
    Image(
        painter = painterResource(weatherIconRes(iconCode, phase, LocalSouthernHemisphere.current)),
        contentDescription = contentDescription,
        modifier = modifier.size(sizeDp.dp),
    )
}

/**
 * Maps an OpenWeatherMap icon code ("01d", "10n"…; Open-Meteo's WMO codes are mapped onto the same
 * set) to its drawable. Only clear, few clouds and rain look different at night, where they show
 * the moon at [phase]; the rest share one icon.
 */
@DrawableRes
internal fun weatherIconRes(
    iconCode: String,
    phase: MoonPhase = MoonPhase.FULL,
    southernHemisphere: Boolean = false,
): Int {
    fun moon(
        north: IntArray,
        south: IntArray,
    ) = (if (southernHemisphere) south else north)[phase.ordinal]
    val night = iconCode.endsWith("n")
    return when (iconCode.take(2)) {
        "01" -> if (night) moon(CLEAR_NIGHT, CLEAR_NIGHT_SOUTH) else R.drawable.wx_01d
        "02" -> if (night) moon(FEW_CLOUDS_NIGHT, FEW_CLOUDS_NIGHT_SOUTH) else R.drawable.wx_02d
        "03" -> R.drawable.wx_03d
        "04" -> R.drawable.wx_04d
        "09" -> R.drawable.wx_09d
        "10" -> if (night) moon(RAIN_NIGHT, RAIN_NIGHT_SOUTH) else R.drawable.wx_10d
        "11" -> R.drawable.wx_11d
        "13" -> R.drawable.wx_13d
        "50" -> R.drawable.wx_50d
        else -> R.drawable.wx_03d
    }
}

// The night icons for each MoonPhase, in order; new and full are shared by both hemispheres.
private val CLEAR_NIGHT = intArrayOf(
    R.drawable.wx_01n_new,
    R.drawable.wx_01n_waxing_crescent,
    R.drawable.wx_01n_first_quarter,
    R.drawable.wx_01n_waxing_gibbous,
    R.drawable.wx_01n_full,
    R.drawable.wx_01n_waning_gibbous,
    R.drawable.wx_01n_last_quarter,
    R.drawable.wx_01n_waning_crescent,
)

private val CLEAR_NIGHT_SOUTH = intArrayOf(
    R.drawable.wx_01n_new,
    R.drawable.wx_01n_waxing_crescent_s,
    R.drawable.wx_01n_first_quarter_s,
    R.drawable.wx_01n_waxing_gibbous_s,
    R.drawable.wx_01n_full,
    R.drawable.wx_01n_waning_gibbous_s,
    R.drawable.wx_01n_last_quarter_s,
    R.drawable.wx_01n_waning_crescent_s,
)

private val FEW_CLOUDS_NIGHT = intArrayOf(
    R.drawable.wx_02n_new,
    R.drawable.wx_02n_waxing_crescent,
    R.drawable.wx_02n_first_quarter,
    R.drawable.wx_02n_waxing_gibbous,
    R.drawable.wx_02n_full,
    R.drawable.wx_02n_waning_gibbous,
    R.drawable.wx_02n_last_quarter,
    R.drawable.wx_02n_waning_crescent,
)

private val FEW_CLOUDS_NIGHT_SOUTH = intArrayOf(
    R.drawable.wx_02n_new,
    R.drawable.wx_02n_waxing_crescent_s,
    R.drawable.wx_02n_first_quarter_s,
    R.drawable.wx_02n_waxing_gibbous_s,
    R.drawable.wx_02n_full,
    R.drawable.wx_02n_waning_gibbous_s,
    R.drawable.wx_02n_last_quarter_s,
    R.drawable.wx_02n_waning_crescent_s,
)

private val RAIN_NIGHT = intArrayOf(
    R.drawable.wx_10n_new,
    R.drawable.wx_10n_waxing_crescent,
    R.drawable.wx_10n_first_quarter,
    R.drawable.wx_10n_waxing_gibbous,
    R.drawable.wx_10n_full,
    R.drawable.wx_10n_waning_gibbous,
    R.drawable.wx_10n_last_quarter,
    R.drawable.wx_10n_waning_crescent,
)

private val RAIN_NIGHT_SOUTH = intArrayOf(
    R.drawable.wx_10n_new,
    R.drawable.wx_10n_waxing_crescent_s,
    R.drawable.wx_10n_first_quarter_s,
    R.drawable.wx_10n_waxing_gibbous_s,
    R.drawable.wx_10n_full,
    R.drawable.wx_10n_waning_gibbous_s,
    R.drawable.wx_10n_last_quarter_s,
    R.drawable.wx_10n_waning_crescent_s,
)
