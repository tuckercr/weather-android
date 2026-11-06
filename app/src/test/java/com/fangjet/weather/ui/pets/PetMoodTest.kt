package com.fangjet.weather.ui.pets

import com.fangjet.weather.domain.PetWeatherThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PetMoodTest {

    @Test
    fun `precipitation and storms map to their moods regardless of temperature`() {
        assertEquals(PetMood.STORM, petMoodFor("Thunderstorm", "11d", 35.0))
        assertEquals(PetMood.SNOW, petMoodFor("Snow", "13d", -5.0))
        assertEquals(PetMood.RAIN, petMoodFor("Rain", "10d", 32.0))
        assertEquals(PetMood.RAIN, petMoodFor("Drizzle", "09n", 12.0))
    }

    @Test
    fun `temperature extremes win over sky and time of day`() {
        assertEquals(PetMood.HOT, petMoodFor("Clear", "01d", 30.0))
        assertEquals(PetMood.COLD, petMoodFor("Clouds", "04n", 0.0))
    }

    @Test
    fun `night icon means night, otherwise clear is fair or sunny and anything else is cloudy`() {
        assertEquals(PetMood.NIGHT, petMoodFor("Clear", "01n", 15.0))
        assertEquals(PetMood.FAIR, petMoodFor("Clear", "01d", 20.0))
        assertEquals(PetMood.CLOUDY, petMoodFor("Clouds", "03d", 20.0))
        assertEquals(PetMood.CLOUDY, petMoodFor("Mist", "50d", 12.0))
    }

    @Test
    fun `remote config thresholds move the hot and cold cut-offs`() {
        val warmer = PetWeatherThresholds.DEFAULT.copy(petHotC = 25.0, petColdC = 5.0)

        // 26C clear is fair with the defaults, hot with a 25C threshold.
        assertEquals(PetMood.FAIR, petMoodFor("Clear", "01d", 26.0))
        assertEquals(PetMood.HOT, petMoodFor("Clear", "01d", 26.0, warmer))
        // 4C cloudy is cloudy with the defaults, cold with a 5C threshold.
        assertEquals(PetMood.CLOUDY, petMoodFor("Clouds", "04d", 4.0))
        assertEquals(PetMood.COLD, petMoodFor("Clouds", "04d", 4.0, warmer))
    }

    @Test
    fun `a clear day is sunny only when the Feels Like tile says warm`() {
        // Chilly and mild: the sun, no sunglasses.
        assertEquals(PetMood.FAIR, petMoodFor("Clear", "01d", 1.0))
        assertEquals(PetMood.FAIR, petMoodFor("Clear", "01d", 20.0))
        assertEquals(PetMood.FAIR, petMoodFor("Clear", "01d", 27.0))
        // Warm, between the walk comfort range and hot: sunglasses on.
        assertEquals(PetMood.SUNNY, petMoodFor("Clear", "01d", 27.5))
        assertEquals(PetMood.SUNNY, petMoodFor("Clear", "01d", 29.9))
        assertEquals(PetMood.HOT, petMoodFor("Clear", "01d", 30.0))
        // A warm day that isn't clear stays cloudy.
        assertEquals(PetMood.CLOUDY, petMoodFor("Clouds", "03d", 28.0))
        // The comfort range comes from Remote Config too.
        val cooler = PetWeatherThresholds.DEFAULT.copy(comfortMaxC = 22.0)
        assertEquals(PetMood.SUNNY, petMoodFor("Clear", "01d", 23.0, cooler))
    }

    @Test
    fun `night is split by the city's local hour`() {
        assertEquals(NightPart.EVENING, nightPartAt(19))
        assertEquals(NightPart.EVENING, nightPartAt(22))
        assertEquals(NightPart.LATE_NIGHT, nightPartAt(23))
        assertEquals(NightPart.LATE_NIGHT, nightPartAt(3))
        assertEquals(NightPart.BEFORE_DAWN, nightPartAt(5))
        assertEquals(NightPart.BEFORE_DAWN, nightPartAt(7))
    }

    @Test
    fun `without a dog, no caption mentions walks or paws`() {
        val noDog = captionLists().filterKeys { it.endsWith("_no_dog") }
        assertTrue(noDog.isNotEmpty())
        noDog.values.flatten().forEach { assertFalse(it, walkWords.containsMatchIn(it)) }
    }

    @Test
    fun `every caption list with a walk in it has a no-dog version`() {
        val lists = captionLists()
        lists
            .filter { (name, captions) -> !name.endsWith("_no_dog") && captions.any(walkWords::containsMatchIn) }
            .keys
            .forEach { assertTrue("$it has no no-dog version", "${it}_no_dog" in lists) }
    }

    private val walkWords = Regex("walk|stroll|paws|dog", RegexOption.IGNORE_CASE)

    /** The shipped pet caption lists, by resource name. */
    private fun captionLists(): Map<String, List<String>> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        val arrays = strings.getElementsByTagName("string-array")
        return (0 until arrays.length)
            .map { arrays.item(it) as Element }
            .filter { it.getAttribute("name").startsWith("pet_caption_") }
            .associate { array ->
                val items = array.getElementsByTagName("item")
                array.getAttribute("name") to (0 until items.length).map { items.item(it).textContent }
            }
    }
}
