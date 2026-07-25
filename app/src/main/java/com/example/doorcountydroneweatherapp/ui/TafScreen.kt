package com.example.doorcountydroneweatherapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flysafeweather.app.data.TafData
import com.flysafeweather.app.data.TafService
import com.flysafeweather.app.data.WeatherCache
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TafScreen(
    tafService: TafService,
    weatherCache: WeatherCache,
    isOnline: Boolean,
    currentAirport: String,
    onBackClick: () -> Unit
) {
    var tafData by remember { mutableStateOf<TafData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hourlyForecasts by remember { mutableStateOf<List<HourlyForecast>>(emptyList()) }

    // Fetch TAF data when the screen loads
    LaunchedEffect(currentAirport, isOnline) {
        isLoading = true
        try {
            tafData = if (isOnline) {
                tafService.fetchTaf(currentAirport)?.also {
                    weatherCache.cacheTafData(it)
                }
            } else {
                weatherCache.getCachedTafData()
            }
            
            // Generate hourly forecasts from TAF data
            tafData?.let { taf ->
                hourlyForecasts = generateHourlyForecasts(taf)
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    "24-Hour TAF Forecast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                // Empty box for alignment
                Box(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                tafData == null -> {
                    Text(
                        text = "No TAF data available",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    // Raw TAF text
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Raw TAF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tafData?.rawText ?: "Not available",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Hourly forecast list
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hourlyForecasts) { forecast ->
                            HourlyForecastCard(forecast = forecast)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(forecast: HourlyForecast) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (forecast.flightCategory) {
                "VFR" -> Color(0xFF4CAF50).copy(alpha = 0.1f)  // Green
                "MVFR" -> Color(0xFF2196F3).copy(alpha = 0.1f) // Blue
                "IFR" -> Color(0xFFF44336).copy(alpha = 0.1f)  // Red
                "LIFR" -> Color(0xFF9C27B0).copy(alpha = 0.1f) // Purple
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Hour ${forecast.hour}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = forecast.time,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Weather conditions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    WeatherInfoRow("Flight Category", forecast.flightCategory)
                    WeatherInfoRow("Wind", forecast.wind)
                    WeatherInfoRow("Visibility", forecast.visibility)
                    WeatherInfoRow("Clouds", forecast.clouds)
                    if (forecast.weather.isNotEmpty()) {
                        WeatherInfoRow("Weather", forecast.weather)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

data class HourlyForecast(
    val hour: Int,
    val time: String,
    val flightCategory: String,
    val wind: String,
    val visibility: String,
    val clouds: String,
    val weather: String
)

private fun generateHourlyForecasts(taf: TafData): List<HourlyForecast> {
    val forecasts = mutableListOf<HourlyForecast>()
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    val now = LocalDateTime.now(ZoneId.systemDefault())
    
    // Generate 24 hourly forecasts
    for (hour in 0..23) {
        val forecastTime = now.plusHours(hour.toLong())
        
        // Find the applicable TAF period for this hour
        val applicablePeriod = taf.periods.find { period ->
            forecastTime.isAfter(period.startTime) && 
            forecastTime.isBefore(period.endTime)
        } ?: continue

        forecasts.add(
            HourlyForecast(
                hour = hour,
                time = forecastTime.format(formatter),
                flightCategory = applicablePeriod.flightCategory,
                wind = "${applicablePeriod.windDirection}° at ${applicablePeriod.windSpeed}kt" +
                    if (applicablePeriod.windGust > 0) " (gusting ${applicablePeriod.windGust}kt)" else "",
                visibility = "${applicablePeriod.visibility} miles",
                clouds = applicablePeriod.clouds.joinToString(", ") { 
                    "${it.coverage} at ${it.heightFeet}ft" 
                },
                weather = applicablePeriod.weather.joinToString(", ")
            )
        )
    }
    
    return forecasts
} 