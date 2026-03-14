package com.example.protradingterminal

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import okhttp3.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.Locale

class HomeFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tradingViewChart: WebView
    private lateinit var pnlValue: TextView
    private lateinit var aiSignal: TextView
    private lateinit var niftyPriceText: TextView
    private lateinit var headerSymbolText: TextView
    private lateinit var etSymbol: EditText
    private lateinit var btnSearch: Button
    private lateinit var marketWatchList: LinearLayout
    
    private lateinit var apiService: MarketApiService
    private var webSocket: WebSocket? = null
    
    private var lastPrice = 0.0f
    private var activeSymbol = "NIFTY 50"
    private var activeTicker = "^NSEI"
    
    private val watchListViews = HashMap<String, TextView>()

    companion object {
        private const val TAG = "TradingAppDebug"
        private const val BASE_URL = "https://trading-api-tj2l.onrender.com/"
        private const val WS_URL = "wss://trading-api-tj2l.onrender.com/ws"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        tradingViewChart = view.findViewById(R.id.tradingViewChart)
        pnlValue = view.findViewById(R.id.pnlValue)
        aiSignal = view.findViewById(R.id.aiSignal)
        niftyPriceText = view.findViewById(R.id.niftyPriceText)
        headerSymbolText = view.findViewById(R.id.headerSymbolText)
        etSymbol = view.findViewById(R.id.etSymbol)
        btnSearch = view.findViewById(R.id.btnSearch)
        marketWatchList = view.findViewById(R.id.marketWatchList)
        
        setupWebView()

        view.findViewById<Button>(R.id.btnBuy).setOnClickListener { 
            PortfolioManager.addTrade(activeTicker, "BUY", 1, lastPrice)
            showOrderPopup("BUY") 
        }
        view.findViewById<Button>(R.id.btnSell).setOnClickListener { 
            PortfolioManager.addTrade(activeTicker, "SELL", 1, lastPrice)
            showOrderPopup("SELL") 
        }

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

        PortfolioManager.totalPnl.observe(viewLifecycleOwner) { totalPnl ->
            pnlValue.text = "₹${String.format(Locale.US, "%.2f", totalPnl)}"
            pnlValue.setTextColor(if (totalPnl >= 0) "#25A750".toColorInt() else "#D13A3B".toColorInt())
        }

        fetchInitialMarketData()
        setupWebSocket(okHttpClient)
        startMarketDataUpdates()
        
        return view
    }

    private fun showOrderPopup(type: String) {
        Toast.makeText(context, "Order Confirmed: $type $activeSymbol at ₹$lastPrice", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = tradingViewChart.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        tradingViewChart.webViewClient = WebViewClient()
        loadTradingViewChart("NSE:NIFTY")
    }

    private fun loadTradingViewChart(symbol: String) {
        val tvSymbol = when (symbol) {
            "^NSEI" -> "NSE:NIFTY"
            "RELIANCE.NS" -> "NSE:RELIANCE"
            "TCS.NS" -> "NSE:TCS"
            "INFY.NS" -> "NSE:INFY"
            "HDFCBANK.NS" -> "NSE:HDFCBANK"
            else -> if (symbol.contains(".NS")) "NSE:${symbol.substringBefore(".NS")}" else symbol
        }

        val html = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;">
                <div id="tradingview_chart" style="height:100vh;width:100vw;"></div>
                <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
                <script type="text/javascript">
                new TradingView.widget({
                    "autosize": true,
                    "symbol": "$tvSymbol",
                    "interval": "1",
                    "timezone": "Asia/Kolkata",
                    "theme": "light",
                    "style": "1",
                    "locale": "in",
                    "toolbar_bg": "#f1f3f6",
                    "enable_publishing": false,
                    "hide_top_toolbar": false,
                    "save_image": false,
                    "container_id": "tradingview_chart"
                });
                </script>
            </body>
            </html>
        """.trimIndent()
        
        tradingViewChart.loadDataWithBaseURL("https://s3.tradingview.com", html, "text/html", "UTF-8", null)
    }

    private fun fetchInitialMarketData() {
        performSearch("^NSEI", isInitial = true)
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
            val color = if (change >= 0) "#25A750".toColorInt() else "#D13A3B".toColorInt()
            val sign = if (change >= 0) "+" else ""
            
            text = "$symbol   ₹${data.regularMarketPrice}   $sign${String.format(Locale.US, "%.2f", change)}%"
            setTextColor(color)
            setPadding(12, 12, 12, 12)
            textSize = 14f
            setBackgroundColor("#F1F3F4".toColorInt())
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
                            headerSymbolText.text = activeSymbol
                            lastPrice = stockData.regularMarketPrice?.toFloat() ?: 0f
                            niftyPriceText.text = "₹$lastPrice"
                            
                            loadTradingViewChart(activeTicker)
                            
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

    private fun setupWebSocket(client: OkHttpClient) {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    activity?.runOnUiThread {
                        if (json.has(activeTicker)) {
                            val price = json.getDouble(activeTicker).toFloat()
                            lastPrice = price
                            niftyPriceText.text = "₹$price"
                            PortfolioManager.updatePrice(activeTicker, lastPrice)
                        }
                        
                        for (symbol in watchListViews.keys) {
                            if (json.has(symbol)) {
                                val price = json.getDouble(symbol)
                                PortfolioManager.updatePrice(symbol, price.toFloat())
                                val view = watchListViews[symbol]
                                val currentText = view?.text.toString()
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
                            "BUY" -> aiSignal.setTextColor("#25A750".toColorInt())
                            "SELL" -> aiSignal.setTextColor("#D13A3B".toColorInt())
                            else -> aiSignal.setTextColor(Color.GRAY)
                        }
                    }
                }
            }
            override fun onFailure(call: Call<AiSignalResponse>, t: Throwable) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Fragment Destroyed")
    }
}