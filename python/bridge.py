#!/usr/bin/env python3
"""
Dual-Mode Trading Bridge - FastAPI + Shioaji + Ollama
Lightweight bridge between Java trading engine and market data/AI

Supports two trading modes via TRADING_MODE environment variable:
- "stock" (default): Trades 2454.TW odd lots via api.Contracts.Stocks.TSE["2454"]
- "futures": Trades MTXF via api.Contracts.Futures.MTXF

2025 Production Strategy:
- 3-min and 5-min momentum alignment
- Volume confirmation (institutional flow)
- Fair-value anchor from overnight session
- Exit on trend reversal only

Bulletproof Features:
- Shioaji auto-reconnect (max 5 retries, exponential backoff)
- Graceful shutdown on /shutdown endpoint
- Strict mode separation: NO futures calls in stock mode and vice versa
"""

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import sys
import os
# Ensure local compat shims are importable (for Python 3.14 compat)
compat_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'compat')
if compat_dir not in sys.path:
    sys.path.insert(0, compat_dir)

import shioaji as sj
import requests
import yaml
import re
import time
import base64
import hashlib
import threading
import json
import argparse
import gc
import weakref
from datetime import datetime, timedelta
from collections import deque
import statistics
import feedparser
from Crypto.Cipher import DES

# Lifespan event handler (replaces deprecated on_event)
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Run trading init in background so FastAPI binds quickly
    import threading
    def _init_bg():
        try:
            init_trading_mode()
        except Exception as e:
            print(f"⚠️ Background init error: {e}")
    t = threading.Thread(target=_init_bg, daemon=True)
    t.start()
    yield
    # Shutdown: cleanup if needed (currently none)

app = FastAPI(lifespan=lifespan)

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
    """Load application.yml and decrypt ENC() values, and fetch dynamic settings from Java"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    config_path = os.path.join(project_root, 'src/main/resources/application.yml')
    
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    if 'shioaji' in config:
        # Decrypt common fields
        for key in ['ca-password', 'person-id']:
            if key in config['shioaji']:
                config['shioaji'][key] = decrypt_config_value(config['shioaji'][key], password)
        
        # Decrypt stock keys
        if 'stock' in config['shioaji']:
             for key in ['api-key', 'secret-key']:
                if key in config['shioaji']['stock']:
                    config['shioaji']['stock'][key] = decrypt_config_value(config['shioaji']['stock'][key], password)

        # Decrypt future keys
        if 'future' in config['shioaji']:
             for key in ['api-key', 'secret-key']:
                if key in config['shioaji']['future']:
                    config['shioaji']['future'][key] = decrypt_config_value(config['shioaji']['future'][key], password)
        
        ca_path = config['shioaji'].get('ca-path', 'Sinopac.pfx')
        if not os.path.isabs(ca_path):
            ca_path = os.path.join(project_root, ca_path)
        config['shioaji']['ca-path'] = os.path.abspath(ca_path)
        
        print(f"✅ CA certificate path: {config['shioaji']['ca-path']}")
        
        # Fetch dynamic simulation setting from Java
        try:
            response = requests.get('http://localhost:16350/api/shioaji/settings', timeout=5)
            if response.status_code == 200:
                java_settings = response.json()
                config['shioaji']['simulation'] = java_settings.get('simulation', True)
                print(f"✅ Simulation mode from Java: {config['shioaji']['simulation']}")
            else:
                # Fallback to existing config or default True
                if 'simulation' not in config['shioaji']:
                    config['shioaji']['simulation'] = True
                print(f"⚠️ Failed to fetch simulation from Java, using config default: {config['shioaji']['simulation']}")
        except Exception as e:
            # Fallback to existing config or default True
            if 'simulation' not in config['shioaji']:
                config['shioaji']['simulation'] = True
            print(f"⚠️ Error fetching simulation from Java: {e}, using config default: {config['shioaji']['simulation']}")
    
    return config

# ============================================================================
# SHIOAJI AUTO-RECONNECT WRAPPER
# ============================================================================

class ShioajiWrapper:
    """
    Shioaji wrapper with auto-reconnect capability and dual-mode support.
    Retries login + subscription up to 5 times with exponential backoff.
    
    Modes:
    - "stock": Uses api.Contracts.Stocks.TSE["2454"] for 2454.TW odd lots
    - "futures": Uses api.Contracts.Futures.TXF.TXFR1 for MTXF
    """
    
    MAX_RETRIES = 5
    BASE_BACKOFF_SECONDS = 2
    
    def __init__(self, config, trading_mode="stock"):
        self.config = config
        self.trading_mode = trading_mode
        self.api = None
        self.contract = None  # Generic contract (stock or futures)
        self.mtxf_contract = None  # Legacy alias for backwards compat
        self.connected = False
        self._lock = threading.Lock()
        self._callback_ref = None  # Keep callback alive
        print(f"📈 ShioajiWrapper initialized in {trading_mode.upper()} mode")
        
    def connect(self) -> bool:
        """Connect to Shioaji with retry logic"""
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                print(f"🔄 Shioaji connection attempt {attempt}/{self.MAX_RETRIES}...")
                
                # Create fresh API instance
                self.api = sj.Shioaji()
                
                # Select credentials based on mode
                if self.trading_mode == "stock":
                    creds = self.config['shioaji'].get('stock', {})
                else:
                    creds = self.config['shioaji'].get('future', {})

                # Login
                self.api.login(
                    api_key=creds.get('api-key'),
                    secret_key=creds.get('secret-key'),
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
                
                # Subscribe based on trading mode
                if self.trading_mode == "stock":
                    self._subscribe_stock()
                else:
                    self._subscribe_futures()
                
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
    
    def _subscribe_stock(self):
        """Subscribe to 2454.TW (MediaTek) for stock mode"""
        self.contract = self.api.Contracts.Stocks.TSE["2454"]
        self.api.quote.subscribe(
            self.contract,
            quote_type=sj.constant.QuoteType.Tick,
            version=sj.constant.QuoteVersion.v1
        )
        
        # Register tick handler
        def safe_tick_handler(exchange, tick):
            try:
                self._handle_tick(exchange, tick)
            except Exception as e:
                print(f"⚠️ Tick callback crashed (recovered): {e}")
        
        self._callback_ref = safe_tick_handler
        self.api.quote.set_on_tick_stk_v1_callback(self._callback_ref)
        
        print(f"✅ Subscribed to {self.contract.symbol} (STOCK mode)")
    
    def _subscribe_futures(self):
        """Subscribe to MTXF for futures mode"""
        self.contract = self.api.Contracts.Futures.TXF.TXFR1
        self.mtxf_contract = self.contract  # Legacy alias
        self.api.quote.subscribe(
            self.contract,
            quote_type=sj.constant.QuoteType.Tick,
            version=sj.constant.QuoteVersion.v1
        )
        
        # Register tick handler
        def safe_tick_handler(exchange, tick):
            try:
                self._handle_tick(exchange, tick)
            except Exception as e:
                print(f"⚠️ Tick callback crashed (recovered): {e}")
        
        self._callback_ref = safe_tick_handler
        self.api.quote.set_on_tick_fop_v1_callback(self._callback_ref)
        
        print(f"✅ Subscribed to {self.contract.symbol} (FUTURES mode)")
    
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
        """Internal tick handler - updates global market data with crash protection"""
        try:
            global session_open_price, session_high, session_low
            
            # Defensive checks to prevent segfaults
            if not tick or not hasattr(tick, 'close') or not hasattr(tick, 'volume'):
                return
            
            price = float(tick.close) if tick.close is not None else 0.0
            volume = tick.volume if tick.volume is not None else 0
            timestamp = getattr(tick, 'datetime', datetime.now())
            
            # Thread-safe updates with error handling
            latest_tick["price"] = price
            latest_tick["volume"] = volume
            latest_tick["timestamp"] = timestamp
            
            if price > 0:  # Only process valid prices
                price_history.append({"price": price, "time": timestamp, "volume": volume})
                volume_history.append(volume)
                
                if session_open_price is None:
                    session_open_price = price
                    session_high = price
                    session_low = price
                else:
                    session_high = max(session_high, price)
                    session_low = min(session_low, price)
        except Exception as e:
            # Log but don't crash - this prevents segfaults from propagating
            print(f"⚠️ Tick handler error (non-fatal): {e}")
    
    def place_order(self, action: str, quantity: int, price: float):
        """Place order with account validation and error handling - mode-aware"""
        if not self.connected:
            if not self.reconnect():
                return {"status": "error", "error": "Not connected"}
        
        try:
            if self.trading_mode == "stock":
                return self._place_stock_order(action, quantity, price)
            else:
                return self._place_futures_order(action, quantity, price)
                
        except Exception as e:
            print(f"❌ Order failed: {e}")
            return {"status": "error", "error": str(e)}
    
    def _place_stock_order(self, action: str, quantity: int, price: float):
        """Place stock order (2454.TW odd lots)"""
        # Check stock account availability
        if not hasattr(self.api, 'stock_account') or self.api.stock_account is None:
            print("⚠️ Stock account not available, reconnecting...")
            if not self.reconnect():
                return {"status": "error", "error": "Stock account unavailable"}
        
        order_obj = self.api.Order(
            price=price,
            quantity=quantity,  # Integer for stocks
            action=sj.constant.Action.Buy if action == "BUY" else sj.constant.Action.Sell,
            price_type=sj.constant.StockPriceType.LMT,
            order_type=sj.constant.OrderType.ROD,
            order_lot=sj.constant.StockOrderLot.IntradayOdd,  # Odd lot for small quantities
            account=self.api.stock_account
        )
        
        trade = self.api.place_order(self.contract, order_obj)
        return {"status": "filled", "order_id": trade.status.id, "mode": "stock"}
    
    def _place_futures_order(self, action: str, quantity: int, price: float):
        """Place futures order (MTXF)"""
        # Check futures account availability
        if not hasattr(self.api, 'futopt_account') or self.api.futopt_account is None:
            print("⚠️ Futures account not available, reconnecting...")
            if not self.reconnect():
                return {"status": "error", "error": "Futures account unavailable"}
        
        order_obj = self.api.Order(
            price=price,
            quantity=quantity,  # Integer works for futures too
            action=sj.constant.Action.Buy if action == "BUY" else sj.constant.Action.Sell,
            price_type=sj.constant.FuturesPriceType.LMT,
            order_type=sj.constant.OrderType.ROD,
            account=self.api.futopt_account
        )
        
        trade = self.api.place_order(self.contract, order_obj)
        return {"status": "filled", "order_id": trade.status.id, "mode": "futures"}
    
    def get_account_info(self):
        """Get account equity and margin info with error handling - mode-aware"""
        if not self.connected:
            return {"equity": 0, "available_margin": 0, "status": "error", "error": "Not connected"}
        
        try:
            if self.trading_mode == "stock":
                return self._get_stock_account_info()
            else:
                return self._get_futures_account_info()
        except Exception as e:
            print(f"❌ Failed to get account info: {e}")
            return {"equity": 0, "available_margin": 0, "status": "error", "error": str(e)}
    
    def _get_stock_account_info(self):
        """Get stock account info"""
        if not hasattr(self.api, 'stock_account') or self.api.stock_account is None:
            return {"equity": 0, "available_margin": 0, "status": "error", "error": "Stock account not available"}
        
        # For stock accounts, get balance
        try:
            balance = self.api.account_balance()
            equity = float(balance.acc_balance) if hasattr(balance, 'acc_balance') else 100000
            return {
                "equity": equity,
                "available_margin": equity,
                "status": "ok",
                "mode": "stock"
            }
        except Exception as e:
            # Return default equity for stock mode
            return {
                "equity": 100000,
                "available_margin": 100000,
                "status": "ok",
                "mode": "stock",
                "note": "Using default equity"
            }
    
    def _get_futures_account_info(self):
        """Get futures account info"""
        if not hasattr(self.api, 'futopt_account') or self.api.futopt_account is None:
            return {"equity": 0, "available_margin": 0, "status": "error", "error": "Futures account not available"}
        
        margin = self.api.margin(self.api.futopt_account)
        equity = float(margin.equity) if hasattr(margin, 'equity') else 0
        available_margin = float(margin.available_margin) if hasattr(margin, 'available_margin') else 0
        
        return {
            "equity": equity,
            "available_margin": available_margin,
            "status": "ok",
            "mode": "futures"
        }
    
    def get_profit_loss_history(self, days=30):
        """Get realized P&L for the last N days with error handling - mode-aware"""
        if not self.connected:
            return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": "Not connected"}
        
        try:
            if self.trading_mode == "stock":
                return self._get_stock_pnl_history(days)
            else:
                return self._get_futures_pnl_history(days)
        except Exception as e:
            print(f"❌ Failed to get P&L history: {e}")
            return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": str(e)}
    
    def _get_stock_pnl_history(self, days):
        """Get stock P&L history"""
        # For stock mode, return 0 as baseline (no futures P&L history)
        return {
            "total_pnl": 0,
            "days": days,
            "record_count": 0,
            "status": "ok",
            "mode": "stock"
        }
    
    def _get_futures_pnl_history(self, days):
        """Get futures P&L history"""
        if not hasattr(self.api, 'futopt_account') or self.api.futopt_account is None:
            return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": "Futures account not available"}
        
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days)
        
        pnl_records = self.api.list_profit_loss(
            self.api.futopt_account,
            start_date.strftime("%Y-%m-%d"),
            end_date.strftime("%Y-%m-%d")
        )
        
        total_pnl = 0.0
        for record in pnl_records:
            if hasattr(record, 'pnl'):
                total_pnl += float(record.pnl)
        
        return {
            "total_pnl": total_pnl,
            "days": days,
            "record_count": len(pnl_records),
            "status": "ok",
            "mode": "futures"
        }
    
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
TRADING_MODE = None  # "stock" or "futures"
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

# ============================================================================
# ANTI-WHIPSAW STATE TRACKING
# ============================================================================
# Track consecutive signals to avoid false entries
consecutive_signal_count = 0
last_signal_direction = "NEUTRAL"
last_trade_time = None  # Cooldown tracking
signal_confirmation_history = deque(maxlen=6)  # Last 6 signals (3 minutes at 30s intervals)


def init_trading_mode():
    """Initialize Shioaji and config - only called in FastAPI server mode"""
    global JASYPT_PASSWORD, TRADING_MODE, config, shioaji, OLLAMA_URL, OLLAMA_MODEL
    
    JASYPT_PASSWORD = os.environ.get('JASYPT_PASSWORD')
    if not JASYPT_PASSWORD:
        print("❌ JASYPT_PASSWORD environment variable not set!")
        return
    
    TRADING_MODE = os.environ.get('TRADING_MODE', 'stock')
    print(f"📈 Trading Mode: {TRADING_MODE.upper()} ({('2454.TW odd lots' if TRADING_MODE == 'stock' else 'MTXF futures')})")
    
    config = load_config_with_decryption(JASYPT_PASSWORD)
    print("✅ Configuration loaded and decrypted")
    
    shioaji = ShioajiWrapper(config, trading_mode=TRADING_MODE)
    if not shioaji.connect():
        print("⚠️ Failed to connect to Shioaji after all retries; running in degraded mode")
        return
    
    OLLAMA_URL = config['ollama']['url']
    OLLAMA_MODEL = config['ollama']['model']


# ============================================================================
# ROUTES

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
        "shioaji_connected": shioaji.connected if shioaji else False,
        "trading_mode": TRADING_MODE or "unknown",
        "time": datetime.now().isoformat()
    }

@app.get("/account")
def get_account():
    """Get account equity and margin info for contract scaling"""
    try:
        account_info = shioaji.get_account_info()
        return {
            "equity": account_info.get("equity", 0),
            "available_margin": account_info.get("available_margin", 0),
            "status": account_info.get("status", "error"),
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {
            "equity": 0,
            "available_margin": 0,
            "status": "error",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }

@app.get("/account/profit-history")
def get_profit_history(days: int = 30):
    """Get realized P&L for the last N days for contract scaling"""
    try:
        pnl_history = shioaji.get_profit_loss_history(days)
        return {
            "total_pnl": pnl_history.get("total_pnl", 0),
            "days": days,
            "record_count": pnl_history.get("record_count", 0),
            "status": pnl_history.get("status", "error"),
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {
            "total_pnl": 0,
            "days": days,
            "record_count": 0,
            "status": "error",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
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
    """
    Generate trading signal using improved momentum + volume strategy.
    
    Anti-Whipsaw Improvements (December 2025):
    - Higher momentum thresholds (0.05% entry, 0.08% strong)
    - Requires 3 consecutive aligned signals before entry
    - 3-minute cooldown after closing a position
    - Exit requires stronger reversal (0.06%) to avoid premature exits
    - RSI-like overbought/oversold filter
    - Volume must confirm direction (ratio > 1.5 for entries)
    """
    global last_direction, consecutive_signal_count, last_signal_direction
    global last_trade_time, signal_confirmation_history
    
    price = latest_tick["price"]
    direction = "NEUTRAL"
    confidence = 0.0
    exit_signal = False
    
    # Need minimum data for calculations
    if len(price_history) < 120:  # Increased warmup period (2 minutes of ticks)
        return {
            "current_price": price,
            "direction": "NEUTRAL",
            "confidence": 0.0,
            "exit_signal": False,
            "reason": "Insufficient data (warming up)",
            "momentum_3min": 0.0,
            "momentum_5min": 0.0,
            "momentum_10min": 0.0,
            "volume_ratio": 1.0,
            "rsi": 50.0,
            "consecutive_signals": 0,
            "in_cooldown": False,
            "cooldown_remaining": 0,
            "session_high": session_high if session_high is not None else price,
            "session_low": session_low if session_low is not None else price,
            "raw_direction": "NEUTRAL",
            "timestamp": datetime.now().isoformat()
        }
    
    # ========================================================================
    # MOMENTUM CALCULATIONS
    # ========================================================================
    
    # 3-minute momentum (short-term)
    lookback_3min = min(180, len(price_history))
    prices_3min = [p["price"] for p in list(price_history)[-lookback_3min:]]
    momentum_3min = (prices_3min[-1] - prices_3min[0]) / prices_3min[0] * 100 if prices_3min[0] > 0 else 0
    
    # 5-minute momentum (medium-term confirmation)
    lookback_5min = min(300, len(price_history))
    prices_5min = [p["price"] for p in list(price_history)[-lookback_5min:]]
    momentum_5min = (prices_5min[-1] - prices_5min[0]) / prices_5min[0] * 100 if prices_5min[0] > 0 else 0
    
    # 10-minute momentum (trend context)
    lookback_10min = min(600, len(price_history))
    prices_10min = [p["price"] for p in list(price_history)[-lookback_10min:]]
    momentum_10min = (prices_10min[-1] - prices_10min[0]) / prices_10min[0] * 100 if prices_10min[0] > 0 else 0
    
    # ========================================================================
    # RSI-LIKE CALCULATION (Relative Strength Index)
    # ========================================================================
    rsi = 50.0  # Default neutral
    if len(prices_3min) >= 60:
        gains = []
        losses = []
        for i in range(1, min(60, len(prices_3min))):
            change = prices_3min[-i] - prices_3min[-i-1]
            if change > 0:
                gains.append(change)
            else:
                losses.append(abs(change))
        
        avg_gain = sum(gains) / 60 if gains else 0
        avg_loss = sum(losses) / 60 if losses else 0.0001  # Avoid division by zero
        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))
    
    # ========================================================================
    # VOLUME ANALYSIS
    # ========================================================================
    if len(volume_history) >= 60:
        recent_vol = sum(list(volume_history)[-30:])
        avg_vol = sum(list(volume_history)[-60:]) / 2
        volume_ratio = recent_vol / avg_vol if avg_vol > 0 else 1.0
    else:
        volume_ratio = 1.0
    
    # ========================================================================
    # IMPROVED THRESHOLDS (Anti-Whipsaw)
    # ========================================================================
    MOMENTUM_ENTRY_THRESHOLD = 0.05      # Increased from 0.02 (2.5x higher)
    MOMENTUM_STRONG_THRESHOLD = 0.08     # Strong signal threshold
    MOMENTUM_EXIT_THRESHOLD = 0.06       # Exit requires stronger reversal
    VOLUME_ENTRY_THRESHOLD = 1.5         # Increased from 1.3 (stricter volume confirmation)
    RSI_OVERBOUGHT = 70                  # Don't buy when overbought
    RSI_OVERSOLD = 30                    # Don't sell when oversold
    MIN_CONSECUTIVE_SIGNALS = 3          # Need 3 aligned signals before entry
    COOLDOWN_SECONDS = 180               # 3-minute cooldown after closing position
    
    # ========================================================================
    # COOLDOWN CHECK
    # ========================================================================
    in_cooldown = False
    cooldown_remaining = 0
    if last_trade_time is not None:
        elapsed = (datetime.now() - last_trade_time).total_seconds()
        if elapsed < COOLDOWN_SECONDS:
            in_cooldown = True
            cooldown_remaining = int(COOLDOWN_SECONDS - elapsed)
    
    # ========================================================================
    # SIGNAL GENERATION
    # ========================================================================
    
    # Check if momentum aligns across timeframes
    all_bullish = (momentum_3min > MOMENTUM_ENTRY_THRESHOLD and 
                   momentum_5min > MOMENTUM_ENTRY_THRESHOLD * 0.8 and
                   momentum_10min > 0)  # 10min just needs to be positive
    
    all_bearish = (momentum_3min < -MOMENTUM_ENTRY_THRESHOLD and 
                   momentum_5min < -MOMENTUM_ENTRY_THRESHOLD * 0.8 and
                   momentum_10min < 0)  # 10min just needs to be negative
    
    # Strong signals (higher thresholds)
    strong_bullish = momentum_3min > MOMENTUM_STRONG_THRESHOLD and momentum_5min > MOMENTUM_ENTRY_THRESHOLD
    strong_bearish = momentum_3min < -MOMENTUM_STRONG_THRESHOLD and momentum_5min < -MOMENTUM_ENTRY_THRESHOLD
    
    # Volume confirmation (stricter)
    volume_confirms = volume_ratio > VOLUME_ENTRY_THRESHOLD
    
    # RSI filter (avoid buying overbought, selling oversold)
    rsi_allows_long = rsi < RSI_OVERBOUGHT
    rsi_allows_short = rsi > RSI_OVERSOLD
    
    # ========================================================================
    # DIRECTION DETERMINATION
    # ========================================================================
    raw_direction = "NEUTRAL"
    
    if all_bullish and rsi_allows_long:
        raw_direction = "LONG"
        confidence = min(0.95, 0.4 + abs(momentum_3min) * 8 + abs(momentum_5min) * 4)
        if volume_confirms:
            confidence = min(0.95, confidence + 0.2)
        if strong_bullish:
            confidence = min(0.95, confidence + 0.1)
            
    elif all_bearish and rsi_allows_short:
        raw_direction = "SHORT"
        confidence = min(0.95, 0.4 + abs(momentum_3min) * 8 + abs(momentum_5min) * 4)
        if volume_confirms:
            confidence = min(0.95, confidence + 0.2)
        if strong_bearish:
            confidence = min(0.95, confidence + 0.1)
    else:
        raw_direction = "NEUTRAL"
        confidence = 0.3
    
    # ========================================================================
    # CONSECUTIVE SIGNAL TRACKING (Anti-Whipsaw)
    # ========================================================================
    signal_confirmation_history.append(raw_direction)
    
    if raw_direction == last_signal_direction and raw_direction != "NEUTRAL":
        consecutive_signal_count += 1
    else:
        consecutive_signal_count = 1 if raw_direction != "NEUTRAL" else 0
        last_signal_direction = raw_direction
    
    # Only emit non-NEUTRAL direction if we have enough consecutive signals
    # AND we're not in cooldown AND volume confirms
    if consecutive_signal_count >= MIN_CONSECUTIVE_SIGNALS and not in_cooldown and volume_confirms:
        direction = raw_direction
    else:
        direction = "NEUTRAL"
        if in_cooldown:
            confidence = 0.0  # Zero confidence during cooldown
    
    # ========================================================================
    # EXIT SIGNAL (Stronger reversal required)
    # ========================================================================
    if last_direction == "LONG" and momentum_3min < -MOMENTUM_EXIT_THRESHOLD:
        # Require both short-term AND medium-term to confirm reversal
        if momentum_5min < -MOMENTUM_EXIT_THRESHOLD * 0.5:
            exit_signal = True
    elif last_direction == "SHORT" and momentum_3min > MOMENTUM_EXIT_THRESHOLD:
        if momentum_5min > MOMENTUM_EXIT_THRESHOLD * 0.5:
            exit_signal = True
    
    # Update last_direction only on confirmed entries
    if direction != "NEUTRAL" and confidence >= 0.70:  # Increased from 0.65
        last_direction = direction
    
    return {
        "current_price": price,
        "direction": direction,
        "confidence": round(confidence, 3),
        "exit_signal": exit_signal,
        "momentum_3min": round(momentum_3min, 4),
        "momentum_5min": round(momentum_5min, 4),
        "momentum_10min": round(momentum_10min, 4),
        "volume_ratio": round(volume_ratio, 2),
        "rsi": round(rsi, 1),
        "consecutive_signals": consecutive_signal_count,
        "in_cooldown": in_cooldown,
        "cooldown_remaining": cooldown_remaining,
        "session_high": session_high,
        "session_low": session_low,
        "raw_direction": raw_direction,
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

from pydantic import BaseModel

class OrderRequest(BaseModel):
    action: str
    quantity: int
    price: float
    is_exit: bool = False  # New field to indicate if this is an exit order

@app.post("/order")
def place_order(order: OrderRequest):
    """Execute order via Shioaji with auto-reconnect"""
    global last_trade_time, consecutive_signal_count, last_signal_direction
    
    result = shioaji.place_order(order.action, order.quantity, order.price)
    
    # If this is an exit order, set the cooldown timer and reset signal tracking
    if order.is_exit:
        last_trade_time = datetime.now()
        consecutive_signal_count = 0
        last_signal_direction = "NEUTRAL"
        result["cooldown_started"] = True
        result["cooldown_until"] = (last_trade_time + timedelta(seconds=180)).isoformat()
    
    return result

@app.post("/order/dry-run")
def place_order_dry_run(order: OrderRequest):
    """Validate order request without executing - for pre-market health checks"""
    if order.action not in ("BUY", "SELL"):
        return JSONResponse(status_code=400, content={"error": f"Invalid action: {order.action}"})
    if order.quantity <= 0:
        return JSONResponse(status_code=400, content={"error": f"Invalid quantity: {order.quantity}"})
    if order.price <= 0:
        return JSONResponse(status_code=400, content={"error": f"Invalid price: {order.price}"})
    
    return {
        "status": "validated",
        "dry_run": True,
        "order": {
            "action": order.action,
            "quantity": order.quantity,
            "price": order.price
        },
        "message": "Order would be accepted (dry-run mode)",
        "timestamp": datetime.now().isoformat()
    }

# ============================================================================
# EARNINGS BLACKOUT SCRAPER
# ============================================================================

def send_telegram_message(message: str, password: str):
    """
    Send a Telegram message using credentials from application.yml
    Requires Jasypt password to decrypt bot-token and chat-id
    """
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.dirname(script_dir)
        config_path = os.path.join(project_root, 'src/main/resources/application.yml')
        
        with open(config_path, 'r') as f:
            config = yaml.safe_load(f)
        
        if 'telegram' not in config:
            print("⚠️ Telegram config not found")
            return False
        
        bot_token = decrypt_config_value(config['telegram'].get('bot-token'), password)
        chat_id = decrypt_config_value(config['telegram'].get('chat-id'), password)
        
        if not bot_token or not chat_id:
            print("⚠️ Telegram credentials missing")
            return False
        
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": message,
            "parse_mode": "HTML"
        }
        
        response = requests.post(url, json=payload, timeout=10)
        if response.status_code == 200:
            print(f"📱 Telegram: {message[:50]}...")
            return True
        else:
            print(f"⚠️ Telegram failed: {response.status_code}")
            return False
    except Exception as e:
        print(f"⚠️ Telegram error: {e}")
        return False


def scrape_earnings_dates(jasypt_password: str = None):
    """
    Deprecated: kept only as a one-time migration helper.
    Prefer calling the Java admin endpoint /admin/earnings-blackout/seed or /refresh.
    Scrapes earnings dates with yfinance and writes to legacy JSON for bootstrapping.
    """
    import yfinance as yf
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    config_dir = os.path.join(project_root, 'config')
    output_file = os.path.join(config_dir, 'earnings-blackout-dates.json')
    
    # Major Taiwan stocks to track (Yahoo Finance tickers)
    TICKERS = [
        'TSM',      # TSMC (ADR)
        '2454.TW',  # MediaTek (Taiwan)
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
    
    # Send Telegram start notification if password provided
    if jasypt_password:
        send_telegram_message(
            f"📅 <b>Earnings Scraper Started</b>\n"
            f"Checking {len(TICKERS)} Taiwan stocks for earnings dates...",
            jasypt_password
        )
    
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
            time.sleep(5)
            
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
    
    # Send Telegram completion notification if password provided
    if jasypt_password:
        next_dates_str = ", ".join(future_dates[:3]) if future_dates else "None"
        send_telegram_message(
            f"✅ <b>Earnings Scraper Completed</b>\n"
            f"Found {len(future_dates)} blackout dates\n"
            f"Next: {next_dates_str}",
            jasypt_password
        )
    
    return future_dates


if __name__ == "__main__":
    # Log Python version for debugging
    import sys
    print(f"🐍 Python {sys.version} on {sys.platform}")
    
    parser = argparse.ArgumentParser(description='MTXF Trading Bridge')
    parser.add_argument('--jasypt-password', type=str, default=None,
                        help='Jasypt password for Telegram notifications (optional)')
    args = parser.parse_args()
    
    # Also check environment variable for password
    jasypt_password = args.jasypt_password or os.environ.get('JASYPT_PASSWORD')
    
    # Normal FastAPI server mode - initialize trading components
    init_trading_mode()
    
    import uvicorn
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
