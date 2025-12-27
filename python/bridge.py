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
import os
import re
import base64
import hashlib
from datetime import datetime
import feedparser
from Crypto.Cipher import DES

app = FastAPI()

# Jasypt PBEWithMD5AndDES decryption
def jasypt_decrypt(encrypted_value: str, password: str) -> str:
    """Decrypt Jasypt PBEWithMD5AndDES encrypted value"""
    # Decode base64
    encrypted_bytes = base64.b64decode(encrypted_value)
    
    # Salt is first 8 bytes
    salt = encrypted_bytes[:8]
    ciphertext = encrypted_bytes[8:]
    
    # PBE key derivation: iterate MD5 1000 times (Jasypt default)
    password_bytes = password.encode('utf-8')
    key_material = password_bytes + salt
    
    for _ in range(1000):
        key_material = hashlib.md5(key_material).digest()
    
    # Split into key (8 bytes) and IV (8 bytes)
    key = key_material[:8]
    iv = key_material[8:16]
    
    # Decrypt using DES-CBC
    cipher = DES.new(key, DES.MODE_CBC, iv)
    decrypted = cipher.decrypt(ciphertext)
    
    # Remove PKCS5 padding
    pad_len = decrypted[-1]
    if isinstance(pad_len, int) and 1 <= pad_len <= 8:
        return decrypted[:-pad_len].decode('utf-8')
    return decrypted.decode('utf-8')

def decrypt_config_value(value, password: str):
    """Decrypt value if it's ENC() wrapped, otherwise return as-is"""
    if isinstance(value, str):
        match = re.match(r'^ENC\((.+)\)$', value)
        if match:
            return jasypt_decrypt(match.group(1), password)
    return value

def load_config_with_decryption(password: str):
    """Load application.yml and decrypt ENC() values"""
    with open('../src/main/resources/application.yml', 'r') as f:
        config = yaml.safe_load(f)
    
    # Decrypt shioaji credentials
    if 'shioaji' in config:
        for key in ['api-key', 'secret-key', 'ca-password', 'person-id']:
            if key in config['shioaji']:
                config['shioaji'][key] = decrypt_config_value(config['shioaji'][key], password)
    
    return config

# Get Jasypt password from environment variable
JASYPT_PASSWORD = os.environ.get('JASYPT_PASSWORD')
if not JASYPT_PASSWORD:
    print("❌ JASYPT_PASSWORD environment variable not set!")
    sys.exit(1)

# Load config with decryption
config = load_config_with_decryption(JASYPT_PASSWORD)
print("✅ Configuration loaded and decrypted")

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
        person_id=config['shioaji']['person-id']
    )
    print("📄 Paper trading mode activated")
else:
    print("💰 LIVE TRADING MODE - Using real account")
    api.activate_ca(
        ca_path=config['shioaji']['ca-path'],
        ca_passwd=config['shioaji']['ca-password'],
        person_id=config['shioaji']['person-id']
    )

# Subscribe to MTXF
mtxf_contract = api.Contracts.Futures.TXF.TXFR1  # Current month Mini-TXF
api.quote.subscribe(
    mtxf_contract,
    quote_type=sj.constant.QuoteType.Tick,
    version=sj.constant.QuoteVersion.v1
)

# Latest market data
latest_tick = {"price": 0, "volume": 0, "timestamp": None}

@api.quote.on_quote
def handle_tick(exchange: sj.constant.Exchange, tick):
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

@app.post("/shutdown")
def shutdown():
    """Graceful shutdown endpoint - called by Java app before exit"""
    import threading
    
    def do_shutdown():
        import time
        time.sleep(1)  # Give time for response to be sent
        print("🛑 Shutdown requested - cleaning up...")
        try:
            api.logout()
            print("✅ Shioaji logged out")
        except:
            pass
        os._exit(0)
    
    threading.Thread(target=do_shutdown).start()
    return {"status": "shutting_down", "message": "Python bridge shutting down gracefully"}

@app.get("/signal")
def get_signal():
    """Generate trading signal with strategy (NO news veto - use /signal/news separately)
    
    UNLIMITED UPSIDE STRATEGY:
    - Clean trend continuation (3/5-min momentum alignment)
    - Volume confirmation (institutional flow)
    - Fair-value anchor from overnight US futures
    - Exit ONLY on: stop-loss OR trend reversal
    - NO profit targets - let winners run!
    """
    
    price = latest_tick["price"]
    direction = "NEUTRAL"
    confidence = 0.5
    exit_signal = False
    
    # TODO: Implement your full strategy here
    # Example skeleton (replace with your actual logic):
    # 1. Calculate 3-min and 5-min momentum
    # 2. Check volume imbalance (bid/ask ratio)
    # 3. Compare to fair-value from overnight NQ/ES
    # 4. Detect clean trend vs. choppy consolidation
    
    # Placeholder momentum example
    if latest_tick["volume"] > 100:
        if price > 0:  # Replace with actual momentum logic
            direction = "LONG"
            confidence = 0.72
            # exit_signal = True if momentum reverses
    
    return {
        "current_price": price,
        "direction": direction,
        "confidence": confidence,
        "exit_signal": exit_signal,  # TRUE only on trend reversal
        "timestamp": datetime.now().isoformat()
    }

@app.get("/signal/news")
def get_news_veto():
    """Check news veto via Ollama Llama 3.1 8B - call every 10 minutes only"""
    headlines = fetch_news_headlines()
    news_analysis = call_llama_news_veto(headlines)
    
    return {
        "news_veto": news_analysis.get("veto", False),
        "news_score": news_analysis.get("score", 0.5),
        "news_reason": news_analysis.get("reason", ""),
        "headlines_count": len(headlines),
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
