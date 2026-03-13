import os
import joblib
import requests
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

# --- MODEL LOADING ---
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# We check if the model exists in the same directory or parent
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

# --- DATA STRUCTURE ---
class MarketData(BaseModel):
    rsi: float
    macd: float
    volume: float
    vwap_diff: float
    pcr: float
    news_sentiment: float
    supertrend_dir: int

# --- PCR CALCULATION ---
@app.get("/pcr")
def get_pcr_calculation(call: float, put: float):
    if call == 0:
        return {"error": "Call OI cannot be zero", "PCR": 0.0}
    pcr_value = round(put / call, 2)
    
    # Determine Sentiment
    if pcr_value > 1.1:
        sentiment = "BULLISH"
    elif pcr_value < 0.7:
        sentiment = "BEARISH"
    else:
        sentiment = "NEUTRAL"

    return {
        "PCR": pcr_value,
        "sentiment": sentiment,
        "call_oi": call,
        "put_oi": put
    }

# --- ENDPOINT 1: NSE OPTION CHAIN ---
@app.get("/option-chain")
def get_option_chain():
    # Note: NSE API often requires complex session management and headers
    url = "https://www.nseindia.com/api/option-chain-indices?symbol=NIFTY"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br"
    }
    try:
        session = requests.Session()
        session.get("https://www.nseindia.com", headers=headers) # Get Cookies
        response = session.get(url, headers=headers)
        return response.json()
    except Exception as e:
        return {"error": str(e)}

# --- ENDPOINT 2: AI PREDICTION ---
@app.post("/predict")
def predict_signal(data: MarketData):
    if not model_loaded:
        return {"error": "AI Model not found on server"}
    
    input_df = pd.DataFrame([data.dict()])
    prediction = model.predict(input_df)[0]
    
    signals = {1: "BUY", 0: "HOLD", -1: "SELL"}
    return {"signal": signals.get(prediction, "HOLD")}

# --- ENDPOINT 3: BASIC STOCK QUOTE ---
@app.get("/stock/{symbol}")
def get_stock(symbol: str):
    url = f"https://query1.finance.yahoo.com/v7/finance/quote?symbols={symbol}"
    try:
        response = requests.get(url, headers={"User-Agent": "Mozilla/5.0"})
        return response.json()
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
