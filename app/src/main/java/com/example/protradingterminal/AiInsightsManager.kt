package com.example.protradingterminal

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.GenerativeBackend
import java.util.Locale

class AiInsightsManager {
    // Initialize the Gemini model via Firebase AI Logic
    // We use the Gemini Developer API backend as recommended
    private val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel("gemini-1.5-flash")

    suspend fun getMarketAnalysis(
        symbol: String,
        price: Float,
        pcr: Double,
        maxPain: Int,
        gex: Double,
        signal: String
    ): String? {
        val prompt = """
            You are a professional NSE/BSE India options trader. 
            Analyze the following data for $symbol:
            - Current Price: ₹$price
            - Put-Call Ratio (PCR): $pcr
            - Max Pain Strike: $maxPain
            - Gamma Exposure (GEX): ${String.format(Locale.US, "%.2f", gex / 10000000.0)} Cr
            - Trend Signal: $signal
            
            Provide a 2-sentence concise "Pro Insight" for a retail trader. 
            Focus on where the support/resistance might be based on Max Pain and PCR.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text
        } catch (e: Exception) {
            "Analysis currently unavailable. Please ensure Firebase AI Logic is set up in the console."
        }
    }
}