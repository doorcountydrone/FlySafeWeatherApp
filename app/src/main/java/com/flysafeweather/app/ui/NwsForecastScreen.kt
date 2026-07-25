package com.flysafeweather.app.ui

import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flysafeweather.app.data.HourlyForecastService
import com.flysafeweather.app.data.AirportService
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun NwsForecastScreen(
    latitude: Double,
    longitude: Double,
    currentAirport: String,
    onBackClick: () -> Unit
) {
    var forecasts by remember { mutableStateOf<List<HourlyForecastService.HourlyForecast>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(latitude, longitude, currentAirport) {
        isLoading = true
        error = null
        try {
            val hourlyForecastService = HourlyForecastService(context)
            val airportService = AirportService(context)
            
            // Get airport coordinates if we don't have them
            val airport = if (latitude == 0.0 && longitude == 0.0) {
                airportService.findAirport(currentAirport)
            } else null

            val forecastData = hourlyForecastService.fetchHourlyForecast(
                latitude = airport?.latitude ?: latitude,
                longitude = airport?.longitude ?: longitude,
                icaoCode = currentAirport  // Always pass the ICAO code
            )
            
            forecasts = forecastData.take(24)
            if (forecasts.isEmpty()) {
                error = "No forecast data available"
            }
        } catch (e: Exception) {
            error = when {
                e.message?.contains("HTTP response code: 404") == true -> 
                    "Weather service does not provide forecasts for this location"
                e.message?.contains("HTTP response code: 429") == true -> 
                    "Too many requests to weather service, please try again later"
                e.message?.contains("HTTP response code: 5") == true -> 
                    "Weather service is currently unavailable"
                e.message?.contains("Unable to resolve host") == true -> 
                    "No internet connection available"
                else -> "Error fetching forecast: ${e.message}"
            }
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "24-Hour Weather Forecast",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentAirport,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                forecasts.isEmpty() -> {
                    Text(
                        text = "No forecast data available",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    Text(
                        text = if (currentAirport.startsWith("K")) {
                            "Next 24 hours of detailed weather from the National Weather Service"
                        } else {
                            "Next 24 hours of detailed weather forecast"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(forecasts) { forecast ->
                            NwsHourlyForecastCard(forecast = forecast)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NwsHourlyForecastCard(forecast: HourlyForecastService.HourlyForecast) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = forecast.time.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Column {
                // Convert Fahrenheit to Celsius
                val tempC = (forecast.temperature - 32) * 5/9
                WeatherInfoRow("Temperature", "%d°F / %.1f°C".format(forecast.temperature.toInt(), tempC))
                
                // Convert knots to m/s (1 knot = 0.514444 m/s)
                val windSpeedMps = (forecast.windSpeed * 0.514444).roundToInt()
                val windGustMps = if (forecast.windGust > 0) (forecast.windGust * 0.514444).roundToInt() else 0
                
                WeatherInfoRow(
                    "Wind",
                    "${forecast.windDirection}° at ${forecast.windSpeed}kt/${windSpeedMps}m/s" +
                    if (forecast.windGust > 0) " gusting ${forecast.windGust}kt/${windGustMps}m/s" else ""
                )
                WeatherInfoRow("Cloud Cover", "${forecast.cloudCover}%")
                WeatherInfoRow("Precipitation Chance", "${forecast.precipitation}%")
                WeatherInfoRow("Relative Humidity", "${forecast.relativeHumidity}%")
                if (forecast.conditions.isNotEmpty()) {
                    WeatherInfoRow("Weather", forecast.conditions.joinToString(", "))
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