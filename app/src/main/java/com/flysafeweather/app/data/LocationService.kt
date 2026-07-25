package com.flysafeweather.app.data



import android.Manifest

import android.content.Context

import android.content.pm.PackageManager

import android.location.Location

import android.os.Build

import android.util.Log

import androidx.core.app.ActivityCompat

import com.google.android.gms.location.FusedLocationProviderClient

import com.google.android.gms.location.LocationServices

import com.google.android.gms.location.Priority

import com.google.android.gms.tasks.CancellationTokenSource

import kotlinx.coroutines.suspendCancellableCoroutine

import kotlin.coroutines.resume



class LocationService(private val context: Context) {

    private val TAG = "LocationService"

    private val fusedLocationClient: FusedLocationProviderClient =

        LocationServices.getFusedLocationProviderClient(context)

    private val isEmulator = Build.FINGERPRINT.contains("generic") ||

        Build.FINGERPRINT.startsWith("sdk_")



    fun hasLocationPermission(): Boolean =

        ActivityCompat.checkSelfPermission(

            context,

            Manifest.permission.ACCESS_FINE_LOCATION

        ) == PackageManager.PERMISSION_GRANTED ||

            ActivityCompat.checkSelfPermission(

                context,

                Manifest.permission.ACCESS_COARSE_LOCATION

            ) == PackageManager.PERMISSION_GRANTED



    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->

        try {

            if (isEmulator) {

                continuation.resume(

                    Location("TEST_PROVIDER").apply {

                        latitude = 33.6367

                        longitude = -84.4281

                        altitude = 1026.0

                        verticalAccuracyMeters = 1.0f

                    }

                )

                return@suspendCancellableCoroutine

            }



            if (!hasLocationPermission()) {

                Log.w(TAG, "Location permission not granted")

                continuation.resume(null)

                return@suspendCancellableCoroutine

            }



            @Suppress("MissingPermission")

            fusedLocationClient.lastLocation

                .addOnSuccessListener { lastKnown ->

                    if (lastKnown != null) {

                        Log.d(TAG, "Last known: ${lastKnown.latitude}, ${lastKnown.longitude}")

                        continuation.resume(lastKnown)

                    } else {

                        requestFreshLocation(continuation)

                    }

                }

                .addOnFailureListener { e ->

                    Log.w(TAG, "lastLocation failed: ${e.message}")

                    requestFreshLocation(continuation)

                }

        } catch (e: Exception) {

            Log.e(TAG, "Error in getCurrentLocation", e)

            continuation.resume(null)

        }

    }



    @Suppress("MissingPermission")

    private fun requestFreshLocation(continuation: kotlin.coroutines.Continuation<Location?>) {

        val cancellationToken = CancellationTokenSource().token

        fusedLocationClient.getCurrentLocation(

            Priority.PRIORITY_BALANCED_POWER_ACCURACY,

            cancellationToken

        ).addOnSuccessListener { location ->

            if (location != null) {

                Log.d(TAG, "Fresh location: ${location.latitude}, ${location.longitude}")

            } else {

                Log.w(TAG, "getCurrentLocation returned null — enable GPS on device")

            }

            continuation.resume(location)

        }.addOnFailureListener { e ->

            Log.e(TAG, "getCurrentLocation failed: ${e.message}")

            continuation.resume(null)

        }

    }

}



/** GPS if available, otherwise airport coordinates for sun times / airspace checks. */

fun resolveLocationForWeather(gps: Location?, airport: Airport?): Location? {

    if (gps != null) return gps

    if (airport == null) return null

    return Location("airport").apply {

        latitude = airport.latitude

        longitude = airport.longitude

    }

}


