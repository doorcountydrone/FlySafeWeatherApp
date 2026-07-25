package com.flysafeweather.app.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DroneLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val timestamp: Long
)

class RemoteIdService(private val context: Context) {
    private val TAG = "REMOTE_ID_DEBUG"
    private var bluetoothScanner: BluetoothLeScanner? = null
    private val _droneLocations = MutableStateFlow<List<DroneLocation>>(emptyList())
    val droneLocations: StateFlow<List<DroneLocation>> = _droneLocations

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                parseRemoteIdData(result)?.let { drone ->
                    val currentDrones = _droneLocations.value.toMutableList()
                    val index = currentDrones.indexOfFirst { it.id == drone.id }
                    if (index >= 0) {
                        currentDrones[index] = drone
                    } else {
                        currentDrones.add(drone)
                    }
                    _droneLocations.value = currentDrones
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing Remote ID data", e)
            }
        }
    }

    fun startScanning() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission")
            return
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner

            val scanFilter = ScanFilter.Builder()
                // Add Remote ID service UUID when known
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bluetoothScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            Log.d(TAG, "Started scanning for Remote ID broadcasts")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Remote ID scan", e)
        }
    }

    fun stopScanning() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothScanner?.stopScan(scanCallback)
    }

    private fun parseRemoteIdData(result: ScanResult): DroneLocation? {
        // TODO: Implement parsing according to ASTM F3411-19 standard
        // This will depend on the exact format of the Remote ID broadcast
        return null
    }
} 
