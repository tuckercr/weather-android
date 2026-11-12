package com.fangjet.weather.domain

import com.fangjet.weather.R
import com.fangjet.weather.model.WalkText
import com.fangjet.weather.model.hourRange
import com.fangjet.weather.model.walkCardContent
import com.fangjet.weather.util.PavementBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkAdvisorTest {

    @Test
    fun `great when the next slot is dry and comfortable`() {
        val advice = walkAdviceFor(18.0, listOf(slot("3 PM", 18.0, 10), slot("6 PM", 16.0, 80)))
        assertEquals(WalkAdvice(WalkRating.GREAT, null), advice.withoutCheck())
    }

    @Test
    fun `okay with the first good window when it is wet now`() {
        val advice = walkAdviceFor(12.0, listOf(slot("3 PM", 12.0, 90), slot("6 PM", 13.0, 10)))
        assertEquals(WalkAdvice(WalkRating.OKAY, "6 PM"), advice.withoutCheck())
    }

    @Test
    fun `poor when there is no good window in the lookahead`() {
        val advice = walkAdviceFor(12.0, List(8) { slot("${it}h", 12.0, 90) })
        assertEquals(WalkAdvice(WalkRating.POOR, null), advice.withoutCheck())
    }

    @Test
    fun `bad air now points at the first cleaner hour`() {
        val advice = walkAdviceFor(
            18.0,
            listOf(slot("3 PM", 18.0, 0, airBand = 3), slot("4 PM", 18.0, 0, airBand = 4), slot("6 PM", 18.0, 0, airBand = 2)),
        )
        assertEquals(WalkAdvice(WalkRating.BAD_AIR, "6 PM"), advice.withoutCheck())
    }

    @Test
    fun `air that is only poor for some still allows a walk`() {
        assertEquals(WalkRating.GREAT, walkAdviceFor(18.0, listOf(slot("3 PM", 18.0, 0, airBand = 2)))?.rating)
    }

    @Test
    fun `Remote Config can move the bad air band`() {
        val stricter = PetWeatherThresholds.validated(badAirBand = 2)
        assertEquals(WalkRating.BAD_AIR, walkAdviceFor(18.0, listOf(slot("3 PM", 18.0, 0, airBand = 2)), stricter)?.rating)
    }

    @Test
    fun `a bad air band outside 1 to 5 uses the default`() {
        assertEquals(3, PetWeatherThresholds.validated(badAirBand = 0).badAirBand)
        assertEquals(3, PetWeatherThresholds.validated(badAirBand = 6).badAirBand)
        assertEquals(5, PetWeatherThresholds.validated(badAirBand = 5).badAirBand)
    }

    @Test
    fun `heat outranks bad air at the same level, but hazardous air outranks heat`() {
        assertEquals(WalkRating.PAWS_HOT, walkAdviceFor(33.0, listOf(slot("3 PM", 33.0, 0, airBand = 4)))?.rating)
        // The worst band keeps pets inside, which is worse than potty breaks in the heat.
        assertEquals(WalkRating.BAD_AIR, walkAdviceFor(33.0, listOf(slot("3 PM", 33.0, 0, airBand = 5)))?.rating)
    }

    @Test
    fun `paws hot above the heat threshold, pointing at the first cooler slot`() {
        val advice = walkAdviceFor(33.0, listOf(slot("3 PM", 33.0, 0), slot("7 PM", 24.0, 0)))
        assertEquals(WalkAdvice(WalkRating.PAWS_HOT, "7 PM"), advice.withoutCheck())
    }

    @Test
    fun `too cold at or below freezing`() {
        assertEquals(WalkRating.TOO_COLD, walkAdviceFor(-3.0, listOf(slot("3 PM", -3.0, 0)))?.rating)
    }

    @Test
    fun `imperial slots are compared in celsius`() {
        // 64F is ~18C: comfortable.
        val advice = walkAdviceFor(18.0, listOf(slot("3 PM", 64.0, 0, WeatherUnits.IMPERIAL)))
        assertEquals(WalkRating.GREAT, advice?.rating)
    }

    @Test
    fun `night slots are skipped in favour of the first daytime window`() {
        val advice = walkAdviceFor(18.0, listOf(slot("2 AM", 18.0, 0), slot("5 AM", 17.0, 0), slot("8 AM", 16.0, 0)))
        assertEquals(WalkAdvice(WalkRating.OKAY, "8 AM"), advice.withoutCheck())
    }

    @Test
    fun `no slots means no advice`() {
        assertEquals(null, walkAdviceFor(18.0, emptyList()))
    }

    @Test
    fun `night is judged by local hour, not the label text`() {
        // A label in a locale the old parser couldn't read; the hour still rules it out.
        val advice = walkAdviceFor(18.0, listOf(slot("2 午前", 18.0, 0, hour = 2), slot("8 午前", 16.0, 0, hour = 8)))
        assertEquals("8 午前", advice?.bestTimeLabel)
    }

    @Test
    fun `hot pavement on a mild day means paws hot, pointing at the first cooler hour`() {
        val advice = walkAdviceFor(
            24.0,
            listOf(slot("1 PM", 24.0, 0, pavement = 58.0), slot("6 PM", 22.0, 0, pavement = 30.0)),
        )
        assertEquals(WalkAdvice(WalkRating.PAWS_HOT, "6 PM", 58.0), advice.withoutCheck())
        assertEquals(WalkReason.Pavement(PavementBand.DANGEROUS), advice?.now?.reason)
        assertEquals(WalkLevel.STAY_IN, advice?.now?.level)
    }

    @Test
    fun `imperial pavement is compared in celsius`() {
        // 100F is ~38C: under the 40C warm limit, so a warm afternoon is still walkable.
        val advice = walkAdviceFor(26.0, listOf(slot("2 PM", 79.0, 0, WeatherUnits.IMPERIAL, pavement = 100.0)))
        assertEquals(WalkRating.GREAT, advice?.rating)
    }

    @Test
    fun `pavement that is getting hot makes now only okay, pointing at a cooler hour`() {
        // 43C is between the 40C warm and 46C hot limits.
        val advice = walkAdviceFor(
            24.0,
            listOf(slot("2 PM", 24.0, 0, pavement = 43.0), slot("6 PM", 22.0, 0, pavement = 35.0)),
        )
        assertEquals(WalkAdvice(WalkRating.OKAY, "6 PM", 43.0), advice.withoutCheck())
        assertEquals(WalkReason.Pavement(PavementBand.WARM), advice?.now?.reason)
    }

    @Test
    fun `too hot air on mild pavement is paws hot for the heat, not the pavement`() {
        // A muggy evening: 31C feels-like, pavement no hotter than the air.
        val advice = walkAdviceFor(31.0, listOf(slot("7 PM", 29.0, 0, pavement = 29.0)))
        assertEquals(WalkRating.PAWS_HOT, advice?.rating)
        assertEquals(WalkReason.Heat, advice?.now?.reason)
        assertNull(advice?.now?.pavement)
    }

    @Test
    fun `hours are judged by how warm they feel`() {
        // 25C air, but 31C in the humidity: past the 27C comfort limit.
        assertFalse(slot("2 PM", 25.0, 0).copy(feelsLike = 31.0).isWalkable(default))
        // 5C air, but 1C in the wind: under the 3C comfort limit.
        assertFalse(slot("2 PM", 5.0, 0).copy(feelsLike = 1.0).isWalkable(default))
        assertTrue(slot("2 PM", 25.0, 0).isWalkable(default))
    }

    @Test
    fun `now is judged by the current feels-like rather than the forecast hour`() {
        // The forecast hour said 25C; the current reading feels warmer.
        assertEquals(WalkRating.POOR, walkAdviceFor(28.0, listOf(slot("2 PM", 25.0, 0)))?.rating)
        assertEquals(WalkRating.PAWS_HOT, walkAdviceFor(31.0, listOf(slot("2 PM", 25.0, 0)))?.rating)
        assertEquals(WalkRating.GREAT, walkAdviceFor(25.0, listOf(slot("2 PM", 25.0, 0)))?.rating)
    }

    // --- Remote Config thresholds ---

    private val default = PetWeatherThresholds.DEFAULT

    @Test
    fun `a higher rain-chance limit makes a damp hour walkable`() {
        val slots = listOf(slot("3 PM", 18.0, 40))

        assertEquals(WalkRating.POOR, walkAdviceFor(18.0, slots)?.rating)
        assertEquals(WalkRating.GREAT, walkAdviceFor(18.0, slots, default.copy(maxRainChancePercent = 50))?.rating)
    }

    @Test
    fun `a narrower comfort range rules out a warm hour`() {
        val slots = listOf(slot("3 PM", 24.0, 0))

        assertEquals(WalkRating.GREAT, walkAdviceFor(24.0, slots)?.rating)
        assertEquals(WalkRating.POOR, walkAdviceFor(24.0, slots, default.copy(comfortMaxC = 22.0))?.rating)
    }

    @Test
    fun `a lower pavement limit flags paws hot sooner`() {
        val slots = listOf(slot("1 PM", 24.0, 0, pavement = 38.0))

        assertEquals(WalkRating.GREAT, walkAdviceFor(24.0, slots)?.rating)
        assertEquals(WalkRating.PAWS_HOT, walkAdviceFor(24.0, slots, default.copy(hotPavementC = 37.0, warmPavementC = 33.0))?.rating)
    }

    @Test
    fun `later walking hours skip an early slot`() {
        val slots = listOf(slot("7 AM", 15.0, 0), slot("10 AM", 16.0, 0))

        assertEquals(WalkAdvice(WalkRating.GREAT, null), walkAdviceFor(15.0, slots).withoutCheck())
        assertEquals(
            WalkAdvice(WalkRating.OKAY, "10 AM"),
            walkAdviceFor(15.0, slots, default.copy(walkHoursStart = 9)).withoutCheck(),
        )
    }

    @Test
    fun `pet hot and cold thresholds also drive the walk rating`() {
        val mild = listOf(slot("3 PM", 26.0, 0))
        val chilly = listOf(slot("3 PM", 4.0, 0))

        assertEquals(WalkRating.GREAT, walkAdviceFor(26.0, mild)?.rating)
        assertEquals(WalkRating.PAWS_HOT, walkAdviceFor(26.0, mild, default.copy(petHotC = 25.0))?.rating)
        assertEquals(WalkRating.GREAT, walkAdviceFor(4.0, chilly)?.rating)
        assertEquals(WalkRating.TOO_COLD, walkAdviceFor(4.0, chilly, default.copy(petColdC = 5.0))?.rating)
    }

    // --- tomorrow ---

    // 2026-01-01T12:00:00Z
    private val noonUtc = 1_767_268_800L
    private val hourSec = 3_600L

    /** Rainy now, so the advice points at the first dry slot ([best], at [bestEpoch]). */
    private fun adviceWithBestAt(
        bestEpoch: Long?,
        utcOffsetSeconds: Int,
        best: String = "7 AM",
    ) = walkAdviceFor(
        15.0,
        listOf(
            slot("3 PM", 15.0, 90, epoch = noonUtc),
            slot(best, 15.0, 0, epoch = bestEpoch),
        ),
        utcOffsetSeconds = utcOffsetSeconds,
        nowEpochSeconds = noonUtc,
    )

    @Test
    fun `a best time later the same day is not tomorrow`() {
        val advice = adviceWithBestAt(noonUtc + 6 * hourSec, utcOffsetSeconds = 0, best = "6 PM")

        // The best slot's start comes along too, for the walk-time notification.
        assertEquals(
            WalkAdvice(WalkRating.OKAY, "6 PM", bestTimeIsTomorrow = false, bestTimeEpochSeconds = noonUtc + 6 * hourSec),
            advice.withoutCheck(),
        )
    }

    @Test
    fun `a best time on the next day is tomorrow`() {
        // 07:00 UTC on 2 January, with a UTC city.
        val advice = adviceWithBestAt(noonUtc + 19 * hourSec, utcOffsetSeconds = 0)

        assertEquals(
            WalkAdvice(WalkRating.OKAY, "7 AM", bestTimeIsTomorrow = true, bestTimeEpochSeconds = noonUtc + 19 * hourSec),
            advice.withoutCheck(),
        )
    }

    @Test
    fun `tomorrow is judged by the city's calendar, not UTC or the device`() {
        // Tokyo (+9): now is 21:00 on 1 Jan local; a slot 4 hours later is 01:00 on 2 Jan local,
        // though still 1 Jan in UTC.
        val tokyo = 9 * 3600
        assertEquals(true, adviceWithBestAt(noonUtc + 4 * hourSec, tokyo)?.bestTimeIsTomorrow)

        // Los Angeles (-8): now is 04:00 on 1 Jan local; a slot 13 hours later is 2 Jan in UTC but
        // still 17:00 on 1 Jan in LA.
        val losAngeles = -8 * 3600
        assertEquals(false, adviceWithBestAt(noonUtc + 13 * hourSec, losAngeles)?.bestTimeIsTomorrow)
    }

    @Test
    fun `a best slot without a timestamp is never tomorrow`() {
        val advice = adviceWithBestAt(bestEpoch = null, utcOffsetSeconds = 0)

        assertEquals(WalkAdvice(WalkRating.OKAY, "7 AM", bestTimeIsTomorrow = false), advice.withoutCheck())
    }

    @Test
    fun `slots more than a day ahead are not considered`() {
        // 3-hourly slots reach three days out; a dry slot 30 hours away is not "soon".
        val advice = adviceWithBestAt(noonUtc + 30 * hourSec, utcOffsetSeconds = 0)

        assertEquals(WalkAdvice(WalkRating.POOR, null), advice.withoutCheck())
    }

    @Test
    fun `a slot before sunrise is not a walk time, even inside the walking hours`() {
        // 7 AM is within 7 AM..8 PM, but on a winter morning it can still be dark (a night icon).
        val advice = walkAdviceFor(12.0, listOf(slot("7 AM", 12.0, 5, icon = "03n"), slot("8 AM", 12.0, 5, icon = "03d")))
        assertEquals(WalkAdvice(WalkRating.OKAY, "8 AM"), advice.withoutCheck())
    }

    @Test
    fun `raining now means now is not the walk time, even if the forecast hour looks dry`() {
        val slots = listOf(slot("3 PM", 18.0, 10), slot("4 PM", 18.0, 10))
        assertEquals(WalkAdvice(WalkRating.GREAT, null), walkAdviceFor(18.0, slots).withoutCheck())
        assertEquals(WalkAdvice(WalkRating.OKAY, "4 PM"), walkAdviceFor(18.0, slots, conditionNow = "Rain").withoutCheck())
    }

    @Test
    fun `raining now with no dry window later is poor`() {
        val advice = walkAdviceFor(18.0, listOf(slot("3 PM", 18.0, 10), slot("4 PM", 18.0, 90)), conditionNow = "Drizzle")
        assertEquals(WalkAdvice(WalkRating.POOR, null), advice.withoutCheck())
    }

    @Test
    fun `precipitation is read from the condition`() {
        listOf("Rain", "drizzle", "Snow", "Thunderstorm").forEach { assertEquals(it, true, isPrecipitating(it)) }
        listOf("Clear", "Clouds", "Mist", "Fog").forEach { assertEquals(it, false, isPrecipitating(it)) }
    }

    // --- after a walk now ---

    /** 3 PM; the slots below are an hour apart from it. */
    private val t0 = 1_800_000_000L

    private fun outlook(
        slots: List<HourlySlot>,
        feelsLikeC: Double = 20.0,
        sunsetIn: Long? = null,
    ): WalkOutlook? = walkAdviceFor(feelsLikeC, slots, nowEpochSeconds = t0, sunsetEpochSeconds = sunsetIn?.let { t0 + it })?.outlook

    @Test
    fun `a walk now says when storms arrive`() {
        val slots = listOf(
            slot("3 PM", 20.0, 0, epoch = t0),
            slot("4 PM", 20.0, 10, epoch = t0 + 3600),
            slot("5 PM", 20.0, 80, epoch = t0 + 7200, icon = "11d"),
        )
        assertEquals(WalkOutlook.Until("5 PM", WalkChange.STORMS), outlook(slots))
    }

    @Test
    fun `a modest chance of rain is maybe and snow is snow`() {
        assertEquals(
            WalkOutlook.Until("4 PM", WalkChange.MAYBE_RAIN),
            outlook(listOf(slot("3 PM", 20.0, 0, epoch = t0), slot("4 PM", 20.0, 40, epoch = t0 + 3600, icon = "10d"))),
        )
        assertEquals(
            WalkOutlook.Until("4 PM", WalkChange.SNOW),
            outlook(listOf(slot("3 PM", 5.0, 0, epoch = t0), slot("4 PM", 5.0, 70, epoch = t0 + 3600, icon = "13d")), feelsLikeC = 5.0),
        )
    }

    @Test
    fun `a near gale ends the window and rules an hour out`() {
        val calm = slot("3 PM", 20.0, 0, epoch = t0)
        val gale = slot("4 PM", 20.0, 0, epoch = t0 + 3600, wind = 15.0) // force 7, about 34 mph
        assertEquals(WalkOutlook.Until("4 PM", WalkChange.WIND), outlook(listOf(calm, gale)))
        val windyNow = listOf(slot("3 PM", 20.0, 0, wind = 15.0), slot("4 PM", 20.0, 0))
        assertEquals(WalkAdvice(WalkRating.OKAY, "4 PM"), walkAdviceFor(20.0, windyNow).withoutCheck())
    }

    @Test
    fun `heat, hot pavement, cold and bad air later end the window`() {
        val now = slot("3 PM", 20.0, 0, epoch = t0)
        assertEquals(WalkOutlook.Until("4 PM", WalkChange.HEAT), outlook(listOf(now, slot("4 PM", 29.0, 0, epoch = t0 + 3600))))
        assertEquals(
            WalkOutlook.Until("4 PM", WalkChange.HOT_PAVEMENT),
            outlook(listOf(now, slot("4 PM", 22.0, 0, pavement = 48.0, epoch = t0 + 3600))),
        )
        assertEquals(WalkOutlook.Until("4 PM", WalkChange.COLD), outlook(listOf(now, slot("4 PM", 1.0, 0, epoch = t0 + 3600))))
        assertEquals(
            WalkOutlook.Until("4 PM", WalkChange.BAD_AIR),
            outlook(listOf(now, slot("4 PM", 20.0, 0, epoch = t0 + 3600, airBand = 3))),
        )
    }

    @Test
    fun `a change more than six hours away is not news`() {
        val slots = listOf(slot("9 AM", 20.0, 0, epoch = t0), slot("4 PM", 20.0, 90, epoch = t0 + 7 * 3600, icon = "11d"))
        assertNull(outlook(slots))
    }

    @Test
    fun `the end of the walking hours falls back to sunset`() {
        val slots = listOf(slot("7 PM", 20.0, 0, epoch = t0), slot("9 PM", 20.0, 0, epoch = t0 + 7200))
        assertNull(outlook(slots))
        assertEquals(WalkOutlook.DarkIn(2), outlook(slots, sunsetIn = 2 * 3600L))
    }

    @Test
    fun `sunset soon is mentioned and rounded to the hour`() {
        val slots = listOf(slot("5 PM", 20.0, 0, epoch = t0))
        assertEquals(WalkOutlook.DarkIn(1), outlook(slots, sunsetIn = 50 * 60L))
        assertEquals(WalkOutlook.DarkIn(0), outlook(slots, sunsetIn = 20 * 60L))
        assertNull(outlook(slots, sunsetIn = 4 * 3600L))
        assertNull(outlook(slots, sunsetIn = -600L))
    }

    @Test
    fun `weather outranks so-so air, which outranks sunset`() {
        val soSoAir = slot("3 PM", 20.0, 0, epoch = t0, airBand = 2)
        assertEquals(WalkOutlook.ModerateAir, outlook(listOf(soSoAir), sunsetIn = 3600L))
        val storm = slot("4 PM", 20.0, 80, epoch = t0 + 3600, icon = "11d")
        assertEquals(WalkOutlook.Until("4 PM", WalkChange.STORMS), outlook(listOf(soSoAir, storm), sunsetIn = 3600L))
        assertNull(outlook(listOf(slot("3 PM", 20.0, 0, epoch = t0, airBand = 1))))
    }

    @Test
    fun `after sunset now isn't a great time even if the hour's icon still says day`() {
        // 4:35 PM: the 4 PM hour is still "01d", but the sun set at 4:31.
        val slots = listOf(slot("4 PM", 20.0, 0, epoch = t0), slot("5 PM", 20.0, 0, epoch = t0 + 3600, icon = "01n"))
        assertEquals(WalkRating.GREAT, walkAdviceFor(20.0, slots, nowEpochSeconds = t0)?.rating)

        val dark = walkAdviceFor(20.0, slots, nowEpochSeconds = t0, darkNow = true)
        assertNotEquals(WalkRating.GREAT, dark?.rating)
        assertNull(dark?.outlook)
    }

    @Test
    fun `only a walk now has an outlook`() {
        val slots = listOf(slot("3 PM", 20.0, 80, epoch = t0), slot("4 PM", 20.0, 0, epoch = t0 + 3600))
        assertNull(outlook(slots, sunsetIn = 3600L))
    }

    // --- walk windows ---

    /** Now at 11 AM UTC, then [later] as (label, rain chance) an hour apart from noon. */
    private fun windows(
        later: List<Pair<String, Int>>,
        conditionNow: String? = null,
    ) = walkAdviceFor(
        15.0,
        listOf(slot("11 AM", 15.0, 0, epoch = noonUtc - 3600)) +
            later.mapIndexed { i, (label, rain) -> slot(label, 15.0, rain, epoch = noonUtc + i * 3600L) },
        nowEpochSeconds = noonUtc - 3600,
        conditionNow = conditionNow,
    )?.let(::walkCardContent)

    private fun and(
        first: WalkText,
        then: WalkText,
    ) = WalkText(R.string.walk_caption_and_more, listOf(first, then))

    @Test
    fun `a later best time gives its spell and the next one as a second option`() {
        val card = windows(listOf("12 PM" to 0, "1 PM" to 0, "2 PM" to 80, "3 PM" to 0, "4 PM" to 0, "5 PM" to 80), conditionNow = "Rain")!!
        assertEquals(R.string.walk_title_two, card.title)
        assertEquals(WalkText(R.string.walk_range, listOf("12", "2 PM")), card.headline)
        assertEquals(
            and(
                WalkText(R.string.walk_or, listOf(WalkText(R.string.walk_range, listOf("3", "5 PM")))),
                WalkText(R.string.walk_clears_rain),
            ),
            card.caption,
        )
    }

    @Test
    fun `a second option on the next day says tomorrow`() {
        val advice = walkAdviceFor(
            15.0,
            listOf(
                slot("11 AM", 15.0, 0, epoch = noonUtc - 3600),
                slot("12 PM", 15.0, 80, epoch = noonUtc),
                slot("1 PM", 15.0, 0, epoch = noonUtc + 3600),
                slot("2 PM", 15.0, 80, epoch = noonUtc + 2 * 3600),
                slot("7 AM", 15.0, 0, epoch = noonUtc + 19 * 3600),
                slot("8 AM", 15.0, 80, epoch = noonUtc + 20 * 3600),
            ),
            nowEpochSeconds = noonUtc - 3600,
            conditionNow = "Rain",
        )!!
        val card = walkCardContent(advice)
        assertEquals(WalkText(R.string.walk_range, listOf("1", "2 PM")), card.headline)
        assertEquals(
            and(
                WalkText(R.string.walk_or_tomorrow, listOf(WalkText(R.string.walk_range, listOf("7", "8 AM")))),
                WalkText(R.string.walk_clears_rain),
            ),
            card.caption,
        )
    }

    @Test
    fun `a best time without an end is still around that hour`() {
        val card = windows(listOf("12 PM" to 0), conditionNow = "Rain")!!
        assertEquals(R.string.walk_title, card.title)
        assertEquals(WalkText(R.string.walk_around, listOf("12 PM")), card.headline)
        assertEquals(WalkText(R.string.walk_clears_rain), card.caption)
    }

    @Test
    fun `a walk now gives the next spell after the outlook`() {
        val card = windows(listOf("12 PM" to 0, "1 PM" to 80, "2 PM" to 0, "3 PM" to 80))!!
        assertEquals(WalkText(R.string.walk_now), card.headline)
        assertEquals(R.string.walk_title_two, card.title)
        assertEquals(
            and(
                WalkText(R.string.walk_until_rain, listOf("1 PM")),
                WalkText(R.string.walk_again, listOf(WalkText(R.string.walk_range, listOf("2", "3 PM")))),
            ),
            card.caption,
        )
        // A spell that lasts past the outlook's reach isn't news, so no second option either.
        val long = windows((12..19).map { "${if (it > 12) it - 12 else it} ${if (it >= 12) "PM" else "AM"}" to 0 } + ("8 PM" to 80))!!
        assertEquals(R.string.walk_title, long.title)
        assertNull(long.caption)
    }

    @Test
    fun `the range drops the half of the day it shares with its end`() {
        assertEquals(WalkText(R.string.walk_range, listOf("5", "8 PM")), hourRange("5 PM", "8 PM"))
        assertEquals(WalkText(R.string.walk_range, listOf("11 AM", "1 PM")), hourRange("11 AM", "1 PM"))
        assertEquals(WalkText(R.string.walk_range, listOf("17:00", "20:00")), hourRange("17:00", "20:00"))
    }

    private fun slot(
        label: String,
        temp: Double,
        rainChance: Int,
        units: WeatherUnits = WeatherUnits.METRIC,
        pavement: Double? = null,
        hour: Int? = hourOf(label),
        epoch: Long? = null,
        icon: String = "01d",
        airBand: Int? = null,
        wind: Double = 1.0,
    ) = HourlySlot(
        timeLabel = label,
        iconCode = icon,
        description = "clear",
        temperature = temp,
        feelsLike = temp,
        windSpeed = wind,
        windDeg = 0,
        humidity = 50,
        pressure = 1013,
        units = units,
        precipitationChance = rainChance,
        pavementTemperature = pavement,
        localHour = hour,
        epochSeconds = epoch,
        // The lowest US index in the band.
        airQuality = airBand?.let {
            AirQuality(
                AirQuality.Scale.US.bandEdges[it]
                    .toInt() + if (it ==
                    0
                ) {
                    0
                } else {
                    1
                },
                AirQuality.Scale.US,
                null,
            )
        },
    )

    /** Test-only: reads the hour from a "3 PM"-style label. */
    private fun hourOf(label: String): Int? {
        val m = Regex("""^(\d{1,2})\s*([AP])M""").find(label) ?: return null
        val h = m.groupValues[1].toInt() % 12
        return if (m.groupValues[2] == "P") h + 12 else h
    }
}

/** The advice without the verdict behind it, to compare with an expected rating and time. */
private fun WalkAdvice?.withoutCheck(): WalkAdvice? = this?.copy(now = WalkCheck.CLEAR)
