package com.example.protradingterminal

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// Data class to match the response from your Python backend
data class PcrResponse(
    val PCR: Double,
    val sentiment: String,
    val call_oi: Double,
    val put_oi: Double
)

interface MarketApiService {
    @GET("pcr")
    fun getPcrData(
        @Query("call") call: Double,
        @Query("put") put: Double
    ): Call<PcrResponse>
}