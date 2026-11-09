package com.fangjet.weather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SeasonalThemeTest {
    private val shipped = SeasonalThemes.parse(File("src/main/res/raw/seasonal_themes.json").readText())!!

    private val json =
        """
        [{"id": "halloween", "dates": {"type": "fixed", "month": 10, "day": 31},
          "colors": {"heroDay": "#F5A94E", "heroNight": "#26215C", "inkDay": "#412402", "inkNight": "#EEEDFE",
                     "cardLight": "#FCE9D2", "cardDark": "#3A2A1A", "accentLight": "#A34F0B", "accentDark": "#F5A94E"},
          "captions": {"day": ["Day"], "night": ["Night"]},
          "decorations": ["bats", "turkey", "witchHat"]}]
        """.trimIndent()

    private fun epoch(
        text: String,
        offsetHours: Int,
    ) = LocalDateTime.parse(text).toEpochSecond(ZoneOffset.ofHours(offsetHours))

    @Test
    fun `the shipped list has Halloween with orange tile headers`() {
        assertEquals(listOf("halloween", "thanksgiving"), shipped.map { it.id })
        val halloween = shipped.first()
        assertEquals(
            setOf(SceneDecoration.BATS, SceneDecoration.WITCH_HAT, SceneDecoration.BANDANA, SceneDecoration.JACK_O_LANTERN),
            halloween.decorations,
        )
        assertEquals(0xC25E00, halloween.colors.tileHeaderLight)
        assertEquals(0xF5A94E, halloween.colors.tileHeaderDark)
    }

    @Test
    fun `the shipped list has Thanksgiving with leaves and a pie`() {
        val thanksgiving = shipped.single { it.id == "thanksgiving" }
        assertEquals(setOf(SceneDecoration.AUTUMN_LEAVES, SceneDecoration.PIE), thanksgiving.decorations)
        assertEquals(0x8C3F14, thanksgiving.colors.heroDay)
        assertEquals(0x8F3E0E, thanksgiving.colors.tileHeaderLight)
        assertEquals(0xF2B27A, thanksgiving.colors.tileHeaderDark)
        assertEquals("Sunny is on crumb patrol under the table.", thanksgiving.caption(night = false, date = LocalDate.of(2026, 11, 26)))
        assertEquals(
            "Too full to move. Misty is napping on the coats.",
            thanksgiving.caption(night = true, date = LocalDate.of(2026, 11, 26)),
        )
    }

    @Test
    fun `Thanksgiving is the fourth Thursday of November`() {
        val noon = { date: String -> epoch("${date}T12:00", -5) }
        assertEquals("thanksgiving", SeasonalThemes.themeOn(noon("2026-11-26"), -5 * 3600, shipped)?.id)
        assertEquals("thanksgiving", SeasonalThemes.themeOn(noon("2027-11-25"), -5 * 3600, shipped)?.id)
        assertNull(SeasonalThemes.themeOn(noon("2026-11-19"), -5 * 3600, shipped))
        assertNull(SeasonalThemes.themeOn(noon("2026-11-27"), -5 * 3600, shipped))
    }

    @Test
    fun `the night caption shows from sunset on the day, not from midnight to sunrise`() {
        val halloween = shipped.first()
        val day = "Misty's going as a witch. Sunny's going as Sunny."
        val night = "Too many doorbells. Misty is under the bed."
        assertEquals(night, halloween.captionAt(dark = true, local = LocalDateTime.parse("2026-10-31T19:00")))
        assertEquals(day, halloween.captionAt(dark = true, local = LocalDateTime.parse("2026-10-31T03:00")))
        assertEquals(day, halloween.captionAt(dark = false, local = LocalDateTime.parse("2026-10-31T12:00")))
        assertEquals(day, halloween.captionAt(dark = false, local = LocalDateTime.parse("2026-10-31T17:00")))
    }

    @Test
    fun `Halloween is the city's own 31 October and ends with it`() {
        // 10 AM on 31 October in Los Angeles (UTC-7) is already 1 November in Tokyo (UTC+9).
        val instant = epoch("2026-10-31T10:00", -7)
        assertEquals("halloween", SeasonalThemes.themeOn(instant, -7 * 3600, shipped)?.id)
        assertNull(SeasonalThemes.themeOn(instant, 9 * 3600, shipped))
        assertEquals("halloween", SeasonalThemes.themeOn(epoch("2026-10-31T23:59", -7), -7 * 3600, shipped)?.id)
        assertNull(SeasonalThemes.themeOn(epoch("2026-11-01T00:00", -7), -7 * 3600, shipped))
    }

    @Test
    fun `a range includes both ends and can cross New Year`() {
        val rule = DateRule.Range(12, 24, 1, 1)
        assertTrue(rule.contains(LocalDate.of(2026, 12, 24)))
        assertTrue(rule.contains(LocalDate.of(2027, 1, 1)))
        assertFalse(rule.contains(LocalDate.of(2026, 12, 23)))
        assertFalse(rule.contains(LocalDate.of(2027, 1, 2)))
    }

    @Test
    fun `the nth weekday finds Thanksgiving and the last Monday of May`() {
        val thanksgiving = DateRule.NthWeekday(month = 11, weekday = 5, n = 4)
        assertTrue(thanksgiving.contains(LocalDate.of(2026, 11, 26)))
        assertFalse(thanksgiving.contains(LocalDate.of(2026, 11, 19)))
        val memorialDay = DateRule.NthWeekday(month = 5, weekday = 2, n = -1)
        assertTrue(memorialDay.contains(LocalDate.of(2026, 5, 25)))
        assertFalse(memorialDay.contains(LocalDate.of(2026, 5, 18)))
    }

    @Test
    fun `a theme from Remote Config skips decorations this version can't draw`() {
        val theme = SeasonalThemes.parse(json)!!.single()
        assertEquals(setOf(SceneDecoration.BATS, SceneDecoration.WITCH_HAT), theme.decorations)
        assertEquals("Night", theme.caption(night = true, date = LocalDate.of(2026, 10, 31)))
        // Without tile header colours the tiles keep the brand colour; the old accent colours this
        // theme still carries are ignored.
        assertEquals(null, theme.colors.tileHeaderLight)
    }

    @Test
    fun `an unknown decoration keeps the theme and the rest of the list`() {
        val other = json.replace("\"halloween\"", "\"other\"").removeSurrounding("[", "]")
        val list = "[${json.removeSurrounding("[", "]")}, $other]"
        val themes = SeasonalThemes.parse(list)!!
        assertEquals(listOf("halloween", "other"), themes.map { it.id })
        assertEquals(setOf(SceneDecoration.BATS, SceneDecoration.WITCH_HAT), themes.first().decorations)
        assertEquals("Day", themes.first().caption(night = false, date = LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `a theme this version can't read is skipped and the others kept`() {
        val easter = json
            .replace("\"halloween\"", "\"easter\"")
            .replace("\"fixed\"", "\"easterSunday\"")
            .removeSurrounding("[", "]")
        val list = "[$easter, ${json.removeSurrounding("[", "]")}]"

        assertEquals(listOf("halloween"), SeasonalThemes.parse(list)?.map { it.id })
        assertEquals(emptyList<SeasonalTheme>(), SeasonalThemes.parse("[]"))
    }

    @Test
    fun `bad JSON is rejected so the shipped list stays`() {
        assertNull(SeasonalThemes.parse("""{"id": 1}"""))
        assertNull(SeasonalThemes.parse(json.replace("#F5A94E", "orange")))
        assertNull(SeasonalThemes.parse(json.replace("\"fixed\"", "\"easter\"")))
    }

    @Test
    fun `when two themes share a day the earlier wins`() {
        val halloween = SeasonalThemes.parse(json)!!.single()
        val autumn = halloween.copy(id = "autumn", dates = DateRule.Range(10, 1, 11, 30))
        assertEquals("halloween", SeasonalThemes.themeOn(epoch("2026-10-31T12:00", 0), 0, listOf(halloween, autumn))?.id)
        assertEquals("autumn", SeasonalThemes.themeOn(epoch("2026-10-30T12:00", 0), 0, listOf(halloween, autumn))?.id)
    }
}
