import os
import joblib
import requests
import pandas as pd
from fastapi import FastAPI, WebSocket
from pydantic import BaseModel
import asyncio
import numpy as np

app = FastAPI()

# Global variables for model
model = None
model_loaded = False

def load_ai_model():
    global model, model_loaded
    if model_loaded:
        return True
    try:
        BASE_DIR = os.path.dirname(os.path.abspath(__file__))
        model_path = os.path.join(BASE_DIR, "..", "ai_model", "trading_ai.pkl")
        if os.path.exists(model_path):
            model = joblib.load(model_path)
            model_loaded = True
            return True
    except Exception as e:
        print(f"❌ Lazy Load Failed: {e}")
    return False

# Health check for Render
@app.get("/")
def health_check():
    return {"status": "alive", "model_ready": model_loaded}

# --- ANALYTICS LOGIC ---
def calculate_pcr(put_oi: float, call_oi: float):
    if call_oi == 0: return 0.0
    return round(put_oi / call_oi, 2)

@app.get("/pcr")
def get_pcr_calculation(call: float, put: float):
    pcr_value = calculate_pcr(put, call)
    sentiment = "BULLISH" if pcr_value > 1.1 else "BEARISH" if pcr_value < 0.7 else "NEUTRAL"
    return {"PCR": pcr_value, "sentiment": sentiment, "call_oi": call, "put_oi": put}

@app.get("/ai-signal")
def get_ai_signal():
    if not model_loaded and not load_ai_model():
        return {"signal": "BUY", "confidence": 0.85, "note": "fallback"}
    
    features = [[40.0, 1.2, 200000.0, 5.5, 1.1, 0.6, 1]]
    try:
        prediction = model.predict(features)[0]
        signals = {1: "BUY", 0: "HOLD", -1: "SELL"}
        return {"signal": signals.get(prediction, "HOLD")}
    except Exception as e:
        return {"error": str(e), "signal": "HOLD"}

@app.get("/oi-heatmap")
def get_oi_heatmap():
    data = [
        {"strike": 22000, "call_oi": 1200000, "put_oi": 800000},
        {"strike": 22100, "call_oi": 900000, "put_oi": 1500000},
    ]
    return {"heatmap": data}

@app.get("/stock/{symbol}")
def get_stock(symbol: str):
    url = f"https://query1.finance.yahoo.com/v7/finance/quote?symbols={symbol}"
    try:
        response = requests.get(url, headers={"User-Agent": "Mozilla/5.0"})
        return response.json()
    except Exception as e:
        return {"error": str(e)}

# --- MULTI-SYMBOL WEBSOCKET ---
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    # Base prices for simulation
    prices = {
        "^NSEI": 22147.90,
        "RELIANCE.NS": 2980.00,
        "TCS.NS": 3850.00,
        "INFY.NS": 1420.00,
        "HDFCBANK.NS": 1610.00
    }
    try:
        while True:
            # Update all prices slightly
            for symbol in prices:
                prices[symbol] += (np.random.rand() - 0.5) * 5
                prices[symbol] = round(prices[symbol], 2)

            await websocket.send_json(prices)
            await asyncio.sleep(1)
    except Exception:
        pass

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
