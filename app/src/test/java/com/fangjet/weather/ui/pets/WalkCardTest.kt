package com.fangjet.weather.ui.pets

import com.fangjet.weather.R
import com.fangjet.weather.util.PavementBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkCardTest {

    private fun advice(
        rating: WalkRating,
        bestTimeLabel: String? = null,
        bestTimeIsTomorrow: Boolean = false,
        pavementNow: Double? = null,
        now: WalkCheck = WalkCheck.CLEAR,
    ) = WalkAdvice(rating, bestTimeLabel, pavementNow, bestTimeIsTomorrow, now = now)

    private fun check(
        reason: WalkReason,
        level: WalkLevel,
    ) = WalkCheck(listOf(WalkFactor(reason, level)))

    // --- after a walk now ---

    @Test
    fun `a walk now says what comes next`() {
        val great = WalkAdvice(WalkRating.GREAT, null, outlook = WalkOutlook.Until("5 PM", WalkChange.MAYBE_RAIN))
        assertEquals(WalkText(R.string.walk_until_maybe_rain, listOf("5 PM")), walkCaption(great))
        assertNull(walkCaption(WalkAdvice(WalkRating.GREAT, null)))
    }

    @Test
    fun `sunset is soon, in an hour, or in hours`() {
        assertEquals(WalkText(R.string.walk_dark_soon), outlookText(WalkOutlook.DarkIn(0)))
        assertEquals(WalkText(R.string.walk_dark_in_an_hour), outlookText(WalkOutlook.DarkIn(1)))
        assertEquals(WalkText(R.string.walk_dark_in_hours, listOf(3)), outlookText(WalkOutlook.DarkIn(3)))
        assertEquals(WalkText(R.string.walk_moderate_air), outlookText(WalkOutlook.ModerateAir))
    }

    // --- the sentence ---

    @Test
    fun `great now says walk now even when a later time is known`() {
        assertEquals(WalkText(R.string.walk_now), walkDetail(advice(WalkRating.GREAT, bestTimeLabel = "6 PM")))
    }

    @Test
    fun `a later time today names the time`() {
        assertEquals(
            WalkText(R.string.walk_around, listOf("6 PM")),
            walkDetail(advice(WalkRating.POOR, bestTimeLabel = "6 PM")),
        )
    }

    @Test
    fun `a time tomorrow says tomorrow`() {
        assertEquals(
            WalkText(R.string.walk_around_tomorrow, listOf("7 AM")),
            walkDetail(advice(WalkRating.TOO_COLD, bestTimeLabel = "7 AM", bestTimeIsTomorrow = true)),
        )
    }

    @Test
    fun `no good time soon says so`() {
        assertEquals(WalkText(R.string.walk_none), walkDetail(advice(WalkRating.POOR)))
    }

    // --- the caption: pavement when it's the reason ---

    @Test
    fun `dangerous pavement names the rounded temperature`() {
        assertEquals(
            WalkText(R.string.walk_pavement_dangerous, listOf(61)),
            walkCaption(
                advice(
                    WalkRating.PAWS_HOT,
                    pavementNow = 60.6,
                    now = check(WalkReason.Pavement(PavementBand.DANGEROUS), WalkLevel.STAY_IN),
                ),
            ),
        )
    }

    @Test
    fun `too-hot pavement names the temperature`() {
        assertEquals(
            WalkText(R.string.walk_pavement, listOf(54)),
            walkCaption(
                advice(
                    WalkRating.PAWS_HOT,
                    pavementNow = 54.2,
                    now = check(WalkReason.Pavement(PavementBand.TOO_HOT), WalkLevel.POTTY_ONLY),
                ),
            ),
        )
    }

    @Test
    fun `warm pavement on an okay walk names the temperature`() {
        assertEquals(
            WalkText(R.string.walk_pavement_warm, listOf(47)),
            walkCaption(advice(WalkRating.OKAY, pavementNow = 47.0, now = check(WalkReason.Pavement(PavementBand.WARM), WalkLevel.WAIT))),
        )
    }

    // --- the caption: the air when the pavement isn't the reason ---

    @Test
    fun `hot air with safe pavement blames the heat, not the pavement`() {
        assertEquals(
            WalkText(R.string.walk_too_hot),
            walkCaption(advice(WalkRating.PAWS_HOT, pavementNow = 40.0, now = check(WalkReason.Heat, WalkLevel.POTTY_ONLY))),
        )
    }

    @Test
    fun `hot with no pavement estimate blames the heat`() {
        assertEquals(
            WalkText(R.string.walk_too_hot),
            walkCaption(advice(WalkRating.PAWS_HOT, now = check(WalkReason.Heat, WalkLevel.POTTY_ONLY))),
        )
        assertEquals(
            WalkText(R.string.walk_air_potty, listOf(WalkText(R.string.walk_air_bad))),
            walkCaption(advice(WalkRating.BAD_AIR, now = check(WalkReason.Air(3, null), WalkLevel.POTTY_ONLY))),
        )
    }

    @Test
    fun `too cold blames the cold, with or without a pavement reading`() {
        val cold = check(WalkReason.Cold, WalkLevel.POTTY_ONLY)
        assertEquals(WalkText(R.string.walk_too_cold), walkCaption(advice(WalkRating.TOO_COLD, now = cold)))
        assertEquals(WalkText(R.string.walk_too_cold), walkCaption(advice(WalkRating.TOO_COLD, pavementNow = -3.0, now = cold)))
    }

    // --- no caption ---

    @Test
    fun `great, poor and okay without warm pavement have no caption`() {
        assertNull(walkCaption(advice(WalkRating.GREAT, pavementNow = 30.0)))
        assertNull(walkCaption(advice(WalkRating.POOR, pavementNow = 30.0, now = check(WalkReason.Rain(true), WalkLevel.WAIT))))
        assertNull(walkCaption(advice(WalkRating.OKAY, pavementNow = 30.0, now = check(WalkReason.Dark, WalkLevel.WAIT))))
        assertNull(walkCaption(advice(WalkRating.OKAY)))
    }

    @Test
    fun `a band without a pavement temperature never shows a pavement caption`() {
        assertNull(walkCaption(advice(WalkRating.OKAY, now = check(WalkReason.Pavement(PavementBand.WARM), WalkLevel.WAIT))))
    }
}
