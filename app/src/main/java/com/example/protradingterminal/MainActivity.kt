package com.example.protradingterminal

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chart = findViewById<CandleStickChart>(R.id.candleStickChart)
        setupChart(chart)
    }

    private fun setupChart(chart: CandleStickChart) {
        val entries = ArrayList<CandleEntry>()

        // Sample Trading Data (x, high, low, open, close)
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

        // Customize Chart Appearance
        chart.setBackgroundColor(Color.parseColor("#1E1E1E"))
        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.xAxis.textColor = Color.WHITE
        chart.axisLeft.textColor = Color.WHITE
        chart.axisRight.textColor = Color.WHITE
        
        chart.invalidate() // refresh
    }
}