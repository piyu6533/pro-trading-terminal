from fastapi import FastAPI
from pydantic import BaseModel
import joblib
import pandas as pd
import os

app = FastAPI()

# Load the model (Make sure trading_ai.pkl is in the root or backend folder)
# We use a path check to ensure it loads correctly on Render
model_path = os.path.join(os.getcwd(), "trading_ai.pkl")
model = joblib.load(model_path)

# Define the data structure the Android app will send
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
    return {"status": "Pro Trading API is Live"}

@app.post("/predict")
def predict_signal(data: MarketData):
    # Convert incoming JSON data to a Format the AI understands
    input_data = pd.DataFrame([data.dict()])
    
    # Get prediction
    prediction = model.predict(input_data)[0]
    
    # Map numeric signal to text
    signals = {1: "BUY", 0: "HOLD", -1: "SELL"}
    
    return {
        "signal": signals.get(prediction, "HOLD"),
        "confidence": "high" # You can add probability logic later
    }
