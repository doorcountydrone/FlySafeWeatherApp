package com.flysafeweather.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.Instant
import java.time.LocalDate
import com.google.gson.Gson
import retrofit2.Retrofit
import java.util.Locale

class MetarService(
    private val context: Context,
    private val retrofit: Retrofit? = null
) {
    private val TAG = "METAR_DEBUG"
    private val gson = Gson()
    
    private fun getCurrentDateTime(): String {
        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        // Round down to nearest 5-minute interval
        val minute = (now.minute / 5) * 5
        val roundedTime = now.withMinute(minute).withSecond(0).withNano(0)
        return roundedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'"))
    }

    suspend fun fetchMetar(stationId: String): MetarData = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting METAR fetch for station: $stationId")
            val station = stationId.trim().uppercase(Locale.US)
            // Build candidate station codes: try exact ICAO; if user entered IATA (3 letters), try K+ and P+ heuristics
            val stationCandidates = buildList {
                add(station)
                if (station.length == 3 && station.all { it.isLetter() }) {
                    add("K$station")
                    add("P$station")
                }
            }
            
            // Create a default MetarData in case of errors
            val defaultData = MetarData(
                stationId = station,
                windSpeed = 0,
                windGust = 0,
                windDirection = 0,
                visibility = 10.0,
                temperature = 20.0,
                dewPoint = 15.0,
                altimeter = 29.92,
                cloudLayers = listOf(CloudLayer("CLR", 0)),
                precipitation = emptyList(),
                rawText = "No data available"
            )

            // Use Retrofit for testing, HttpURLConnection for production
            val result = if (retrofit != null) {
                try {
                    // Use Retrofit API call
                    val call = retrofit.create(MetarApi::class.java).getMetar(stationId)
                    val retrofitResponse = call.execute()
                    
                    if (!retrofitResponse.isSuccessful) {
                        Log.e(TAG, "Retrofit error: ${retrofitResponse.code()}")
                        return@withContext defaultData
                    }
                    
                    val response = retrofitResponse.body() ?: ""
                    
                    if (response.isBlank()) {
                        Log.e(TAG, "Empty response from Retrofit API")
                        return@withContext defaultData
                    }
                    
                    Log.d(TAG, "Retrofit response (first 200 chars): ${response.take(200)}")
                    
                    try {
                        // Try parsing as JSON first (new API), then fallback to XML
                        val parsed = if (response.trimStart().startsWith("{") || response.trimStart().startsWith("[")) {
                            Log.d(TAG, "Attempting JSON parsing (Retrofit)")
                            parseMetarJson(response)
                        } else if (response.contains("<METAR>")) {
                            Log.d(TAG, "Attempting XML parsing (Retrofit)")
                            parseMetarXml(response)
                        } else {
                            Log.e(TAG, "Unknown response format from Retrofit: ${response.take(100)}")
                            parseMetarRawText(response, stationId)
                        }
                        enrichCloudLayers(parsed)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Retrofit METAR, using default data", e)
                        defaultData
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Retrofit error", e)
                    return@withContext defaultData
                }
            } else {
                try {
                    // Iterate over station candidates and endpoints until one succeeds
                    var response: String? = null
                    var lastException: Exception? = null
                    var usedStation: String? = null
                    stationCandidates@ for (candidate in stationCandidates) {
                        // Try multiple endpoints for METAR data with improved error handling
                        val urls = listOf(
                            // AWWS legacy CGI XML
                            "https://aviationweather.gov/cgi-bin/data/metar.php?ids=$candidate&format=xml&hours=2",
                            // AWWS JSON
                            "https://aviationweather.gov/api/data/metar?ids=$candidate&format=json&hours=2",
                            // WIFS TAC OPMET (recent 5 minutes)
                            "https://aviationweather.gov/wifs/api/collections/tac_opmet_reports/locations/$candidate?datetime=${getCurrentDateTime()}/PT5M",
                            // ADDS XML (dataserver) — aviationweather.gov
                            "https://aviationweather.gov/adds/dataserver_current/httpparam?dataSource=metars&requestType=retrieve&format=XML&hoursBeforeNow=2&stationString=$candidate",
                            // Backup legacy URL variant
                            "https://www.aviationweather.gov/cgi-bin/data/metar.php?ids=$candidate&format=xml&hours=2"
                        )
                    
                        for (urlString in urls) {
                        try {
                            val url = URL(urlString)
                                Log.d(TAG, "Trying URL: $url for station: $candidate")
                            
                            var connection = (url.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 10000
                                readTimeout = 10000
                                requestMethod = "GET"
                                setRequestProperty("Accept", "application/json, application/xml, text/plain")
                                setRequestProperty("User-Agent", "DoorCountyDroneWeatherApp/1.0")
                            }
                            
                                val responseCode = connection.responseCode
                                Log.d(TAG, "Response code for $candidate: $responseCode")
                            
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                response = connection.inputStream.bufferedReader().use { it.readText() }
                                    Log.d(TAG, "Success with URL: $urlString for $candidate")
                                Log.d(TAG, "Response length: ${response.length}")
                                Log.d(TAG, "Response preview: ${response.take(500)}")
                                    usedStation = candidate
                                    break
                            } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                                // Retry once with a browser-like User-Agent (some endpoints block unknown UAs)
                                try {
                                    Log.w(TAG, "403 for $urlString, retrying with browser User-Agent")
                                    connection.disconnect()
                                    connection = (url.openConnection() as HttpURLConnection).apply {
                                        connectTimeout = 10000
                                        readTimeout = 10000
                                        requestMethod = "GET"
                                        setRequestProperty("Accept", "application/json, application/xml, text/plain")
                                        setRequestProperty(
                                            "User-Agent",
                                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                                        )
                                        setRequestProperty("Referer", "https://aviationweather.gov/")
                                    }
                                    val retryCode = connection.responseCode
                                    Log.d(TAG, "Retry response code for $candidate: $retryCode")
                                    if (retryCode == HttpURLConnection.HTTP_OK) {
                                        response = connection.inputStream.bufferedReader().use { it.readText() }
                                            Log.d(TAG, "Retry success with URL: $urlString for $candidate")
                                        Log.d(TAG, "Response length: ${response.length}")
                                        Log.d(TAG, "Response preview: ${response.take(500)}")
                                            usedStation = candidate
                                            break
                                    } else {
                                        Log.w(TAG, "Retry failed with HTTP $retryCode for URL: $urlString (station: $stationId)")
                                        try {
                                            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                                            Log.w(TAG, "Error response: $errorResponse")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Could not read error response: ${e.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Retry with browser UA failed: ${e.message}")
                                }
                            } else {
                                Log.w(TAG, "HTTP $responseCode for URL: $urlString (station: $stationId)")
                                // Try to read error response for debugging
                                try {
                                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                                    Log.w(TAG, "Error response: $errorResponse")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not read error response: ${e.message}")
                                }
                            }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed URL: $urlString for $candidate - ${e.message}")
                                lastException = e
                            }
                        }
                        if (response != null) break@stationCandidates
                    }
                    
                    if (response == null) {
                        Log.e(TAG, "All METAR endpoints failed for $stationId", lastException)
                        // Try a simple test with a known working airport
                        if (station != "KORD") {
                            Log.d(TAG, "Trying fallback with KORD to test API connectivity")
                            try {
                                val testUrl = "https://aviationweather.gov/cgi-bin/data/metar.php?ids=KORD&format=xml&hours=2"
                                val testConnection = URL(testUrl).openConnection() as HttpURLConnection
                                testConnection.apply {
                                    connectTimeout = 5000
                                    readTimeout = 5000
                                    requestMethod = "GET"
                                    setRequestProperty("User-Agent", "DoorCountyDroneWeatherApp/1.0")
                                }
                                
                                if (testConnection.responseCode == HttpURLConnection.HTTP_OK) {
                                    val testResponse = testConnection.inputStream.bufferedReader().use { it.readText() }
                                    Log.d(TAG, "API is working - KORD response: ${testResponse.take(200)}")
                                } else {
                                    Log.e(TAG, "API test failed - KORD response code: ${testConnection.responseCode}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "API test failed: ${e.message}")
                            }
                        }
                        return@withContext defaultData
                    }

                    if (response.isBlank()) {
                        Log.e(TAG, "Empty response from API")
                        return@withContext defaultData
                    }

                    Log.d(TAG, "Raw response (first 200 chars): ${response.take(200)}")

                    try {
                        // Try parsing as JSON first (new API), then fallback to XML
                        val result = if (response.trimStart().startsWith("{") || response.trimStart().startsWith("[")) {
                            Log.d(TAG, "Attempting JSON parsing")
                            parseMetarJson(response)
                        } else if (response.contains("<METAR>")) {
                            Log.d(TAG, "Attempting XML parsing")
                            parseMetarXml(response)
                        } else {
                            Log.e(TAG, "Unknown response format: ${response.take(100)}")
                            // Try to extract METAR data from raw text format
                            parseMetarRawText(response, usedStation ?: station)
                        }
                        return@withContext enrichCloudLayers(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing METAR, using default data", e)
                        return@withContext defaultData
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "HTTP error", e)
                    return@withContext defaultData
                }
            }
            
            // Return the result from either Retrofit or HttpURLConnection
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchMetar", e)
            return@withContext MetarData(
                stationId = stationId,
                windSpeed = 0,
                windGust = 0,
                windDirection = 0,
                visibility = 10.0,
                temperature = 20.0,
                dewPoint = 15.0,
                altimeter = 29.92,
                cloudLayers = listOf(CloudLayer("CLR", 0)),
                precipitation = emptyList(),
                rawText = "Error: ${e.message}"
            )
        }
    }

    private fun parseMetarXml(xml: String): MetarData {
        try {
            if (!xml.contains("<METAR>")) {
                Log.e(TAG, "Invalid METAR XML format: $xml")
                throw Exception("Invalid weather data format")
            }

            // Parse XML document
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())
            
            val stationId = safeExtract(xml, "station_id")
            Log.d(TAG, "Parsing METAR for station: $stationId")

            // Get raw METAR text first for debugging
            val rawText = document.getElementsByTagName("raw_text").item(0)?.textContent ?: ""
            Log.d(TAG, "Raw METAR text: $rawText")

            return MetarData(
                stationId = stationId,
                windSpeed = safeExtractInt(xml, "wind_speed_kt"),
                windGust = safeExtractInt(xml, "wind_gust_kt"),
                windDirection = safeExtractInt(xml, "wind_dir_degrees"),
                visibility = parseVisibility(xml),
                temperature = safeExtractDouble(xml, "temp_c").also { 
                    Log.d(TAG, "XML temp_c: $it")
                },
                dewPoint = safeExtractDouble(xml, "dewpoint_c").also { 
                    Log.d(TAG, "XML dewpoint_c: $it")
                },
                altimeter = safeExtractDouble(xml, "altim_in_hg"),
                cloudLayers = parseCloudLayers(xml),
                precipitation = parsePrecipitation(rawText),
                rawText = rawText
            ).also {
                Log.d(TAG, "Parsed METAR data: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing METAR XML", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            throw Exception("Error processing weather data: ${e.message}")
        }
    }

    private fun parseMetarJson(json: String): MetarData {
        try {
            Log.d(TAG, "Parsing METAR JSON response")
            
            // Check if the response is a direct array or an object with data array
            val metarObject = if (json.trimStart().startsWith("[")) {
                // Direct array response
                val metarArray = org.json.JSONArray(json)
                if (metarArray.length() == 0) {
                    Log.e(TAG, "No METAR data in JSON array")
                    throw Exception("No METAR data available")
                }
                metarArray.getJSONObject(0)
            } else {
                // Object with data array
                val jsonObject = org.json.JSONObject(json)
                val metarArray = jsonObject.optJSONArray("data")
                if (metarArray == null || metarArray.length() == 0) {
                    Log.e(TAG, "No METAR data in JSON response")
                    throw Exception("No METAR data available")
                }
                metarArray.getJSONObject(0)
            }
            
            return MetarData(
                stationId = metarObject.optString("icaoId", ""),
                windSpeed = metarObject.optInt("wspd", 0),
                windGust = metarObject.optInt("wgust", 0),
                windDirection = metarObject.optInt("wdir", 0),
                visibility = parseVisibilityFromJson(metarObject),
                temperature = metarObject.optDouble("temp", 20.0).also { 
                    Log.d(TAG, "JSON temp: $it")
                },
                dewPoint = metarObject.optDouble("dewp", 15.0).also { 
                    Log.d(TAG, "JSON dewp: $it")
                },
                altimeter = metarObject.optDouble("altim", 29.92),
                cloudLayers = parseCloudLayersFromJson(metarObject),
                precipitation = parsePrecipitationFromJson(metarObject),
                rawText = metarObject.optString("rawOb", "")
            ).also {
                Log.d(TAG, "Parsed METAR JSON data: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing METAR JSON", e)
            throw Exception("Error processing JSON weather data: ${e.message}")
        }
    }

    private fun parseCloudLayersFromJson(metarObject: org.json.JSONObject): List<CloudLayer> {
        try {
            // Support both legacy "skyCondition" (ADDS) and new "clouds" (AWC API) fields
            val layers = mutableListOf<CloudLayer>()

            val skyConditions = metarObject.optJSONArray("skyCondition")
            if (skyConditions != null && skyConditions.length() > 0) {
                for (i in 0 until skyConditions.length()) {
                    val skyCondition = skyConditions.getJSONObject(i)
                    val coverage = skyCondition.optString("skyCover", "CLR")
                    val heightFeet = when {
                        skyCondition.has("cloudBaseFt") -> skyCondition.optInt("cloudBaseFt", 0)
                        skyCondition.has("cloudBase") -> (skyCondition.optInt("cloudBase", 0) * 100)
                        else -> 0
                    }
                    if (coverage.equals("VV", ignoreCase = true)) {
                        layers.add(CloudLayer("VV", heightFeet))
                    } else {
                        layers.add(CloudLayer(coverage, heightFeet))
                    }
                }
            }

            val awcClouds = metarObject.optJSONArray("clouds")
            if (awcClouds != null && awcClouds.length() > 0) {
                for (i in 0 until awcClouds.length()) {
                    val cloud = awcClouds.getJSONObject(i)
                    val coverage = cloud.optString("cover", "CLR")
                    val baseFeet = when {
                        cloud.has("base") -> cloud.optDouble("base", 0.0).toInt()
                        cloud.has("baseFt") -> cloud.optInt("baseFt", 0)
                        else -> 0
                    }
                    layers.add(CloudLayer(coverage.uppercase(Locale.US), baseFeet))
                }
            }

            if (layers.isNotEmpty()) {
                return layers
            }

            return listOf(CloudLayer("CLR", 0))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cloud layers from JSON", e)
            return listOf(CloudLayer("CLR", 0))
        }
    }

    private fun parsePrecipitationFromJson(metarObject: org.json.JSONObject): List<String> {
        try {
            val precipList = mutableListOf<String>()
            
            // First, try the new AWC API format with wxString field
            val wxString = metarObject.optString("wxString", "")
            if (wxString.isNotEmpty()) {
                Log.d(TAG, "Found wxString: $wxString")
                // Parse wxString (e.g., "-RA BR" or "RA SN")
                val wxCodes = wxString.split(" ").filter { it.isNotEmpty() }
                wxCodes.forEach { code ->
                    val description = parseWeatherCode(code)
                    if (description.isNotEmpty()) {
                        precipList.add(description)
                    }
                }
                if (precipList.isNotEmpty()) {
                    Log.d(TAG, "Parsed precipitation from wxString: $precipList")
                    return precipList
                }
            }
            
            // Second, try legacy ADDS format with presentWeather array
            val presentWeather = metarObject.optJSONArray("presentWeather")
            if (presentWeather != null && presentWeather.length() > 0) {
                for (i in 0 until presentWeather.length()) {
                    val weather = presentWeather.getJSONObject(i)
                    val intensity = weather.optString("intensity", "")
                    val descriptor = weather.optString("descriptor", "")
                    val phenomenon = weather.optString("phenomena", "")
                    
                    val description = when {
                        phenomenon.isNotEmpty() -> "$intensity$descriptor$phenomenon".trim()
                        else -> ""
                    }
                    if (description.isNotEmpty()) {
                        precipList.add(description)
                    }
                }
                if (precipList.isNotEmpty()) {
                    Log.d(TAG, "Parsed precipitation from presentWeather: $precipList")
                    return precipList
                }
            }
            
            // Third, fallback to parsing from raw METAR text
            val rawText = metarObject.optString("rawOb", "")
            if (rawText.isNotEmpty()) {
                val parsedFromRaw = parsePrecipitation(rawText)
                if (parsedFromRaw.isNotEmpty()) {
                    Log.d(TAG, "Parsed precipitation from raw text: $parsedFromRaw")
                    return parsedFromRaw
                }
            }
            
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing precipitation from JSON", e)
            return emptyList()
        }
    }
    
    private fun parseWeatherCode(code: String): String {
        // Handle intensity prefixes (-, +)
        val intensity = when {
            code.startsWith("-") -> "-"
            code.startsWith("+") -> "+"
            else -> ""
        }
        val codeWithoutIntensity = code.removePrefix("-").removePrefix("+")
        
        return when (codeWithoutIntensity) {
            "RA" -> when (intensity) {
                "-" -> "Light Rain"
                "+" -> "Heavy Rain"
                else -> "Rain"
            }
            "SN" -> when (intensity) {
                "-" -> "Light Snow"
                "+" -> "Heavy Snow"
                else -> "Snow"
            }
            "DZ" -> when (intensity) {
                "-" -> "Light Drizzle"
                "+" -> "Heavy Drizzle"
                else -> "Drizzle"
            }
            "GR" -> "Hail"
            "GS" -> "Small Hail"
            "IC" -> "Ice Crystals"
            "PL" -> "Ice Pellets"
            "SG" -> "Snow Grains"
            "BR" -> "Mist"
            "FG" -> "Fog"
            "FU" -> "Smoke"
            "DU" -> "Dust"
            "SA" -> "Sand"
            "HZ" -> "Haze"
            "PY" -> "Spray"
            "VA" -> "Volcanic Ash"
            "TS" -> "Thunderstorm"
            "SQ" -> "Squall"
            "FC" -> "Funnel Cloud"
            "SS" -> "Sandstorm"
            "DS" -> "Duststorm"
            "PO" -> "Dust/Sand Whirls"
            else -> code // Return original code if not recognized
        }
    }

    private fun parseMetarRawText(rawText: String, stationId: String): MetarData {
        try {
            Log.d(TAG, "Attempting raw text parsing")
            
            // Normalize lines (TGFTP is two-line: timestamp line then METAR line without keyword)
            val lines = rawText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // Select METAR line:
            // 1) If second line starts with station, use it
            // 2) Else any line starting with station id
            // 3) Else any line matching ICAO + timestamp pattern
            val icaoStartRegex = """^[A-Z]{4}\s+\d{6}Z.*""".toRegex()
            val metarLine = when {
                lines.size >= 2 && lines[1].startsWith(stationId) -> lines[1]
                lines.any { it.startsWith(stationId) } -> lines.first { it.startsWith(stationId) }
                lines.any { it.matches(icaoStartRegex) } -> lines.first { it.matches(icaoStartRegex) }
                else -> {
                    Log.e(TAG, "No METAR data found for station $stationId")
                    throw Exception("No METAR data found")
                }
            }
            Log.d(TAG, "Found METAR line: $metarLine")
            
            // Parse basic METAR elements using regex
            val windRegex = """(\d{3})(\d{2,3})(G\d{2,3})?KT""".toRegex()
            val windMatch = windRegex.find(metarLine)
            
            val visRegex = """(\d+)(?:/(\d+))?SM""".toRegex()
            val visMatch = visRegex.find(metarLine)
            val visibilityMiles = when {
                metarLine.contains("P6SM") -> 10.0
                visMatch != null -> {
                    val whole = visMatch.groupValues.getOrNull(1)?.toDoubleOrNull()
                    val denom = visMatch.groupValues.getOrNull(2)?.toDoubleOrNull()
                    if (whole != null && denom != null) whole / denom
                    else whole ?: 10.0
                }
                else -> 10.0
            }
            
            val tempRegex = """M?(\d{2})/(M?\d{2})""".toRegex()
            val tempMatch = tempRegex.find(metarLine)
            
            val altRegex = """A(\d{4})""".toRegex()
            val altMatch = altRegex.find(metarLine)
            
            val tempC = parseTemperature(tempMatch?.groupValues?.get(1))
            val dewPointC = parseTemperature(tempMatch?.groupValues?.get(2))
            
            Log.d(TAG, "Raw temp/dewpoint parsing: temp=${tempMatch?.groupValues?.get(1)}, dewpoint=${tempMatch?.groupValues?.get(2)}")
            Log.d(TAG, "Parsed values: tempC=$tempC, dewPointC=$dewPointC")
            
            return MetarData(
                stationId = stationId,
                windDirection = windMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                windSpeed = windMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                windGust = windMatch?.groupValues?.get(3)?.removePrefix("G")?.toIntOrNull() ?: 0,
                visibility = visibilityMiles,
                temperature = tempC,
                dewPoint = dewPointC,
                altimeter = (altMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 2992.0) / 100.0,
                cloudLayers = parseCloudLayersFromRawText(metarLine),
                precipitation = emptyList(),
                rawText = metarLine
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing raw METAR text", e)
            throw Exception("Error processing raw METAR data: ${e.message}")
        }
    }

    private fun parseTemperature(tempStr: String?): Double {
        if (tempStr == null) return 20.0
        val isNegative = tempStr.startsWith("M")
        val value = if (isNegative) {
            "-${tempStr.substring(1)}".toDoubleOrNull() ?: 20.0
        } else {
            tempStr.toDoubleOrNull() ?: 20.0
        }
        return value
    }

    private fun parseTemperatureF(tempStr: String?): Double {
        val tempC = parseTemperature(tempStr)
        return (tempC * 9.0/5.0) + 32.0
    }

    private fun parseCloudLayersFromRawText(metarLine: String): List<CloudLayer> {
        try {
            Log.d(TAG, "Parsing cloud layers from raw text: $metarLine")
            
            // Check for clear skies first
            if (metarLine.contains("CLR") || metarLine.contains("SKC")) {
                Log.d(TAG, "Found clear skies in raw METAR")
                return listOf(CloudLayer("CLR", 0))
            }
            
            // Look for cloud layers with coverage and height
            // Supports FEW010, SCT015, BKN020, OVC025, VV003, optional suffixes (CB, TCU), ignores /// unknown
            val cloudRegex = """\b(FEW|SCT|BKN|OVC|VV)(\d{3}|///)(?:CB|TCU)?\b""".toRegex()
            val matches = cloudRegex.findAll(metarLine)
            
            val layers = matches.mapNotNull { match ->
                val coverage = match.groupValues[1]
                val heightCode = match.groupValues[2]
                if (heightCode == "///") {
                    // Unknown height: skip layer for ceiling purposes
                    null
                } else {
                    val hundreds = heightCode.toIntOrNull() ?: 0
                    val feet = hundreds * 100
                    Log.d(TAG, "Found cloud layer: $coverage at ${feet} ft")
                    CloudLayer(coverage, feet)
                }
            }.toList()
            
            return if (layers.isNotEmpty()) {
                layers
            } else {
                Log.d(TAG, "No cloud layers found in raw METAR")
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cloud layers from raw text", e)
            return emptyList()
        }
    }

    private fun safeExtract(xml: String, tag: String): String {
        return try {
            xml.substringAfter("<$tag>").substringBefore("</$tag>")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting $tag", e)
            ""
        }
    }

    private fun safeExtractInt(xml: String, tag: String): Int {
        return try {
            safeExtract(xml, tag).toIntOrNull() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting int for $tag", e)
            0
        }
    }

    private fun safeExtractDouble(xml: String, tag: String): Double {
        return try {
            val value = safeExtract(xml, tag)
            Log.d(TAG, "Extracting $tag: $value")
            value.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting double for $tag", e)
            0.0
        }
    }

    private fun parseVisibility(xml: String): Double {
        return try {
            if (!xml.contains("<visibility_statute_mi>")) {
                Log.d(TAG, "No visibility tag found, returning 10.0")
                return 10.0
            }
            
            val visTag = xml.substringAfter("<visibility_statute_mi>")
                           .substringBefore("</visibility_statute_mi>")
                           .trim()
            
            Log.d(TAG, "Raw visibility value: $visTag")
            
            when {
                visTag == "10+" -> 10.0
                visTag.endsWith("+") -> visTag.removeSuffix("+").toDoubleOrNull() ?: 10.0
                else -> visTag.toDoubleOrNull() ?: 10.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing visibility: ${e.message}")
            10.0  // Default to 10 miles visibility on error
        }
    }

    private fun parseCloudLayers(xml: String): List<CloudLayer> {
        try {
            // Log the raw METAR first
            val rawMetar = safeExtract(xml, "raw_text")
            Log.d(TAG, "Raw METAR: $rawMetar")
            
            // Check for clear skies first
            if (xml.contains("<sky_condition sky_cover=\"CLR\"") || 
                xml.contains("<sky_condition sky_cover=\"SKC\"")) {
                Log.d(TAG, "Found clear skies")
                return listOf(CloudLayer("CLR", 0))
            }

            // Collect all sky_condition tags with coverage and optional height
            val skyConditionRegex = "<sky_condition\\s+[^>]*sky_cover=\"([^\"]+)\"(?:[^>]*cloud_base_ft_agl=\"([^\"]+)\")?".toRegex()
            val layers = mutableListOf<CloudLayer>()
            skyConditionRegex.findAll(xml).forEach { m ->
                val coverage = m.groupValues.getOrNull(1) ?: ""
                val height = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                if (coverage.isNotEmpty()) {
                    Log.d(TAG, "Found XML cloud layer: $coverage at $height ft")
                    layers.add(CloudLayer(coverage, height))
                }
            }

            // Handle vertical visibility if present
            val vvRegex = "<vert_vis_ft>(\\d+)</vert_vis_ft>".toRegex()
            val vvMatch = vvRegex.find(xml)
            if (vvMatch != null) {
                val vvFeet = vvMatch.groupValues[1].toIntOrNull() ?: 0
                Log.d(TAG, "Found vertical visibility: $vvFeet ft")
                layers.add(CloudLayer("VV", vvFeet))
            }

            if (layers.isNotEmpty()) return layers

            Log.d(TAG, "No cloud layers found in XML, checking for clear skies")
            // Check if we have clear skies indication
            if (rawMetar.contains("CLR") || rawMetar.contains("SKC")) {
                Log.d(TAG, "Found clear skies in raw METAR")
                return listOf(CloudLayer("CLR", 0))
            }
            Log.d(TAG, "No clear skies indication found")
            return emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cloud layers", e)
            return emptyList()
        }
    }

    private fun findNearestAirport(lat: Double, lng: Double): Airport? {
        val airports = listOf(
            Airport(
                icao = "KSUE",
                name = "Door County Cherryland",
                latitude = 44.8436,
                longitude = -87.4215,
                airspaceClass = "E",
                airspaceFloor = 0,
                airspaceCeiling = 1200
            ),
            Airport(
                icao = "KGRB",
                name = "Green Bay Austin Straubel",
                latitude = 44.4851,
                longitude = -88.1296,
                airspaceClass = "C",
                airspaceFloor = 0,
                airspaceCeiling = 4300
            ),
            Airport(
                icao = "KMKE",
                name = "Milwaukee Mitchell",
                latitude = 42.9472,
                longitude = -87.8966,
                airspaceClass = "C",
                airspaceFloor = 0,
                airspaceCeiling = 4800
            ),
            Airport(
                icao = "KMSN",
                name = "Dane County Regional",
                latitude = 43.1399,
                longitude = -89.3375,
                airspaceClass = "C",
                airspaceFloor = 0,
                airspaceCeiling = 4300
            )
        )
        
        return airports.minByOrNull { airport ->
            val dlat = lat - airport.latitude
            val dlng = lng - airport.longitude
            dlat * dlat + dlng * dlng  // Simple distance calculation
        }
    }

    private fun findAirport(code: String): Airport? {
        return try {
            val airports = context.assets.open("us_airports.json").use { input ->
                val reader = input.bufferedReader()
                val airportList = gson.fromJson(reader, AirportList::class.java)
                airportList.airports
            }
            
            airports.find { it.icao == code }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding airport: $code", e)
            null
        }
    }

    private fun getAirportName(code: String): String {
        return when (code) {
            "KSUE" -> "Door County Cherryland Airport"
            "KGRB" -> "Green Bay Austin Straubel International"
            "KATW" -> "Appleton International Airport"
            "KOSH" -> "Wittman Regional Airport"
            "KMKE" -> "Milwaukee Mitchell International"
            "KMSN" -> "Dane County Regional"
            "KFLD" -> "Fond du Lac County Airport"
            "KMTW" -> "Manitowoc County Airport"
            "KSBM" -> "Sheboygan County Memorial Airport"
            // Add more airport names as needed
            else -> "$code Airport"
        }
    }

    private suspend fun calculateSunriseSunset(airport: Airport): SunriseSunset {
        return try {
            val latitude = airport.city?.latitude ?: airport.latitude
            val longitude = airport.city?.longitude ?: airport.longitude
            val locationName = airport.city?.name ?: airport.name
            val timeZoneId = airport.city?.timeZoneId ?: "America/Chicago"
            
            Log.d(TAG, """
                Starting sunrise/sunset calculation for:
                Airport: ${airport.icao}
                Location: $locationName
                Timezone: $timeZoneId
            """.trimIndent())
            
            val response = ApiClient.sunriseSunsetApi.getSunriseSunset(
                latitude = latitude,
                longitude = longitude,
                formatted = 0  // Get raw UTC time
            )

            if (response.status != "OK") {
                Log.e(TAG, "Error from sunrise-sunset API: ${response.status}")
                return createDefaultSunriseSunset()
            }

            val timeZone = ZoneId.of(timeZoneId)
            
            // Parse the UTC times from API response
            val rawSunriseUTC = response.results.sunrise
            val rawSunsetUTC = response.results.sunset
            
            Log.d(TAG, """
                Raw API Response for ${airport.icao}:
                Raw Sunrise UTC: $rawSunriseUTC
                Raw Sunset UTC: $rawSunsetUTC
            """.trimIndent())

            val sunriseUTC = ZonedDateTime.parse(rawSunriseUTC)
            val sunsetUTC = ZonedDateTime.parse(rawSunsetUTC)

            // Convert UTC to local time
            val sunrise = sunriseUTC.withZoneSameInstant(timeZone).toLocalDateTime()
            val sunset = sunsetUTC.withZoneSameInstant(timeZone).toLocalDateTime()

            Log.d(TAG, """
                Final times for ${airport.icao}:
                Timezone: $timeZone (DST Active: ${timeZone.rules.isDaylightSavings(Instant.now())})
                Sunrise UTC: $sunriseUTC
                Sunset UTC: $sunsetUTC
                Local Sunrise: $sunrise
                Local Sunset: $sunset
            """.trimIndent())

            SunriseSunset(sunrise, sunset, timeZone.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating sunrise/sunset for ${airport.name}", e)
            createDefaultSunriseSunset()
        }
    }

    private fun createDefaultSunriseSunset(): SunriseSunset {
        val timeZone = ZoneId.of("America/Chicago")
        val now = LocalDateTime.now(timeZone)
        
        // Default times for Door County area
        val (sunriseHour, sunriseMinute, sunsetHour, sunsetMinute) = when (now.monthValue) {
            12, 1 -> listOf(7, 20, 16, 30)  // Winter
            2 -> listOf(6, 45, 17, 15)      // February
            3 -> listOf(6, 0, 17, 45)       // March
            4 -> listOf(5, 15, 19, 15)      // April
            5 -> listOf(5, 0, 20, 0)        // May
            6 -> listOf(4, 45, 20, 30)      // June
            7 -> listOf(5, 0, 20, 30)       // July
            8 -> listOf(5, 30, 19, 45)      // August
            9 -> listOf(6, 15, 19, 0)       // September
            10 -> listOf(6, 45, 18, 0)      // October
            11 -> listOf(7, 0, 16, 30)      // November
            else -> listOf(7, 20, 16, 30)   // Default to winter times
        }

        val sunrise = now
            .withHour(sunriseHour)
            .withMinute(sunriseMinute)
            .withSecond(0)
            .withNano(0)

        val sunset = now
            .withHour(sunsetHour)
            .withMinute(sunsetMinute)
            .withSecond(0)
            .withNano(0)

        return SunriseSunset(sunrise, sunset, timeZone.toString())
    }

    // Helper function to format coordinates
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun parsePrecipitation(rawMetar: String): List<String> {
        val precipTypes = mutableListOf<String>()
        
        Log.d(TAG, "Parsing precipitation from raw METAR: $rawMetar")
        
        // Match weather codes with optional intensity prefix and descriptors
        // Pattern: (optional - or +)(optional descriptor)(phenomenon)
        // Examples: -RA, +SN, BR, TSRA, FZRA, etc.
        val weatherCodes = listOf("DZ", "RA", "SN", "SG", "IC", "PL", "GR", "GS", "UP", 
                                  "BR", "FG", "FU", "VA", "DU", "SA", "HZ", "PY", "PO", 
                                  "SQ", "FC", "SS", "DS", "TS")
        
        // Build regex pattern to match weather codes
        val codePattern = weatherCodes.joinToString("|")
        val regex = """\b([-+])?((?:TS|FZ|BL|DR|MI|PR|BC|UP|VC)?)($codePattern)\b""".toRegex(RegexOption.IGNORE_CASE)
        
        regex.findAll(rawMetar).forEach { match ->
            val intensity = match.groupValues[1]
            val descriptor = match.groupValues[2]
            val phenomenon = match.groupValues[3].uppercase()
            
            Log.d(TAG, "Found weather code: intensity='$intensity', descriptor='$descriptor', phenomenon='$phenomenon'")
            
            val description = when (phenomenon) {
                "RA" -> when (intensity) {
                    "-" -> "Light Rain"
                    "+" -> "Heavy Rain"
                    else -> if (descriptor.isNotEmpty()) "$descriptor Rain" else "Rain"
                }
                "SN" -> when (intensity) {
                    "-" -> "Light Snow"
                    "+" -> "Heavy Snow"
                    else -> if (descriptor.isNotEmpty()) "$descriptor Snow" else "Snow"
                }
                "DZ" -> when (intensity) {
                    "-" -> "Light Drizzle"
                    "+" -> "Heavy Drizzle"
                    else -> if (descriptor.isNotEmpty()) "$descriptor Drizzle" else "Drizzle"
                }
                "GR" -> "Hail"
                "GS" -> "Small Hail"
                "IC" -> "Ice Crystals"
                "PL" -> "Ice Pellets"
                "SG" -> "Snow Grains"
                "BR" -> "Mist"
                "FG" -> "Fog"
                "FU" -> "Smoke"
                "DU" -> "Dust"
                "SA" -> "Sand"
                "HZ" -> "Haze"
                "PY" -> "Spray"
                "VA" -> "Volcanic Ash"
                "TS" -> "Thunderstorm"
                "SQ" -> "Squall"
                "FC" -> "Funnel Cloud"
                "SS" -> "Sandstorm"
                "DS" -> "Duststorm"
                "PO" -> "Dust/Sand Whirls"
                else -> if (descriptor.isNotEmpty()) "$descriptor$phenomenon" else phenomenon
            }
            
            if (description.isNotEmpty() && description !in precipTypes) {
                precipTypes.add(description)
            }
        }
        
        Log.d(TAG, "Parsed precipitation types: $precipTypes")
        return precipTypes
    }

    private fun parseVisibilityFromJson(metarObject: org.json.JSONObject): Double {
        try {
            val visib = metarObject.optString("visib", "10+")
            return when {
                visib == "10+" -> 10.0
                visib.contains("/") -> {
                    // Handle fractional visibility like "1/2"
                    val parts = visib.split("/")
                    if (parts.size == 2) {
                        parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0) ?: 10.0
                    } else 10.0
                }
                else -> visib.toDoubleOrNull() ?: 10.0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing visibility from JSON: ${e.message}")
            return 10.0
        }
    }

    private fun enrichCloudLayers(metar: MetarData): MetarData {
        return try {
            val hasAnyCeiling = metar.cloudLayers.any { it.coverage in listOf("BKN", "OVC", "VV") }
            val hasAnyLayer = metar.cloudLayers.isNotEmpty() && !(metar.cloudLayers.size == 1 && metar.cloudLayers[0].coverage == "CLR")
            if (hasAnyLayer && hasAnyCeiling) return metar
            if (metar.rawText.isNullOrBlank()) return metar
            val parsed = parseCloudLayersFromRawText(metar.rawText)
            if (parsed.isNotEmpty()) metar.copy(cloudLayers = parsed) else metar
        } catch (e: Exception) {
            Log.w(TAG, "enrichCloudLayers failed: ${e.message}")
            metar
        }
    }

    private fun createDefaultAirport(): Airport {
        return Airport(
            icao = "KSUE",
            name = "Door County Cherryland Airport",
            latitude = 44.8435,
            longitude = -87.4215,
            airspaceClass = "E",
            airspaceFloor = 0,
            airspaceCeiling = 1200
        )
    }
} 
