package com.example.protradingterminal

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
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
            val query = etSymbol.text.toString().trim()
            if (query.isNotEmpty()) {
                val ticker = mapQueryToTicker(query)
                performSearch(ticker)
            } else {
                Toast.makeText(context, "Please enter a symbol", Toast.LENGTH_SHORT).show()
            }
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

    private fun mapQueryToTicker(query: String): String {
        return when (query.uppercase()) {
            "NIFTY", "NIFTY50", "NIFTY 50" -> "^NSEI"
            "BANKNIFTY", "BANK NIFTY" -> "^NSEBANK"
            "RELIANCE" -> "RELIANCE.NS"
            "TCS" -> "TCS.NS"
            "INFY", "INFOSYS" -> "INFY.NS"
            "HDFC", "HDFCBANK" -> "HDFCBANK.NS"
            else -> if (query.contains(".") || query.contains("^")) query else "${query.uppercase()}.NS"
        }
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

    private fun loadTradingViewChart(ticker: String) {
        val tvSymbol = when (ticker) {
            "^NSEI" -> "NSE:NIFTY"
            "^NSEBANK" -> "NSE:BANKNIFTY"
            else -> {
                if (ticker.contains(".NS")) "NSE:${ticker.substringBefore(".NS")}"
                else ticker
            }
        }

        val html = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background-color:#121212;">
                <div id="tradingview_chart" style="height:100vh;width:100vw;"></div>
                <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
                <script type="text/javascript">
                new TradingView.widget({
                    "autosize": true,
                    "symbol": "$tvSymbol",
                    "interval": "1",
                    "timezone": "Asia/Kolkata",
                    "theme": "dark",
                    "style": "1",
                    "locale": "in",
                    "toolbar_bg": "#1e1e1e",
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
        val stocks = listOf("RELIANCE.NS", "TCS.NS", "INFY.NS", "HDFCBANK.NS", "^NSEBANK")
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
        val ticker = data.symbol ?: return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor("#1E1E1E".toColorInt())
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 4)
            layoutParams = params
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performSearch(ticker)
            }
        }

        val nameView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = data.shortName ?: ticker
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val priceView = TextView(context).apply {
            val change = data.regularMarketChangePercent ?: 0.0
            val color = if (change >= 0) "#25A750".toColorInt() else "#D13A3B".toColorInt()
            val sign = if (change >= 0) "+" else ""
            
            text = "₹${data.regularMarketPrice}\n$sign${String.format(Locale.US, "%.2f", change)}%"
            setTextColor(color)
            gravity = android.view.Gravity.END
        }

        row.addView(nameView)
        row.addView(priceView)
        marketWatchList.addView(row)
        watchListViews[ticker] = priceView
    }

    private fun performSearch(ticker: String, isInitial: Boolean = false) {
        apiService.getStockData(ticker).enqueue(object : Callback<StockResponse> {
            override fun onResponse(call: Call<StockResponse>, response: Response<StockResponse>) {
                if (response.isSuccessful) {
                    val stockData = response.body()?.quoteResponse?.result?.firstOrNull()
                    if (stockData != null) {
                        activity?.runOnUiThread {
                            activeSymbol = stockData.shortName ?: ticker
                            activeTicker = ticker
                            headerSymbolText.text = activeSymbol
                            lastPrice = stockData.regularMarketPrice?.toFloat() ?: 0f
                            niftyPriceText.text = "₹$lastPrice"
                            
                            // Update global state for other fragments
                            PortfolioManager.activeTicker.postValue(activeTicker)
                            PortfolioManager.activeSymbolName.postValue(activeSymbol)
                            
                            loadTradingViewChart(activeTicker)
                            
                            if (!isInitial) {
                                Toast.makeText(context, "Loaded $activeSymbol", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            override fun onFailure(call: Call<StockResponse>, t: Throwable) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }
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
                                if (view != null) {
                                    val currentText = view.text.toString()
                                    val changePart = currentText.substringAfter("\n")
                                    view.text = "₹$price\n$changePart"
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