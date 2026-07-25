package com.flysafeweather.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SunCalculator {
    data class SunTimes(
        val sunrise: String,
        val sunset: String,
        val civilTwilightBegin: String,
        val civilTwilightEnd: String,
        val timeZoneLabel: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val displayFormatter = DateTimeFormatter.ofPattern("h:mm a")

    suspend fun calculateSunriseSunset(
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SunTimes = withContext(Dispatchers.IO) {
        val date = LocalDate.now(zoneId)
        Log.d(TAG, "Sun times for lat=$latitude, lon=$longitude, date=$date, zone=${zoneId.id}")

        fetchFromOpenMeteo(latitude, longitude, zoneId)?.let {
            Log.d(TAG, "Using Open-Meteo sun times")
            return@withContext it
        }

        Log.w(TAG, "Open-Meteo unavailable; using local solar calculation")
        SolarTimesCalculator.calculate(latitude, longitude, zoneId, date).copy(
            timeZoneLabel = "${zoneId.id} (calculated offline)"
        )
    }

    private fun fetchFromOpenMeteo(
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId
    ): SunTimes? {
        return try {
            val tzParam = URLEncoder.encode(zoneId.id, Charsets.UTF_8.name())
            val url =
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&daily=sunrise,sunset&timezone=$tzParam&forecast_days=1"

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Open-Meteo HTTP ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val daily = json.getJSONObject("daily")
            val sunriseIso = daily.getJSONArray("sunrise").getString(0)
            val sunsetIso = daily.getJSONArray("sunset").getString(0)
            val tzLabel = json.optString("timezone", zoneId.id)

            fun parseAndFormat(isoLocal: String): String {
                val timePart = isoLocal.substringAfter('T')
                return LocalTime.parse(timePart).format(displayFormatter)
            }

            val sunriseLt = LocalTime.parse(sunriseIso.substringAfter('T'))
            val sunsetLt = LocalTime.parse(sunsetIso.substringAfter('T'))

            SunTimes(
                sunrise = parseAndFormat(sunriseIso),
                sunset = parseAndFormat(sunsetIso),
                civilTwilightBegin = sunriseLt.minusMinutes(30).format(displayFormatter),
                civilTwilightEnd = sunsetLt.plusMinutes(30).format(displayFormatter),
                timeZoneLabel = tzLabel
            )
        } catch (e: Exception) {
            Log.w(TAG, "Open-Meteo fetch failed: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TAG = "SunCalculator"
    }
}
