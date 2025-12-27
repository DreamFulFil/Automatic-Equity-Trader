#!/usr/bin/env python3
"""
MTXF Trading Bridge - FastAPI + Shioaji + Ollama
Lightweight bridge between Java trading engine and market data/AI
"""

from fastapi import FastAPI
from fastapi.responses import JSONResponse
import shioaji as sj
import requests
import yaml
import sys
from datetime import datetime
import feedparser

app = FastAPI()

# Load config
with open('../src/main/resources/application.yml', 'r') as f:
    config = yaml.safe_load(f)

# Initialize Shioaji
api = sj.Shioaji()
api.login(
    api_key=config['shioaji']['api-key'],
    secret_key=config['shioaji']['secret-key'],
    contracts_cb=lambda security_type: print(f"✅ Contracts loaded: {security_type}")
)

if config['shioaji']['simulation']:
    api.activate_ca(
        ca_path=config['shioaji']['ca-path'],
        ca_passwd=config['shioaji']['ca-password'],
        person_id=config['shioaji']['api-key'][:10]
    )
    print("📄 Paper trading mode activated")
else:
    print("💰 LIVE TRADING MODE")

# Subscribe to MTXF
mtxf_contract = api.Contracts.Futures.TXF.TXFR1  # Current month Mini-TXF
api.quote.subscribe(
    mtxf_contract,
    quote_type=sj.constant.QuoteType.Tick,
    version=sj.constant.QuoteVersion.v1
)

# Latest market data
latest_tick = {"price": 0, "volume": 0, "timestamp": None}

@api.quote.on_tick_fop_v1()
def tick_callback(exchange, tick):
    latest_tick["price"] = tick.close
    latest_tick["volume"] = tick.volume
    latest_tick["timestamp"] = tick.datetime

print(f"✅ Subscribed to {mtxf_contract.symbol}")

# Ollama client
OLLAMA_URL = config['ollama']['url']
OLLAMA_MODEL = config['ollama']['model']

def fetch_news_headlines():
    """Scrape MoneyDJ + UDN RSS feeds"""
    feeds = [
        "https://www.moneydj.com/rss/RssNews.djhtm",
        "https://udn.com/rssfeed/news/2/6638"
    ]
    headlines = []
    for feed_url in feeds:
        try:
            feed = feedparser.parse(feed_url)
            for entry in feed.entries[:5]:
                headlines.append(entry.title)
        except:
            pass
    return headlines[:15]

def call_llama_news_veto(headlines):
    """Call Ollama Llama 3.1 8B for news sentiment veto"""
    prompt = f"""You are a Taiwan stock market news analyst. Analyze these headlines and decide if trading should be VETOED due to major negative news.

Headlines:
{chr(10).join(f"- {h}" for h in headlines)}

Respond ONLY with valid JSON:
{{"veto": true/false, "score": 0.0-1.0, "reason": "brief explanation"}}

Veto if: geopolitical crisis, major crash, regulatory halt, war.
Score: 0.0=very bearish, 0.5=neutral, 1.0=very bullish"""

    try:
        response = requests.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "stream": False,
                "options": {"temperature": 0.3}
            },
            timeout=5
        )
        result = response.json()['response']
        # Parse JSON from response
        import json
        return json.loads(result)
    except:
        return {"veto": False, "score": 0.5, "reason": "Analysis failed"}

@app.get("/health")
def health():
    return {"status": "ok", "time": datetime.now().isoformat()}

@app.get("/signal")
def get_signal():
    """Generate trading signal with strategy + news veto"""
    
    # News veto check (every call for real-time risk)
    headlines = fetch_news_headlines()
    news_analysis = call_llama_news_veto(headlines)
    
    # Simple momentum strategy (placeholder - expand with your logic)
    price = latest_tick["price"]
    direction = "NEUTRAL"
    confidence = 0.5
    
    # Example: Simple momentum (replace with your fair-value + volume logic)
    if latest_tick["volume"] > 100:
        if price > 0:  # Add your actual strategy here
            direction = "LONG"
            confidence = 0.7
    
    return {
        "current_price": price,
        "direction": direction,
        "confidence": confidence,
        "news_veto": news_analysis.get("veto", False),
        "news_score": news_analysis.get("score", 0.5),
        "news_reason": news_analysis.get("reason", ""),
        "timestamp": datetime.now().isoformat()
    }

@app.post("/order")
def place_order(order: dict):
    """Execute order via Shioaji"""
    action = order['action']
    quantity = order['quantity']
    price = order['price']
    
    order_obj = api.Order(
        price=price,
        quantity=quantity,
        action=sj.constant.Action.Buy if action == "BUY" else sj.constant.Action.Sell,
        price_type=sj.constant.FuturesPriceType.LMT,
        order_type=sj.constant.OrderType.ROD,
        account=api.futopt_account
    )
    
    trade = api.place_order(mtxf_contract, order_obj)
    return {"status": "filled", "order_id": trade.status.id}

if __name__ == "__main__":
    import uvicorn
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
