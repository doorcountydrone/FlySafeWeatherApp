package com.flysafeweather.app.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import com.google.gson.JsonArray
import retrofit2.http.Query
import com.google.gson.JsonObject

interface TfrApiService {
    @GET("notamapi/v1/notams")
    suspend fun getTfrs(
        @Query("responseFormat") responseFormat: String = "geoJson",
        @Query("locationLatitude") lat: Double = 44.8435,
        @Query("locationLongitude") lon: Double = -87.4215,
        @Query("locationRadius") radius: Int = 100,
        @Query("featureType") featureType: String = "AIRSPACE",
        @Query("sortBy") sortBy: String = "effectiveStartDate",
        @Query("pageSize") pageSize: Int = 100
    ): Response<JsonObject>
} 
