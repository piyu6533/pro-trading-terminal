package com.example.protradingterminal

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AnalyseFragment : Fragment() {

    private lateinit var rvOptionChain: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var adapter: OptionChainAdapter
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var apiService: MarketApiService
    private var currentSymbol: String = "NIFTY"

    companion object {
        private const val BASE_URL = "https://trading-api-tj2l.onrender.com/"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_analyse, container, false)
        
        rvOptionChain = view.findViewById(R.id.rvOptionChain)
        tvTitle = view.findViewById(R.id.tvOptionChainTitle)
        adapter = OptionChainAdapter(emptyList())
        rvOptionChain.adapter = adapter

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(MarketApiService::class.java)

        // Observe the active symbol from PortfolioManager
        PortfolioManager.activeSymbolName.observe(viewLifecycleOwner) { name ->
            currentSymbol = name
            tvTitle.text = "Option Chain ($name)"
            fetchOptionChainData() // Fetch immediately on change
        }

        startDataUpdates()
        return view
    }

    private fun startDataUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchOptionChainData()
                handler.postDelayed(this, 10000)
            }
        }, 0)
    }

    private fun fetchOptionChainData() {
        // Fetch data for the currently active symbol
        apiService.getOiHeatmap(currentSymbol).enqueue(object : Callback<OiHeatmapResponse> {
            override fun onResponse(call: Call<OiHeatmapResponse>, response: Response<OiHeatmapResponse>) {
                if (response.isSuccessful) {
                    val heatmap = response.body()?.heatmap
                    if (heatmap != null) {
                        activity?.runOnUiThread {
                            adapter.updateData(heatmap)
                        }
                    }
                }
            }
            override fun onFailure(call: Call<OiHeatmapResponse>, t: Throwable) {
                Log.e("AnalyseFragment", "API Error: ${t.message}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }
}