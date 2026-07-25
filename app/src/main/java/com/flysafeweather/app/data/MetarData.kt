package com.flysafeweather.app.data

import android.util.Log

data class MetarData(
    val stationId: String = "",
    val windSpeed: Int = 0,
    val windGust: Int = 0,
    val windDirection: Int = 0,
    val visibility: Double = 0.0,
    val temperature: Double = 0.0,
    val dewPoint: Double = 0.0,
    val altimeter: Double = 0.0,
    val cloudLayers: List<CloudLayer> = emptyList(),
    val precipitation: List<String> = emptyList(),
    val sunriseSunset: SunriseSunset? = null,
    val rawText: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val temperatureF: Double
        get() = (temperature * 9.0/5.0) + 32.0

    val dewPointF: Double
        get() {
            val result = (dewPoint * 9.0/5.0) + 32.0
            Log.d("MetarData", "dewPointF calculation: dewPoint=$dewPoint, result=$result")
            return result
        }

    val cloudLayersText: String
        get() {
            if (cloudLayers.isEmpty()) return "CLR"
            
            return cloudLayers
                .sortedBy { it.heightFeet }
                .joinToString(" ") { layer ->
                    if (layer.coverage == "CLR") {
                        "CLR"
                    } else {
                        "${layer.coverage} ${layer.heightFeet}ft (${(layer.heightFeet * 0.3048).toInt()}m)"
                    }
                }
        }

    val isSafeToFly: Boolean
        get() = flightSafety == FlightSafety.SAFE
    
    val flightSafety: FlightSafety
        get() = when {
            // Unsafe conditions
            windSpeed > 20 || 
            visibility < 3.0 ||  // Red only when less than 3 miles
            cloudLayers.any { it.coverage in listOf("OVC", "BKN") && it.heightFeet < 1000 } -> 
                FlightSafety.UNSAFE
            
            // Marginal conditions
            windSpeed > 15 || 
            visibility in 3.0..4.99 ||  // Yellow between 3 and 5 miles
            cloudLayers.any { it.coverage in listOf("OVC", "BKN") && it.heightFeet < 2000 } -> 
                FlightSafety.MARGINAL
            
            // Safe conditions
            else -> FlightSafety.SAFE
        }

    val precipitationDescription: String
        get() = when {
            precipitation.isEmpty() -> "None"
            else -> precipitation.joinToString(", ")
        }

    val flightCategory: FlightCategory
        get() {
            Log.d("FlightCategory", """
                === Flight Category Calculation ===
                Station: $stationId
                Visibility: $visibility miles
                Cloud Layers: ${cloudLayersText}
                Raw METAR: $rawText
            """.trimIndent())

            // Compute ceiling using BKN/OVC/VV only (FEW/SCT do not form a ceiling)
            val ceiling = cloudLayers
                .filter { it.coverage in listOf("BKN", "OVC", "VV") }
                .minOfOrNull { it.heightFeet }
            
            Log.d("FlightCategory", """
                Ceiling calculation:
                - Lowest ceiling: ${ceiling ?: "None"} ft
                - All layers: ${cloudLayers.map { "${it.coverage} ${it.heightFeet}ft" }}
            """.trimIndent())

            // If no ceiling, classify purely by visibility per FAA guidance
            if (ceiling == null) {
                val cat = when {
                    visibility < 1.0 -> FlightCategory.LIFR
                    visibility < 3.0 -> FlightCategory.IFR
                    visibility < 5.0 -> FlightCategory.MVFR
                    else -> FlightCategory.VFR
                }
                Log.d("FlightCategory", "No ceiling. Category by visibility only: $cat")
                return cat
            }

            val category = when {
                ceiling != null && ceiling < 500 || visibility < 1.0 -> {
                    Log.d("FlightCategory", "LIFR - Ceiling < 500ft or Visibility < 1mi")
                    FlightCategory.LIFR
                }
                ceiling != null && ceiling in 500..999 || visibility in 1.0..2.99 -> {
                    Log.d("FlightCategory", "IFR - Ceiling 500-999ft or Visibility 1-3mi")
                    FlightCategory.IFR
                }
                ceiling != null && ceiling in 1000..2999 || visibility in 3.0..4.99 -> {
                    Log.d("FlightCategory", "MVFR - Ceiling 1000-2999ft or Visibility 3-5mi")
                    FlightCategory.MVFR
                }
                else -> {
                    Log.d("FlightCategory", "VFR - Ceiling >= 3000ft and Visibility >= 5mi")
                    FlightCategory.VFR
                }
            }

            Log.d("FlightCategory", "Final category: $category")
            return category
        }
}

enum class FlightSafety {
    SAFE, MARGINAL, UNSAFE
}

enum class FlightCategory {
    VFR, MVFR, IFR, LIFR
} 
