package com.flysafeweather.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.util.Log

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val DEFAULT_AIRPORT = stringPreferencesKey("default_airport")
        private val MANUAL_AIRPORT = stringPreferencesKey("manual_airport")
        private val TFR_RADIUS_NM = intPreferencesKey("tfr_radius_nm")
        val THEME_KEY = booleanPreferencesKey("theme_dark")
    }

    suspend fun saveDefaultAirport(code: String) {
        Log.d("PreferencesManager", "Saving default airport: $code")
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_AIRPORT] = code
        }
    }

    suspend fun saveManualAirport(code: String) {
        Log.d("PreferencesManager", "Saving manual airport: $code")
        context.dataStore.edit { preferences ->
            preferences[MANUAL_AIRPORT] = code
        }
    }

    val defaultAirport: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_AIRPORT] ?: "KSUE"
        }

    val manualAirport: Flow<String> = context.dataStore.data
        .map { preferences ->
            val value = preferences[MANUAL_AIRPORT] ?: ""
            Log.d("PreferencesManager", "Loading manual airport: $value")
            value
        }

    suspend fun saveThemePreference(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = isDark
        }
    }

    val themePreference: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: false  // Default to light theme
        }

    suspend fun saveTfrRadiusNm(nm: Int) {
        val radius = if (nm in TfrService.TFR_RADIUS_OPTIONS_NM) nm else TfrService.DEFAULT_TFR_RADIUS_NM
        Log.d("PreferencesManager", "Saving TFR search radius: ${radius}nm")
        context.dataStore.edit { preferences ->
            preferences[TFR_RADIUS_NM] = radius
        }
    }

    val tfrRadiusNm: Flow<Int> = context.dataStore.data
        .map { preferences ->
            val saved = preferences[TFR_RADIUS_NM] ?: TfrService.DEFAULT_TFR_RADIUS_NM
            if (saved in TfrService.TFR_RADIUS_OPTIONS_NM) saved else TfrService.DEFAULT_TFR_RADIUS_NM
        }
} 
