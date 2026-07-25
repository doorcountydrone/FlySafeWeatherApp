package com.flysafeweather.app.data

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AirportList(
    @SerializedName("airports") val airports: List<Airport>
)

class AirportService(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private const val TAG = "AirportService"
    }

    fun loadAirports(): List<Airport> {
        val inputStream = context.assets.open("us_reporting_airports.json")
        val reader = InputStreamReader(inputStream)
        val airportList = gson.fromJson(reader, AirportList::class.java)
        return airportList.airports
    }

    fun findNearestAirport(location: Location): Airport {
        val airports = loadAirports()
        Log.d("AirportDistance", """
            === Finding Nearest Airport ===
            Current Location: ${location.latitude}, ${location.longitude}
            Looking for nearest among ${airports.size} airports
        """.trimIndent())
        
        val nearestAirport = airports.minByOrNull { airport ->
            val distance = calculateDistance(
                location.latitude,
                location.longitude,
                airport.latitude,
                airport.longitude
            )
            airport.distance = distance
            distance
        } ?: Airport(
            icao = "KSUE",
            name = "Door County Cherryland Airport",
            latitude = 44.8435,
            longitude = -87.4215,
            airspaceClass = "E",
            airspaceFloor = 0,
            airspaceCeiling = 1200
        )

        Log.d("AirportDistance", """
            === Nearest Airport Found ===
            Airport: ${nearestAirport.icao}
            Distance: ${nearestAirport.distance} mi
            Location: ${nearestAirport.latitude}, ${nearestAirport.longitude}
        """.trimIndent())

        return nearestAirport
    }

    fun findNearbyAirports(lat: Double, lon: Double, radiusNM: Double): List<Airport> {
        val airports = loadAirports()
        return airports.filter { airport ->
            calculateDistance(lat, lon, airport.latitude, airport.longitude) <= radiusNM
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        
        // Convert meters to statute miles (1 meter = 0.000621371 miles)
        val distanceMiles = results[0] * 0.000621371
        
        Log.d("AirportDistance", """
            Distance calculation details:
            From: ($lat1, $lon1)
            To: ($lat2, $lon2)
            Distance in meters: ${results[0]}
            Distance in miles: $distanceMiles
        """.trimIndent())
        
        // Round to 1 decimal place
        return Math.round(distanceMiles * 10.0) / 10.0
    }

    private suspend fun fetchAirportCoordinates(code: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            // Try the FAA API first
            val url = URL("https://api.weather.gov/stations/$code")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "(Door County Drone Weather App, contact@doorcountydrone.app)")
            }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "NWS API response for $code: $response")
                
                try {
                    val jsonResponse = org.json.JSONObject(response)
                    val geometry = jsonResponse.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")
                    
                    // NWS API returns [longitude, latitude]
                    val lon = coordinates.getDouble(0)
                    val lat = coordinates.getDouble(1)
                    
                    Log.d(TAG, "Found coordinates for $code: $lat, $lon")
                    return@withContext Pair(lat, lon)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing NWS API response for $code", e)
                }
            } else {
                Log.d(TAG, "NWS API returned ${connection.responseCode} for $code")
            }
            
            // If NWS API fails, try AirNav's API
            val airnavUrl = URL("https://www.airnav.com/airport/$code")
            val airnavConnection = url.openConnection() as HttpURLConnection
            airnavConnection.apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            
            if (airnavConnection.responseCode == 200) {
                val response = airnavConnection.inputStream.bufferedReader().use { it.readText() }
                
                // Extract coordinates from AirNav's HTML
                val coordPattern = """(\d{2}°\d{2}\.\d+'[NS])\s*/\s*(\d{2,3}°\d{2}\.\d+'[WE])""".toRegex()
                val match = coordPattern.find(response)
                
                if (match != null) {
                    val (latStr, lonStr) = match.destructured
                    val lat = parseCoordinate(latStr)
                    val lon = parseCoordinate(lonStr)
                    
                    if (lat != null && lon != null) {
                        Log.d(TAG, "Found coordinates for $code from AirNav: $lat, $lon")
                        return@withContext Pair(lat, lon)
                    }
                }
            }
            
            Log.d(TAG, "Could not find coordinates for $code from any source")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching coordinates for $code", e)
            null
        }
    }
    
    private fun parseCoordinate(coord: String): Double? {
        try {
            val degrees = coord.substringBefore('°').toDouble()
            val minutes = coord.substringAfter('°').substringBefore('\'').toDouble()
            val direction = coord.last()
            
            var decimal = degrees + (minutes / 60.0)
            if (direction == 'S' || direction == 'W') {
                decimal = -decimal
            }
            
            return decimal
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun findAirport(code: String): Airport? = withContext(Dispatchers.IO) {
        try {
            // First try to find in our JSON file
            val airports = loadAirports()
            val jsonAirport = airports.find { it.icao == code }
            
            if (jsonAirport != null) {
                return@withContext jsonAirport
            }
            
            // Check if it's a valid ICAO code (4 letters)
            if (code.matches(Regex("^[A-Z]{4}$"))) {
                Log.d(TAG, "Checking if $code is a reporting airport")
                
                try {
                    // Check if the airport is reporting by attempting to fetch METAR
                    val url = URL("https://aviationweather.gov/cgi-bin/data/metar.php?ids=$code&format=xml&hours=2")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/xml")
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    
                    val responseCode = connection.responseCode
                    Log.d(TAG, "Response code for $code: $responseCode")
                    
                    if (responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.d(TAG, "Response for $code: $response")
                        
                        if (response.contains("<raw_text>") || response.contains("<observation>")) {
                            Log.d(TAG, "$code is a reporting airport")
                            // Airport is reporting, return hardcoded coordinates for known airports or fetch coordinates
                            return@withContext when (code) {
                                "KSUE" -> Airport(
                                    icao = code,
                                    name = "Door County Cherryland Airport",
                                    latitude = 44.8435,
                                    longitude = -87.4215,
                                    airspaceClass = "E",
                                    airspaceFloor = 700,
                                    airspaceCeiling = 14500
                                )
                                "KGRB" -> Airport(
                                    icao = code,
                                    name = "Green Bay Austin Straubel International Airport",
                                    latitude = 44.4851,
                                    longitude = -88.1296,
                                    airspaceClass = "C",
                                    airspaceFloor = 0,
                                    airspaceCeiling = 4300
                                )
                                "KMKE" -> Airport(
                                    icao = code,
                                    name = "Milwaukee Mitchell International Airport",
                                    latitude = 42.9472,
                                    longitude = -87.8966,
                                    airspaceClass = "C",
                                    airspaceFloor = 0,
                                    airspaceCeiling = 4800
                                )
                                "KMSN" -> Airport(
                                    icao = code,
                                    name = "Dane County Regional Airport",
                                    latitude = 43.1399,
                                    longitude = -89.3375,
                                    airspaceClass = "C",
                                    airspaceFloor = 0,
                                    airspaceCeiling = 4300
                                )
                                else -> {
                                    Log.d(TAG, "Fetching coordinates for $code")
                                    val coordinates = when (code) {
                                        // European Airports
                                        "EDDB" -> Pair(52.3667, 13.5033)  // Berlin Brandenburg
                                        "EGLL" -> Pair(51.4700, -0.4543)  // London Heathrow
                                        "EHAM" -> Pair(52.3086, 4.7639)   // Amsterdam Schiphol
                                        "LFPG" -> Pair(49.0097, 2.5478)   // Paris Charles de Gaulle
                                        "LEMD" -> Pair(40.4983, -3.5676)  // Madrid Barajas
                                        "LIRF" -> Pair(41.8003, 12.2389)  // Rome Fiumicino
                                        "EDDM" -> Pair(48.3537, 11.7750)  // Munich
                                        "LSZH" -> Pair(47.4647, 8.5492)   // Zurich
                                        "EKCH" -> Pair(55.6180, 12.6508)  // Copenhagen
                                        "ESSA" -> Pair(59.6519, 17.9186)  // Stockholm Arlanda
                                        
                                        // Asian Airports
                                        "RJTT" -> Pair(35.5494, 139.7798) // Tokyo Haneda
                                        "ZBAA" -> Pair(40.0799, 116.6031) // Beijing Capital
                                        "VHHH" -> Pair(22.3080, 113.9185) // Hong Kong
                                        "WSSS" -> Pair(1.3644, 103.9915)  // Singapore Changi
                                        "VTBS" -> Pair(13.6900, 100.7501) // Bangkok Suvarnabhumi
                                        "RKSI" -> Pair(37.4691, 126.4505) // Seoul Incheon
                                        
                                        // Middle Eastern Airports
                                        "OMDB" -> Pair(25.2532, 55.3657)  // Dubai
                                        "OBBI" -> Pair(26.2708, 50.6336)  // Bahrain
                                        "OTHH" -> Pair(25.2731, 51.6081)  // Doha Hamad
                                        
                                        // Australian/NZ Airports
                                        "YSSY" -> Pair(-33.9399, 151.1753) // Sydney
                                        "YMML" -> Pair(-37.6733, 144.8433) // Melbourne
                                        "NZAA" -> Pair(-37.0081, 174.7918) // Auckland
                                        
                                        // Canadian Airports
                                        "CYYZ" -> Pair(43.6777, -79.6248)  // Toronto Pearson
                                        "CYVR" -> Pair(49.1967, -123.1815) // Vancouver
                                        "CYUL" -> Pair(45.4706, -73.7408)  // Montreal
                                        
                                        // South American Airports
                                        "SBGR" -> Pair(-23.4356, -46.4731) // São Paulo
                                        "SCEL" -> Pair(-33.3930, -70.7858) // Santiago
                                        "SAEZ" -> Pair(-34.8222, -58.5358) // Buenos Aires
                                        
                                        // African Airports
                                        "FACT" -> Pair(-33.9715, 18.6021)  // Cape Town
                                        "FAOR" -> Pair(-26.1392, 28.2460)  // Johannesburg
                                        "HECA" -> Pair(30.1219, 31.4056)   // Cairo
                                        
                                        else -> fetchAirportCoordinates(code)
                                    }
                                    
                                    if (coordinates != null) {
                                        Airport(
                                            icao = code,
                                            name = "$code International Airport",
                                            latitude = coordinates.first,
                                            longitude = coordinates.second,
                                            airspaceClass = "E",
                                            airspaceFloor = 0,
                                            airspaceCeiling = 1200
                                        )
                                    } else {
                                        Log.w(TAG, "Could not get coordinates for $code, using defaults")
                                        Airport(
                                            icao = code,
                                            name = "$code Airport",
                                            latitude = 0.0,
                                            longitude = 0.0,
                                            airspaceClass = "E",
                                            airspaceFloor = 0,
                                            airspaceCeiling = 1200
                                        )
                                    }
                                }
                            }
                        } else {
                            Log.d(TAG, "$code is not a reporting airport")
                        }
                    } else {
                        Log.d(TAG, "Failed to get METAR for $code: $responseCode")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking METAR for $code", e)
                }
            }
            
            Log.d(TAG, "No valid airport found for $code")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding airport: $code", e)
            null
        }
    }

    suspend fun fetchAllAirports(): List<Airport> {
        return withContext(Dispatchers.IO) {
            try {
                context.assets.open("us_reporting_airports.json").use { input ->
                    val reader = input.bufferedReader()
                    val airportList = gson.fromJson(reader, AirportList::class.java)
                    airportList.airports
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching airports", e)
                emptyList()
            }
        }
    }
} 
