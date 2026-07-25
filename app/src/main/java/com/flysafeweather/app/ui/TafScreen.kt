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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flysafeweather.app.data.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun NwsForecastScreen(
    tafService: TafService,
    weatherCache: WeatherCache,
    isOnline: Boolean,
    currentAirport: String,
    onBackClick: () -> Unit
) {
    var tafData by remember { mutableStateOf<TafData?>(null) }
    var nwsForecasts by remember { mutableStateOf<List<HourlyForecastService.HourlyForecast>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Fetch TAF and NWS data when the screen loads
    LaunchedEffect(currentAirport, isOnline) {
        isLoading = true
        error = null
        try {
            // Get airport coordinates from AirportService
            val airportService = AirportService(context)
            val airport = airportService.findAirport(currentAirport)
            
            if (airport != null) {
                try {
                    val hourlyForecastService = HourlyForecastService(context)
                    val nwsData = hourlyForecastService.fetchHourlyForecast(
                        airport.latitude,
                        airport.longitude
                    )
                    // Take exactly 24 forecasts
                    nwsForecasts = nwsData.take(24)
                    Log.d("NwsForecastScreen", "Fetched ${nwsForecasts.size} NWS forecasts")
                } catch (e: Exception) {
                    Log.e("NwsForecastScreen", "Error fetching NWS forecast: ${e.message}", e)
                    error = "Error fetching NWS forecast: ${e.message}"
                }
            } else {
                error = "Airport not found: $currentAirport"
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
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                nwsForecasts.isEmpty() -> {
                    Text(
                        text = "No forecast data available",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    Text(
                        text = "Next 24 hours of detailed weather from the National Weather Service",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(nwsForecasts) { forecast ->
                            NwsForecastCard(forecast = forecast)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NwsForecastCard(forecast: HourlyForecastService.HourlyForecast) {
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
                    "${forecast.windDirection}° (${degreesToCardinal(forecast.windDirection)}) at ${forecast.windSpeed}kt (${windSpeedMps}m/s)" +
                    if (forecast.windGust > 0) " gusting ${forecast.windGust}kt (${windGustMps}m/s)" else ""
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

private fun degreesToCardinal(degrees: Int): String {
    val directions = arrayOf(
        "N", "NNE", "NE", "ENE", 
        "E", "ESE", "SE", "SSE", 
        "S", "SSW", "SW", "WSW", 
        "W", "WNW", "NW", "NNW"
    )
    val index = ((degrees + 11.25) / 22.5).toInt() % 16
    return directions[index]
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