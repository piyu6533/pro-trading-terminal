package com.example.protradingterminal

import androidx.lifecycle.MutableLiveData

data class Position(
    val symbol: String,
    var quantity: Int,
    var avgPrice: Float,
    var lastPrice: Float = 0f
) {
    val pnl: Float
        get() = (lastPrice - avgPrice) * quantity
}

object PortfolioManager {
    val positions = MutableLiveData<MutableMap<String, Position>>(mutableMapOf())
    val totalPnl = MutableLiveData<Float>(0f)

    fun addTrade(symbol: String, type: String, quantity: Int, price: Float) {
        val currentPositions = positions.value ?: mutableMapOf()
        val pos = currentPositions.getOrPut(symbol) { Position(symbol, 0, 0f) }

        if (type == "BUY") {
            val totalCost = (pos.avgPrice * pos.quantity) + (price * quantity)
            pos.quantity += quantity
            pos.avgPrice = totalCost / pos.quantity
        } else {
            // Simplify: Just reduce quantity for SELL
            pos.quantity -= quantity
            if (pos.quantity <= 0) {
                currentPositions.remove(symbol)
            }
        }
        
        pos.lastPrice = price
        positions.postValue(currentPositions)
        calculateTotalPnl()
    }

    fun updatePrice(symbol: String, price: Float) {
        val currentPositions = positions.value ?: return
        if (currentPositions.containsKey(symbol)) {
            currentPositions[symbol]?.lastPrice = price
            positions.postValue(currentPositions)
            calculateTotalPnl()
        }
    }

    private fun calculateTotalPnl() {
        var total = 0f
        positions.value?.values?.forEach {
            total += it.pnl
        }
        totalPnl.postValue(total)
    }
}