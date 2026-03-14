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

@app.get("/")
def health_check():
    return {"status": "alive", "model_ready": model_loaded}

@app.get("/pcr")
def get_pcr_calculation(call: float, put: float):
    pcr_value = round(put / call, 2) if call != 0 else 0.0
    sentiment = "BULLISH" if pcr_value > 1.1 else "BEARISH" if pcr_value < 0.7 else "NEUTRAL"
    return {"PCR": pcr_value, "sentiment": sentiment, "call_oi": call, "put_oi": put}

@app.get("/stock/{symbol}")
def get_stock(symbol: str):
    url = f"https://query1.finance.yahoo.com/v7/finance/quote?symbols={symbol}"
    try:
        response = requests.get(url, headers={"User-Agent": "Mozilla/5.0"})
        return response.json()
    except Exception as e:
        return {"error": str(e)}

@app.get("/oi-heatmap")
def get_oi_heatmap(symbol: str = "NIFTY"):
    # Map friendly names to market symbols
    ticker_map = {
        "NIFTY": "^NSEI",
        "BANKNIFTY": "^NSEBANK",
        "RELIANCE": "RELIANCE.NS",
        "TCS": "TCS.NS"
    }

    clean_symbol = symbol.upper()
    ticker = ticker_map.get(clean_symbol, symbol)

    # Determine base strike from live price
    try:
        res = requests.get(f"https://query1.finance.yahoo.com/v7/finance/quote?symbols={ticker}", headers={"User-Agent": "Mozilla/5.0"}).json()
        price = res['quoteResponse']['result'][0]['regularMarketPrice']
        # Different step for different symbols (50 for NIFTY, 100 for BANKNIFTY)
        step = 100 if "BANK" in ticker else 50
        base_strike = round(price / step) * step
    except:
        base_strike = 24300

    data = []
    for i in range(-10, 11):
        step = 100 if "BANK" in symbol.upper() else 50
        strike = base_strike + (i * step)
        data.append({
            "strike": strike,
            "call_oi": int(np.random.randint(500000, 5000000)),
            "put_oi": int(np.random.randint(500000, 5000000)),
            "call_delta": round(max(0, 0.5 - (i * 0.05)), 2),
            "put_delta": round(min(0, -0.5 - (i * 0.05)), 2),
            "theta": round(-10 - np.random.rand() * 5, 2)
        })
    return {"heatmap": data}

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

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    symbols = ["^NSEI", "RELIANCE.NS", "TCS.NS", "INFY.NS", "HDFCBANK.NS", "^NSEBANK"]
    prices = {}

    # Initial fetch of real prices
    for s in symbols:
        try:
            res = requests.get(f"https://query1.finance.yahoo.com/v7/finance/quote?symbols={s}", headers={"User-Agent": "Mozilla/5.0"}).json()
            prices[s] = res['quoteResponse']['result'][0]['regularMarketPrice']
        except:
            prices[s] = 20000.0 # Fallback

    try:
        while True:
            for s in prices:
                prices[s] += (np.random.rand() - 0.5) * 2
                prices[s] = round(prices[s], 2)
            await websocket.send_json(prices)
            await asyncio.sleep(1)
    except Exception:
        pass

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
