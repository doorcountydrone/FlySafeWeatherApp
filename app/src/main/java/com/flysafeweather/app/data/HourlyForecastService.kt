package com.flysafeweather.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round

class HourlyForecastService(private val context: Context) {
    companion object {
        private const val WEATHER_API_KEY = "001866a7068448fc9b105521252202"
        private const val TAG = "HourlyForecastService"
    }
    
    data class HourlyForecast(
        val time: LocalDateTime,
        val temperature: Double,
        val windSpeed: Int,
        val windDirection: Int,
        val windGust: Int,
        val cloudCover: Int,
        val precipitation: Double,
        val relativeHumidity: Int,
        val conditions: List<String>
    )

    suspend fun fetchHourlyForecast(latitude: Double, longitude: Double, icaoCode: String? = null): List<HourlyForecast> = withContext(Dispatchers.IO) {
        try {
            // First try NWS API for US locations (roughly within US bounds)
            if (icaoCode?.startsWith("K") == true || isLocationInUS(latitude, longitude)) {
                try {
                    return@withContext fetchNWSForecast(latitude, longitude)
                } catch (e: Exception) {
                    Log.w(TAG, "NWS API failed, falling back to WeatherAPI", e)
                }
            }
            
            // For non-US locations or if NWS fails, use WeatherAPI
            return@withContext fetchWeatherAPIForecast(latitude, longitude, icaoCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching forecast: ${e.message}", e)
            throw e
        }
    }

    private fun isLocationInUS(lat: Double, lon: Double): Boolean {
        // Rough bounds for continental US, Alaska, and Hawaii
        return (lat in 24.0..50.0 && lon in -125.0..-66.0) || // Continental US
               (lat in 51.0..72.0 && lon in -169.0..-129.0) || // Alaska
               (lat in 18.0..23.0 && lon in -161.0..-154.0)    // Hawaii
    }

    private suspend fun fetchWeatherAPIForecast(latitude: Double, longitude: Double, icaoCode: String? = null): List<HourlyForecast> {
        val location = if (icaoCode != null) {
            // Use METAR format for airport codes
            "metar:$icaoCode"
        } else {
            "$latitude,$longitude"
        }
        
        val url = "https://api.weatherapi.com/v1/forecast.json?key=$WEATHER_API_KEY&q=$location&days=1&aqi=no"
        Log.d(TAG, "Fetching WeatherAPI forecast for location: $location")
        
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("Accept", "application/json")
        
        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "WeatherAPI Response: $response")  // Log the full response
            val json = JSONObject(response)
            
            // Check if there's an error
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                throw Exception(error.getString("message"))
            }
            
            // Verify we got the correct location
            val locationData = json.getJSONObject("location")
            val returnedLat = locationData.getDouble("lat")
            val returnedLon = locationData.getDouble("lon")
            val returnedName = locationData.getString("name")
            Log.d(TAG, "WeatherAPI returned location: $returnedName ($returnedLat, $returnedLon)")
            
            // If we're using an airport code and the coordinates are too far from what we expect
            if (icaoCode != null) {
                try {
                    val airportService = AirportService(context)
                    val airport = airportService.findAirport(icaoCode)
                    if (airport != null) {
                        val distance = calculateDistance(airport.latitude, airport.longitude, returnedLat, returnedLon)
                        if (distance > 50) { // If more than 50km away, probably wrong location
                            throw Exception("Weather API returned incorrect location for $icaoCode")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify airport location: ${e.message}")
                }
            }
            
            val forecast = json.getJSONObject("forecast")
            val forecastDay = forecast.getJSONArray("forecastday").getJSONObject(0)
            val hours = forecastDay.getJSONArray("hour")
            
            val forecasts = mutableListOf<HourlyForecast>()
            
            for (i in 0 until hours.length()) {
                val hour = hours.getJSONObject(i)
                val condition = hour.getJSONObject("condition")
                
                // Parse time
                val time = LocalDateTime.parse(
                    hour.getString("time"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )
                
                // WeatherAPI provides temperature in Celsius
                val tempC = hour.getDouble("temp_c")
                val tempF = (tempC * 9/5) + 32
                
                Log.d(TAG, "Hour $i - Time: $time, Temp C: $tempC, Temp F: $tempF")  // Log temperature values
                
                // Convert wind speed from kph to knots (1 kph = 0.539957 knots)
                val windSpeedKph = hour.getDouble("wind_kph")
                val windSpeedKts = (windSpeedKph * 0.539957).roundToInt()
                
                // Convert gust from kph to knots
                val windGustKph = hour.getDouble("gust_kph")
                val windGustKts = (windGustKph * 0.539957).roundToInt()
                
                forecasts.add(
                    HourlyForecast(
                        time = time,
                        temperature = tempF,
                        windSpeed = windSpeedKts,
                        windDirection = hour.getInt("wind_degree"),
                        windGust = windGustKts,
                        cloudCover = hour.getInt("cloud"),
                        precipitation = hour.getDouble("chance_of_rain"),
                        relativeHumidity = hour.getInt("humidity"),
                        conditions = listOf(condition.getString("text"))
                    )
                )
            }
            
            return forecasts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching WeatherAPI forecast: ${e.message}", e)
            throw Exception("Error fetching forecast for $location: ${e.message}")
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private suspend fun fetchNWSForecast(latitude: Double, longitude: Double): List<HourlyForecast> {
        Log.d(TAG, "Starting forecast fetch for coordinates: $latitude, $longitude")
        
        // First, get the forecast grid endpoint
        val pointsUrl = "https://api.weather.gov/points/$latitude,$longitude"
        Log.d(TAG, "Fetching points data from: $pointsUrl")
        
        // Add User-Agent header as required by NWS API
        val connection = URL(pointsUrl).openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("User-Agent", "(Door County Drone Weather App, contact@doorcountydrone.app)")
        connection.setRequestProperty("Accept", "application/json")
        
        val pointsResponse = connection.inputStream.bufferedReader().use { it.readText() }
        Log.d(TAG, "Points API Response: $pointsResponse")
        
        val pointsJson = JSONObject(pointsResponse)
        val forecastGridUrl = pointsJson.getJSONObject("properties").getString("forecastGridData")
        Log.d(TAG, "Got forecast grid URL: $forecastGridUrl")

        // Then fetch the detailed forecast data
        Log.d(TAG, "Fetching forecast data...")
        val forecastConnection = URL(forecastGridUrl).openConnection() as java.net.HttpURLConnection
        forecastConnection.setRequestProperty("User-Agent", "(Door County Drone Weather App, contact@doorcountydrone.app)")
        forecastConnection.setRequestProperty("Accept", "application/json")
        
        val forecastResponse = forecastConnection.inputStream.bufferedReader().use { it.readText() }
        val forecastJson = JSONObject(forecastResponse)
        val properties = forecastJson.getJSONObject("properties")

        // Get all the forecast components
        val temperatureObj = properties.getJSONObject("temperature")
        val temperatures = temperatureObj.getJSONArray("values")
        
        val windSpeedObj = properties.getJSONObject("windSpeed")
        val windSpeeds = windSpeedObj.getJSONArray("values")
        
        val windGustObj = properties.getJSONObject("windGust")
        val windGusts = windGustObj.getJSONArray("values")
        
        val windDirections = properties.getJSONObject("windDirection").getJSONArray("values")
        val skyCover = properties.getJSONObject("skyCover").getJSONArray("values")
        val precipitation = properties.getJSONObject("probabilityOfPrecipitation").getJSONArray("values")
        val relativeHumidity = properties.getJSONObject("relativeHumidity").getJSONArray("values")
        val weatherConditions = properties.getJSONObject("weather").getJSONArray("values")

        // Process the next 24 hours of forecasts
        val forecasts = mutableListOf<HourlyForecast>()
        val now = Instant.now()
        
        for (hour in 0..23) {
            val targetTime = now.plusSeconds(hour * 3600L)
            
            try {
                val temp = findValueForTime(temperatures, targetTime)?.toDoubleOrNull() ?: 14.0
                val tempF = (temp * 9/5) + 32
                
                val windSpeedMph = findValueForTime(windSpeeds, targetTime)?.toDoubleOrNull() ?: 0.0
                val windSpeedKts = (windSpeedMph * 0.868976).roundToInt()
                
                val windGustMph = findValueForTime(windGusts, targetTime)?.toDoubleOrNull() ?: 0.0
                val windGustKts = (windGustMph * 0.868976).roundToInt()
                
                val windDir = findValueForTime(windDirections, targetTime)?.toDoubleOrNull()?.roundToInt() ?: 0
                val clouds = findValueForTime(skyCover, targetTime)?.toDoubleOrNull()?.roundToInt() ?: 0
                val precip = findValueForTime(precipitation, targetTime)?.toDoubleOrNull() ?: 0.0
                val humidity = findValueForTime(relativeHumidity, targetTime)?.toDoubleOrNull()?.roundToInt() ?: 50
                val conditions = findWeatherForTime(weatherConditions, targetTime)

                forecasts.add(
                    HourlyForecast(
                        time = LocalDateTime.ofInstant(targetTime, ZoneId.systemDefault()),
                        temperature = tempF,
                        windSpeed = windSpeedKts,
                        windDirection = windDir,
                        windGust = windGustKts,
                        cloudCover = clouds,
                        precipitation = precip,
                        relativeHumidity = humidity,
                        conditions = conditions
                    )
                )
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Error parsing forecast values for hour $hour", e)
            }
        }

        return forecasts
    }

    private fun findValueForTime(array: org.json.JSONArray, targetTime: Instant): String? {
        try {
            var bestMatch: Pair<String, Long>? = null  // value to duration
            var smallestDiff = Long.MAX_VALUE

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val validTime = obj.getString("validTime")
                val (time, duration) = parseValidTime(validTime)
                val value = obj.getString("value")
                
                if (value.isBlank()) continue

                // Calculate how far this time is from our target
                val diff = kotlin.math.abs(time.epochSecond - targetTime.epochSecond)
                
                // If this time exactly matches or is closer than our previous best match
                if (diff < smallestDiff) {
                    smallestDiff = diff
                    bestMatch = value to duration
                }
            }

            if (bestMatch != null) {
                Log.d("HourlyForecastService", "Found value ${bestMatch.first} for time $targetTime (duration: ${bestMatch.second}s)")
                return bestMatch.first
            }
            
            Log.w("HourlyForecastService", "No matching value found for time $targetTime")
            return null
        } catch (e: Exception) {
            Log.e("HourlyForecastService", "Error finding value for time: $targetTime", e)
            return null
        }
    }

    private fun findWeatherForTime(array: org.json.JSONArray, targetTime: Instant): List<String> {
        try {
            var bestMatch: List<String> = emptyList()
            var smallestDiff = Long.MAX_VALUE

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val validTime = obj.getString("validTime")
                val (time, duration) = parseValidTime(validTime)
                
                // Calculate how far this time is from our target
                val diff = kotlin.math.abs(time.epochSecond - targetTime.epochSecond)
                
                // If this time exactly matches or is closer than our previous best match
                if (diff < smallestDiff) {
                    smallestDiff = diff
                    val values = obj.getJSONArray("value")
                    bestMatch = List(values.length()) { index ->
                        try {
                            values.getJSONObject(index).getString("weather")
                        } catch (e: Exception) {
                            ""
                        }
                    }.filter { it.isNotBlank() }
                }
            }

            return bestMatch
        } catch (e: Exception) {
            Log.e("HourlyForecastService", "Error finding weather for time: $targetTime", e)
            return emptyList()
        }
    }

    private fun parseValidTime(validTime: String): Pair<Instant, Long> {
        try {
            Log.d("HourlyForecastService", "Parsing validTime: $validTime")
            
            val parts = validTime.split("/")
            if (parts.isEmpty() || parts[0].isBlank()) {
                Log.w("HourlyForecastService", "Invalid validTime format: empty or missing time")
                return Instant.now() to 3600L
            }
            
            val time = Instant.parse(parts[0])
            val duration = if (parts.size > 1 && parts[1].isNotBlank()) {
                val durationStr = parts[1]
                var seconds = 0L
                
                // Parse ISO 8601 duration format (e.g., PT1H, PT30M, etc.)
                val numberStr = StringBuilder()
                var i = 1  // Skip the 'P' at the start
                
                // Validate duration string format
                if (!durationStr.startsWith("P")) {
                    Log.w("HourlyForecastService", "Invalid duration format, missing 'P': $durationStr")
                    return time to 3600L
                }
                
                while (i < durationStr.length) {
                    val c = durationStr[i]
                    when (c) {
                        'T' -> {
                            if (numberStr.isNotEmpty()) {
                                Log.w("HourlyForecastService", "Unexpected number before 'T': $durationStr")
                                numberStr.clear()
                            }
                            i++
                        }
                        'H', 'M', 'S' -> {
                            if (numberStr.isEmpty()) {
                                Log.w("HourlyForecastService", "Empty number for duration unit $c: $durationStr")
                                i++
                                continue
                            }
                            try {
                                val value = numberStr.toString().toLong()
                                seconds += when (c) {
                                    'H' -> value * 3600
                                    'M' -> value * 60
                                    'S' -> value
                                    else -> 0
                                }
                            } catch (e: NumberFormatException) {
                                Log.w("HourlyForecastService", "Invalid number in duration: ${numberStr}", e)
                            }
                            numberStr.clear()
                        }
                        in '0'..'9' -> numberStr.append(c)
                        else -> {
                            Log.w("HourlyForecastService", "Unexpected character in duration: $c")
                            numberStr.clear()
                        }
                    }
                    i++
                }
                
                if (seconds == 0L) {
                    Log.w("HourlyForecastService", "Duration parsed to 0 seconds, using default")
                    3600L
                } else {
                    seconds
                }
            } else {
                Log.d("HourlyForecastService", "No duration specified, using default")
                3600L // Default to 1 hour if no duration specified
            }
            
            Log.d("HourlyForecastService", "Successfully parsed validTime: time=$time, duration=${duration}s")
            return time to duration
            
        } catch (e: Exception) {
            Log.e("HourlyForecastService", "Error parsing validTime: $validTime", e)
            // Return a safe default value instead of throwing
            return Instant.now() to 3600L
        }
    }

    private fun Double.roundToInt() = kotlin.math.round(this).toInt()
} 