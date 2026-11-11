package com.idlix.data.api

import com.idlix.data.model.RedeemRequest
import com.idlix.data.model.RedeemResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface MajorplayApiService {

    @POST
    suspend fun redeemPlay(
        @Url url: String,
        @Header("Cookie") cookie: String? = null,
        @Header("Referer") referer: String = "https://z2.idlixku.com/",
        @Header("Origin") origin: String = "https://z2.idlixku.com",
        @Body request: RedeemRequest
    ): RedeemResponse
}
