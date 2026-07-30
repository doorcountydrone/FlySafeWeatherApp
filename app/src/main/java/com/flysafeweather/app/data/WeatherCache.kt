package com.flysafeweather.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val Context.weatherCache: DataStore<Preferences> by preferencesDataStore(name = "weather_cache")

class WeatherCache(private val context: Context) {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .create()

    companion object {
        private val METAR_DATA = stringPreferencesKey("metar_data")
        private val TFR_DATA = stringPreferencesKey("tfr_data")
        private val KP_INDEX_DATA = stringPreferencesKey("kp_index_data")
        private val LAST_UPDATE = longPreferencesKey("last_update")
        private const val CACHE_DURATION_HOURS = 1L
    }

    suspend fun cacheMetarData(data: MetarData) {
        context.weatherCache.edit { preferences ->
            preferences[METAR_DATA] = gson.toJson(data)
            preferences[LAST_UPDATE] = Instant.now().toEpochMilli()
        }
    }

    suspend fun cacheTfrData(data: List<TfrData>) {
        context.weatherCache.edit { preferences ->
            preferences[TFR_DATA] = gson.toJson(data)
            preferences[LAST_UPDATE] = Instant.now().toEpochMilli()
        }
    }

    suspend fun cacheKpIndexData(data: KpIndexData) {
        context.weatherCache.edit { preferences ->
            preferences[KP_INDEX_DATA] = gson.toJson(data)
            preferences[LAST_UPDATE] = Instant.now().toEpochMilli()
        }
    }

    suspend fun getCachedMetarData(): MetarData? {
        return try {
            val preferences = context.weatherCache.data.first()
            val jsonData = preferences[METAR_DATA] ?: return null
            val lastUpdate = preferences[LAST_UPDATE] ?: return null
            
            if (isCacheValid(lastUpdate)) {
                gson.fromJson(jsonData, MetarData::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCachedTfrData(): List<TfrData>? {
        return try {
            val preferences = context.weatherCache.data.first()
            val jsonData = preferences[TFR_DATA] ?: return null
            val lastUpdate = preferences[LAST_UPDATE] ?: return null
            
            if (isCacheValid(lastUpdate)) {
                gson.fromJson(jsonData, Array<TfrData>::class.java).toList()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCachedKpIndexData(): KpIndexData? {
        return try {
            val preferences = context.weatherCache.data.first()
            val jsonData = preferences[KP_INDEX_DATA] ?: return null
            val lastUpdate = preferences[LAST_UPDATE] ?: return null
            
            if (isCacheValid(lastUpdate)) {
                gson.fromJson(jsonData, KpIndexData::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isCacheValid(lastUpdate: Long): Boolean {
        val lastUpdateInstant = Instant.ofEpochMilli(lastUpdate)
        val hoursSinceUpdate = ChronoUnit.HOURS.between(lastUpdateInstant, Instant.now())
        return hoursSinceUpdate < CACHE_DURATION_HOURS
    }

    suspend fun clearCache() {
        context.weatherCache.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun isCacheAvailable(): Boolean {
        val preferences = context.weatherCache.data.first()
        val lastUpdate = preferences[LAST_UPDATE] ?: return false
        return isCacheValid(lastUpdate)
    }
} 
