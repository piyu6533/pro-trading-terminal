package com.example.protradingterminal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OptionChainAdapter(private var heatmapEntries: List<HeatmapEntry>) :
    RecyclerView.Adapter<OptionChainAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCallOi: TextView = view.findViewById(R.id.tvCallOi)
        val tvStrike: TextView = view.findViewById(R.id.tvStrike)
        val tvPutOi: TextView = view.findViewById(R.id.tvPutOi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_option_chain, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = heatmapEntries[position]
        holder.tvCallOi.text = formatOi(entry.call_oi)
        holder.tvStrike.text = entry.strike.toString()
        holder.tvPutOi.text = formatOi(entry.put_oi)
    }

    override fun getItemCount() = heatmapEntries.size

    fun updateData(newEntries: List<HeatmapEntry>) {
        heatmapEntries = newEntries
        notifyDataSetChanged()
    }

    private fun formatOi(oi: Long?): String {
        if (oi == null) return "0.0M"
        return String.format("%.1fM", oi.toDouble() / 1000000.0)
    }
}