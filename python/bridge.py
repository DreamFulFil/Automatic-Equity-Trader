#!/usr/bin/env python3
"""
MTXF Trading Bridge - FastAPI + Shioaji + Ollama
Lightweight bridge between Java trading engine and market data/AI

2025 Production Strategy:
- 3-min and 5-min momentum alignment
- Volume confirmation (institutional flow)
- Fair-value anchor from overnight session
- Exit on trend reversal only

Bulletproof Features:
- Shioaji auto-reconnect (max 5 attempts, exponential backoff)
- Graceful shutdown on /shutdown endpoint
- Auto-scrape earnings dates from Yahoo Finance (--scrape-earnings)
"""

from fastapi import FastAPI
from fastapi.responses import JSONResponse
import shioaji as sj
import requests
import yaml
import sys
import os
import re
import time
import base64
import hashlib
import threading
import json
import argparse
from datetime import datetime, timedelta
from collections import deque
import statistics
import feedparser
from Crypto.Cipher import DES

app = FastAPI()

# ============================================================================
# JASYPT DECRYPTION
# ============================================================================

def jasypt_decrypt(encrypted_value: str, password: str) -> str:
    """Decrypt Jasypt PBEWithMD5AndDES encrypted value"""
    encrypted_bytes = base64.b64decode(encrypted_value)
    salt = encrypted_bytes[:8]
    ciphertext = encrypted_bytes[8:]
    
    password_bytes = password.encode('utf-8')
    key_material = password_bytes + salt
    
    for _ in range(1000):
        key_material = hashlib.md5(key_material).digest()
    
    key = key_material[:8]
    iv = key_material[8:16]
    
    cipher = DES.new(key, DES.MODE_CBC, iv)
    decrypted = cipher.decrypt(ciphertext)
    
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
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    config_path = os.path.join(project_root, 'src/main/resources/application.yml')
    
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    if 'shioaji' in config:
        for key in ['api-key', 'secret-key', 'ca-password', 'person-id']:
            if key in config['shioaji']:
                config['shioaji'][key] = decrypt_config_value(config['shioaji'][key], password)
        
        ca_path = config['shioaji'].get('ca-path', 'Sinopac.pfx')
        if not os.path.isabs(ca_path):
            ca_path = os.path.join(project_root, ca_path)
        config['shioaji']['ca-path'] = os.path.abspath(ca_path)
        
        print(f"✅ CA certificate path: {config['shioaji']['ca-path']}")
    
    return config

# ============================================================================
# SHIOAJI AUTO-RECONNECT WRAPPER
# ============================================================================

class ShioajiWrapper:
    """
    Shioaji wrapper with auto-reconnect capability.
    Retries login + subscription up to 5 times with exponential backoff.
    """
    
    MAX_RETRIES = 5
    BASE_BACKOFF_SECONDS = 2
    
    def __init__(self, config):
        self.config = config
        self.api = None
        self.mtxf_contract = None
        self.connected = False
        self._lock = threading.Lock()
        
    def connect(self) -> bool:
        """Connect to Shioaji with retry logic"""
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                print(f"🔄 Shioaji connection attempt {attempt}/{self.MAX_RETRIES}...")
                
                # Create fresh API instance
                self.api = sj.Shioaji()
                
                # Login
                self.api.login(
                    api_key=self.config['shioaji']['api-key'],
                    secret_key=self.config['shioaji']['secret-key'],
                    contracts_cb=lambda security_type: print(f"✅ Contracts loaded: {security_type}")
                )
                
                # Activate CA
                self.api.activate_ca(
                    ca_path=self.config['shioaji']['ca-path'],
                    ca_passwd=self.config['shioaji']['ca-password'],
                    person_id=self.config['shioaji']['person-id']
                )
                
                mode = "📄 Paper trading" if self.config['shioaji']['simulation'] else "💰 LIVE TRADING"
                print(f"{mode} mode activated")
                
                # Subscribe to MTXF
                self.mtxf_contract = self.api.Contracts.Futures.TXF.TXFR1
                self.api.quote.subscribe(
                    self.mtxf_contract,
                    quote_type=sj.constant.QuoteType.Tick,
                    version=sj.constant.QuoteVersion.v1
                )
                
                # Register tick handler
                self.api.quote.set_on_tick_fop_v1_callback(self._handle_tick)
                
                print(f"✅ Subscribed to {self.mtxf_contract.symbol}")
                self.connected = True
                return True
                
            except Exception as e:
                print(f"❌ Connection attempt {attempt} failed: {e}")
                if attempt < self.MAX_RETRIES:
                    backoff = self.BASE_BACKOFF_SECONDS ** attempt
                    print(f"⏳ Waiting {backoff}s before retry...")
                    time.sleep(backoff)
                else:
                    print("❌ All connection attempts failed!")
                    return False
        
        return False
    
    def reconnect(self) -> bool:
        """Force reconnect (called when connection is lost)"""
        with self._lock:
            print("🔄 Reconnecting to Shioaji...")
            self.connected = False
            try:
                if self.api:
                    self.api.logout()
            except:
                pass
            return self.connect()
    
    def _handle_tick(self, exchange, tick):
        """Internal tick handler - updates global market data"""
        global session_open_price, session_high, session_low
        
        latest_tick["price"] = float(tick.close)
        latest_tick["volume"] = tick.volume
        latest_tick["timestamp"] = tick.datetime
        
        price_history.append({"price": float(tick.close), "time": tick.datetime, "volume": tick.volume})
        volume_history.append(tick.volume)
        
        if session_open_price is None:
            session_open_price = float(tick.close)
            session_high = float(tick.close)
            session_low = float(tick.close)
        else:
            session_high = max(session_high, float(tick.close))
            session_low = min(session_low, float(tick.close))
    
    def place_order(self, action: str, quantity: int, price: float):
        """Place order with auto-reconnect on failure"""
        if not self.connected:
            if not self.reconnect():
                raise Exception("Cannot place order - not connected")
        
        try:
            order_obj = self.api.Order(
                price=price,
                quantity=quantity,
                action=sj.constant.Action.Buy if action == "BUY" else sj.constant.Action.Sell,
                price_type=sj.constant.FuturesPriceType.LMT,
                order_type=sj.constant.OrderType.ROD,
                account=self.api.futopt_account
            )
            
            trade = self.api.place_order(self.mtxf_contract, order_obj)
            return {"status": "filled", "order_id": trade.status.id}
            
        except Exception as e:
            print(f"❌ Order failed: {e}, attempting reconnect...")
            if self.reconnect():
                # Retry once after reconnect
                return self.place_order(action, quantity, price)
            raise
    
    def logout(self):
        """Graceful logout"""
        try:
            if self.api:
                self.api.logout()
                print("✅ Shioaji logged out")
        except Exception as e:
            print(f"⚠️ Logout error: {e}")

# ============================================================================
# GLOBAL STATE
# ============================================================================

# Global variables - initialized lazily for FastAPI mode only
JASYPT_PASSWORD = None
config = None
shioaji = None
latest_tick = {"price": 0, "volume": 0, "timestamp": None}
price_history = deque(maxlen=600)
volume_history = deque(maxlen=600)
session_open_price = None
session_high = None
session_low = None
last_direction = "NEUTRAL"
OLLAMA_URL = None
OLLAMA_MODEL = None


def init_trading_mode():
    """Initialize Shioaji and config - only called in FastAPI server mode"""
    global JASYPT_PASSWORD, config, shioaji, OLLAMA_URL, OLLAMA_MODEL
    
    JASYPT_PASSWORD = os.environ.get('JASYPT_PASSWORD')
    if not JASYPT_PASSWORD:
        print("❌ JASYPT_PASSWORD environment variable not set!")
        sys.exit(1)
    
    config = load_config_with_decryption(JASYPT_PASSWORD)
    print("✅ Configuration loaded and decrypted")
    
    shioaji = ShioajiWrapper(config)
    if not shioaji.connect():
        print("❌ Failed to connect to Shioaji after all retries!")
        sys.exit(1)
    
    OLLAMA_URL = config['ollama']['url']
    OLLAMA_MODEL = config['ollama']['model']

# ============================================================================
# NEWS ANALYSIS
# ============================================================================

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
        import json
        return json.loads(result)
    except:
        return {"veto": False, "score": 0.5, "reason": "Analysis failed"}

# ============================================================================
# API ENDPOINTS
# ============================================================================

@app.get("/health")
def health():
    return {
        "status": "ok",
        "shioaji_connected": shioaji.connected,
        "time": datetime.now().isoformat()
    }

@app.post("/shutdown")
def shutdown():
    """Graceful shutdown endpoint - called by Java app before exit"""
    def do_shutdown():
        time.sleep(1)
        print("🛑 Shutdown requested - cleaning up...")
        shioaji.logout()
        os._exit(0)
    
    threading.Thread(target=do_shutdown).start()
    return {"status": "shutting_down", "message": "Python bridge shutting down gracefully"}

@app.get("/signal")
def get_signal():
    """Generate trading signal using momentum + volume strategy"""
    global last_direction
    
    price = latest_tick["price"]
    direction = "NEUTRAL"
    confidence = 0.0
    exit_signal = False
    
    # Need minimum data for calculations
    if len(price_history) < 60:
        return {
            "current_price": price,
            "direction": "NEUTRAL",
            "confidence": 0.0,
            "exit_signal": False,
            "reason": "Insufficient data (warming up)",
            "timestamp": datetime.now().isoformat()
        }
    
    # 3-minute momentum
    lookback_3min = min(180, len(price_history))
    prices_3min = [p["price"] for p in list(price_history)[-lookback_3min:]]
    momentum_3min = (prices_3min[-1] - prices_3min[0]) / prices_3min[0] * 100 if prices_3min[0] > 0 else 0
    
    # 5-minute momentum
    lookback_5min = min(300, len(price_history))
    prices_5min = [p["price"] for p in list(price_history)[-lookback_5min:]]
    momentum_5min = (prices_5min[-1] - prices_5min[0]) / prices_5min[0] * 100 if prices_5min[0] > 0 else 0
    
    # Volume analysis
    if len(volume_history) >= 60:
        recent_vol = sum(list(volume_history)[-30:])
        avg_vol = sum(list(volume_history)[-60:]) / 2
        volume_ratio = recent_vol / avg_vol if avg_vol > 0 else 1.0
    else:
        volume_ratio = 1.0
    
    # Thresholds
    MOMENTUM_THRESHOLD = 0.02
    VOLUME_SURGE_THRESHOLD = 1.3
    
    both_bullish = momentum_3min > MOMENTUM_THRESHOLD and momentum_5min > MOMENTUM_THRESHOLD
    both_bearish = momentum_3min < -MOMENTUM_THRESHOLD and momentum_5min < -MOMENTUM_THRESHOLD
    volume_confirms = volume_ratio > VOLUME_SURGE_THRESHOLD
    
    if both_bullish:
        direction = "LONG"
        confidence = min(0.95, 0.5 + abs(momentum_3min) * 10 + abs(momentum_5min) * 5)
        if volume_confirms:
            confidence = min(0.95, confidence + 0.15)
            
    elif both_bearish:
        direction = "SHORT"
        confidence = min(0.95, 0.5 + abs(momentum_3min) * 10 + abs(momentum_5min) * 5)
        if volume_confirms:
            confidence = min(0.95, confidence + 0.15)
    
    else:
        direction = "NEUTRAL"
        confidence = 0.3
    
    # Exit signal (trend reversal)
    if last_direction == "LONG" and momentum_3min < -MOMENTUM_THRESHOLD:
        exit_signal = True
    elif last_direction == "SHORT" and momentum_3min > MOMENTUM_THRESHOLD:
        exit_signal = True
    
    if direction != "NEUTRAL" and confidence >= 0.65:
        last_direction = direction
    
    return {
        "current_price": price,
        "direction": direction,
        "confidence": round(confidence, 3),
        "exit_signal": exit_signal,
        "momentum_3min": round(momentum_3min, 4),
        "momentum_5min": round(momentum_5min, 4),
        "volume_ratio": round(volume_ratio, 2),
        "session_high": session_high,
        "session_low": session_low,
        "timestamp": datetime.now().isoformat()
    }

@app.get("/signal/news")
def get_news_veto():
    """Check news veto via Ollama Llama 3.1 8B"""
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
    """Execute order via Shioaji with auto-reconnect"""
    action = order['action']
    quantity = order['quantity']
    price = order['price']
    
    return shioaji.place_order(action, quantity, price)

# ============================================================================
# EARNINGS BLACKOUT SCRAPER
# ============================================================================

def scrape_earnings_dates():
    """
    Scrape earnings dates using yfinance library (handles Yahoo auth automatically).
    Saves sorted dates to config/earnings-blackout-dates.json
    
    Run standalone: python3 bridge.py --scrape-earnings
    """
    import yfinance as yf
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    config_dir = os.path.join(project_root, 'config')
    output_file = os.path.join(config_dir, 'earnings-blackout-dates.json')
    
    # Major Taiwan stocks to track (Yahoo Finance tickers)
    TICKERS = [
        'TSM',      # TSMC (ADR)
        '2330.TW',  # TSMC (Taiwan)
        '2454.TW',  # MediaTek
        '2317.TW',  # Hon Hai (Foxconn)
        'UMC',      # UMC (ADR)
        '2303.TW',  # UMC (Taiwan)
        'ASX',      # ASE Technology (ADR)
        '3711.TW',  # ASE (Taiwan)
        '2412.TW',  # Chunghwa Telecom
        '2882.TW',  # Cathay Financial
        '2881.TW',  # Fubon Financial
        '1301.TW',  # Formosa Plastics
        '2002.TW',  # China Steel
    ]
    
    print(f"📅 Scraping earnings dates for {len(TICKERS)} stocks (using yfinance)...")
    
    earnings_dates = set()
    today = datetime.now().date()
    one_year_later = today + timedelta(days=365)
    
    for ticker in TICKERS:
        try:
            stock = yf.Ticker(ticker)
            calendar = stock.calendar
            
            if calendar is None or 'Earnings Date' not in calendar:
                print(f"  ⚠️ {ticker}: No earnings date available")
                continue
            
            earnings_list = calendar.get('Earnings Date', [])
            if not isinstance(earnings_list, list):
                earnings_list = [earnings_list]
            
            for dt in earnings_list:
                if hasattr(dt, 'date'):
                    dt = dt if isinstance(dt, type(today)) else dt.date() if hasattr(dt, 'date') else dt
                if isinstance(dt, type(today)) and today <= dt <= one_year_later:
                    earnings_dates.add(dt.isoformat())
                    print(f"  ✅ {ticker}: {dt.isoformat()}")
            
            # Rate limit: 15 seconds between requests to avoid 429 errors
            time.sleep(15)
            
        except Exception as e:
            print(f"  ❌ {ticker}: {e}")
            continue
    
    # Load existing dates (graceful: keep old if scrape fails)
    existing_dates = set()
    if os.path.exists(output_file):
        try:
            with open(output_file, 'r') as f:
                existing = json.load(f)
                existing_dates = set(existing.get('dates', []))
        except:
            pass
    
    # Merge new dates with existing (never lose data)
    all_dates = earnings_dates.union(existing_dates)
    
    # Filter to only future dates
    future_dates = sorted([d for d in all_dates if d >= today.isoformat()])
    
    # Save to JSON
    os.makedirs(config_dir, exist_ok=True)
    result = {
        "last_updated": datetime.now().isoformat(),
        "source": "Yahoo Finance (yfinance)",
        "tickers_checked": TICKERS,
        "dates": future_dates
    }
    
    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)
    
    print(f"\n✅ Saved {len(future_dates)} blackout dates to {output_file}")
    print(f"   Next dates: {future_dates[:5]}...")
    
    return future_dates


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='MTXF Trading Bridge')
    parser.add_argument('--scrape-earnings', action='store_true', 
                        help='Scrape Yahoo Finance for earnings dates and exit')
    args = parser.parse_args()
    
    if args.scrape_earnings:
        # Standalone scraper mode - no FastAPI, no Shioaji, no credentials needed
        scrape_earnings_dates()
        sys.exit(0)
    
    # Normal FastAPI server mode - initialize trading components
    init_trading_mode()
    
    import uvicorn
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
