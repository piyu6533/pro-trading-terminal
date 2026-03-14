package com.example.protradingterminal

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class PositionsAdapter(private var positions: List<Position>) :
    RecyclerView.Adapter<PositionsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSymbol: TextView = view.findViewById(R.id.tvSymbol)
        val tvPnl: TextView = view.findViewById(R.id.tvPnl)
        val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        val tvLtp: TextView = view.findViewById(R.id.tvLtp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_position, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pos = positions[position]
        holder.tvSymbol.text = pos.symbol
        
        val pnl = pos.pnl
        holder.tvPnl.text = "${if (pnl >= 0) "+" else ""}₹${String.format(Locale.US, "%.2f", pnl)}"
        holder.tvPnl.setTextColor(if (pnl >= 0) Color.parseColor("#25A750") else Color.parseColor("#D13A3B"))
        
        holder.tvDetails.text = "Qty: ${pos.quantity} • Avg: ₹${String.format(Locale.US, "%.2f", pos.avgPrice)}"
        holder.tvLtp.text = "LTP: ₹${String.format(Locale.US, "%.2f", pos.lastPrice)}"
    }

    override fun getItemCount() = positions.size

    fun updateData(newPositions: List<Position>) {
        positions = newPositions
        notifyDataSetChanged()
    }
}