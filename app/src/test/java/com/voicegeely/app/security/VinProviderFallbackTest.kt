package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class VinProviderFallbackTest {
    private val vin = "LB37752Z3RH000001"

    private fun fixed(result: VinReadResult) = object : VinProvider { override fun readVin() = result }

    @Test
    fun systemPropertyValidVin() {
        assertEquals(
            VinReadResult.Available(vin, VinSource.PLATFORM_SYSTEM_PROPERTY),
            SystemPropertyVinProvider { key -> if (key == SystemPropertyVinProvider.PROPERTY_KEY) " ${vin.lowercase()} " else null }.readVin()
        )
    }

    @Test
    fun systemPropertyInvalidOrMissing() {
        assertEquals(VinReadResult.Unavailable(VinUnavailableReason.EMPTY_OR_INVALID), SystemPropertyVinProvider { null }.readVin())
        assertEquals(VinReadResult.Unavailable(VinUnavailableReason.EMPTY_OR_INVALID), SystemPropertyVinProvider { "123" }.readVin())
        assertEquals(VinReadResult.Unavailable(VinUnavailableReason.READ_FAILED), SystemPropertyVinProvider { error("no class") }.readVin())
    }

    @Test
    fun standardFirstThenPlatformProperty() {
        val primary = VinReadResult.Available(vin)
        assertEquals(primary, FallbackVinProvider(listOf(fixed(primary), fixed(VinReadResult.Available("X", VinSource.PLATFORM_SYSTEM_PROPERTY)))).readVin())
        val fallback = VinReadResult.Available(vin, VinSource.PLATFORM_SYSTEM_PROPERTY)
        assertEquals(fallback, FallbackVinProvider(listOf(fixed(VinReadResult.Unavailable(VinUnavailableReason.PERMISSION_DENIED)), fixed(fallback))).readVin())
    }

    @Test
    fun noneAvailableKeepsPrimaryReason() {
        val result = FallbackVinProvider(listOf(
            fixed(VinReadResult.Unavailable(VinUnavailableReason.PERMISSION_DENIED)),
            object : VinProvider { override fun readVin(): VinReadResult = error("boom") }
        )).readVin()
        assertEquals(VinReadResult.Unavailable(VinUnavailableReason.PERMISSION_DENIED), result)
    }
}
