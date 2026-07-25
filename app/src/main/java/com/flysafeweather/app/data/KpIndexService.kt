package com.flysafeweather.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

data class KpIndexData(
    val kpIndex: Double,
    val timeTag: String
)

class KpIndexService(private val context: Context) {
    suspend fun fetchKpIndex(): KpIndexData = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://services.swpc.noaa.gov/json/planetary_k_index_1m.json")
            val response = url.readText()
            val jsonArray = JSONArray(response)
            
            // Get the most recent entry (last item in array)
            val lastEntry = jsonArray.getJSONObject(jsonArray.length() - 1)
            
            KpIndexData(
                kpIndex = lastEntry.getDouble("kp_index"),
                timeTag = lastEntry.getString("time_tag")
            )
        } catch (e: Exception) {
            throw Exception("Failed to fetch KP index: ${e.message}")
        }
    }
} 
