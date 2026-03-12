import os
import joblib
import requests
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

# --- MODEL LOADING ---
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
model_path = os.path.join(BASE_DIR, "ai_model", "trading_ai.pkl")

try:
    model = joblib.load(model_path)
    model_loaded = True
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

# --- ENDPOINT 1: NSE OPTION CHAIN ---
@app.get("/option-chain")
def get_option_chain():
    url = "https://www.nseindia.com/api/option-chain-indices?symbol=NIFTY"
    headers = {"User-Agent": "Mozilla/5.0"}
    session = requests.Session()
    session.get("https://www.nseindia.com", headers=headers) # Get Cookies
    return session.get(url, headers=headers).json()

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
    return requests.get(url).json()
