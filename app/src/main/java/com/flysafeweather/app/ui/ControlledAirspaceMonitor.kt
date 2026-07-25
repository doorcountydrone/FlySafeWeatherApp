package com.flysafeweather.app.ui

import android.location.Location
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

data class USAirport(
    val icao: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val airspaceClass: String?
)

data class AirportResponse(
    val airports: List<USAirport>
)

@Composable
fun ControlledAirspaceMonitor(
    currentLocation: Location?,
    onAirspaceWarning: (String) -> Unit
) {
    val context = LocalContext.current
    var airports by remember { mutableStateOf<List<USAirport>>(emptyList()) }
    var showWarning by remember { mutableStateOf(false) }
    var currentAirport by remember { mutableStateOf<USAirport?>(null) }
    var userDismissedForIcao by remember { mutableStateOf<String?>(null) }

    val locationState = rememberUpdatedState(currentLocation)
    val airportsState = rememberUpdatedState(airports)

    // Load airports once when component mounts
    LaunchedEffect(Unit) {
        try {
            context.assets.open("us_reporting_airports.json").use { stream ->
                val jsonString = stream.bufferedReader().readText()
                val response = Gson().fromJson(jsonString, AirportResponse::class.java)
                airports = response.airports
                Log.d("Airspace", "Loaded ${airports.size} airports")
            }
        } catch (e: Exception) {
            Log.e("Airspace", "Failed to load airports", e)
        }
    }

    // Re-check when location OR airport list becomes available; poll while on screen
    LaunchedEffect(currentLocation, airports.size) {
        while (isActive) {
            val location = locationState.value
            val airportList = airportsState.value

            if (location == null) {
                Log.d("Airspace", "No current location available")
            } else if (airportList.isEmpty()) {
                Log.d("Airspace", "Airport list not loaded yet")
            } else {
                val match = findControlledAirspaceAtLocation(location, airportList)
                if (match != null) {
                    val (airport, distance) = match
                    val shouldShow = airport.icao != userDismissedForIcao &&
                        (currentAirport?.icao != airport.icao || !showWarning)

                    if (shouldShow) {
                        Log.d(
                            "Airspace",
                            "Within Class ${airport.airspaceClass} of ${airport.icao} (${distance / 1000} km)"
                        )
                        currentAirport = airport
                        showWarning = true
                        onAirspaceWarning(buildWarningMessage(airport, distance))
                    }
                } else {
                    if (showWarning || currentAirport != null) {
                        Log.d("Airspace", "Left controlled airspace — alerts can show again on re-entry")
                    }
                    showWarning = false
                    currentAirport = null
                    userDismissedForIcao = null
                }
            }

            delay(30_000)
        }
    }

    if (showWarning && currentAirport != null) {
        val airport = currentAirport!!
        AlertDialog(
            onDismissRequest = {
                Log.d("Airspace", "Warning dismissed")
                userDismissedForIcao = airport.icao
                showWarning = false
            },
            title = { Text("⚠️ Controlled Airspace Alert") },
            text = {
                Text(
                    buildWarningMessage(
                        airport,
                        calculateDistance(
                            currentLocation?.latitude ?: 0.0,
                            currentLocation?.longitude ?: 0.0,
                            airport.latitude,
                            airport.longitude
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Log.d("Airspace", "Warning acknowledged")
                    userDismissedForIcao = airport.icao
                    showWarning = false
                }) {
                    Text("Acknowledge")
                }
            }
        )
    }
}

/** Nearest controlled airport whose airspace volume overlaps the user location. */
private fun findControlledAirspaceAtLocation(
    location: Location,
    airports: List<USAirport>
): Pair<USAirport, Double>? {
    return airports
        .filter { it.airspaceClass in listOf("B", "C", "D") }
        .map { airport ->
            val distance = calculateDistance(
                location.latitude, location.longitude,
                airport.latitude, airport.longitude
            )
            airport to distance
        }
        .filter { (airport, distance) -> distance <= radiusForClass(airport.airspaceClass) }
        .minByOrNull { it.second }
}

private fun radiusForClass(airspaceClass: String?): Double = when (airspaceClass) {
    "B" -> 30_000.0
    "C" -> 20_000.0
    "D" -> 8_000.0
    else -> 0.0
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0].toDouble()
}

private fun buildWarningMessage(airport: USAirport, distance: Double): String {
    val requirements = when (airport.airspaceClass) {
        "B" -> """
            • LAANC approval required (most B airports restrict drones)
            • Maximum altitude varies by grid section
            • Part 107 or TRUST + LAANC required"""
        "C" -> """
            • LAANC approval required
            • Max altitude 400 ft AGL unless authorized
            • Part 107 or TRUST + LAANC required"""
        "D" -> """
            • LAANC approval required
            • Max altitude 400 ft AGL unless authorized
            • Part 107 or TRUST + LAANC required"""
        else -> ""
    }

    val distanceKm = (distance / 1000).roundToInt()
    val distanceMiles = (distance / 1609.34).roundToInt()

    return """
        ⚠️ CONTROLLED AIRSPACE ALERT ⚠️
        
        ${airport.name} (${airport.icao})
        Class ${airport.airspaceClass} Airspace
        Distance: $distanceMiles mi ($distanceKm km)
        
        ⚠️ Check Airspace Map for Your Location ⚠️
        
        Requirements:
        $requirements
        
        Resources:
        • Air Control app for LAANC approval
        • FAA DroneZone
        • 1-844-FLY-MY-UA
    """.trimIndent()
}
