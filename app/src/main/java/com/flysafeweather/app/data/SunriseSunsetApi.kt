package com.flysafeweather.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface SunriseSunsetApi {
    @GET("json")
    suspend fun getSunriseSunset(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("formatted") formatted: Int = 0,
        @Query("date") date: String? = null
    ): SunriseSunsetResponse
}

data class SunriseSunsetResponse(
    val results: Results,
    val status: String
)

data class Results(
    val sunrise: String,
    val sunset: String,
    @SerializedName("civil_twilight_begin")
    val civilTwilightBegin: String? = null,
    @SerializedName("civil_twilight_end")
    val civilTwilightEnd: String? = null
)
