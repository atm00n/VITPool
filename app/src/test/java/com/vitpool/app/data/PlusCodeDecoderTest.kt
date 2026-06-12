package com.vitpool.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusCodeDecoderTest {
    @Test
    fun decodePlusCodeInput_decodesFullPlusCode() {
        val result = decodePlusCodeInput("849VCWC8+R9")

        assertTrue(result is PlusCodeDecodeResult.Success)
        val decoded = (result as PlusCodeDecodeResult.Success).decoded
        assertEquals("849VCWC8+R9", decoded.normalizedCode)
        assertEquals(37.4220625, decoded.latitude, 0.000001)
        assertEquals(-122.0840625, decoded.longitude, 0.000001)
    }

    @Test
    fun decodePlusCodeInput_extractsFullPlusCodeFromPastedUrl() {
        val result = decodePlusCodeInput("https://www.google.com/maps/search/?api=1&query=849VCWC8%2BR9")

        assertTrue(result is PlusCodeDecodeResult.Success)
        val decoded = (result as PlusCodeDecodeResult.Success).decoded
        assertEquals("849VCWC8+R9", decoded.normalizedCode)
        assertEquals(37.4220625, decoded.latitude, 0.000001)
        assertEquals(-122.0840625, decoded.longitude, 0.000001)
    }

    @Test
    fun decodePlusCodeInput_rejectsShortPlusCode() {
        val result = decodePlusCodeInput("CWC8+R9 Mountain View")

        assertTrue(result is PlusCodeDecodeResult.Error)
        assertTrue((result as PlusCodeDecodeResult.Error).message.contains("short Plus Code"))
    }

    @Test
    fun decodePlusCodeInput_rejectsInvalidText() {
        val result = decodePlusCodeInput("not a plus code")

        assertTrue(result is PlusCodeDecodeResult.Error)
    }
}
