package com.flysafeweather.app.data

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface TokenService {
    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun getToken(
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): Response<TokenResponse>
}

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
) 
