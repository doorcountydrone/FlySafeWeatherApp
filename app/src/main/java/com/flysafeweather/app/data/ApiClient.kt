package com.flysafeweather.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://api.sunrise-sunset.org/"
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val sunriseSunsetApi: SunriseSunsetApi = retrofit.create(SunriseSunsetApi::class.java)
} 
