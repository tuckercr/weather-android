package com.fangjet.weather.ui

import com.fangjet.weather.R
import com.fangjet.weather.util.FluffLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class FluffLineTest {
    @Test
    fun `Misty's lines mention panting only with a dog`() {
        assertEquals(R.string.fluff_dry, fluffLineRes(FluffLevel.DRY, hasDog = true))
        assertEquals(R.string.fluff_comfy, fluffLineRes(FluffLevel.COMFY, hasDog = true))
        assertEquals(R.string.fluff_muggy_dog, fluffLineRes(FluffLevel.MUGGY, hasDog = true))
        assertEquals(R.string.fluff_muggy, fluffLineRes(FluffLevel.MUGGY, hasDog = false))
        assertEquals(R.string.fluff_soupy, fluffLineRes(FluffLevel.SOUPY, hasDog = false))
    }
}
