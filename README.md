# Pro Trading Terminal

Android trading terminal with AI-powered signals and real-time market analytics.

## Features

- Live market data
- Option chain analytics
- AI trading signals
- Candlestick charts
- OI heatmap
- Gamma exposure
- Real-time updates

## Architecture

Android App  
↓  
Backend API (FastAPI)  
↓  
AI Model  
↓  
Market Data

## Tech Stack

- Android (Kotlin)
- FastAPI Backend
- Python AI Model
- GitHub for version control
- Render for cloud deployment

  ## Project Structure

pro-trading-terminal
│
├── android_app
├── backend
│   ├── main.py
│   └── requirements.txt
├── ai_model
│   └── trading_ai.pkl
└── README.md

## Installation

1. Clone repository

git clone https://github.com/piyu6533/pro-trading-terminal.git

2. Open project in Android Studio

3. Run backend server

uvicorn backend.main:app --reload

## Future Improvements

- Full NSE option chain
- Smart money flow detection
- Institutional order tracking
- Portfolio analytics
