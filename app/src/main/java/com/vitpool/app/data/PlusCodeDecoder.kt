package com.vitpool.app.data

import kotlin.math.min

private const val PLUS_CODE_ALPHABET = "23456789CFGHJMPQRVWX"
private const val PLUS_CODE_SEPARATOR = '+'
private const val FULL_CODE_SEPARATOR_INDEX = 8
private const val MIN_PRECISE_CODE_LENGTH = 10
private const val MAX_CODE_LENGTH = 15
private val PAIR_RESOLUTIONS = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)
private val PLUS_CODE_PATTERN = Regex("[23456789CFGHJMPQRVWX0]{2,8}\\+[23456789CFGHJMPQRVWX0]{0,8}")

internal data class DecodedPlusCode(
    val normalizedCode: String,
    val latitude: Double,
    val longitude: Double,
)

internal sealed class PlusCodeDecodeResult {
    data class Success(val decoded: DecodedPlusCode) : PlusCodeDecodeResult()
    data class Error(val message: String) : PlusCodeDecodeResult()
}

internal fun decodePlusCodeInput(input: String): PlusCodeDecodeResult {
    val normalizedInput = input
        .trim()
        .uppercase()
        .replace("%2B", "+")
        .replace("%20", " ")

    if (normalizedInput.isBlank()) {
        return PlusCodeDecodeResult.Error("Enter the full Plus Code from Google Maps.")
    }

    val rawCode = PLUS_CODE_PATTERN.find(normalizedInput)?.value
        ?: return PlusCodeDecodeResult.Error("Enter a valid full Plus Code from Google Maps.")
    val separatorIndex = rawCode.indexOf(PLUS_CODE_SEPARATOR)
    if (separatorIndex < FULL_CODE_SEPARATOR_INDEX) {
        return PlusCodeDecodeResult.Error("This is a short Plus Code. Please paste the full Plus Code from Google Maps.")
    }

    val code = rawCode
        .replace(PLUS_CODE_SEPARATOR.toString(), "")
        .replace("0", "")
    if (code.length < MIN_PRECISE_CODE_LENGTH) {
        return PlusCodeDecodeResult.Error("Paste a full Plus Code with at least 10 characters.")
    }
    if (code.length > MAX_CODE_LENGTH) {
        return PlusCodeDecodeResult.Error("Plus Code is too long.")
    }

    val decoded = decodeFullPlusCode(code, rawCode)
        ?: return PlusCodeDecodeResult.Error("Enter a valid full Plus Code from Google Maps.")
    return PlusCodeDecodeResult.Success(decoded)
}

private fun decodeFullPlusCode(code: String, normalizedCode: String): DecodedPlusCode? {
    var latitude = -90.0
    var longitude = -180.0
    var latitudeResolution = PAIR_RESOLUTIONS.last()
    var longitudeResolution = PAIR_RESOLUTIONS.last()
    val pairCodeLength = min(code.length, MIN_PRECISE_CODE_LENGTH)

    for (index in 0 until pairCodeLength step 2) {
        val resolution = PAIR_RESOLUTIONS[index / 2]
        val latitudeDigit = PLUS_CODE_ALPHABET.indexOf(code[index])
        val longitudeDigit = PLUS_CODE_ALPHABET.indexOf(code[index + 1])
        if (latitudeDigit < 0 || longitudeDigit < 0) return null

        latitude += latitudeDigit * resolution
        longitude += longitudeDigit * resolution
        latitudeResolution = resolution
        longitudeResolution = resolution
    }

    for (index in MIN_PRECISE_CODE_LENGTH until code.length) {
        val digit = PLUS_CODE_ALPHABET.indexOf(code[index])
        if (digit < 0) return null

        latitudeResolution /= 5.0
        longitudeResolution /= 4.0
        latitude += (digit / 4) * latitudeResolution
        longitude += (digit % 4) * longitudeResolution
    }

    return DecodedPlusCode(
        normalizedCode = normalizedCode,
        latitude = latitude + latitudeResolution / 2.0,
        longitude = longitude + longitudeResolution / 2.0,
    )
}
