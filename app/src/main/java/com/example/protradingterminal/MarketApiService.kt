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

// OI Heatmap data structure
data class OiHeatmapResponse(
    val heatmap: List<HeatmapEntry>
)

data class HeatmapEntry(
    val strike: Int,
    val call_oi: Long,
    val put_oi: Long
)

// Gamma Exposure data structure
data class GexResponse(
    val gamma_exposure: Double
)

// Max Pain data structure
data class MaxPainResponse(
    val max_pain: Int
)

// AI Signal data structure
data class AiSignalResponse(
    val signal: String,
    val confidence: Double
)

interface MarketApiService {
    
    @GET("pcr")
    fun getPcrData(
        @Query("call") call: Double,
        @Query("put") put: Double
    ): Call<PcrResponse>

    @GET("stock/{symbol}")
    fun getStockData(
        @Path("symbol") symbol: String
    ): Call<StockResponse>

    @GET("oi-heatmap")
    fun getOiHeatmap(): Call<OiHeatmapResponse>

    @GET("gamma-exposure")
    fun getGammaExposure(): Call<GexResponse>

    @GET("max-pain")
    fun getMaxPain(): Call<MaxPainResponse>

    @GET("ai-signal")
    fun getAiSignal(): Call<AiSignalResponse>
}