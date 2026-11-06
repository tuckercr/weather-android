package com.fangjet.weather.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePlaceNamesTest {
    private var remembered: DevicePlaceNames.Remembered? = null
    private var lookUps = 0

    private fun names(vararg answers: String?): DevicePlaceNames {
        val queue = answers.toMutableList()
        return DevicePlaceNames(
            load = { remembered },
            save = { remembered = it },
            lookUp = { _, _ ->
                lookUps += 1
                if (queue.isEmpty()) null else queue.removeAt(0)
            },
        )
    }

    @Test
    fun `remembers the name until My Location moves`() =
        runTest {
            val names = names("Knoxville", "Farragut")

            val first = names.name(35.9168, -83.9597)
            // About 500 m away: the same place, not looked up again.
            val nearby = names.name(35.9213, -83.9597)
            // About 2 km away: looked up.
            val moved = names.name(35.9348, -83.9597)

            assertEquals("Knoxville", first)
            assertEquals("Knoxville", nearby)
            assertEquals("Farragut", moved)
            assertEquals(2, lookUps)
        }

    @Test
    fun `remembers across launches`() =
        runTest {
            names("Knoxville").name(35.9168, -83.9597)

            assertEquals("Knoxville", names().name(35.9168, -83.9597))
        }

    @Test
    fun `failed or blank lookups give no name and are tried again`() =
        runTest {
            val names = names(null, "  ", "Knoxville")

            assertNull(names.name(35.9168, -83.9597))
            assertNull(names.name(35.9168, -83.9597))
            assertEquals("Knoxville", names.name(35.9168, -83.9597))
        }
}
