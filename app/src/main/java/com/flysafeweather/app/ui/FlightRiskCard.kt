package com.flysafeweather.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flysafeweather.app.data.*

@Composable
fun FlightRiskCard(
    metarData: MetarData,
    tfrs: List<TfrData>,
    kpIndexData: KpIndexData?,
    gnssData: GnssData?,
    airport: Airport?,
    modifier: Modifier = Modifier
) {
    val riskScore = calculateRiskScore(metarData, tfrs, kpIndexData, gnssData, airport)
    val (riskLevel, riskColor) = getRiskLevelAndColor(riskScore)
    
    // Define background colors with transparency
    val backgroundColor = when {
        riskScore >= 80 -> Color(0x1F4CAF50)  // Green with 12% opacity
        riskScore >= 60 -> Color(0x1FFFA726)  // Orange with 12% opacity
        else -> Color(0x1FF44336)  // Red with 12% opacity
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Flight Risk Assessment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Risk Score: $riskScore/100",
                style = MaterialTheme.typography.bodyLarge,
                color = riskColor
            )
            
            Text(
                text = "Risk Level: $riskLevel",
                style = MaterialTheme.typography.bodyLarge,
                color = riskColor
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Risk Factors:",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                appendRiskFactors(metarData, tfrs, kpIndexData, gnssData, airport)
            }
        }
    }
}

private fun calculateRiskScore(
    metar: MetarData,
    tfrs: List<TfrData>,
    kpIndexData: KpIndexData?,
    gnssData: GnssData?,
    airport: Airport?
): Int {
    var score = 100 // Start with perfect score

    // Calculate air density
    val tempC = (metar.temperatureF - 32) * 5.0/9.0
    val pressureHpa = metar.altimeter * 33.8639  // Convert inHg to hPa
    val airDensity = calculateAirDensity(tempC, pressureHpa)
    val standardDensity = 1.225 // kg/m³ at sea level, 15°C
    val densityAltitude = calculateDensityAltitude(airDensity)
    
    // Air density impact (max deduction: 10 points)
    val densityRatio = airDensity / standardDensity
    when {
        densityRatio < 0.8 -> score -= 10  // Significant performance impact
        densityRatio < 0.9 -> score -= 5   // Moderate performance impact
    }

    // Flight Category (max deduction: 20 points)
    when (metar.flightCategory) {
        FlightCategory.LIFR -> score -= 20  // Low Instrument Flight Rules
        FlightCategory.IFR -> score -= 15   // Instrument Flight Rules
        FlightCategory.MVFR -> score -= 10  // Marginal Visual Flight Rules
        FlightCategory.VFR -> {}            // Visual Flight Rules - no deduction
    }

    // Temperature conditions (max deduction: 15 points)
    val tempF = metar.temperatureF
    if (tempF < 20.0 || tempF > 100.0) {
        score -= 15  // Extreme temperatures
    } else if (tempF < 35.0 || tempF > 95.0) {
        score -= 10   // Challenging temperatures
    }

    // Wind conditions (max deduction: 25 points)
    when {
        metar.windGust > 25 -> score -= 25  // Severe gusts
        metar.windGust > 20 -> score -= 20  // Strong gusts
        metar.windGust > 15 -> score -= 15  // Moderate gusts
        metar.windSpeed > 20 -> score -= 25  // Severe steady wind
        metar.windSpeed > 15 -> score -= 15  // Strong steady wind
        metar.windSpeed > 10 -> score -= 5   // Moderate steady wind
    }

    // Additional deduction for gust spread (max: 10 points)
    if (metar.windGust > 0) {
        val gustSpread = metar.windGust - metar.windSpeed
        when {
            gustSpread > 15 -> score -= 10  // Severe turbulence
            gustSpread > 10 -> score -= 7   // Strong turbulence
            gustSpread > 5 -> score -= 3    // Moderate turbulence
        }
    }

    // Visibility conditions (max deduction: 20 points)
    when {
        metar.visibility < 3.0 -> score -= 20  // Changed: Now unsafe below 3 miles
        metar.visibility < 5.0 -> score -= 5
    }

    // Cloud ceiling (max deduction: 15 points)
    val ceiling = metar.cloudLayers
        .filter { it.coverage in listOf("BKN", "OVC") }
        .minOfOrNull { it.heightFeet } ?: Int.MAX_VALUE
    when {
        ceiling < 500 -> score -= 15
        ceiling < 1000 -> score -= 10
        ceiling < 2000 -> score -= 5
    }

    // Precipitation (max deduction: 10 points)
    if (metar.precipitation.isNotEmpty()) {
        score -= 10
    }

    // Temperature-Dewpoint spread (max deduction: 5 points)
    val spread = metar.temperatureF - metar.dewPointF
    val spreadC = metar.temperature - metar.dewPoint
    if (spread < 2.0) {
        score -= 5
    } else if (spread < 4.0) {
        score -= 3
    }

    // Icing conditions (additional 10 point deduction)
    if (metar.temperatureF < 35.0 && spread < 3.0) {
        score -= 10
    }

    // TFRs (max deduction: 10 points) - Only consider TFRs within 5nm
    val nearbyTfrs = tfrs.filter { tfr ->
        val tfrLat = tfr.coordinates.firstOrNull()?.latitude ?: return@filter false
        val tfrLon = tfr.coordinates.firstOrNull()?.longitude ?: return@filter false
        
        // Get the reference location (current location or airport location)
        val refLat = when {
            metar.latitude != null -> metar.latitude
            airport != null -> airport.latitude
            else -> return@filter false
        }
        
        val refLon = when {
            metar.longitude != null -> metar.longitude
            airport != null -> airport.longitude
            else -> return@filter false
        }
        
        val distance = calculateDistance(refLat, refLon, tfrLat, tfrLon)
        
        // Debug logging
        android.util.Log.d("FlightRiskCard", """
            TFR Distance Calculation:
            Reference Location: ($refLat, $refLon)
            TFR: ${tfr.reason} at ($tfrLat, $tfrLon)
            Distance: $distance nm
            Within 5nm: ${distance <= 5.0}
        """.trimIndent())
        
        distance <= 5.0 // 5 nautical mile radius
    }
    
    if (nearbyTfrs.isNotEmpty()) {
        when {
            nearbyTfrs.any { it.type == TfrType.SECURITY || it.type == TfrType.VIP_PRESIDENTIAL } -> score -= 10
            nearbyTfrs.any { it.type == TfrType.SPACE_OPERATIONS || it.type == TfrType.HAZARDS } -> score -= 7
            else -> score -= 5
        }
    }

    // Controlled Airspace (max deduction: 5 points)
    when (airport?.airspaceClass) {
        "B" -> score -= 5
        "C" -> score -= 3
        "D" -> score -= 2
        else -> {} // No deduction for E or G
    }

    // KP Index (max deduction: 5 points)
    when (kpIndexData?.kpIndex?.toDouble() ?: 0.0) {
        in 5.0..Double.MAX_VALUE -> score -= 5
        in 4.0..5.0 -> score -= 3
        in 3.0..4.0 -> score -= 1
    }

    // GNSS Satellite Status (max deduction: 5 points)
    if (gnssData != null) {
        when {
            !gnssData.hasGnssFix -> score -= 5
            gnssData.satellitesUsed < 6 -> score -= 3
            gnssData.satellitesUsed < 8 -> score -= 1
        }
    }

    // Maximum Flight Height (max deduction: 15 points)
    val maxFlightHeight = when {
        metar.visibility < 3.0 -> 0
        else -> {
            val ceilingHeight = metar.cloudLayers
                .filter { it.coverage in listOf("BKN", "OVC") }
                .minOfOrNull { it.heightFeet }
            maxOf(0, minOf(
                400,  // Part 107 max altitude
                ceilingHeight?.minus(500) ?: 400,  // 500ft buffer below clouds
                tfrs.fold(400) { acc, tfr ->  // Check TFR restrictions
                    val tfrMin = tfr.minAltitude ?: 0
                    if (tfrMin > 0) minOf(acc, tfrMin) else acc
                }
            ))
        }
    }

    when {
        maxFlightHeight == 0 -> score -= 15  // No flight possible
        maxFlightHeight < 100 -> score -= 10 // Severely restricted
        maxFlightHeight < 200 -> score -= 5  // Limited operations
    }

    return score.coerceIn(0, 100)
}

private fun getRiskLevelAndColor(score: Int): Pair<String, Color> {
    return when {
        score >= 80 -> "Low Risk" to Color(0xFF4CAF50)  // Green
        score >= 60 -> "Moderate Risk" to Color(0xFFFFA726)  // Orange
        else -> "High Risk" to Color(0xFFF44336)  // Red
    }
}

@Composable
private fun appendRiskFactors(
    metar: MetarData,
    tfrs: List<TfrData>,
    kpIndexData: KpIndexData?,
    gnssData: GnssData?,
    airport: Airport?
) {
    var hasRiskFactors = false

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Flight Category
        if (metar.flightCategory != FlightCategory.VFR) {
            hasRiskFactors = true
            val (text, color) = when (metar.flightCategory) {
                FlightCategory.LIFR -> "• LIFR Conditions - Flight not recommended for drones" to Color(0xFFF44336)
                FlightCategory.IFR -> "• IFR Conditions - Exercise extreme caution" to Color(0xFFF44336)
                FlightCategory.MVFR -> "• MVFR Conditions - Marginal visual flight conditions" to Color(0xFFFFA726)
                FlightCategory.VFR -> "" to Color(0xFF4CAF50) // This case won't be reached due to the if condition
            }
            Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
        }

        // Temperature
        val tempF = metar.temperatureF
        val tempC = metar.temperature
        if (tempF < 35.0 || tempF > 95.0) {
            hasRiskFactors = true
            val (text, color) = when {
                tempF < 20.0 -> "• Temperature ${tempF.toInt()}°F / ${tempC.toInt()}°C - Extreme cold, high risk for battery and equipment" to Color(0xFFF44336)
                tempF < 35.0 -> "• Temperature ${tempF.toInt()}°F / ${tempC.toInt()}°C - Cold conditions, monitor battery performance" to Color(0xFFFFA726)
                tempF > 100.0 -> "• Temperature ${tempF.toInt()}°F / ${tempC.toInt()}°C - Extreme heat, high risk for equipment" to Color(0xFFF44336)
                tempF > 95.0 -> "• Temperature ${tempF.toInt()}°F / ${tempC.toInt()}°C - Hot conditions, monitor equipment temperature" to Color(0xFFFFA726)
                else -> "• Temperature ${tempF.toInt()}°F / ${tempC.toInt()}°C - Monitor conditions" to Color(0xFF4CAF50)
            }
            Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
        }

        // Wind
        if (metar.windSpeed > 10 || metar.windGust > 0) {
            hasRiskFactors = true
            // METAR wind speeds are always in knots, convert to m/s
            val windSpeedKts = metar.windSpeed
            val windSpeedMps = (windSpeedKts / 1.94384).toInt()
            
            val gustText = if (metar.windGust > 0) {
                val gustKts = metar.windGust
                val gustMps = (gustKts / 1.94384).toInt()
                val gustSpread = metar.windGust - metar.windSpeed
                " gusting ${gustKts}kt (${gustMps}m/s), spread ${gustSpread}kt"
            } else ""
            
            val (text, color) = when {
                metar.windGust > 25 -> "• Wind speed ${windSpeedKts}kt (${windSpeedMps}m/s)${gustText} - Severe gusts, unsafe for flight" to Color(0xFFF44336)
                metar.windGust > 20 -> "• Wind speed ${windSpeedKts}kt (${windSpeedMps}m/s)${gustText} - Strong gusts, flight not recommended" to Color(0xFFF44336)
                metar.windGust > 15 -> "• Wind speed ${windSpeedKts}kt (${windSpeedMps}m/s)${gustText} - Moderate gusts, exercise caution" to Color(0xFFFFA726)
                metar.windSpeed > 20 -> "• Wind speed ${windSpeedKts}kt (${windSpeedMps}m/s)${gustText} - Unsafe for flight" to Color(0xFFF44336)
                metar.windSpeed > 15 -> "• Wind speed ${windSpeedKts}kt (${windSpeedMps}m/s)${gustText} - Marginal conditions" to Color(0xFFFFA726)
                else -> "• Wind speed ${windSpeedKts}kt (${windSpeedMps}m/s)${gustText} - Use caution" to Color(0xFF4CAF50)
            }
            Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
            
            // Add turbulence warning if gust spread is significant
            if (metar.windGust > 0) {
                val gustSpread = metar.windGust - metar.windSpeed
                when {
                    gustSpread > 15 -> Text(
                        text = "  - Severe turbulence likely with ${gustSpread}kt gust spread",
                        color = Color(0xFFF44336),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    gustSpread > 10 -> Text(
                        text = "  - Strong turbulence possible with ${gustSpread}kt gust spread",
                        color = Color(0xFFFFA726),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    gustSpread > 5 -> Text(
                        text = "  - Moderate turbulence possible with ${gustSpread}kt gust spread",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Visibility
        if (metar.visibility < 5.0) {
            hasRiskFactors = true
            val visibilityKm = (metar.visibility * 1.60934).toInt()
            val (text, color) = when {
                metar.visibility < 3.0 -> "• Visibility ${metar.visibility} miles / ${visibilityKm} km - Unsafe for flight" to Color(0xFFF44336)
                else -> "• Visibility ${metar.visibility} miles / ${visibilityKm} km - Use caution" to Color(0xFF4CAF50)
            }
            Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
        }

        // Ceiling
        val ceiling = metar.cloudLayers
            .filter { it.coverage in listOf("BKN", "OVC") }
            .minOfOrNull { it.heightFeet }
        if (ceiling != null && ceiling < 2000) {
            hasRiskFactors = true
            val (text, color) = when {
                ceiling < 500 -> "• Cloud ceiling ${ceiling}ft (${(ceiling * 0.3048).toInt()}m) - Unsafe for flight" to Color(0xFFF44336)
                ceiling < 1000 -> "• Cloud ceiling ${ceiling}ft (${(ceiling * 0.3048).toInt()}m) - Marginal conditions" to Color(0xFFFFA726)
                else -> "• Cloud ceiling ${ceiling}ft (${(ceiling * 0.3048).toInt()}m) - Use caution" to Color(0xFF4CAF50)
            }
            Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
        }

        // Precipitation
        if (metar.precipitation.isNotEmpty()) {
            hasRiskFactors = true
            Text(
                text = "• Present weather: ${metar.precipitation.joinToString(", ")}",
                color = Color(0xFFFFA726),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Temperature-Dewpoint spread
        val spread = metar.temperatureF - metar.dewPointF
        val spreadC = metar.temperature - metar.dewPoint
        if (spread < 4.0) {
            hasRiskFactors = true
            val (text, color) = when {
                metar.temperatureF < 35.0 && spread < 3.0 -> "• DRONE ICING MAY OCCUR - Temperature ${metar.temperatureF.toInt()}°F / ${metar.temperature.toInt()}°C with ${spread.toInt()}°F / ${spreadC.toInt()}°C spread" to Color(0xFFF44336)
                spread < 2.0 -> "• Temperature-dewpoint spread ${spread.toInt()}°F / ${spreadC.toInt()}°C - High risk of fog" to Color(0xFFF44336)
                else -> "• Temperature-dewpoint spread ${spread.toInt()}°F / ${spreadC.toInt()}°C - Possible fog formation" to Color(0xFFFFA726)
            }
            Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
        }

        // TFRs - Only show TFRs within 5nm
        val nearbyTfrs = tfrs.filter { tfr ->
            val tfrLat = tfr.coordinates.firstOrNull()?.latitude ?: return@filter false
            val tfrLon = tfr.coordinates.firstOrNull()?.longitude ?: return@filter false
            
            val refLat = when {
                metar.latitude != null -> metar.latitude
                airport != null -> airport.latitude
                else -> return@filter false
            }
            
            val refLon = when {
                metar.longitude != null -> metar.longitude
                airport != null -> airport.longitude
                else -> return@filter false
            }
            
            val distance = calculateDistance(refLat, refLon, tfrLat, tfrLon)
            distance <= 5.0
        }
        
        if (nearbyTfrs.isNotEmpty()) {
            hasRiskFactors = true
            Text(
                text = "• Active TFRs within 5nm: ${nearbyTfrs.size}",
                color = Color(0xFFF44336),
                style = MaterialTheme.typography.bodyLarge
            )
            nearbyTfrs.firstOrNull { it.type == TfrType.SECURITY || it.type == TfrType.VIP_PRESIDENTIAL }?.let {
                Text(
                    text = "  - High priority TFR: ${it.reason}",
                    color = Color(0xFFF44336),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Controlled Airspace - Only show if current location is within airspace
        if (metar.latitude != null && metar.longitude != null) {
            // Check all nearby airports for controlled airspace
            airport?.let {
                if (it.airspaceClass in listOf("B", "C", "D")) {
                    // Calculate distance from current location to airport
                    val distanceToAirport = calculateDistance(
                        metar.latitude,
                        metar.longitude,
                        it.latitude,
                        it.longitude
                    )
                    
                    // Check if within airspace based on class
                    val isWithinAirspace = when (it.airspaceClass) {
                        "B" -> distanceToAirport <= 30.0  // 30nm radius for Class B
                        "C" -> distanceToAirport <= 10.0  // 10nm radius for Class C
                        "D" -> distanceToAirport <= 4.0   // 4nm radius for Class D
                        else -> false
                    }
                    
                    if (isWithinAirspace) {
                        hasRiskFactors = true
                        val (text, color) = when (it.airspaceClass) {
                            "B" -> "• Operating in Class B airspace - Authorization required" to Color(0xFFF44336)
                            "C" -> "• Operating in Class C airspace - Communication required" to Color(0xFFFFA726)
                            else -> "• Operating in Class D airspace - Communication required" to Color(0xFF4CAF50)
                        }
                        Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // KP Index
        kpIndexData?.let {
            if (it.kpIndex >= 3.0) {
                hasRiskFactors = true
                val (text, color) = when {
                    it.kpIndex >= 5.0 -> "• KP index ${it.kpIndex} - High GNSS interference risk" to Color(0xFFF44336)
                    it.kpIndex >= 4.0 -> "• KP index ${it.kpIndex} - Moderate GNSS interference risk" to Color(0xFFFFA726)
                    else -> "• KP index ${it.kpIndex} - Minor GNSS interference possible" to Color(0xFF4CAF50)
                }
                Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // GNSS Status
        gnssData?.let {
            if (!it.hasGnssFix || it.satellitesUsed < 8) {
                hasRiskFactors = true
                val (text, color) = when {
                    !it.hasGnssFix -> "• No GNSS fix available - Flight not recommended" to Color(0xFFF44336)
                    it.satellitesUsed < 6 -> "• Limited GNSS satellites (${it.satellitesUsed}) - Marginal accuracy" to Color(0xFFFFA726)
                    else -> "• Limited GNSS satellites (${it.satellitesUsed}) - Acceptable accuracy" to Color(0xFF4CAF50)
                }
                Text(text = text, color = color, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Add Air Density section before the Maximum Flight Height section
        val pressureHpa = metar.altimeter * 33.8639
        val airDensity = calculateAirDensity(tempC, pressureHpa)
        val standardDensity = 1.225
        val densityRatio = airDensity / standardDensity
        val densityAltitude = calculateDensityAltitude(airDensity)

        // Always show density altitude, with color-coded safety assessment
        val (densityAltitudeText, densityAltitudeColor) = when {
            densityAltitude > 8000 -> {
                "• Density Altitude: ${densityAltitude.toInt()}ft (${(densityAltitude * 0.3048).toInt()}m) - UNSAFE" to Color(0xFFF44336)
            }
            densityAltitude > 5000 -> {
                "• Density Altitude: ${densityAltitude.toInt()}ft (${(densityAltitude * 0.3048).toInt()}m) - CAUTION" to Color(0xFFFFA726)
            }
            else -> {
                "• Density Altitude: ${densityAltitude.toInt()}ft (${(densityAltitude * 0.3048).toInt()}m) - SAFE" to Color(0xFF4CAF50)
            }
        }
        
        Text(
            text = densityAltitudeText,
            color = densityAltitudeColor,
            style = MaterialTheme.typography.bodyLarge
        )

        when {
            densityRatio < 0.8 -> {
                hasRiskFactors = true
                Text(
                    text = "  - Air Density: ${String.format("%.3f", airDensity)} kg/m³ (${String.format("%.1f", densityRatio * 100)}% of sea level)",
                    color = Color(0xFFF44336),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "  - SIGNIFICANT performance impact - Reduce payload and flight time by 30%",
                    color = Color(0xFFF44336),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            densityRatio < 0.9 -> {
                hasRiskFactors = true
                Text(
                    text = "  - Air Density: ${String.format("%.3f", airDensity)} kg/m³ (${String.format("%.1f", densityRatio * 100)}% of sea level)",
                    color = Color(0xFFFFA726),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "  - MODERATE performance impact - Reduce payload and flight time by 15%",
                    color = Color(0xFFFFA726),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                Text(
                    text = "  - Air Density: ${String.format("%.3f", airDensity)} kg/m³ (${String.format("%.1f", densityRatio * 100)}% of sea level)",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "  - Normal performance - No restrictions",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Maximum Flight Height
        val maxFlightHeight = when {
            metar.visibility < 3.0 -> 0
            else -> {
                val ceilingHeight = metar.cloudLayers
                    .filter { it.coverage in listOf("BKN", "OVC") }
                    .minOfOrNull { it.heightFeet }
                maxOf(0, minOf(
                    400,  // Part 107 max altitude
                    ceilingHeight?.minus(500) ?: 400,  // 500ft buffer below clouds
                    tfrs.fold(400) { acc, tfr ->  // Check TFR restrictions
                        val tfrMin = tfr.minAltitude ?: 0
                        if (tfrMin > 0) minOf(acc, tfrMin) else acc
                    }
                ))
            }
        }

        when {
            maxFlightHeight == 0 -> {
                hasRiskFactors = true
                Text(
                    text = "• Maximum flight height: 0 ft (0 m) - Flight not possible",
                    color = Color(0xFFF44336),  // Red for unsafe
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            maxFlightHeight < 200 -> {
                hasRiskFactors = true
                Text(
                    text = "• Maximum flight height: ${maxFlightHeight}ft (${(maxFlightHeight * 0.3048).toInt()}m) - Limited operations",
                    color = Color(0xFFFFA726),  // Orange for marginal
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                Text(
                    text = "• Maximum flight height: ${maxFlightHeight}ft (${(maxFlightHeight * 0.3048).toInt()}m) - Safe for operations",
                    color = Color(0xFF4CAF50),  // Green for safe
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // If no risk factors
        if (!hasRiskFactors) {
            Text(
                text = "• No significant risk factors identified",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// Update distance calculation function for more accuracy
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    // Return max value if any coordinate is invalid
    if (lat1 == 0.0 && lon1 == 0.0 || lat2 == 0.0 && lon2 == 0.0) {
        return Double.MAX_VALUE
    }

    val R = 3440.065 // Earth's radius in nautical miles
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val latDelta = Math.toRadians(lat2 - lat1)
    val lonDelta = Math.toRadians(lon2 - lon1)

    val a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2) +
            Math.cos(lat1Rad) * Math.cos(lat2Rad) *
            Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    
    val distance = R * c
    
    // Debug logging
    android.util.Log.d("FlightRiskCard", """
        Distance Calculation Details:
        Point 1: ($lat1, $lon1)
        Point 2: ($lat2, $lon2)
        Distance: $distance nm
    """.trimIndent())
    
    return distance
}

// Add these new functions for air density calculations
private fun calculateAirDensity(tempC: Double, pressureHpa: Double): Double {
    android.util.Log.d("AirDensity", """
        === Air Density Calculation Start ===
        Input temperature: ${tempC}°C
        Input pressure: ${pressureHpa} hPa
    """.trimIndent())

    // Validate inputs
    if (pressureHpa <= 0) {
        android.util.Log.d("AirDensity", "Invalid pressure (≤ 0), returning standard density")
        return 1.225
    }
    if (tempC < -89.2 || tempC > 56.7) {
        android.util.Log.d("AirDensity", "Temperature outside world records, returning standard density")
        return 1.225
    }
    
    val R = 287.05 // Gas constant for dry air (J/(kg·K))
    val tempK = tempC + 273.15 // Convert to Kelvin
    val density = (pressureHpa * 100) / (R * tempK) // Result in kg/m³
    
    android.util.Log.d("AirDensity", """
        Calculation steps:
        1. Temperature in Kelvin: ${tempK}K
        2. Pressure in Pascals: ${pressureHpa * 100} Pa
        3. Raw calculated density: ${density} kg/m³
        4. Bounded density: ${density.coerceIn(0.1, 1.5)} kg/m³
    """.trimIndent())
    
    // Validate result is within reasonable bounds (0.1 to 1.5 kg/m³)
    return density.coerceIn(0.1, 1.5)
}

private fun calculateDensityAltitude(density: Double): Double {
    android.util.Log.d("DensityAltitude", """
        === Density Altitude Calculation Start ===
        Input density: ${density} kg/m³
        Standard density: 1.225 kg/m³
    """.trimIndent())
    
    val standardDensity = 1.225 // kg/m³ at sea level, 15°C
    
    // Validate input
    if (density <= 0 || density > 1.5) {
        android.util.Log.d("DensityAltitude", "Invalid density value, returning sea level")
        return 0.0
    }
    
    // Calculate pressure ratio using density ratio
    val densityRatio = density / standardDensity
    val boundedRatio = densityRatio.coerceIn(0.1, 1.2)
    val pressureRatio = Math.pow(boundedRatio, 1.0/5.2561)
    val altitude = 145366.45 * (1 - pressureRatio)
    val finalAltitude = altitude.coerceIn(0.0, 30000.0)
    
    android.util.Log.d("DensityAltitude", """
        Calculation steps:
        1. Density ratio: ${densityRatio}
        2. Bounded ratio: ${boundedRatio}
        3. Pressure ratio: ${pressureRatio}
        4. Raw altitude: ${altitude} ft
        5. Final bounded altitude: ${finalAltitude} ft
    """.trimIndent())
    
    return finalAltitude
} 
