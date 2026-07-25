package com.flysafeweather.app.ui

import android.location.Location
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.flysafeweather.app.data.TfrData
import com.flysafeweather.app.data.TfrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

private const val TAG = "TFR_DEBUG"
private const val CHECK_INTERVAL = 900_000L // 15 minutes

@Composable
fun TFRMonitor(
    currentLocation: Location?,
    tfrService: TfrService,
    radiusNm: Int = TfrService.DEFAULT_TFR_RADIUS_NM,
    onTFRWarning: (String) -> Unit
) {
    var showWarning by remember { mutableStateOf(false) }
    var nearbyTfr by remember { mutableStateOf<TfrData?>(null) }

    LaunchedEffect(currentLocation) {
        val location = currentLocation ?: return@LaunchedEffect
        while (isActive) {
            try {
                val tfrs = withContext(Dispatchers.IO) {
                    tfrService.fetchTfrs(location.latitude, location.longitude, radiusNm = radiusNm)
                }
                val inside = tfrs.firstOrNull { isLocationNearTfr(location, it) }
                if (inside != null && !showWarning) {
                    nearbyTfr = inside
                    showWarning = true
                    onTFRWarning(buildTfrWarningMessage(inside, location))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TFRs", e)
            }
            delay(CHECK_INTERVAL)
        }
    }

    Column {
        if (showWarning && nearbyTfr != null && currentLocation != null) {
            AlertDialog(
                onDismissRequest = { showWarning = false },
                title = { Text("TFR Alert") },
                text = {
                    Text(buildTfrWarningMessage(nearbyTfr!!, currentLocation!!))
                },
                confirmButton = {
                    TextButton(onClick = { showWarning = false }) {
                        Text("Acknowledge")
                    }
                }
            )
        }
    }
}

private fun isLocationNearTfr(location: Location, tfr: TfrData): Boolean {
    if (tfr.coordinates.size < 3) return false
    if (pointInPolygon(location.latitude, location.longitude, tfr.coordinates)) return true

    val centroid = centroidOf(tfr.coordinates)
    val results = FloatArray(1)
    Location.distanceBetween(
        location.latitude, location.longitude,
        centroid.latitude, centroid.longitude,
        results
    )
    // Warn if within ~5 nm of TFR boundary centroid (approximate for large polygons)
    return results[0] <= 5 * 1852
}

private fun pointInPolygon(lat: Double, lon: Double, polygon: List<com.google.android.gms.maps.model.LatLng>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val yi = polygon[i].latitude
        val xi = polygon[i].longitude
        val yj = polygon[j].latitude
        val xj = polygon[j].longitude
        if ((yi > lat) != (yj > lat) &&
            lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun centroidOf(points: List<com.google.android.gms.maps.model.LatLng>): com.google.android.gms.maps.model.LatLng {
    var latSum = 0.0
    var lonSum = 0.0
    for (p in points) {
        latSum += p.latitude
        lonSum += p.longitude
    }
    return com.google.android.gms.maps.model.LatLng(latSum / points.size, lonSum / points.size)
}

private fun buildTfrWarningMessage(tfr: TfrData, location: Location): String {
    val centroid = centroidOf(tfr.coordinates)
    val results = FloatArray(1)
    Location.distanceBetween(
        location.latitude, location.longitude,
        centroid.latitude, centroid.longitude,
        results
    )
    val distanceNm = (results[0] / 1852).toInt()

    val legalSnippet = tfr.legal.take(400).let { snippet ->
        if (tfr.legal.length > 400) "$snippet…" else snippet
    }

    return """
        TFR Alert: You are within ${distanceNm}nm of an active TFR

        Type: ${tfr.type.name.replace('_', ' ')}
        Reason: ${tfr.reason}
        ${if (tfr.notamKey.isNotBlank()) "NOTAM: ${tfr.notamKey}\n" else ""}
        ${if (tfr.title.isNotBlank()) "${tfr.title}\n" else ""}
        ${if (legalSnippet.isNotBlank()) "$legalSnippet\n" else tfr.notamText}

        NO DRONE OPERATIONS PERMITTED IN THIS AREA
        Open the TFR map for full NOTAM details or check B4UFLY.
    """.trimIndent()
}
