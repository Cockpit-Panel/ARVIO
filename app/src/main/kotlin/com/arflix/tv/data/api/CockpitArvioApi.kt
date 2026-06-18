package com.arflix.tv.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface CockpitArvioApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: ArvioLoginRequest): ArvioLoginResponse

    @GET("portals.php")
    suspend fun getPortals(): ArvioPortalsResponse

    @GET("account.php")
    suspend fun getAccountSyncPayload(@Header("Authorization") authorization: String): ArvioAccountSyncPayload
}
