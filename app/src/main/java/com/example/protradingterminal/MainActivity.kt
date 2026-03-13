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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var chart: CandleStickChart
    private lateinit var pnlValue: TextView
    private lateinit var apiService: MarketApiService

    companion object {
        private const val BASE_URL = "https://trading-api-tj2l.onrender.com/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.candleStickChart)
        pnlValue = findViewById(R.id.pnlValue)
        setupChart(chart)

        // Initialize Retrofit with the live Render URL
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(MarketApiService::class.java)

        // Start periodic updates
        startMarketDataUpdates()
    }

    private fun startMarketDataUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchMarketData()
                handler.postDelayed(this, 5000)
            }
        }, 5000)
    }

    private fun fetchMarketData() {
        Log.d("TradingApp", "Calling Backend API at $BASE_URL...")

        // Example: Call backend with some sample OI data
        apiService.getPcrData(call = 500000.0, put = 650000.0).enqueue(object : Callback<PcrResponse> {
            override fun onResponse(call: Call<PcrResponse>, response: Response<PcrResponse>) {
                if (response.isSuccessful) {
                    val pcrData = response.body()
                    Log.d("TradingApp", "PCR Received: ${pcrData?.PCR}")
                    
                    // Update UI with the new data
                    runOnUiThread {
                        pnlValue.text = "PCR: ${pcrData?.PCR} (${pcrData?.sentiment})"
                        if (pcrData?.sentiment == "BULLISH") {
                            pnlValue.setTextColor(Color.GREEN)
                        } else {
                            pnlValue.setTextColor(Color.RED)
                        }
                    }
                } else {
                    Log.e("TradingApp", "API Response Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<PcrResponse>, t: Throwable) {
                Log.e("TradingApp", "API Error: ${t.message}")
            }
        })
    }

    private fun setupChart(chart: CandleStickChart) {
        val entries = ArrayList<CandleEntry>()
        entries.add(CandleEntry(0f, 2500f, 2400f, 2420f, 2480f))
        entries.add(CandleEntry(1f, 2550f, 2450f, 2480f, 2520f))
        entries.add(CandleEntry(2f, 2530f, 2480f, 2520f, 2490f))
        entries.add(CandleEntry(3f, 2580f, 2490f, 2490f, 2560f))
        entries.add(CandleEntry(4f, 2600f, 2550f, 2560f, 2580f))

        val dataSet = CandleDataSet(entries, "Reliance Price")
        dataSet.shadowColor = Color.DKGRAY
        dataSet.shadowWidth = 0.7f
        dataSet.decreasingColor = Color.RED
        dataSet.decreasingPaintStyle = Paint.Style.FILL
        dataSet.increasingColor = Color.GREEN
        dataSet.increasingPaintStyle = Paint.Style.FILL
        dataSet.neutralColor = Color.BLUE
        dataSet.valueTextColor = Color.WHITE

        val candleData = CandleData(dataSet)
        chart.data = candleData
        chart.setBackgroundColor(Color.parseColor("#1E1E1E"))
        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.xAxis.textColor = Color.WHITE
        chart.axisLeft.textColor = Color.WHITE
        chart.axisRight.textColor = Color.WHITE
        chart.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}