package com.flysafeweather.app.data

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface MetarApi {
    @GET("api/data/metar")
    fun getMetar(
        @Query("ids") stationId: String
    ): Call<String>
} 
