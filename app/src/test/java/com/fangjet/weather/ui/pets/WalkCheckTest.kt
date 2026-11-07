package com.fangjet.weather.ui.pets

import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.ui.airQualityDogLine
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.WindForce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as iOS's WalkCheckTests. */
class WalkCheckTest {
    private val default = PetWeatherThresholds.DEFAULT

    private fun check(
        slot: HourlySlot,
        feelsLikeC: Double? = null,
        conditionNow: String? = null,
        darkNow: Boolean = false,
        thresholds: PetWeatherThresholds = default,
    ) = walkCheckFor(slot, thresholds, feelsLikeC, conditionNow, darkNow)

    // --- the verdict ---

    @Test
    fun `a mild clear hour is great with nothing in the way`() {
        val result = check(slot())
        assertEquals(WalkLevel.GREAT, result.level)
        assertNull(result.reason)
        assertTrue(result.isWalkable)
    }

    @Test
    fun `wind climbs the scale from a near gale to hurricane force`() {
        // Metres per second in the middle of Beaufort 7, 8, 10 and 12.
        assertEquals(WalkLevel.WAIT, check(slot(wind = 15.0)).level)
        assertEquals(WalkLevel.POTTY_ONLY, check(slot(wind = 19.0)).level)
        assertEquals(WalkLevel.STAY_IN, check(slot(wind = 26.0)).level)
        assertEquals(WalkLevel.DANGER, check(slot(wind = 35.0)).level)
        assertEquals(WalkReason.Wind(WindForce.HURRICANE), check(slot(wind = 35.0)).reason)
        assertEquals(WalkLevel.GREAT, check(slot(wind = 8.0)).level)
    }

    @Test
    fun `air follows the remote config band and names the pollutant`() {
        assertEquals(WalkLevel.GREAT, check(slot(airBand = 1)).level)
        assertEquals(WalkLevel.FINE, check(slot(airBand = 2)).level)
        assertTrue(check(slot(airBand = 2)).isWalkable)
        val smoky = check(slot(airBand = 3, pollutant = AirQuality.Pollutant.FINE_PARTICLES))
        assertEquals(WalkLevel.POTTY_ONLY, smoky.level)
        assertEquals(WalkReason.Air(3, AirQuality.Pollutant.FINE_PARTICLES), smoky.reason)
        assertEquals(WalkLevel.STAY_IN, check(slot(airBand = 5)).level)

        val laxer = default.copy(badAirBand = 4)
        assertEquals(WalkLevel.FINE, check(slot(airBand = 3), thresholds = laxer).level)
        assertEquals(WalkLevel.POTTY_ONLY, check(slot(airBand = 4), thresholds = laxer).level)
    }

    @Test
    fun `pavement goes from a wait to keeping pets inside`() {
        assertEquals(WalkLevel.WAIT, check(slot(pavement = 49.0)).level)
        assertEquals(WalkLevel.POTTY_ONLY, check(slot(pavement = 54.0)).level)
        assertEquals(WalkLevel.STAY_IN, check(slot(pavement = 60.0)).level)
        assertEquals(WalkReason.Pavement(PavementBand.DANGEROUS), check(slot(pavement = 60.0)).reason)
        assertEquals(WalkLevel.GREAT, check(slot(pavement = 35.0)).level)
    }

    @Test
    fun `heat and cold are a wait outside the comfort range and potty breaks past the pet limits`() {
        assertEquals(WalkLevel.WAIT, check(slot(), feelsLikeC = 28.0).level)
        assertEquals(WalkLevel.POTTY_ONLY, check(slot(), feelsLikeC = 31.0).level)
        assertEquals(WalkLevel.WAIT, check(slot(), feelsLikeC = 2.0).level)
        assertEquals(WalkLevel.POTTY_ONLY, check(slot(), feelsLikeC = -2.0).level)
        assertEquals(WalkReason.Cold, check(slot(), feelsLikeC = -2.0).reason)
    }

    @Test
    fun `thunderstorms now keep pets inside and rain is a wait`() {
        assertEquals(WalkLevel.STAY_IN, check(slot(), conditionNow = "Thunderstorm").level)
        assertEquals(WalkReason.Storms(likely = true), check(slot(), conditionNow = "Thunderstorm").reason)
        assertEquals(WalkLevel.WAIT, check(slot(), conditionNow = "Drizzle").level)
        assertEquals(WalkLevel.STAY_IN, check(slot(rain = 70, icon = "11d")).level)
        assertEquals(WalkLevel.WAIT, check(slot(rain = 40, icon = "11d")).level)
    }

    @Test
    fun `dark is a wait`() {
        assertEquals(WalkReason.Dark, check(slot(icon = "01n")).reason)
        assertEquals(WalkLevel.WAIT, check(slot(), darkNow = true).level)
    }

    @Test
    fun `rain comes before warm pavement at the same level`() {
        // Nairobi on 11-07: drizzle, and pavement getting hot in the sun between showers.
        assertEquals(WalkReason.Rain(likely = true), check(slot(pavement = 49.0), conditionNow = "Drizzle").reason)
    }

    @Test
    fun `the worst factor leads, and heat comes before air before cold`() {
        val result = check(slot(airBand = 3, wind = 15.0), feelsLikeC = 31.0)
        assertEquals(WalkReason.Heat, result.reason)
        assertEquals(WalkLevel.POTTY_ONLY, result.air?.level)
        assertEquals(listOf(WalkLevel.POTTY_ONLY, WalkLevel.POTTY_ONLY, WalkLevel.WAIT), result.factors.map { it.level })
        assertEquals(WalkReason.Wind(WindForce.STORM), check(slot(airBand = 3, wind = 26.0)).reason)
    }

    @Test
    fun `air names`() {
        assertEquals(R.string.walk_air_smoky, airNameRes(AirQuality.Pollutant.FINE_PARTICLES))
        assertEquals(R.string.walk_air_dusty, airNameRes(AirQuality.Pollutant.COARSE_PARTICLES))
        assertEquals(R.string.walk_air_ozone, airNameRes(AirQuality.Pollutant.OZONE))
        assertNull(airNameRes(AirQuality.Pollutant.NITROGEN_DIOXIDE))
        assertNull(airNameRes(null))
    }

    // --- the walk card ---

    private fun advice(
        slots: List<HourlySlot>,
        feelsLikeC: Double = 18.0,
        conditionNow: String? = null,
    ) = walkAdviceFor(feelsLikeC, slots, conditionNow = conditionNow)

    private fun caption(
        slots: List<HourlySlot>,
        conditionNow: String? = null,
    ) = advice(slots, conditionNow = conditionNow)?.let(::walkCaption)

    @Test
    fun `the rating says why when it is more than a wait`() {
        assertEquals(WalkRating.WINDY, advice(listOf(slot(wind = 19.0), slot("4 PM")))?.rating)
        assertEquals(WalkRating.DANGER, advice(listOf(slot(wind = 35.0), slot("4 PM")))?.rating)
        assertEquals(WalkRating.STORMY, advice(listOf(slot(), slot("4 PM")), conditionNow = "Thunderstorm")?.rating)
        assertEquals(WalkRating.OKAY, advice(listOf(slot(wind = 15.0), slot("4 PM")))?.rating)
        assertEquals(WalkText(R.string.walk_danger_hurricane), walkDetail(advice(listOf(slot(wind = 35.0)))!!))
    }

    @Test
    fun `the card caption names the pollutant`() {
        fun air(res: Int) = listOf(WalkText(res))
        assertEquals(
            WalkText(R.string.walk_air_potty, air(R.string.walk_air_smoky)),
            caption(listOf(slot(airBand = 3, pollutant = AirQuality.Pollutant.FINE_PARTICLES), slot("4 PM"))),
        )
        assertEquals(
            WalkText(R.string.walk_air_potty, air(R.string.walk_air_ozone)),
            caption(listOf(slot(airBand = 3, pollutant = AirQuality.Pollutant.OZONE), slot("4 PM"))),
        )
        assertEquals(
            WalkText(R.string.walk_air_potty, air(R.string.walk_air_bad)),
            caption(listOf(slot(airBand = 4, pollutant = AirQuality.Pollutant.NITROGEN_DIOXIDE), slot("4 PM"))),
        )
        assertEquals(
            WalkText(R.string.walk_air_stay_in, air(R.string.walk_air_dusty)),
            caption(listOf(slot(airBand = 5, pollutant = AirQuality.Pollutant.COARSE_PARTICLES), slot("4 PM"))),
        )
    }

    @Test
    fun `the card caption for wind and storms`() {
        assertEquals(WalkText(R.string.walk_gale), caption(listOf(slot(wind = 19.0), slot("4 PM"))))
        assertEquals(WalkText(R.string.walk_storm_wind), caption(listOf(slot(wind = 26.0), slot("4 PM"))))
        assertEquals(WalkText(R.string.walk_danger_stay_inside), caption(listOf(slot(wind = 35.0), slot("4 PM"))))
        assertNull(caption(listOf(slot(wind = 15.0), slot("4 PM"))))
        assertEquals(WalkText(R.string.walk_storms), caption(listOf(slot(), slot("4 PM")), conditionNow = "Thunderstorm"))
    }

    @Test
    fun `the pavement note says it is about now and never stands in for rain`() {
        val slots = listOf(slot(pavement = 49.0), slot("6 PM", pavement = 30.0))
        assertEquals(WalkText(R.string.walk_pavement_warm, listOf(49)), caption(slots))
        assertNull(caption(slots, conditionNow = "Drizzle"))
    }

    // --- the hero's caption ---

    @Test
    fun `bad air gives the hero a reason caption naming the pollutant`() {
        val smoky = check(slot(airBand = 3, pollutant = AirQuality.Pollutant.FINE_PARTICLES))
        assertEquals(
            listOf(WalkText(R.string.hero_air_1, listOf(WalkText(R.string.walk_air_smoky))), WalkText(R.string.hero_air_2)),
            heroWalkCaptions(smoky, hasDog = true),
        )
        assertEquals(
            WalkText(R.string.hero_air_no_dog_1, listOf(WalkText(R.string.walk_air_poor))),
            heroWalkCaptions(check(slot(airBand = 3)), hasDog = false)?.first(),
        )
    }

    @Test
    fun `wind and pavement get reason captions but a wait does not`() {
        assertEquals(listOf(WalkText(R.string.hero_hurricane)), heroWalkCaptions(check(slot(wind = 35.0)), hasDog = false))
        assertNotNull(heroWalkCaptions(check(slot(wind = 19.0)), hasDog = true))
        assertNotNull(heroWalkCaptions(check(slot(pavement = 54.0)), hasDog = true))
        assertNull(heroWalkCaptions(check(slot(pavement = 54.0)), hasDog = false))
        assertNull(heroWalkCaptions(check(slot(wind = 15.0)), hasDog = true))
        assertNull(heroWalkCaptions(check(slot(airBand = 2)), hasDog = true))
        // Heat is the scene's to show: the hot mood's captions already say so.
        assertNull(heroWalkCaptions(check(slot(), feelsLikeC = 31.0), hasDog = true))
    }

    @Test
    fun `walk captions are left out for anything but the dark at a wait or worse`() {
        assertTrue(rulesOutWalkNow(check(slot(), feelsLikeC = 2.0)))
        assertTrue(rulesOutWalkNow(check(slot(icon = "10n"), conditionNow = "Rain")))
        assertFalse(rulesOutWalkNow(check(slot(icon = "01n"))))
        assertFalse(rulesOutWalkNow(check(slot(airBand = 2))))
        assertEquals(5, WALK_SUGGESTIONS.size)
    }

    @Test
    fun `the air quality tile follows the walk rules`() {
        assertEquals(3, airQualityDogLine(3, default))
        assertEquals(2, airQualityDogLine(3, default.copy(badAirBand = 4)))
        assertEquals(5, airQualityDogLine(5, default))
        assertEquals(0, airQualityDogLine(0, default))
        assertEquals(1, airQualityDogLine(1, default))
    }

    // --- helpers ---

    /** A metric hour at 3 PM, 18C, clear and calm unless told otherwise. */
    private fun slot(
        label: String = "3 PM",
        rain: Int = 0,
        icon: String = "01d",
        pavement: Double? = null,
        airBand: Int? = null,
        pollutant: AirQuality.Pollutant? = null,
        wind: Double = 1.0,
    ) = HourlySlot(
        timeLabel = label,
        iconCode = icon,
        description = "clear",
        temperature = 18.0,
        feelsLike = 18.0,
        windSpeed = wind,
        windDeg = 0,
        humidity = 50,
        pressure = 1013,
        units = WeatherUnits.METRIC,
        precipitationChance = rain,
        pavementTemperature = pavement,
        localHour = if (label == "3 PM") 15 else 16,
        // The lowest US index in the band.
        airQuality = airBand?.let {
            AirQuality(
                AirQuality.Scale.US.bandEdges[it]
                    .toInt() + if (it == 0) 0 else 1,
                AirQuality.Scale.US,
                pollutant,
            )
        },
    )
}
