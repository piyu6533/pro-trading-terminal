package com.example.protradingterminal

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.*
import okhttp3.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var combinedChart: CombinedChart
    private lateinit var pnlValue: TextView
    private lateinit var aiSignal: TextView
    private lateinit var niftyPriceText: TextView
    private lateinit var etSymbol: EditText
    private lateinit var btnSearch: Button
    private lateinit var marketWatchList: LinearLayout
    
    private lateinit var apiService: MarketApiService
    private var webSocket: WebSocket? = null
    
    private val chartEntries = ArrayList<CandleEntry>()
    private val smaEntries = ArrayList<Entry>()
    private var lastPrice = 0.0f
    private var currentMinute = -1L
    private var activeSymbol = "NIFTY 50"
    private var activeTicker = "^NSEI"
    
    // Map to keep track of watchlist views for real-time updates
    private val watchListViews = HashMap<String, TextView>()

    companion object {
        private const val TAG = "TradingAppDebug"
        private const val BASE_URL = "https://trading-api-tj2l.onrender.com/"
        private const val WS_URL = "wss://trading-api-tj2l.onrender.com/ws"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        combinedChart = view.findViewById(R.id.combinedChart)
        pnlValue = view.findViewById(R.id.pnlValue)
        aiSignal = view.findViewById(R.id.aiSignal)
        niftyPriceText = view.findViewById(R.id.niftyPriceText)
        etSymbol = view.findViewById(R.id.etSymbol)
        btnSearch = view.findViewById(R.id.btnSearch)
        marketWatchList = view.findViewById(R.id.marketWatchList)
        
        view.findViewById<Button>(R.id.btnBuy).setOnClickListener { showOrderPopup("BUY") }
        view.findViewById<Button>(R.id.btnSell).setOnClickListener { showOrderPopup("SELL") }

        btnSearch.setOnClickListener {
            val symbol = etSymbol.text.toString().trim()
            if (symbol.isNotEmpty()) performSearch(symbol)
        }

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

        // Fetch real data for today immediately
        fetchInitialMarketData()
        setupWebSocket(okHttpClient)
        startMarketDataUpdates()
        
        return view
    }

    private fun fetchInitialMarketData() {
        // 1. Get real Nifty Price to start with
        performSearch("^NSEI", isInitial = true)
        
        // 2. Populate Watchlist with initial real data
        updateWatchlistItems()
    }

    private fun updateWatchlistItems() {
        val stocks = listOf("RELIANCE.NS", "TCS.NS", "INFY.NS", "HDFCBANK.NS")
        marketWatchList.removeAllViews()
        watchListViews.clear()
        
        for (symbol in stocks) {
            apiService.getStockData(symbol).enqueue(object : Callback<StockResponse> {
                override fun onResponse(call: Call<StockResponse>, response: Response<StockResponse>) {
                    val data = response.body()?.quoteResponse?.result?.firstOrNull()
                    if (data != null) {
                        activity?.runOnUiThread {
                            addWatchlistItem(data)
                        }
                    }
                }
                override fun onFailure(call: Call<StockResponse>, t: Throwable) {}
            })
        }
    }

    private fun addWatchlistItem(data: StockResult) {
        val symbol = data.symbol ?: return
        val row = TextView(context).apply {
            val change = data.regularMarketChangePercent ?: 0.0
            val color = if (change >= 0) Color.parseColor("#25A750") else Color.parseColor("#D13A3B")
            val sign = if (change >= 0) "+" else ""
            
            text = "$symbol   ₹${data.regularMarketPrice}   $sign${String.format(Locale.US, "%.2f", change)}%"
            setTextColor(color)
            setPadding(12, 12, 12, 12)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#F1F3F4"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 8)
            layoutParams = params
        }
        marketWatchList.addView(row)
        watchListViews[symbol] = row
    }

    private fun performSearch(symbol: String, isInitial: Boolean = false) {
        apiService.getStockData(symbol).enqueue(object : Callback<StockResponse> {
            override fun onResponse(call: Call<StockResponse>, response: Response<StockResponse>) {
                if (response.isSuccessful) {
                    val stockData = response.body()?.quoteResponse?.result?.firstOrNull()
                    if (stockData != null) {
                        activity?.runOnUiThread {
                            activeSymbol = stockData.shortName ?: symbol
                            activeTicker = symbol
                            lastPrice = stockData.regularMarketPrice?.toFloat() ?: 0f
                            niftyPriceText.text = "₹$lastPrice"
                            
                            chartEntries.clear()
                            smaEntries.clear()
                            currentMinute = -1L
                            initChartData()
                            
                            if (!isInitial) {
                                Toast.makeText(context, "Loaded $activeSymbol", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            override fun onFailure(call: Call<StockResponse>, t: Throwable) {}
        })
    }

    private fun initChartData() {
        if (lastPrice == 0f) return
        // Generate mock historical candles based on today's real price
        for (i in 0..3) {
            val offset = (Math.random() * 20 - 10).toFloat()
            chartEntries.add(CandleEntry(i.toFloat(), lastPrice + offset + 5, lastPrice + offset - 5, lastPrice + offset - 2, lastPrice + offset + 2))
        }
        updateChartDisplay()
    }

    private fun setupWebSocket(client: OkHttpClient) {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    
                    activity?.runOnUiThread {
                        // 1. Update the active stock (Main Display and Chart)
                        if (json.has(activeTicker)) {
                            val price = json.getDouble(activeTicker).toFloat()
                            lastPrice = price
                            niftyPriceText.text = "₹$price"
                            handlePriceUpdate(price)
                        }
                        
                        // 2. Update all items in the watchlist
                        for (symbol in watchListViews.keys) {
                            if (json.has(symbol)) {
                                val price = json.getDouble(symbol)
                                val view = watchListViews[symbol]
                                // Note: For a true live feeling, we'd also need the change% from the stream
                                // For now we just update the price part of the string
                                val currentText = view?.text.toString()
                                val tickerPart = currentText.substringBefore("   ₹")
                                val changePart = currentText.substringAfterLast("%").let { if (it.isEmpty()) currentText.substringAfterLast("   ") else it }
                                // Simplified update logic for the demo
                                if (view != null) {
                                    val parts = currentText.split("   ")
                                    if (parts.size >= 3) {
                                        view.text = "${parts[0]}   ₹$price   ${parts[2]}"
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WS Parse Error: ${e.message}")
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                handler.postDelayed({ setupWebSocket(client) }, 10000)
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
            val lastEntry = chartEntries.lastOrNull()
            if (lastEntry != null) {
                if (price > lastEntry.high) lastEntry.high = price
                if (price < lastEntry.low) lastEntry.low = price
                lastEntry.close = price
            }
        }
        updateChartDisplay()
    }

    private fun updateChartDisplay() {
        val combinedData = CombinedData()
        val candleDataSet = CandleDataSet(chartEntries, "$activeSymbol Live")
        candleDataSet.apply {
            shadowColor = Color.DKGRAY
            shadowWidth = 0.8f
            decreasingColor = Color.parseColor("#D13A3B")
            decreasingPaintStyle = Paint.Style.FILL
            increasingColor = Color.parseColor("#25A750")
            increasingPaintStyle = Paint.Style.FILL
            neutralColor = Color.BLUE
            setDrawValues(false)
        }
        combinedData.setData(CandleData(candleDataSet))

        if (chartEntries.size >= 3) {
            smaEntries.clear()
            for (i in 2 until chartEntries.size) {
                val avg = (chartEntries[i].close + chartEntries[i-1].close + chartEntries[i-2].close) / 3
                smaEntries.add(Entry(i.toFloat(), avg))
            }
            val lineDataSet = LineDataSet(smaEntries, "SMA (3)").apply {
                color = Color.parseColor("#2196F3")
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
            }
            combinedData.setData(LineData(lineDataSet))
        }

        combinedChart.apply {
            data = combinedData
            setBackgroundColor(Color.WHITE)
            description.isEnabled = false
            legend.textColor = Color.BLACK
            xAxis.textColor = Color.BLACK
            axisLeft.textColor = Color.BLACK
            axisRight.textColor = Color.BLACK
            moveViewToX(chartEntries.size.toFloat())
            invalidate()
        }
    }

    private fun startMarketDataUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchMarketData()
                handler.postDelayed(this, 15000) 
            }
        }, 5000)
    }

    private fun fetchMarketData() {
        apiService.getAiSignal().enqueue(object : Callback<AiSignalResponse> {
            override fun onResponse(call: Call<AiSignalResponse>, response: Response<AiSignalResponse>) {
                if (response.isSuccessful) {
                    val signal = response.body()?.signal ?: "HOLD"
                    activity?.runOnUiThread {
                        aiSignal.text = signal
                        when (signal) {
                            "BUY" -> aiSignal.setTextColor(Color.parseColor("#25A750"))
                            "SELL" -> aiSignal.setTextColor(Color.parseColor("#D13A3B"))
                            else -> aiSignal.setTextColor(Color.GRAY)
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
                    activity?.runOnUiThread { pnlValue.text = "PCR: ${pcrData?.PCR} (${pcrData?.sentiment})" }
                }
            }
            override fun onFailure(call: Call<PcrResponse>, t: Throwable) {}
        })
    }

    private fun showOrderPopup(type: String) {
        Toast.makeText(context, "Order Confirmed: $type $activeSymbol at ₹$lastPrice", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Fragment Destroyed")
    }
}