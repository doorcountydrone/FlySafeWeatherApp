package com.flysafeweather.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TafService(private val context: Context) {
    companion object {
        private const val TAG = "TafService"
        private const val SEARCH_RADIUS_NM = 50.0  // Search radius in nautical miles
    }

    suspend fun fetchTaf(airportCode: String): TafData? = withContext(Dispatchers.IO) {
        try {
            // Get airport coordinates from AirportService
            val airportService = AirportService(context)
            val primaryAirport = airportService.findAirport(airportCode)
            
            if (primaryAirport == null) {
                Log.w(TAG, "Airport not found: $airportCode")
                return@withContext null
            }

            // First try to get TAF for the requested airport
            val primaryTaf = fetchTafForAirport(primaryAirport)
            if (primaryTaf != null) {
                Log.d(TAG, "Found TAF for primary airport: ${primaryAirport.icao}")
                return@withContext primaryTaf
            }

            // If no TAF for primary airport, find nearby airports
            Log.d(TAG, "No TAF for ${primaryAirport.icao}, searching nearby airports within ${SEARCH_RADIUS_NM}nm")
            val nearbyAirports = airportService.findNearbyAirports(
                primaryAirport.latitude,
                primaryAirport.longitude,
                SEARCH_RADIUS_NM
            ).filter { it.icao != primaryAirport.icao }  // Exclude the primary airport

            // Try each nearby airport in order of distance
            for (airport in nearbyAirports) {
                val taf = fetchTafForAirport(airport)
                if (taf != null) {
                    Log.d(TAG, "Found TAF from nearby airport: ${airport.icao}")
                    return@withContext taf.copy(
                        rawText = "TAF from nearest reporting station ${airport.icao} (${airport.distance}nm away):\n${taf.rawText}"
                    )
                }
            }

            Log.w(TAG, "No TAF found for ${airportCode} or any nearby airports within ${SEARCH_RADIUS_NM}nm")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TAF for $airportCode", e)
            null
        }
    }

    private suspend fun fetchTafForAirport(airport: Airport): TafData? {
        // TODO: Implement actual TAF fetching from an aviation weather API
        // For now, generate dummy TAF data for any valid airport
        if (airport.icao.matches(Regex("^K[A-Z]{3}$"))) {
            Log.d(TAG, "Generating TAF data for ${airport.icao}")
            return TafData(
                rawText = "TAF ${airport.icao} 201720Z 2018/2118 27015KT P6SM BKN040 \n     FM202300 30012KT P6SM SCT050",
                stationId = airport.icao,
                issueTime = LocalDateTime.now(),
                latitude = airport.latitude,
                longitude = airport.longitude,
                periods = listOf(
                    TafPeriod(
                        startTime = LocalDateTime.now(),
                        endTime = LocalDateTime.now().plusHours(6),
                        flightCategory = "VFR",
                        windDirection = 270,
                        windSpeed = 15,
                        windGust = 0,
                        visibility = 6.0,
                        clouds = listOf(
                            CloudLayer("BKN", 4000)
                        ),
                        weather = emptyList()
                    ),
                    TafPeriod(
                        startTime = LocalDateTime.now().plusHours(6),
                        endTime = LocalDateTime.now().plusHours(24),
                        flightCategory = "VFR",
                        windDirection = 300,
                        windSpeed = 12,
                        windGust = 0,
                        visibility = 6.0,
                        clouds = listOf(
                            CloudLayer("SCT", 5000)
                        ),
                        weather = emptyList()
                    )
                )
            )
        }
        
        Log.w(TAG, "No TAF available for ${airport.icao}")
        return null  // No TAF available for this airport
    }
} 