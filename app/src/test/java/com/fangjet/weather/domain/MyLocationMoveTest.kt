package com.fangjet.weather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyLocationMoveTest {

    private val london = SavedLocation("London", 51.5072, -0.1276)
    private val myLocation = SavedLocation("My Location", 40.0, -83.0, isCurrentLocation = true)

    @Test
    fun `a real move moves My Location and leaves the other cities alone`() {
        val moved = listOf(london, myLocation).withMyLocationAt(40.1, -83.0)

        assertEquals(listOf(london, myLocation.copy(latitude = 40.1)), moved)
    }

    @Test
    fun `GPS wobble under a kilometre changes nothing`() {
        assertNull(listOf(myLocation).withMyLocationAt(40.001, -83.0))
    }

    @Test
    fun `without a My Location there's nothing to move`() {
        assertNull(listOf(london).withMyLocationAt(40.1, -83.0))
    }

    @Test
    fun `a My Location saved without coordinates takes the new ones`() {
        val noFix = myLocation.copy(latitude = null, longitude = null)

        assertEquals(listOf(myLocation), listOf(noFix).withMyLocationAt(40.0, -83.0))
    }
}
