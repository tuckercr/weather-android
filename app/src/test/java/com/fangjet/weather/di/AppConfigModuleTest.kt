package com.fangjet.weather.di

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigModuleTest {
    @Test
    fun `remote key wins when set`() {
        assertEquals("remote", AppConfigModule.resolveApiKey(" remote ", "bundled"))
    }

    @Test
    fun `bundled key is used when the remote key is missing or blank`() {
        assertEquals("bundled", AppConfigModule.resolveApiKey(null, " bundled "))
        assertEquals("bundled", AppConfigModule.resolveApiKey("   ", "bundled"))
    }
}
