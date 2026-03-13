package com.example.protradingterminal

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// PCR Response data structure
data class PcrResponse(
    val PCR: Double,
    val sentiment: String,
    val call_oi: Double,
    val put_oi: Double
)

// Stock Quote response data structure
data class StockResponse(
    val quoteResponse: QuoteResponseContent
)

data class QuoteResponseContent(
    val result: List<StockResult>
)

data class StockResult(
    val symbol: String,
    val regularMarketPrice: Double,
    val regularMarketChangePercent: Double,
    val shortName: String
)

interface MarketApiService {
    
    // To get the PCR calculation from your Render backend
    @GET("pcr")
    fun getPcrData(
        @Query("call") call: Double,
        @Query("put") put: Double
    ): Call<PcrResponse>

    // To get stock data from your Render backend (which proxy-calls Yahoo Finance)
    @GET("stock/{symbol}")
    fun getStockData(
        @Path("symbol") symbol: String
    ): Call<StockResponse>
}