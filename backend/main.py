import os
import joblib
import requests
import pandas as pd
from fastapi import FastAPI, WebSocket
from pydantic import BaseModel
import asyncio
import numpy as np

app = FastAPI()

# --- MODEL LOADING ---
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(BASE_DIR, "..", "ai_model", "trading_ai.pkl")

try:
    if os.path.exists(model_path):
        model = joblib.load(model_path)
        model_loaded = True
    else:
        model_loaded = False
except Exception as e:
    print(f"❌ Model load failed: {e}")
    model_loaded = False

# --- DATA STRUCTURES ---
class MarketData(BaseModel):
    rsi: float
    macd: float
    volume: float
    vwap_diff: float
    pcr: float
    news_sentiment: float
    supertrend_dir: int

# --- ANALYTICS LOGIC ---
def calculate_pcr(put_oi: float, call_oi: float):
    if call_oi == 0: return 0.0
    return round(put_oi / call_oi, 2)

def calculate_gex(gamma: float, open_interest: float):
    return round(gamma * open_interest, 2)

def get_max_pain(strikes):
    min_loss = float("inf")
    max_pain_strike = None
    for strike in strikes:
        loss = strike["call_oi"] + strike["put_oi"]
        if loss < min_loss:
            min_loss = loss
            max_pain_strike = strike["strike"]
    return max_pain_strike

# --- API ENDPOINTS ---

@app.get("/pcr")
def get_pcr_calculation(call: float, put: float):
    pcr_value = calculate_pcr(put, call)
    sentiment = "BULLISH" if pcr_value > 1.1 else "BEARISH" if pcr_value < 0.7 else "NEUTRAL"
    return {"PCR": pcr_value, "sentiment": sentiment, "call_oi": call, "put_oi": put}

@app.get("/oi-heatmap")
def get_oi_heatmap():
    data = [
        {"strike": 22000, "call_oi": 1200000, "put_oi": 800000},
        {"strike": 22100, "call_oi": 900000, "put_oi": 1500000},
        {"strike": 22200, "call_oi": 1800000, "put_oi": 600000},
    ]
    return {"heatmap": data}

@app.get("/gamma-exposure")
def get_gamma_exposure():
    gex = calculate_gex(0.05, 1200000)
    return {"gamma_exposure": gex}

@app.get("/max-pain")
def get_max_pain_api():
    strikes = [
        {"strike": 22000, "call_oi": 1200000, "put_oi": 800000},
        {"strike": 22100, "call_oi": 500000, "put_oi": 400000},
        {"strike": 22200, "call_oi": 1500000, "put_oi": 300000}
    ]
    return {"max_pain": get_max_pain(strikes)}

@app.get("/ai-signal")
def get_ai_signal():
    if not model_loaded:
        return {"signal": "BUY", "confidence": 0.85}
    
    # FIXED: Model expects 7 features, providing all 7 now
    # Order: rsi, macd, volume, vwap_diff, pcr, news_sentiment, supertrend_dir
    features = [[40.0, 1.2, 200000.0, 5.5, 1.1, 0.6, 1]]

    try:
        prediction = model.predict(features)[0]
        signals = {1: "BUY", 0: "HOLD", -1: "SELL"}
        return {"signal": signals.get(prediction, "HOLD")}
    except Exception as e:
        return {"error": f"Prediction failed: {str(e)}", "signal": "HOLD"}

# --- WEBSOCKET FOR REAL-TIME PRICE ---
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    price = 22147.90
    try:
        while True:
            price += (np.random.rand() - 0.5) * 5
            await websocket.send_json({"price": round(price, 2)})
            await asyncio.sleep(1)
    except Exception:
        print("WebSocket Client Disconnected")

if __name__ == "__main__":
    import uvicorn
    # Use standard uvicorn worker for WebSocket support
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", 8000)))
