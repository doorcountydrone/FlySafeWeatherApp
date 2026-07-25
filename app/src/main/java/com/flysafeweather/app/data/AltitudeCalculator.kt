package com.flysafeweather.app.data

import android.location.Location
import kotlin.math.roundToInt

class AltitudeCalculator {
    companion object {
        // Regional geoid height data (in meters) based on NGS GEOID12B model
        // Values are negative because the geoid is below the WGS84 ellipsoid in the US
        private val GEOID_REGIONS = listOf(
            GeoidRegion(25.0, 50.0, -130.0, -110.0, -22.0),  // Pacific Northwest
            GeoidRegion(25.0, 50.0, -110.0, -100.0, -18.0),  // Rocky Mountains
            GeoidRegion(25.0, 50.0, -100.0, -90.0, -28.0),   // Central Plains
            GeoidRegion(25.0, 50.0, -90.0, -80.0, -32.0),    // Great Lakes
            GeoidRegion(25.0, 50.0, -80.0, -65.0, -34.0),    // Northeast
            GeoidRegion(25.0, 35.0, -120.0, -110.0, -26.0),  // Southwest
            GeoidRegion(25.0, 35.0, -100.0, -80.0, -30.0),   // Southeast
            GeoidRegion(20.0, 25.0, -160.0, -155.0, -8.0),   // Hawaii
            GeoidRegion(55.0, 72.0, -165.0, -140.0, -14.0)   // Alaska
        )

        private fun getGeoidHeight(latitude: Double, longitude: Double): Double {
            // Find the region containing this location
            val region = GEOID_REGIONS.find { region ->
                latitude >= region.minLat && latitude <= region.maxLat &&
                longitude >= region.minLon && longitude <= region.maxLon
            }

            // Return the geoid height for the region, or a reasonable default
            return region?.geoidHeight ?: -24.0
        }

        fun calculateMSLAltitude(location: Location): Double {
            // Get the appropriate geoid height for this location
            val geoidHeight = getGeoidHeight(location.latitude, location.longitude)
            
            // GPS altitude is above WGS84 ellipsoid
            val gpsAltitude = location.altitude
            
            // Convert to MSL by subtracting geoid height
            val mslAltitude = gpsAltitude - geoidHeight
            
            // Convert to feet and round to nearest foot
            return (mslAltitude * 3.28084).roundToInt().toDouble()
        }

        fun getAltitudeText(location: Location): String {
            val mslAltitude = calculateMSLAltitude(location)
            val metersRounded = (mslAltitude / 3.28084).roundToInt()
            return "${mslAltitude.roundToInt()} ft / $metersRounded m MSL"
        }

        fun getVerticalAccuracyText(location: Location): String {
            return if (location.hasVerticalAccuracy()) {
                val accuracyFeet = (location.verticalAccuracyMeters * 3.28084).roundToInt()
                "±$accuracyFeet ft"
            } else {
                "Accuracy unknown"
            }
        }
    }
}

// Data class to hold geoid region information
private data class GeoidRegion(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val geoidHeight: Double
) 