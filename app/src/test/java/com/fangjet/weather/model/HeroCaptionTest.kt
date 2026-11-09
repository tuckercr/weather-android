package com.fangjet.weather.model

import com.fangjet.weather.R
import com.fangjet.weather.StringsXml
import com.fangjet.weather.domain.PetMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory

/** The cloudy mood's captions by the sky, and the afternoon-only ones by the city's hour. */
class HeroCaptionTest {
    // Five hours behind UTC, so the city's noon isn't UTC's.
    private val offset = -5 * 3600

    private val sunnySpot = "Misty has found the one sunny spot on the floor."
    private val nap = "Nap weather, says Misty."

    @Test
    fun `partly cloudy has its own captions, with and without a dog`() {
        val weather = caption(PetMood.CLOUDY, hour = 15, partlyCloudy = true)
        assertEquals(R.array.pet_caption_partly_cloudy, weather.captions)
        assertEquals(R.array.pet_caption_partly_cloudy_no_dog, weather.noDogCaptions)
        assertEquals(
            listOf("Bit of sun, bit of cloud. Good sniffing weather.", "Sun in, sun out. Sunny is ready either way.", sunnySpot),
            array("pet_caption_partly_cloudy"),
        )
        assertEquals(
            listOf(
                "Bit of sun, bit of cloud. Misty is chasing the sunny patches.",
                "Sun in, sun out. Misty is supervising from the window.",
                sunnySpot,
            ),
            array("pet_caption_partly_cloudy_no_dog"),
        )
        assertEquals(R.array.pet_caption_partly_cloudy_no_dog, caption(PetMood.CLOUDY, 15, partlyCloudy = true, hasDog = false).captions)
    }

    @Test
    fun `mostly cloudy and overcast keep the grey captions`() {
        val weather = caption(PetMood.CLOUDY, hour = 15, partlyCloudy = false)
        assertEquals(R.array.pet_caption_cloudy, weather.captions)
        assertEquals(R.array.pet_caption_cloudy_no_dog, weather.noDogCaptions)
        // The flag only changes the cloudy mood's words.
        assertEquals(R.array.pet_caption_rain, caption(PetMood.RAIN, hour = 15, partlyCloudy = true).captions)
    }

    @Test
    fun `the partly cloudy walk suggestions are left out when now isn't the time`() {
        val options = captionOptions(
            array("pet_caption_partly_cloudy"),
            array("pet_caption_partly_cloudy_no_dog"),
            WALK_SUGGESTIONS.map(StringsXml::get).toSet(),
            leaveOutWalkSuggestions = true,
        )
        assertEquals(listOf(sunnySpot), options)
        assertEquals(
            array("pet_caption_partly_cloudy"),
            captionOptions(
                array("pet_caption_partly_cloudy"),
                array("pet_caption_partly_cloudy_no_dog"),
                WALK_SUGGESTIONS.map(StringsXml::get).toSet(),
                leaveOutWalkSuggestions = false,
            ),
        )
    }

    @Test
    fun `the nap caption never shows before noon in the city, and can from noon`() {
        // 1 Jan: day 1 of the year, so the grey lists' daily pick is their second caption, the nap.
        val date = LocalDate.of(2026, 1, 1)
        assertEquals(nap, pick(date, hour = 12, hasDog = true))
        assertEquals(nap, pick(date, hour = 12, hasDog = false))
        assertEquals(nap, pick(date, hour = 17, hasDog = true))
        assertFalse(pick(date, hour = 11, hasDog = true) == nap)
        assertFalse(pick(date, hour = 11, hasDog = false) == nap)
        // Every morning hour of a year, with and without a dog, walk or no walk.
        for (day in 0L until 366L) {
            for (hour in 0 until 12) {
                for (hasDog in listOf(true, false)) {
                    for (noWalk in listOf(true, false)) {
                        assertFalse(pick(date.plusDays(day), hour, hasDog, noWalk) == nap)
                    }
                }
            }
        }
    }

    @Test
    fun `the partly cloudy flag comes from the sky as Misty's View reads it`() {
        assertTrue(partlyCloudyCaptions("Clouds", 20, "02d"))
        assertTrue(partlyCloudyCaptions("Clouds", 50, "03d"))
        assertTrue(partlyCloudyCaptions("Clear", 0, "01d"))
        assertFalse(partlyCloudyCaptions("Clouds", 70, "04d"))
        assertFalse(partlyCloudyCaptions("Clouds", 100, "04d"))
        assertFalse(partlyCloudyCaptions("Mist", 20, "50d"))
        // The forecast's hour standing in has no cloud cover: its icon decides.
        assertTrue(partlyCloudyCaptions("Clouds", null, "02d"))
        assertFalse(partlyCloudyCaptions("Clouds", null, "03d"))
    }

    // --- helpers ---

    private fun epochAt(
        date: LocalDate,
        hour: Int,
    ): Long = date.atTime(hour, 0).toEpochSecond(ZoneOffset.ofTotalSeconds(offset))

    private fun caption(
        mood: PetMood,
        hour: Int,
        partlyCloudy: Boolean,
        hasDog: Boolean = true,
        date: LocalDate = LocalDate.of(2026, 1, 1),
    ) = heroCaption(null, null, mood, epochAt(date, hour), offset, hasDog, partlyCloudy) as HeroCaption.Weather

    /** The caption a grey day shows, resolved as the screen does. */
    private fun pick(
        date: LocalDate,
        hour: Int,
        hasDog: Boolean,
        noWalk: Boolean = false,
    ): String {
        val weather = caption(PetMood.CLOUDY, hour, partlyCloudy = false, hasDog = hasDog, date = date)
        val options = captionOptions(
            array(name(weather.captions)),
            array(name(weather.noDogCaptions)),
            WALK_SUGGESTIONS.map(StringsXml::get).toSet(),
            leaveOutWalkSuggestions = noWalk,
            afternoonOnly = AFTERNOON_ONLY.map(StringsXml::get).toSet(),
            leaveOutAfternoonOnly = weather.leaveOutAfternoonOnly,
        )
        return options[weather.day % options.size]
    }

    private fun name(arrayRes: Int): String =
        R.array::class.java.fields
            .first { it.getInt(null) == arrayRes }
            .name

    /** The shipped string array [name], with `@string/` references resolved. */
    private fun array(name: String): List<String> = arrays.getValue(name)

    private val arrays: Map<String, List<String>> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string-array")
        (0 until nodes.length).map { nodes.item(it) as Element }.associate { array ->
            val items = array.getElementsByTagName("item")
            array.getAttribute("name") to (0 until items.length).map { items.item(it).textContent.trim() }.map { text ->
                if (text.startsWith("@string/")) StringsXml.raw(text.removePrefix("@string/")) else text.replace("\\'", "'")
            }
        }
    }
}
