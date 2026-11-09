package com.fangjet.weather.domain

import com.fangjet.weather.domain.PetAccessory.MISTY_SCARF
import com.fangjet.weather.domain.PetAccessory.MISTY_WITCH_HAT
import com.fangjet.weather.domain.PetAccessory.SUNNY_BANDANA
import com.fangjet.weather.domain.PetAccessory.SUNNY_SCARF
import com.fangjet.weather.domain.PetAccessory.SUNNY_SUNGLASSES
import com.fangjet.weather.domain.PetAccessory.UMBRELLA
import com.fangjet.weather.domain.SceneDecoration.BANDANA
import com.fangjet.weather.domain.SceneDecoration.BATS
import com.fangjet.weather.domain.SceneDecoration.JACK_O_LANTERN
import com.fangjet.weather.domain.SceneDecoration.WITCH_HAT
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as iOS's. */
class SceneAccessoriesTest {
    private val halloween = setOf(BATS, WITCH_HAT, BANDANA, JACK_O_LANTERN)

    @Test
    fun `without a theme the mood's own props show`() {
        assertEquals(setOf(UMBRELLA), sceneAccessories(PetMood.RAIN, emptySet(), dark = false))
        assertEquals(setOf(SUNNY_SUNGLASSES), sceneAccessories(PetMood.SUNNY, emptySet(), dark = false))
        assertEquals(setOf(SUNNY_SCARF, MISTY_SCARF), sceneAccessories(PetMood.COLD, emptySet(), dark = false))
        assertEquals(emptySet<PetAccessory>(), sceneAccessories(PetMood.NIGHT, emptySet(), dark = true))
    }

    @Test
    fun `the umbrella stays with the costumes on a rainy Halloween`() {
        assertEquals(setOf(MISTY_WITCH_HAT, SUNNY_BANDANA, UMBRELLA), sceneAccessories(PetMood.RAIN, halloween, dark = false))
    }

    @Test
    fun `a costume replaces only what it covers on its own pet`() {
        // The witch hat is on Misty's head: Sunny keeps his sunglasses and both keep their scarves.
        assertEquals(setOf(MISTY_WITCH_HAT, SUNNY_SUNGLASSES), sceneAccessories(PetMood.SUNNY, setOf(WITCH_HAT), dark = false))
        assertEquals(setOf(MISTY_WITCH_HAT, SUNNY_SCARF, MISTY_SCARF), sceneAccessories(PetMood.SNOW, setOf(WITCH_HAT), dark = false))
        // The bandana is round Sunny's neck, in place of his scarf only.
        assertEquals(setOf(SUNNY_BANDANA, MISTY_SCARF), sceneAccessories(PetMood.COLD, setOf(BANDANA), dark = false))
        assertEquals(setOf(SUNNY_BANDANA, SUNNY_SUNGLASSES), sceneAccessories(PetMood.SUNNY, setOf(BANDANA), dark = false))
    }

    @Test
    fun `after dark by the clock the costumes are off whatever the weather`() {
        assertEquals(setOf(UMBRELLA), sceneAccessories(PetMood.RAIN, halloween, dark = true))
        assertEquals(setOf(SUNNY_SCARF, MISTY_SCARF), sceneAccessories(PetMood.COLD, halloween, dark = true))
        assertEquals(emptySet<PetAccessory>(), sceneAccessories(PetMood.NIGHT, halloween, dark = true))
    }
}
