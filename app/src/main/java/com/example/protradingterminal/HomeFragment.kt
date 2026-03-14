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

class HomeFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var combinedChart: CombinedChart
    private lateinit var pnlValue: TextView
    private lateinit var aiSignal: TextView
    private lateinit var niftyPriceText: TextView
    private lateinit var etSymbol: EditText
    private lateinit var btnSearch: Button
    
    private lateinit var apiService: MarketApiService
    private var webSocket: WebSocket? = null
    
    private val chartEntries = ArrayList<CandleEntry>()
    private val smaEntries = ArrayList<Entry>()
    private var lastPrice = 22147.90f
    private var currentMinute = -1L
    private var activeSymbol = "NIFTY 50"

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
        
        view.findViewById<Button>(R.id.btnBuy).setOnClickListener { showOrderPopup("BUY") }
        view.findViewById<Button>(R.id.btnSell).setOnClickListener { showOrderPopup("SELL") }

        btnSearch.setOnClickListener {
            val symbol = etSymbol.text.toString().trim()
            if (symbol.isNotEmpty()) performSearch(symbol)
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
        
        return view
    }

    private fun performSearch(symbol: String) {
        apiService.getStockData(symbol).enqueue(object : Callback<StockResponse> {
            override fun onResponse(call: Call<StockResponse>, response: Response<StockResponse>) {
                if (response.isSuccessful) {
                    val stockData = response.body()?.quoteResponse?.result?.firstOrNull()
                    if (stockData != null) {
                        activity?.runOnUiThread {
                            activeSymbol = stockData.shortName ?: symbol
                            lastPrice = stockData.regularMarketPrice?.toFloat() ?: lastPrice
                            niftyPriceText.text = "₹${stockData.regularMarketPrice ?: lastPrice}"
                            chartEntries.clear()
                            smaEntries.clear()
                            currentMinute = -1L
                            initChartData()
                        }
                    }
                }
            }
            override fun onFailure(call: Call<StockResponse>, t: Throwable) {}
        })
    }

    private fun showOrderPopup(type: String) {
        Toast.makeText(context, "Order Confirmed: $type $activeSymbol at ₹$lastPrice", Toast.LENGTH_LONG).show()
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
                        activity?.runOnUiThread {
                            niftyPriceText.text = "₹$price"
                            handlePriceUpdate(price)
                        }
                    }
                } catch (e: Exception) {}
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
        candleDataSet.shadowColor = Color.LTGRAY
        candleDataSet.shadowWidth = 0.8f
        candleDataSet.decreasingColor = Color.RED
        candleDataSet.decreasingPaintStyle = Paint.Style.FILL
        candleDataSet.increasingColor = Color.parseColor("#25A750")
        candleDataSet.increasingPaintStyle = Paint.Style.FILL
        candleDataSet.neutralColor = Color.BLUE
        candleDataSet.setDrawValues(false)
        combinedData.setData(CandleData(candleDataSet))

        if (chartEntries.size >= 3) {
            smaEntries.clear()
            for (i in 2 until chartEntries.size) {
                val avg = (chartEntries[i].close + chartEntries[i-1].close + chartEntries[i-2].close) / 3
                smaEntries.add(Entry(i.toFloat(), avg))
            }
            val lineDataSet = LineDataSet(smaEntries, "SMA (3)")
            lineDataSet.color = Color.BLUE
            lineDataSet.lineWidth = 2f
            lineDataSet.setDrawCircles(false)
            lineDataSet.setDrawValues(false)
            combinedData.setData(LineData(lineDataSet))
        }

        combinedChart.data = combinedData
        combinedChart.setBackgroundColor(Color.WHITE)
        combinedChart.description.isEnabled = false
        combinedChart.legend.textColor = Color.BLACK
        combinedChart.xAxis.textColor = Color.BLACK
        combinedChart.axisLeft.textColor = Color.BLACK
        combinedChart.axisRight.textColor = Color.BLACK
        combinedChart.moveViewToX(chartEntries.size.toFloat())
        combinedChart.invalidate() 
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

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Fragment Destroyed")
    }
}