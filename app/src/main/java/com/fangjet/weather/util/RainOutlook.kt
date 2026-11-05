package com.fangjet.weather.util

import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.WeatherUnits
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

// The rain outlook card: the chance of rain hour by hour for the next few hours, since the hourly
// icon (the best single model) and the chance (the ensemble) often disagree. Kept free of Compose
// so it can be tested. Same as iOS's `RainOutlook`.

/** What the card says and charts. */
data class RainOutlook(
    val headline: RainOutlookHeadline,
    /** The last hour in the window that's still wet (at the minimum chance or more). */
    val lastWetHour: String,
    /** That hour is the window's last: "The chance stays up past 6 PM." rather than "Drying out after 6 PM." */
    val staysUp: Boolean,
    /** The window's rain in all, or null when it's too little to mention. */
    val total: RainTotal?,
    val bars: List<RainBar>,
)

sealed interface RainOutlookHeadline {
    /** The next-rain line's own words ("Rain starting around 3:15 PM"): the card takes its place. */
    data class NextRain(
        val text: String,
    ) : RainOutlookHeadline

    /** The peak is [LIKELY_CHANCE] or more: "Rain likely now", or around [hour] when it's later. */
    data class Likely(
        val hour: String?,
    ) : RainOutlookHeadline

    /** Under that: "A chance of rain now", or around [hour]. */
    data class Chance(
        val hour: String?,
    ) : RainOutlookHeadline
}

/** "About 2.5 mm in all.", "About 0.25 inches in all." or "About 1 inch in all.". */
sealed interface RainTotal {
    data class Millimetres(
        val amount: String,
    ) : RainTotal

    data class Inches(
        val amount: String,
    ) : RainTotal

    data object OneInch : RainTotal
}

/** One hour of the chart. */
data class RainBar(
    /** As the hourly strip labels it: "4 PM". */
    val hour: String,
    /** 0 to 100. */
    val chance: Int,
    /** The hour's rain in mm, or null when unknown. */
    val amountMm: Double?,
) {
    /** Under the bars, where space is short: "4PM". */
    val compactHour: String get() = hour.filterNot(Char::isWhitespace)
}

/** From this chance the headline says rain is likely rather than a chance. */
const val LIKELY_CHANCE = 50

private const val MIN_TOTAL_MM = 0.5
private const val MIN_TOTAL_INCHES = 0.01
private const val MIN_BAR_MM = 0.1
private const val MIN_BAR_INCHES = 0.01

private const val MM_PER_INCH = 25.4

/** From this many mm the total shows without a decimal. */
private const val WHOLE_MM_FROM = 10.0

/**
 * The card for [slots] (from the hour in progress, as the hourly strip shows them), or null when
 * it's switched off or no hour in the first [RainOutlookSettings.hours] reaches
 * [RainOutlookSettings.minChance]. [nextRainText] is the next-rain line's text when that line
 * would show: the card replaces the line and uses it as its headline.
 */
fun rainOutlook(
    slots: List<HourlySlot>,
    settings: RainOutlookSettings,
    units: WeatherUnits,
    nextRainText: String?,
    locale: Locale = Locale.getDefault(),
): RainOutlook? {
    if (!settings.enabled) return null
    val window = slots.take(settings.hours)
    val chances = window.map { it.precipitationChance.coerceIn(0, 100) }
    val lastWet = chances.indexOfLast { it >= settings.minChance }
    if (lastWet < 0) return null
    // The first hour with the highest chance.
    val peak = chances.indexOf(chances.max())
    val peakHour = window[peak].timeLabel.takeIf { peak > 0 }
    val headline = when {
        nextRainText != null -> RainOutlookHeadline.NextRain(nextRainText)
        chances[peak] >= LIKELY_CHANCE -> RainOutlookHeadline.Likely(peakHour)
        else -> RainOutlookHeadline.Chance(peakHour)
    }
    return RainOutlook(
        headline = headline,
        lastWetHour = window[lastWet].timeLabel,
        staysUp = lastWet == window.lastIndex,
        total = rainTotal(window.sumOf { it.precipitationMm ?: 0.0 }, units, locale),
        bars = window.mapIndexed { i, slot -> RainBar(slot.timeLabel, chances[i], slot.precipitationMm) },
    )
}

/**
 * Metric from 0.5 mm, with one decimal below 10 mm ("2.5", and "10.0" for 9.97) and none from 10
 * ("12"); imperial from 0.01 inches, with up to two decimals ("0.1", "0.25"), and "1 inch" when
 * that rounds to exactly 1. Rounded half up.
 */
internal fun rainTotal(
    totalMm: Double,
    units: WeatherUnits,
    locale: Locale = Locale.getDefault(),
): RainTotal? {
    if (units == WeatherUnits.METRIC) {
        if (totalMm < MIN_TOTAL_MM) return null
        return RainTotal.Millimetres(format(rounded(totalMm, if (totalMm < WHOLE_MM_FROM) 1 else 0), locale))
    }
    val inches = totalMm / MM_PER_INCH
    if (inches < MIN_TOTAL_INCHES) return null
    val hundredths = rounded(inches, 2).stripTrailingZeros()
    return if (hundredths.compareTo(BigDecimal.ONE) == 0) RainTotal.OneInch else RainTotal.Inches(format(hundredths, locale))
}

/**
 * A bar's amount as its label shows it, without the unit: one decimal of mm from 0.1 mm ("2.5"),
 * or up to two decimals of an inch from 0.01 in ("0.1"). Null below that, or when unknown.
 */
fun RainBar.amountShown(
    units: WeatherUnits,
    locale: Locale = Locale.getDefault(),
): String? {
    val mm = amountMm ?: return null
    if (units == WeatherUnits.METRIC) return if (mm < MIN_BAR_MM) null else format(rounded(mm, 1), locale)
    val inches = mm / MM_PER_INCH
    return if (inches < MIN_BAR_INCHES) null else format(rounded(inches, 2).stripTrailingZeros(), locale)
}

private fun rounded(
    value: Double,
    decimals: Int,
): BigDecimal = value.toBigDecimal().setScale(decimals, RoundingMode.HALF_UP)

/** [value] with exactly its own decimals, in the [locale]'s digits and decimal separator. */
private fun format(
    value: BigDecimal,
    locale: Locale,
): String =
    (NumberFormat.getNumberInstance(locale) as DecimalFormat)
        .apply {
            minimumFractionDigits = value.scale().coerceAtLeast(0)
            maximumFractionDigits = value.scale().coerceAtLeast(0)
            isGroupingUsed = false
        }.format(value)
