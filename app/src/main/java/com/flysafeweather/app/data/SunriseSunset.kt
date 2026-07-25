package com.flysafeweather.app.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SunriseSunset(
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val timeZoneId: String
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    val sunriseFormatted: String
        get() = sunrise.format(timeFormatter)

    val sunsetFormatted: String
        get() = sunset.format(timeFormatter)
} 
