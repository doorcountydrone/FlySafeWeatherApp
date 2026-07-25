package com.flysafeweather.app.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Airport(
    @SerializedName("icao") val icao: String,
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("airspaceClass") val airspaceClass: String,
    @SerializedName("airspaceFloor") val airspaceFloor: Int = 0,  // in feet AGL
    @SerializedName("airspaceCeiling") val airspaceCeiling: Int = 0,  // in feet AGL
    @SerializedName("city") val city: City? = null,
    @Transient var distance: Double = 0.0
): Serializable 
