package com.example.protradingterminal

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
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
    private lateinit var combinedChart: CombinedChart
    private lateinit var pnlValue: TextView
    private lateinit var aiSignal: TextView
    private lateinit var niftyPriceText: TextView
    private lateinit var heatmapLayout: LinearLayout
    private lateinit var etSymbol: EditText
    private lateinit var btnSearch: Button
    
    private lateinit var apiService: MarketApiService
    private lateinit var webSocket: WebSocket
    
    private val chartEntries = ArrayList<CandleEntry>()
    private val smaEntries = ArrayList<Entry>()
    private var lastPrice = 22147.90f
    private var currentMinute = -1L
    private var activeSymbol = "NIFTY 50"

    companion object {
        private const val BASE_URL = "https://trading-api-tj2l.onrender.com/"
        private const val WS_URL = "wss://trading-api-tj2l.onrender.com/ws"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        combinedChart = findViewById(R.id.combinedChart)
        pnlValue = findViewById(R.id.pnlValue)
        aiSignal = findViewById(R.id.aiSignal)
        niftyPriceText = findViewById(R.id.niftyPriceText)
        heatmapLayout = findViewById(R.id.heatmapLayout)
        etSymbol = findViewById(R.id.etSymbol)
        btnSearch = findViewById(R.id.btnSearch)
        
        findViewById<Button>(R.id.btnBuy).setOnClickListener { showOrderPopup("BUY") }
        findViewById<Button>(R.id.btnSell).setOnClickListener { showOrderPopup("SELL") }

        btnSearch.setOnClickListener {
            val symbol = etSymbol.text.toString().trim()
            if (symbol.isNotEmpty()) performSearch(symbol)
            else Toast.makeText(this, "Please enter a symbol", Toast.LENGTH_SHORT).show()
        }
        
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

    private fun performSearch(symbol: String) {
        apiService.getStockData(symbol).enqueue(object : Callback<StockResponse> {
            override fun onResponse(call: Call<StockResponse>, response: Response<StockResponse>) {
                if (response.isSuccessful) {
                    val stockData = response.body()?.quoteResponse?.result?.firstOrNull()
                    if (stockData != null) {
                        runOnUiThread {
                            activeSymbol = stockData.shortName ?: symbol
                            lastPrice = stockData.regularMarketPrice.toFloat()
                            niftyPriceText.text = "₹${stockData.regularMarketPrice}"
                            chartEntries.clear()
                            smaEntries.clear()
                            currentMinute = -1L
                            initChartData()
                            Toast.makeText(this@MainActivity, "Showing data for $activeSymbol", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            override fun onFailure(call: Call<StockResponse>, t: Throwable) {}
        })
    }

    private fun showOrderPopup(type: String) {
        Toast.makeText(this, "Order Confirmed: $type $activeSymbol at ₹$lastPrice", Toast.LENGTH_LONG).show()
    }

    private fun initChartData() {
        chartEntries.add(CandleEntry(0f, lastPrice + 10, lastPrice - 20, lastPrice - 10, lastPrice + 5))
        chartEntries.add(CandleEntry(1f, lastPrice + 20, lastPrice - 10, lastPrice + 5, lastPrice + 15))
        chartEntries.add(CandleEntry(2f, lastPrice + 15, lastPrice - 15, lastPrice + 15, lastPrice - 5))
        chartEntries.add(CandleEntry(3f, lastPrice + 5, lastPrice - 25, lastPrice - 5, lastPrice))
        updateChartDisplay()
    }

    private fun setupWebSocket(client: OkHttpClient) {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val price = json.getDouble("price").toFloat()
                    if (activeSymbol.contains("NIFTY", ignoreCase = true)) {
                        lastPrice = price
                        runOnUiThread {
                            niftyPriceText.text = "₹$price"
                            handlePriceUpdate(price)
                        }
                    }
                } catch (e: Exception) {}
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                handler.postDelayed({ setupWebSocket(client) }, 5000)
            }
        })
    }

    private fun handlePriceUpdate(price: Float) {
        val now = System.currentTimeMillis() / 1000 / 60
        if (currentMinute == -1L || now > currentMinute) {
            currentMinute = now
            chartEntries.add(CandleEntry(chartEntries.size.toFloat(), price, price, price, price))
            if (chartEntries.size > 30) chartEntries.removeAt(0)
        } else {
            val lastEntry = chartEntries.last()
            if (price > lastEntry.high) lastEntry.high = price
            if (price < lastEntry.low) lastEntry.low = price
            lastEntry.close = price
        }
        updateChartDisplay()
    }

    private fun updateChartDisplay() {
        val combinedData = CombinedData()

        // 1. Candlestick Data
        val candleDataSet = CandleDataSet(chartEntries, "$activeSymbol Live")
        candleDataSet.shadowColor = Color.LTGRAY
        candleDataSet.shadowWidth = 0.8f
        candleDataSet.decreasingColor = Color.RED
        candleDataSet.decreasingPaintStyle = Paint.Style.FILL
        candleDataSet.increasingColor = Color.parseColor("#00FF66")
        candleDataSet.increasingPaintStyle = Paint.Style.FILL
        candleDataSet.neutralColor = Color.BLUE
        candleDataSet.setDrawValues(false)
        combinedData.setData(CandleData(candleDataSet))

        // 2. SMA (Simple Moving Average) Line Data
        if (chartEntries.size >= 3) {
            smaEntries.clear()
            for (i in 2 until chartEntries.size) {
                val avg = (chartEntries[i].close + chartEntries[i-1].close + chartEntries[i-2].close) / 3
                smaEntries.add(Entry(i.toFloat(), avg))
            }
            val lineDataSet = LineDataSet(smaEntries, "SMA (3)")
            lineDataSet.color = Color.YELLOW
            lineDataSet.lineWidth = 2f
            lineDataSet.setDrawCircles(false)
            lineDataSet.setDrawValues(false)
            combinedData.setData(LineData(lineDataSet))
        }

        combinedChart.data = combinedData
        combinedChart.setBackgroundColor(Color.parseColor("#121212"))
        combinedChart.description.isEnabled = false
        combinedChart.legend.textColor = Color.WHITE
        combinedChart.xAxis.textColor = Color.WHITE
        combinedChart.axisLeft.textColor = Color.WHITE
        combinedChart.axisRight.textColor = Color.WHITE
        
        combinedChart.setVisibleXRangeMaximum(15f)
        combinedChart.moveViewToX(chartEntries.size.toFloat())
        combinedChart.invalidate() 
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

        apiService.getPcrData(call = 500000.0, put = 650000.0).enqueue(object : Callback<PcrResponse> {
            override fun onResponse(call: Call<PcrResponse>, response: Response<PcrResponse>) {
                if (response.isSuccessful) {
                    val pcrData = response.body()
                    runOnUiThread { pnlValue.text = "PCR: ${pcrData?.PCR} (${pcrData?.sentiment})" }
                }
            }
            override fun onFailure(call: Call<PcrResponse>, t: Throwable) {}
        })

        apiService.getOiHeatmap().enqueue(object : Callback<OiHeatmapResponse> {
            override fun onResponse(call: Call<OiHeatmapResponse>, response: Response<OiHeatmapResponse>) {
                if (response.isSuccessful) {
                    val heatmap = response.body()?.heatmap
                    if (heatmap != null) runOnUiThread { updateHeatmapUI(heatmap) }
                }
            }
            override fun onFailure(call: Call<OiHeatmapResponse>, t: Throwable) {}
        })
    }

    private fun updateHeatmapUI(heatmap: List<HeatmapEntry>) {
        heatmapLayout.removeAllViews()
        for (entry in heatmap) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(8, 8, 8, 8)
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val strikeText = TextView(this)
            strikeText.layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT)
            strikeText.text = entry.strike.toString()
            strikeText.setTextColor(Color.WHITE)
            row.addView(strikeText)
            val callWidth = (entry.call_oi / 20000).toInt().coerceIn(10, 300)
            val callBar = View(this)
            val callParams = LinearLayout.LayoutParams(callWidth, 40)
            callParams.marginEnd = 16
            callBar.layoutParams = callParams
            callBar.setBackgroundColor(Color.RED)
            row.addView(callBar)
            val putWidth = (entry.put_oi / 20000).toInt().coerceIn(10, 300)
            val putBar = View(this)
            putBar.layoutParams = LinearLayout.LayoutParams(putWidth, 40)
            putBar.setBackgroundColor(Color.parseColor("#00FF66"))
            row.addView(putBar)
            heatmapLayout.addView(row)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::webSocket.isInitialized) webSocket.close(1000, "Activity Destroyed")
    }
}