package com.example.protradingterminal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

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
        val context = holder.itemView.context
        holder.tvSymbol.text = pos.symbol
        
        val pnl = pos.pnl
        val sign = if (pnl >= 0) "+" else ""
        holder.tvPnl.text = context.getString(R.string.pnl_display_format, sign, pnl)
        holder.tvPnl.setTextColor(if (pnl >= 0) "#25A750".toColorInt() else "#D13A3B".toColorInt())
        
        holder.tvDetails.text = context.getString(R.string.qty_details_format, pos.quantity, pos.avgPrice)
        holder.tvLtp.text = context.getString(R.string.ltp_format, pos.lastPrice)
    }

    override fun getItemCount() = positions.size

    fun updateData(newPositions: List<Position>) {
        positions = newPositions
        notifyDataSetChanged()
    }
}