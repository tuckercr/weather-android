package com.fangjet.weather.worker

import com.fangjet.weather.data.DeviceLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MyLocationFollowerTest {

    private val here = DeviceLocation(latitude = 35.96, longitude = -83.92)

    private fun follower(
        on: Boolean = true,
        permission: Boolean = true,
        read: suspend () -> DeviceLocation? = { here },
        moves: MutableList<Pair<Double, Double>>,
        reads: MutableList<Unit> = mutableListOf(),
    ) = MyLocationFollower(
        isOn = { on },
        hasBackgroundPermission = { permission },
        readLocation = {
            reads += Unit
            read()
        },
        moveMyLocation = { lat, lon -> moves += lat to lon },
    )

    @Test
    fun `with the switch on and Allow all the time, My Location moves to where the phone is`() =
        runTest {
            val moves = mutableListOf<Pair<Double, Double>>()
            follower(moves = moves).follow()

            assertEquals(listOf(35.96 to -83.92), moves)
        }

    @Test
    fun `with the switch off, the location isn't even read`() =
        runTest {
            val moves = mutableListOf<Pair<Double, Double>>()
            val reads = mutableListOf<Unit>()
            follower(on = false, moves = moves, reads = reads).follow()

            assertEquals(0, reads.size)
            assertEquals(emptyList<Pair<Double, Double>>(), moves)
        }

    @Test
    fun `without Allow all the time, the location isn't read either`() =
        runTest {
            val moves = mutableListOf<Pair<Double, Double>>()
            val reads = mutableListOf<Unit>()
            follower(permission = false, moves = moves, reads = reads).follow()

            assertEquals(0, reads.size)
            assertEquals(emptyList<Pair<Double, Double>>(), moves)
        }

    @Test
    fun `no fix, or a failed read, leaves My Location where it was`() =
        runTest {
            val moves = mutableListOf<Pair<Double, Double>>()
            follower(read = { null }, moves = moves).follow()
            follower(read = { throw IOException("no location service") }, moves = moves).follow()

            assertEquals(emptyList<Pair<Double, Double>>(), moves)
        }
}
