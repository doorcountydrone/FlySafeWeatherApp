package com.flysafeweather.app.data

import java.time.LocalDateTime

data class TafData(
    val rawText: String = "",
    val stationId: String = "",
    val issueTime: LocalDateTime = LocalDateTime.now(),
    val periods: List<TafPeriod> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class TafPeriod(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val flightCategory: String,
    val windDirection: Int,
    val windSpeed: Int,
    val windGust: Int,
    val visibility: Double,
    val clouds: List<CloudLayer>,
    val weather: List<String>
) 