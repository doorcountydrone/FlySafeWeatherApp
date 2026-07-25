package com.flysafeweather.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GnssData(
    val satellitesInView: Int = 0,
    val satellitesUsed: Int = 0,
    val hasGnssFix: Boolean = false,
    val gpsSatellites: Int = 0,
    val glonassSatellites: Int = 0,
    val galileoSatellites: Int = 0,
    val beidouSatellites: Int = 0,
    val qzssSatellites: Int = 0,
    val sbasSatellites: Int = 0
)

class GnssService(private val context: Context) {
    private val TAG = "GnssService"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _gnssData = MutableStateFlow(GnssData())
    val gnssData: StateFlow<GnssData> = _gnssData
    private var isCallbackRegistered = false

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var usedInFix = 0
            var totalSatellites = status.satelliteCount
            var gps = 0
            var glonass = 0
            var galileo = 0
            var beidou = 0
            var qzss = 0
            var sbas = 0
            
            Log.d(TAG, "=== GNSS Status Update ===")
            Log.d(TAG, "Total satellites detected: $totalSatellites")
            
            // Detailed satellite information
            for (i in 0 until status.satelliteCount) {
                val svid = status.getSvid(i)
                val constellation = status.getConstellationType(i)
                val signalStrength = status.getCn0DbHz(i)
                val used = status.usedInFix(i)
                
                if (used) usedInFix++
                
                when(constellation) {
                    GnssStatus.CONSTELLATION_GPS -> gps++
                    GnssStatus.CONSTELLATION_GLONASS -> glonass++
                    GnssStatus.CONSTELLATION_GALILEO -> galileo++
                    GnssStatus.CONSTELLATION_BEIDOU -> beidou++
                    GnssStatus.CONSTELLATION_QZSS -> qzss++
                    GnssStatus.CONSTELLATION_SBAS -> sbas++
                }
                
                Log.d(TAG, """
                    Satellite $i:
                    - System: $constellation
                    - SVID: $svid
                    - Signal Strength: $signalStrength dB-Hz
                    - Used in Fix: $used
                    - Elevation: ${status.getElevationDegrees(i)}°
                    - Azimuth: ${status.getAzimuthDegrees(i)}°
                """.trimIndent())
            }
            
            Log.d(TAG, """
                Constellation Breakdown:
                - GPS: $gps
                - GLONASS: $glonass
                - Galileo: $galileo
                - BeiDou: $beidou
                - QZSS: $qzss
                - SBAS: $sbas
            """.trimIndent())
            
            Log.d(TAG, "Satellites used in fix: $usedInFix")
            Log.d(TAG, "=== End Status Update ===")
            
            _gnssData.value = GnssData(
                satellitesInView = totalSatellites,
                satellitesUsed = usedInFix,
                hasGnssFix = usedInFix >= 4,
                gpsSatellites = gps,
                glonassSatellites = glonass,
                galileoSatellites = galileo,
                beidouSatellites = beidou,
                qzssSatellites = qzss,
                sbasSatellites = sbas
            )
        }

        override fun onStarted() {
            Log.d(TAG, "GNSS tracking started")
            checkGnssStatus()
        }

        override fun onStopped() {
            Log.d(TAG, "GNSS tracking stopped")
        }

        override fun onFirstFix(ttffMillis: Int) {
            Log.d(TAG, "GNSS first fix obtained in $ttffMillis ms")
        }
    }

    fun initializeGnssTracking() {
        try {
            Log.d(TAG, "Initializing GNSS tracking...")
            
            if (isCallbackRegistered) {
                Log.d(TAG, "GNSS callback already registered")
                return
            }
            
            // Check permissions first
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                
                try {
                    // Register the GNSS status callback
                    locationManager.registerGnssStatusCallback(gnssCallback, null)
                    isCallbackRegistered = true
                    Log.d(TAG, "Successfully registered GNSS status callback")
                    
                    // Request location updates to start GNSS tracking
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000, // 1 second minimum time between updates
                        0f,   // 0 meters minimum distance between updates
                        { /* Empty location listener */ },
                        null
                    )
                    Log.d(TAG, "Requested location updates to start GNSS tracking")
                    
                    // Force an immediate status check after registration
                    checkGnssStatus()
                    Log.d(TAG, "GNSS tracking initialization completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize GNSS tracking", e)
                }
            } else {
                when {
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                        Log.e(TAG, "Location permission not granted - GNSS tracking will not work")
                    !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                        Log.e(TAG, "GPS is not enabled in device settings")
                    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) ->
                        Log.e(TAG, "Device does not have GNSS hardware support")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GNSS tracking", e)
        }
    }

    init {
        initializeGnssTracking()
    }

    fun checkGnssStatus() {
        try {
            Log.d(TAG, "=== GNSS System Status Check ===")
            
            // If callback is not registered, try to initialize tracking
            if (!isCallbackRegistered) {
                Log.d(TAG, "GNSS callback not registered, initializing tracking")
                initializeGnssTracking()
                return
            }
            
            // Check if GPS is enabled
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            Log.d(TAG, "GPS Provider enabled: $gpsEnabled")

            // Check available providers
            val providers = locationManager.allProviders
            Log.d(TAG, "Available location providers: $providers")

            // Check permissions
            val hasFineLocation = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasCoarseLocation = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, """
                Permissions:
                - Fine Location: $hasFineLocation
                - Coarse Location: $hasCoarseLocation
            """.trimIndent())

            // Check GNSS hardware support
            val gnssHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
            Log.d(TAG, "Device has GNSS hardware: $gnssHardware")

            // Log GPS provider properties
            if (gpsEnabled) {
                try {
                    val gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER)
                    Log.d(TAG, """
                        GPS Provider Details:
                        - Accuracy: ${gpsProvider?.accuracy}
                        - Power Requirement: ${gpsProvider?.powerRequirement}
                        - Supports Altitude: ${gpsProvider?.supportsAltitude()}
                        - Supports Speed: ${gpsProvider?.supportsSpeed()}
                    """.trimIndent())
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting GPS provider details", e)
                }
            }

            Log.d(TAG, "=== End System Status Check ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error checking GNSS status", e)
        }
    }

    fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up GNSS service")
            if (isCallbackRegistered) {
                locationManager.unregisterGnssStatusCallback(gnssCallback)
                isCallbackRegistered = false
                // Remove location updates
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.removeUpdates { /* Empty location listener */ }
                }
                Log.d(TAG, "Successfully cleaned up GNSS tracking")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up GNSS service", e)
        }
    }
} 
