package com.example.protradingterminal

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import okhttp3.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var chart: CandleStickChart
    private lateinit var pnlValue: TextView
    private lateinit var aiSignal: TextView
    private lateinit var niftyPriceText: TextView
    private lateinit var apiService: MarketApiService
    private lateinit var webSocket: WebSocket
    
    // Dynamic Chart Data
    private val chartEntries = ArrayList<CandleEntry>()
    private var lastPrice = 22147.90f
    private var currentMinute = -1L

    companion object {
        private const val BASE_URL = "https://trading-api-tj2l.onrender.com/"
        private const val WS_URL = "wss://trading-api-tj2l.onrender.com/ws"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.candleStickChart)
        pnlValue = findViewById(R.id.pnlValue)
        aiSignal = findViewById(R.id.aiSignal)
        niftyPriceText = findViewById(R.id.niftyPriceText)
        
        // Initialize chart with some starting historical candles
        initChartData()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(MarketApiService::class.java)

        setupWebSocket(okHttpClient)
        startMarketDataUpdates()
    }

    private fun initChartData() {
        // Mock historical data
        chartEntries.add(CandleEntry(0f, 22160f, 22130f, 22140f, 22150f))
        chartEntries.add(CandleEntry(1f, 22170f, 22140f, 22150f, 22165f))
        chartEntries.add(CandleEntry(2f, 22165f, 22135f, 22165f, 22145f))
        chartEntries.add(CandleEntry(3f, 22155f, 22125f, 22145f, 22150f))
        updateChartDisplay()
    }

    private fun setupWebSocket(client: OkHttpClient) {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val price = json.getDouble("price").toFloat()
                    
                    runOnUiThread {
                        niftyPriceText.text = "₹$price"
                        handlePriceUpdate(price)
                    }
                } catch (e: Exception) {
                    Log.e("TradingApp", "WS Parse Error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("TradingApp", "WS Failure: ${t.message}. Reconnecting...")
                handler.postDelayed({ setupWebSocket(client) }, 5000)
            }
        })
    }

    private fun handlePriceUpdate(price: Float) {
        val now = System.currentTimeMillis() / 1000 / 60 // Current minute since epoch
        
        if (currentMinute == -1L || now > currentMinute) {
            // New Minute: Create a new candle
            currentMinute = now
            val newIndex = chartEntries.size.toFloat()
            chartEntries.add(CandleEntry(newIndex, price, price, price, price))
            
            // Limit chart to last 20 candles for performance
            if (chartEntries.size > 20) chartEntries.removeAt(0)
            
        } else {
            // Same Minute: Update current candle
            val lastEntry = chartEntries.last()
            if (price > lastEntry.high) lastEntry.high = price
            if (price < lastEntry.low) lastEntry.low = price
            lastEntry.close = price
        }
        
        updateChartDisplay()
    }

    private fun updateChartDisplay() {
        val dataSet = CandleDataSet(chartEntries, "NIFTY Live")
        dataSet.shadowColor = Color.LTGRAY
        dataSet.shadowWidth = 0.8f
        dataSet.decreasingColor = Color.RED
        dataSet.decreasingPaintStyle = Paint.Style.FILL
        dataSet.increasingColor = Color.parseColor("#00FF66")
        dataSet.increasingPaintStyle = Paint.Style.FILL
        dataSet.neutralColor = Color.BLUE
        dataSet.valueTextColor = Color.WHITE
        dataSet.setDrawValues(false)

        val candleData = CandleData(dataSet)
        chart.data = candleData
        chart.setBackgroundColor(Color.parseColor("#121212"))
        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.xAxis.textColor = Color.WHITE
        chart.axisLeft.textColor = Color.WHITE
        chart.axisRight.textColor = Color.WHITE
        
        // Auto-scroll to latest candle
        chart.setVisibleXRangeMaximum(10f)
        chart.moveViewToX(chartEntries.size.toFloat())
        
        chart.invalidate() 
    }

    private fun startMarketDataUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchMarketData()
                handler.postDelayed(this, 10000) 
            }
        }, 5000)
    }

    private fun fetchMarketData() {
        // Fetch AI Signal
        apiService.getAiSignal().enqueue(object : Callback<AiSignalResponse> {
            override fun onResponse(call: Call<AiSignalResponse>, response: Response<AiSignalResponse>) {
                if (response.isSuccessful) {
                    val signal = response.body()?.signal ?: "HOLD"
                    runOnUiThread {
                        aiSignal.text = signal
                        when (signal) {
                            "BUY" -> aiSignal.setTextColor(Color.parseColor("#00FF66"))
                            "SELL" -> aiSignal.setTextColor(Color.RED)
                            else -> aiSignal.setTextColor(Color.YELLOW)
                        }
                    }
                }
            }
            override fun onFailure(call: Call<AiSignalResponse>, t: Throwable) {}
        })

        // Fetch PCR Data
        apiService.getPcrData(call = 500000.0, put = 650000.0).enqueue(object : Callback<PcrResponse> {
            override fun onResponse(call: Call<PcrResponse>, response: Response<PcrResponse>) {
                if (response.isSuccessful) {
                    val pcrData = response.body()
                    runOnUiThread {
                        pnlValue.text = "PCR: ${pcrData?.PCR} (${pcrData?.sentiment})"
                    }
                }
            }
            override fun onFailure(call: Call<PcrResponse>, t: Throwable) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::webSocket.isInitialized) webSocket.close(1000, "Closed")
    }
}