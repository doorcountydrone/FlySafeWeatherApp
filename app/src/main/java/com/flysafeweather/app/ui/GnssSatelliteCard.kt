package com.flysafeweather.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flysafeweather.app.data.GnssData
import android.util.Log

@Composable
fun GnssSatelliteCard(
    gnssData: GnssData?,
    modifier: Modifier = Modifier
) {
    Log.d("GnssSatelliteCard", """
        Card Update:
        - Satellites in view: ${gnssData?.satellitesInView ?: 0}
        - Satellites used: ${gnssData?.satellitesUsed ?: 0}
        - Has fix: ${gnssData?.hasGnssFix ?: false}
        Constellation breakdown:
        - GPS: ${gnssData?.gpsSatellites ?: 0}
        - GLONASS: ${gnssData?.glonassSatellites ?: 0}
        - Galileo: ${gnssData?.galileoSatellites ?: 0}
        - BeiDou: ${gnssData?.beidouSatellites ?: 0}
        - QZSS: ${gnssData?.qzssSatellites ?: 0}
        - SBAS: ${gnssData?.sbasSatellites ?: 0}
    """.trimIndent())
    
    // Determine background color based on GNSS status
    val backgroundColor = when {
        gnssData?.hasGnssFix == true && (gnssData.satellitesUsed >= 8) -> 
            Color(0x1F4CAF50)  // Green with 12% opacity - Good fix with 8+ satellites
        gnssData?.hasGnssFix == true && (gnssData.satellitesUsed >= 6) -> 
            Color(0x1FFFA726)  // Orange with 12% opacity - Marginal fix with 6-7 satellites
        else -> 
            Color(0x1FF44336)  // Red with 12% opacity - Poor/no fix or <6 satellites
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
                text = "GNSS Satellites (Global Navigation Satellite System)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            val hasValidFix = (gnssData?.satellitesUsed ?: 0) >= 4
            val textColor = when {
                gnssData?.hasGnssFix == true && (gnssData.satellitesUsed >= 8) -> 
                    Color(0xFF4CAF50)  // Green - Good fix
                gnssData?.hasGnssFix == true && (gnssData.satellitesUsed >= 6) -> 
                    Color(0xFFFFA726)  // Orange - Marginal fix
                else -> 
                    Color(0xFFF44336)  // Red - Poor/no fix
            }
            
            Text(
                text = buildString {
                    append("Total satellites in view: ${gnssData?.satellitesInView ?: 0}\n")
                    append("Satellites used in fix: ${gnssData?.satellitesUsed ?: 0}\n")
                    append("Fix status: ${if (hasValidFix) "Valid" else "No Fix"}\n\n")
                    append("Constellation breakdown:\n")
                    append("• GPS (US): ${gnssData?.gpsSatellites ?: 0}\n")
                    append("• GLONASS (Russia): ${gnssData?.glonassSatellites ?: 0}\n")
                    append("• Galileo (Europe): ${gnssData?.galileoSatellites ?: 0}\n")
                    append("• BeiDou (China): ${gnssData?.beidouSatellites ?: 0}\n")

                    if ((gnssData?.qzssSatellites ?: 0) > 0) {
                        append("• QZSS: ${gnssData?.qzssSatellites}\n")
                    }
                    if ((gnssData?.sbasSatellites ?: 0) > 0) {
                        append("• SBAS: ${gnssData?.sbasSatellites}")
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
} 
