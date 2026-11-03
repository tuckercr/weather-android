package com.fangjet.weather.ui

import com.fangjet.weather.R
import com.fangjet.weather.domain.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class TemperatureTextTest {

    @Test
    fun `with the unit shown, temperatures read as they always have`() {
        assertEquals("89 °F", shown(89.0, WeatherUnits.IMPERIAL, unitShown = true))
        assertEquals("-3 °F", shown(-3.0, WeatherUnits.IMPERIAL, unitShown = true))
        assertEquals("31 °C", shown(31.0, WeatherUnits.METRIC, unitShown = true))
        assertEquals("-3 °C", shown(-3.0, WeatherUnits.METRIC, unitShown = true))
    }

    @Test
    fun `without the unit only the degree sign is left`() {
        assertEquals("89°", shown(89.0, WeatherUnits.IMPERIAL, unitShown = false))
        assertEquals("-3°", shown(-3.0, WeatherUnits.IMPERIAL, unitShown = false))
        assertEquals("31°", shown(31.0, WeatherUnits.METRIC, unitShown = false))
        assertEquals("-3°", shown(-3.0, WeatherUnits.METRIC, unitShown = false))
    }

    @Test
    fun `leaving the unit out keeps the same whole degrees`() {
        listOf(88.5, 88.49, 14.4, 0.0, -0.4, -2.5, -2.6).forEach { value ->
            WeatherUnits.entries.forEach { units ->
                assertEquals(
                    "$value in $units",
                    temperatureText(value, units, unitShown = true).degrees,
                    temperatureText(value, units, unitShown = false).degrees,
                )
            }
        }
        assertEquals("89 °F", shown(88.5, WeatherUnits.IMPERIAL, unitShown = true))
        assertEquals("89°", shown(88.5, WeatherUnits.IMPERIAL, unitShown = false))
        assertEquals("88°", shown(88.49, WeatherUnits.IMPERIAL, unitShown = false))
        assertEquals("0 °C", shown(-0.4, WeatherUnits.METRIC, unitShown = true))
        assertEquals("0°", shown(-0.4, WeatherUnits.METRIC, unitShown = false))
        assertEquals("-2°", shown(-2.5, WeatherUnits.METRIC, unitShown = false))
        assertEquals("-3°", shown(-2.6, WeatherUnits.METRIC, unitShown = false))
    }

    @Test
    fun `a temperature inside a sentence loses its unit too`() {
        assertEquals("About 81 °F", format("format_about_temperature", shown(81.0, WeatherUnits.IMPERIAL, unitShown = true)))
        assertEquals("About 81°", format("format_about_temperature", shown(81.0, WeatherUnits.IMPERIAL, unitShown = false)))
        assertEquals("Actual 53°", format("today_actual", shown(53.0, WeatherUnits.IMPERIAL, unitShown = false)))
        assertEquals("Dew point -3°", format("today_dew_point", shown(-3.0, WeatherUnits.METRIC, unitShown = false)))
    }

    /** What the screen shows: the shipped format filled in, as formatTemperature's stringResource does. */
    private fun shown(
        value: Double,
        units: WeatherUnits,
        unitShown: Boolean,
    ): String {
        val text = temperatureText(value, units, unitShown)
        return format(formatNames.getValue(text.resId), text.degrees)
    }

    private fun format(
        name: String,
        arg: Any,
    ): String = String.format(Locale.US, strings.getValue(name), arg)

    private val formatNames =
        mapOf(
            R.string.format_temperature_c to "format_temperature_c",
            R.string.format_temperature_f to "format_temperature_f",
            R.string.format_temperature_no_unit to "format_temperature_no_unit",
        )

    /** The shipped strings, by resource name. */
    private val strings: Map<String, String> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }
}
