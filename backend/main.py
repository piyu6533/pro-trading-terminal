import os
import joblib
from fastapi import FastAPI
from pydantic import BaseModel
import pandas as pd

app = FastAPI()

# --- FIXED PATH LOGIC ---
# This finds the root directory (pro-trading-terminal)
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# This points specifically to ai_model/trading_ai.pkl
model_path = os.path.join(BASE_DIR, "ai_model", "trading_ai.pkl")

try:
    model = joblib.load(model_path)
    print("✅ Success: Model loaded from ai_model folder!")
except Exception as e:
    print(f"❌ Error: Could not find model at {model_path}. Error: {e}")
    model = None
# ------------------------

class MarketData(BaseModel):
    rsi: float
    macd: float
    volume: float
    vwap_diff: float
    pcr: float
    news_sentiment: float
    supertrend_dir: int

@app.get("/")
def home():
    return {
        "status": "Online",
        "model_status": "Loaded" if model else "Not Found",
        "path_checked": model_path
    }

@app.post("/predict")
def predict_signal(data: MarketData):
    if model is None:
        return {"error": "Model not loaded on server"}
    
    input_df = pd.DataFrame([data.dict()])
    prediction = model.predict(input_df)[0]
    
    signals = {1: "BUY", 0: "HOLD", -1: "SELL"}
    return {"signal": signals.get(prediction, "HOLD")}
