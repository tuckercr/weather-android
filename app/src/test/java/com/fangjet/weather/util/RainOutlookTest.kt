package com.fangjet.weather.util

import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class RainOutlookTest {

    private val defaults = RainOutlookSettings()

    /** One slot per chance, from 3 PM, each with [mm] of rain. */
    private fun slots(
        vararg chances: Int,
        mm: Double? = null,
    ) = chances.mapIndexed { i, chance ->
        HourlySlot(
            timeLabel = hourLabel(i),
            iconCode = "10d",
            description = "Light rain",
            temperature = 12.0,
            feelsLike = 11.0,
            windSpeed = 3.0,
            windDeg = 200,
            humidity = 80,
            pressure = 1008,
            units = WeatherUnits.METRIC,
            precipitationChance = chance,
            precipitationMm = mm,
        )
    }

    private fun hourLabel(i: Int): String {
        val hour = (15 + i) % 24
        return "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"
    }

    private fun outlook(
        slots: List<HourlySlot>,
        settings: RainOutlookSettings = defaults,
        units: WeatherUnits = WeatherUnits.METRIC,
        nextRain: NextRain? = null,
    ) = rainOutlook(slots, settings, units, nextRain, Locale.US)

    // --- when it shows ---

    @Test
    fun `hidden when no hour reaches the minimum chance`() {
        assertNull(outlook(slots(0, 10, 14, 5)))
        assertNotNull(outlook(slots(0, 10, 20, 5)))
        assertNull(outlook(slots(0, 30, 34), RainOutlookSettings(minChance = 40)))
    }

    @Test
    fun `hidden when switched off`() {
        assertNull(outlook(slots(80, 90), RainOutlookSettings(enabled = false)))
    }

    @Test
    fun `hidden without any hours`() {
        assertNull(outlook(emptyList()))
    }

    @Test
    fun `the window is the first rain_outlook_hours hours`() {
        val twelve = slots(0, 0, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0)
        assertEquals(10, outlook(twelve)?.bars?.size)
        assertEquals(12, outlook(twelve, RainOutlookSettings(hours = 12))?.bars?.size)
        // Rain only in the ninth hour is outside a 6-hour window.
        assertNull(outlook(twelve, RainOutlookSettings(hours = 6)))
    }

    @Test
    fun `fewer hours than the window chart what there is`() {
        assertEquals(3, outlook(slots(10, 40, 10))?.bars?.size)
    }

    // --- the headline ---

    @Test
    fun `likely now when the peak of 50 percent or more is the current hour`() {
        assertEquals(RainOutlookHeadline.Likely(null), outlook(slots(50, 40, 20))?.headline)
    }

    @Test
    fun `likely around the peak hour when it's later`() {
        assertEquals(RainOutlookHeadline.Likely("5 PM"), outlook(slots(20, 40, 90, 60))?.headline)
    }

    @Test
    fun `a chance now or around the peak hour under 50 percent`() {
        assertEquals(RainOutlookHeadline.Chance(null), outlook(slots(44, 30, 0))?.headline)
        assertEquals(RainOutlookHeadline.Chance("4 PM"), outlook(slots(10, 30, 20))?.headline)
    }

    @Test
    fun `the peak is the first hour with the highest chance`() {
        assertEquals(RainOutlookHeadline.Likely("4 PM"), outlook(slots(20, 70, 30, 70))?.headline)
    }

    @Test
    fun `the next-rain line's words are the headline when it shows`() {
        val starting = NextRain.Starting(epochSeconds = 1_000L, snow = false)
        val outlook = outlook(slots(10, 80, 20), nextRain = starting)
        assertEquals(RainOutlookHeadline.NextRain(starting), outlook?.headline)
    }

    // --- drying out ---

    @Test
    fun `drying out after the last wet hour`() {
        val outlook = outlook(slots(30, 60, 25, 10, 5, 0))
        assertEquals("5 PM", outlook?.lastWetHour)
        assertEquals(false, outlook?.staysUp)
    }

    @Test
    fun `the chance stays up when the window's last hour is still wet`() {
        val outlook = outlook(slots(0, 10, 30, 60), RainOutlookSettings(hours = 6))
        assertEquals("6 PM", outlook?.lastWetHour)
        assertEquals(true, outlook?.staysUp)
    }

    @Test
    fun `an hour counts as wet from the minimum chance`() {
        assertEquals("4 PM", outlook(slots(50, 40, 34), RainOutlookSettings(minChance = 40))?.lastWetHour)
    }

    // --- the chance as the hourly strip shows it ---

    @Test
    fun `a bar shows the chance as the hourly strip does`() {
        val bars = outlook(slots(55, 5, 14, 96))?.bars
        assertEquals(listOf(60, 0, 10, 100), bars?.map { it.chance })
    }

    @Test
    fun `the minimum chance goes by the chance as shown`() {
        val thirty = RainOutlookSettings(minChance = 30)
        // 25% shows as 30%, 24% as 20%.
        assertEquals("4 PM", outlook(slots(10, 25, 24), thirty)?.lastWetHour)
        assertNull(outlook(slots(10, 24, 5), thirty))
    }

    @Test
    fun `likely goes by the chance as shown`() {
        // 45% shows as 50%, 44% as 40%.
        assertEquals(RainOutlookHeadline.Likely("4 PM"), outlook(slots(20, 45, 30))?.headline)
        assertEquals(RainOutlookHeadline.Chance("4 PM"), outlook(slots(20, 44, 30))?.headline)
    }

    @Test
    fun `the peak is the first hour with the highest chance as shown`() {
        // 52% and 55% show as 50% and 60%; 61% and 64% both show as 60%, so the first wins.
        assertEquals(RainOutlookHeadline.Likely("4 PM"), outlook(slots(52, 55, 30))?.headline)
        assertEquals(RainOutlookHeadline.Likely(null), outlook(slots(61, 64, 30))?.headline)
    }

    // --- the total ---

    @Test
    fun `metric total from half a millimetre`() {
        assertNull(rainTotal(0.49, WeatherUnits.METRIC, Locale.US))
        assertEquals(RainTotal.Millimetres("0.5"), rainTotal(0.5, WeatherUnits.METRIC, Locale.US))
    }

    @Test
    fun `metric total has one decimal below 10 and none from 10`() {
        assertEquals(RainTotal.Millimetres("2.5"), rainTotal(2.47, WeatherUnits.METRIC, Locale.US))
        assertEquals(RainTotal.Millimetres("3.0"), rainTotal(3.0, WeatherUnits.METRIC, Locale.US))
        assertEquals(RainTotal.Millimetres("9.9"), rainTotal(9.94, WeatherUnits.METRIC, Locale.US))
        // The rule goes by the amount itself, so just under 10 keeps its decimal.
        assertEquals(RainTotal.Millimetres("10.0"), rainTotal(9.97, WeatherUnits.METRIC, Locale.US))
        assertEquals(RainTotal.Millimetres("10"), rainTotal(10.0, WeatherUnits.METRIC, Locale.US))
        assertEquals(RainTotal.Millimetres("12"), rainTotal(12.4, WeatherUnits.METRIC, Locale.US))
        // Half up.
        assertEquals(RainTotal.Millimetres("13"), rainTotal(12.5, WeatherUnits.METRIC, Locale.US))
    }

    @Test
    fun `imperial total from a hundredth of an inch, with up to two decimals`() {
        assertNull(rainTotal(0.2, WeatherUnits.IMPERIAL, Locale.US))
        assertEquals(RainTotal.Inches("0.01"), rainTotal(0.254, WeatherUnits.IMPERIAL, Locale.US))
        assertEquals(RainTotal.Inches("0.1"), rainTotal(2.54, WeatherUnits.IMPERIAL, Locale.US))
        assertEquals(RainTotal.Inches("0.25"), rainTotal(6.35, WeatherUnits.IMPERIAL, Locale.US))
        assertEquals(RainTotal.Inches("1.5"), rainTotal(38.1, WeatherUnits.IMPERIAL, Locale.US))
    }

    @Test
    fun `an inch is singular when it rounds to exactly 1`() {
        assertEquals(RainTotal.OneInch, rainTotal(25.4, WeatherUnits.IMPERIAL, Locale.US))
        assertEquals(RainTotal.OneInch, rainTotal(25.3, WeatherUnits.IMPERIAL, Locale.US))
        assertEquals(RainTotal.Inches("1.01"), rainTotal(25.65, WeatherUnits.IMPERIAL, Locale.US))
    }

    @Test
    fun `the total adds up the window, with unknown amounts as none`() {
        val hours = slots(40, 60, 60, 20).mapIndexed { i, slot -> slot.copy(precipitationMm = if (i == 3) null else 1.0) }
        assertEquals(RainTotal.Millimetres("3.0"), outlook(hours)?.total)
        assertNull(outlook(slots(40, 60, mm = null))?.total)
        // Only the window's hours count.
        val beyond = slots(40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, mm = 1.0)
        assertEquals(RainTotal.Millimetres("10"), outlook(beyond)?.total)
    }

    @Test
    fun `the total follows the units`() {
        assertEquals(RainTotal.Inches("0.2"), outlook(slots(40, 60, mm = 2.54), units = WeatherUnits.IMPERIAL)?.total)
    }

    // --- the bars ---

    @Test
    fun `one bar per hour with its label, chance and amount`() {
        val bars = outlook(slots(10, 70, mm = 2.5))?.bars
        assertEquals(listOf(RainBar("3 PM", 10, 2.5), RainBar("4 PM", 70, 2.5)), bars)
        assertEquals("4PM", bars?.last()?.compactHour)
    }

    @Test
    fun `a bar shows its amount from a tenth of a millimetre or a hundredth of an inch`() {
        assertNull(RainBar("4 PM", 70, null).amountShown(WeatherUnits.METRIC, Locale.US))
        assertNull(RainBar("4 PM", 70, 0.05).amountShown(WeatherUnits.METRIC, Locale.US))
        assertEquals("0.1", RainBar("4 PM", 70, 0.1).amountShown(WeatherUnits.METRIC, Locale.US))
        assertEquals("2.5", RainBar("4 PM", 70, 2.46).amountShown(WeatherUnits.METRIC, Locale.US))
        assertEquals("12.0", RainBar("4 PM", 70, 12.0).amountShown(WeatherUnits.METRIC, Locale.US))

        assertNull(RainBar("4 PM", 70, 0.2).amountShown(WeatherUnits.IMPERIAL, Locale.US))
        assertEquals("0.01", RainBar("4 PM", 70, 0.254).amountShown(WeatherUnits.IMPERIAL, Locale.US))
        assertEquals("0.1", RainBar("4 PM", 70, 2.54).amountShown(WeatherUnits.IMPERIAL, Locale.US))
    }

    @Test
    fun `amounts use the locale's decimal separator`() {
        assertEquals(RainTotal.Millimetres("2,5"), rainTotal(2.5, WeatherUnits.METRIC, Locale.GERMANY))
    }
}
