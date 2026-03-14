package com.example.protradingterminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

class PositionsFragment : Fragment() {

    private lateinit var rvPositions: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: PositionsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_positions, container, false)
        
        rvPositions = view.findViewById(R.id.rvPositions)
        emptyState = view.findViewById(R.id.emptyState)
        
        adapter = PositionsAdapter(emptyList())
        rvPositions.adapter = adapter

        PortfolioManager.positions.observe(viewLifecycleOwner) { positionsMap ->
            val positionList = positionsMap.values.toList()
            if (positionList.isEmpty()) {
                rvPositions.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                rvPositions.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                adapter.updateData(positionList)
            }
        }

        return view
    }
}