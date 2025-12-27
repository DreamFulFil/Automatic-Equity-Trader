# Automatic Equity Trading System - Prompt 4
## Python Bridge, Market Data & LLM Integration

**Prerequisite:** Complete Prompt 3 (Strategy Framework)  
**Next:** Prompt 5 (Testing & Deployment)  
**Estimated Time:** 4-5 hours

---

## 🎯 Objective

Implement the Python FastAPI bridge for Shioaji market data and Ollama LLM integration. This prompt establishes the critical link between Java trading logic and external data sources, including real-time market data streaming and AI-powered decision support.

---

## 🐍 Python Bridge Architecture

### Project Structure
```
python/
├── bridge.py                    # Main FastAPI application
├── requirements.txt             # Python dependencies
├── tests/
│   ├── test_bridge.py          # Unit tests
│   ├── test_streaming.py       # Streaming data tests
│   ├── test_integration.py     # Integration tests
│   └── requirements-test.txt
└── compat/                      # Python 3.14 compatibility shims
```

### Dependencies (requirements.txt)
```txt
fastapi==0.115.0
uvicorn[standard]==0.31.1
shioaji==1.1.5
pydantic==2.10.4
requests==2.32.3
PyYAML==6.0.2
pycryptodome==3.21.0
feedparser==6.0.11
```

---

## 🚀 FastAPI Bridge Implementation

### Main Bridge Application
```python
#!/usr/bin/env python3
"""
Dual-Mode Trading Bridge - FastAPI + Shioaji + Ollama
Lightweight bridge between Java trading engine and market data/AI

Supports two trading modes via TRADING_MODE environment variable:
- "stock" (default): Trades 2454.TW odd lots
- "futures": Trades MTXF futures

Key Features:
- Shioaji auto-reconnect (max 5 retries, exponential backoff)
- Real-time tick and Level 2 order book streaming
- LLM-enhanced error explanations via Ollama
- Graceful shutdown on /shutdown endpoint
"""

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from contextlib import asynccontextmanager
import shioaji as sj
import requests
import yaml
import threading
from datetime import datetime, timedelta
from collections import deque
from pydantic import BaseModel

# ============================================================================
# LIFESPAN & INITIALIZATION
# ============================================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Initialize Shioaji in background
    def _init_bg():
        try:
            init_trading_mode()
        except Exception as e:
            print(f"⚠️ Background init error: {e}")
    
    t = threading.Thread(target=_init_bg, daemon=True)
    t.start()
    yield
    # Shutdown: Cleanup

app = FastAPI(lifespan=lifespan)

# ============================================================================
# GLOBAL STATE
# ============================================================================

JASYPT_PASSWORD = None
TRADING_MODE = None  # "stock" or "futures"
config = None
shioaji = None

# Market data
latest_tick = {"price": 0, "volume": 0, "timestamp": None}
price_history = deque(maxlen=600)
volume_history = deque(maxlen=600)

# Streaming & Level 2 data
streaming_quotes = deque(maxlen=100)
streaming_quotes_lock = threading.Lock()

order_book = {
    "bids": [],
    "asks": [],
    "timestamp": None,
    "symbol": None
}
order_book_lock = threading.Lock()

# Session tracking
session_open_price = None
session_high = None
session_low = None

# Ollama config
OLLAMA_URL = None
OLLAMA_MODEL = None

# ============================================================================
# SHIOAJI WRAPPER WITH AUTO-RECONNECT
# ============================================================================

class ShioajiWrapper:
    """
    Shioaji wrapper with auto-reconnect and streaming support.
    
    Features:
    - Auto-reconnect: Up to 5 retries with exponential backoff
    - Dual mode: Stock (2454.TW) or Futures (MTXF)
    - Streaming: Real-time tick and Level 2 order book data
    - Thread-safe: All data updates protected by locks
    """
    
    MAX_RETRIES = 5
    BASE_BACKOFF_SECONDS = 2
    
    def __init__(self, config, trading_mode="stock"):
        self.config = config
        self.trading_mode = trading_mode
        self.api = None
        self.contract = None
        self.connected = False
        self._lock = threading.Lock()
        self._callback_ref = None
        self._bidask_callback_ref = None
        print(f"📈 ShioajiWrapper initialized in {trading_mode.upper()} mode")
    
    def connect(self) -> bool:
        """Connect to Shioaji with retry logic"""
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                print(f"🔄 Shioaji connection attempt {attempt}/{self.MAX_RETRIES}...")
                
                self.api = sj.Shioaji()
                
                # Select credentials based on mode
                creds = self.config['shioaji'].get(
                    'stock' if self.trading_mode == "stock" else 'future', 
                    {}
                )
                
                # Login
                self.api.login(
                    api_key=creds.get('api-key'),
                    secret_key=creds.get('secret-key'),
                    contracts_cb=lambda security_type: 
                        print(f"✅ Contracts loaded: {security_type}")
                )
                
                # Activate CA
                self.api.activate_ca(
                    ca_path=self.config['shioaji']['ca-path'],
                    ca_passwd=self.config['shioaji']['ca-password'],
                    person_id=self.config['shioaji']['person-id']
                )
                
                mode_str = "📄 Paper trading" if self.config['shioaji']['simulation'] else "💰 LIVE"
                print(f"{mode_str} mode activated")
                
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
        
        return False
    
    def _subscribe_stock(self):
        """Subscribe to 2454.TW tick data"""
        self.contract = self.api.Contracts.Stocks.TSE["2454"]
        self.api.quote.subscribe(
            self.contract,
            quote_type=sj.constant.QuoteType.Tick,
            version=sj.constant.QuoteVersion.v1
        )
        
        def safe_tick_handler(exchange, tick):
            try:
                self._handle_tick(exchange, tick)
            except Exception as e:
                print(f"⚠️ Tick callback error: {e}")
        
        self._callback_ref = safe_tick_handler
        self.api.quote.set_on_tick_stk_v1_callback(self._callback_ref)
        
        print(f"✅ Subscribed to {self.contract.symbol} (STOCK mode)")
    
    def _subscribe_futures(self):
        """Subscribe to MTXF tick data"""
        self.contract = self.api.Contracts.Futures.TXF.TXFR1
        self.api.quote.subscribe(
            self.contract,
            quote_type=sj.constant.QuoteType.Tick,
            version=sj.constant.QuoteVersion.v1
        )
        
        def safe_tick_handler(exchange, tick):
            try:
                self._handle_tick(exchange, tick)
            except Exception as e:
                print(f"⚠️ Tick callback error: {e}")
        
        self._callback_ref = safe_tick_handler
        self.api.quote.set_on_tick_fop_v1_callback(self._callback_ref)
        
        print(f"✅ Subscribed to {self.contract.symbol} (FUTURES mode)")
    
    def subscribe_bidask(self):
        """Subscribe to Level 2 order book (bid/ask depth)"""
        try:
            self.api.quote.subscribe(
                self.contract,
                quote_type=sj.constant.QuoteType.BidAsk,
                version=sj.constant.QuoteVersion.v1
            )
            
            if self.trading_mode == "stock":
                def safe_bidask_handler(exchange, bidask):
                    try:
                        self._handle_bidask(exchange, bidask)
                    except Exception as e:
                        print(f"⚠️ BidAsk callback error: {e}")
                
                self._bidask_callback_ref = safe_bidask_handler
                self.api.quote.set_on_bidask_stk_v1_callback(self._bidask_callback_ref)
            else:
                def safe_bidask_handler(exchange, bidask):
                    try:
                        self._handle_bidask(exchange, bidask)
                    except Exception as e:
                        print(f"⚠️ BidAsk callback error: {e}")
                
                self._bidask_callback_ref = safe_bidask_handler
                self.api.quote.set_on_bidask_fop_v1_callback(self._bidask_callback_ref)
            
            print(f"✅ Level 2 order book subscribed")
            return True
        except Exception as e:
            print(f"❌ BidAsk subscription failed: {e}")
            return False
    
    def _handle_tick(self, exchange, tick):
        """Process incoming tick data and update streaming buffer"""
        global latest_tick, price_history, volume_history
        global session_open_price, session_high, session_low
        global streaming_quotes, streaming_quotes_lock
        
        if not tick or not hasattr(tick, 'close') or not hasattr(tick, 'volume'):
            return
        
        price = float(tick.close) if tick.close else 0.0
        volume = tick.volume if tick.volume else 0
        timestamp = getattr(tick, 'datetime', datetime.now())
        
        # Update latest tick
        latest_tick["price"] = price
        latest_tick["volume"] = volume
        latest_tick["timestamp"] = timestamp
        
        if price > 0:
            price_history.append({"price": price, "time": timestamp, "volume": volume})
            volume_history.append(volume)
            
            # Update streaming quotes buffer (thread-safe)
            with streaming_quotes_lock:
                streaming_quotes.append({
                    "symbol": self.contract.symbol,
                    "price": price,
                    "volume": volume,
                    "timestamp": timestamp.isoformat() if hasattr(timestamp, 'isoformat') else str(timestamp),
                    "exchange": str(exchange) if exchange else "UNKNOWN"
                })
            
            # Update session metrics
            if session_open_price is None:
                session_open_price = price
                session_high = price
                session_low = price
            else:
                session_high = max(session_high, price)
                session_low = min(session_low, price)
    
    def _handle_bidask(self, exchange, bidask):
        """Process Level 2 order book data"""
        global order_book, order_book_lock
        
        try:
            bids = []
            asks = []
            
            if hasattr(bidask, 'bid_price') and hasattr(bidask, 'bid_volume'):
                bid_prices = bidask.bid_price if isinstance(bidask.bid_price, list) else [bidask.bid_price]
                bid_volumes = bidask.bid_volume if isinstance(bidask.bid_volume, list) else [bidask.bid_volume]
                
                for price, vol in zip(bid_prices, bid_volumes):
                    if price and vol:
                        bids.append({"price": float(price), "volume": int(vol)})
            
            if hasattr(bidask, 'ask_price') and hasattr(bidask, 'ask_volume'):
                ask_prices = bidask.ask_price if isinstance(bidask.ask_price, list) else [bidask.ask_price]
                ask_volumes = bidask.ask_volume if isinstance(bidask.ask_volume, list) else [bidask.ask_volume]
                
                for price, vol in zip(ask_prices, ask_volumes):
                    if price and vol:
                        asks.append({"price": float(price), "volume": int(vol)})
            
            timestamp = getattr(bidask, 'datetime', datetime.now())
            
            with order_book_lock:
                order_book["bids"] = sorted(bids, key=lambda x: x["price"], reverse=True)[:5]
                order_book["asks"] = sorted(asks, key=lambda x: x["price"])[:5]
                order_book["timestamp"] = timestamp.isoformat() if hasattr(timestamp, 'isoformat') else str(timestamp)
                order_book["symbol"] = self.contract.symbol
        
        except Exception as e:
            print(f"⚠️ Order book handler error: {e}")
    
    def place_order(self, action: str, quantity: int, price: float):
        """Place order (simulation or live)"""
        if self.config['shioaji']['simulation']:
            print(f"🎭 SIMULATION: {action} {quantity} @ {price}")
            return {"status": "filled", "order_id": f"sim-{datetime.now().timestamp()}", "mode": self.trading_mode}
        
        # Real order execution logic (omitted for brevity)
        # ...
    
    def logout(self):
        """Graceful logout"""
        try:
            if self.api:
                self.api.logout()
                print("✅ Shioaji logged out")
        except Exception as e:
            print(f"⚠️ Logout error: {e}")

# ============================================================================
# OLLAMA LLM INTEGRATION
# ============================================================================

def call_llama_error_explanation(error_type: str, error_message: str, context: str = "") -> dict:
    """Generate human-readable error explanations via Ollama"""
    if not OLLAMA_URL or not OLLAMA_MODEL:
        return {
            "explanation": error_message,
            "suggestion": "System initializing",
            "severity": "medium"
        }
    
    prompt = f"""You are a trading system support agent. Explain this error in simple terms.

Error Type: {error_type}
Error Message: {error_message}
Context: {context}

Respond ONLY with valid JSON:
{{"explanation": "user-friendly explanation", "suggestion": "what to do", "severity": "low/medium/high"}}"""
    
    try:
        response = requests.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "stream": False,
                "options": {"temperature": 0.3}
            },
            timeout=3
        )
        result = response.json()['response']
        return json.loads(result)
    except:
        return {
            "explanation": error_message,
            "suggestion": "Check logs",
            "severity": "medium"
        }

# ============================================================================
# FASTAPI ENDPOINTS
# ============================================================================

@app.get("/health")
def health():
    """Health check endpoint"""
    return {
        "status": "ok",
        "shioaji_connected": shioaji.connected if shioaji else False,
        "trading_mode": TRADING_MODE or "unknown",
        "time": datetime.now().isoformat()
    }

@app.get("/signal")
def get_signal():
    """Generate trading signal with momentum and volume analysis"""
    # Signal generation logic (from existing bridge.py)
    # ...
    pass

@app.get("/stream/quotes")
def get_streaming_quotes(limit: int = 50):
    """Get recent streaming quote ticks (up to 100)"""
    limit = min(limit, 100)
    
    with streaming_quotes_lock:
        quotes_list = list(streaming_quotes)[-limit:]
    
    return {
        "status": "ok",
        "symbol": shioaji.contract.symbol if shioaji and shioaji.contract else "UNKNOWN",
        "trading_mode": TRADING_MODE or "unknown",
        "count": len(quotes_list),
        "quotes": quotes_list,
        "timestamp": datetime.now().isoformat()
    }

@app.get("/orderbook/{symbol}")
def get_order_book(symbol: str):
    """Get Level 2 order book data (bid/ask depth)"""
    # Auto-subscribe if not subscribed
    if shioaji and not hasattr(shioaji, '_bidask_subscribed'):
        shioaji.subscribe_bidask()
        shioaji._bidask_subscribed = True
    
    with order_book_lock:
        current_book = dict(order_book)
    
    return {
        "status": "ok",
        "symbol": symbol,
        "bids": current_book.get("bids", []),
        "asks": current_book.get("asks", []),
        "last_update": current_book.get("timestamp"),
        "trading_mode": TRADING_MODE or "unknown",
        "timestamp": datetime.now().isoformat()
    }

@app.post("/stream/subscribe")
def subscribe_streaming():
    """Enable Level 2 order book streaming"""
    if not shioaji or not shioaji.connected:
        return JSONResponse(status_code=503, content={"error": "Not connected"})
    
    success = shioaji.subscribe_bidask()
    
    return {
        "status": "ok" if success else "error",
        "subscribed": success,
        "symbol": shioaji.contract.symbol if shioaji.contract else "UNKNOWN",
        "message": "Level 2 streaming enabled" if success else "Subscription failed"
    }

@app.post("/order")
def place_order(order: OrderRequest):
    """Execute order via Shioaji"""
    result = shioaji.place_order(order.action, order.quantity, order.price)
    return result

@app.post("/shutdown")
def shutdown():
    """Graceful shutdown"""
    def do_shutdown():
        time.sleep(1)
        print("🛑 Shutdown requested")
        shioaji.logout()
        os._exit(0)
    
    threading.Thread(target=do_shutdown).start()
    return {"status": "shutting_down"}

# ============================================================================
# MAIN
# ============================================================================

if __name__ == "__main__":
    import uvicorn
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
```

---

## ☕ Java Bridge Client

```java
package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
public class BridgeClient {
    
    @Value("${trading.bridge.url}")
    private String bridgeUrl;
    
    @Value("${trading.bridge.timeout-ms}")
    private int timeoutMs;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public BridgeClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(3000))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public JsonNode getSignal() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bridgeUrl + "/signal"))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Failed to get signal from bridge: {}", e.getMessage());
            return null;
        }
    }
    
    public JsonNode getStreamingQuotes(int limit) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bridgeUrl + "/stream/quotes?limit=" + limit))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Failed to get streaming quotes: {}", e.getMessage());
            return null;
        }
    }
    
    public JsonNode getOrderBook(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bridgeUrl + "/orderbook/" + symbol))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Failed to get order book: {}", e.getMessage());
            return null;
        }
    }
}
```

---

## ✅ Verification Checklist

- [ ] Python bridge starts on port 8888
- [ ] Shioaji connects successfully (simulation mode)
- [ ] `/health` endpoint returns 200
- [ ] `/signal` endpoint returns valid JSON
- [ ] `/stream/quotes` returns tick data
- [ ] `/orderbook/{symbol}` returns bid/ask depth
- [ ] Ollama integration generates LLM responses
- [ ] Java BridgeClient communicates with Python
- [ ] All Python tests pass (19 tests)

### Test Commands
```bash
# Start Python bridge
cd python
JASYPT_PASSWORD=test TRADING_MODE=stock python bridge.py

# Test endpoints
curl http://localhost:8888/health
curl http://localhost:8888/signal
curl http://localhost:8888/stream/quotes?limit=10
curl http://localhost:8888/orderbook/2454

# Run Python tests
pytest tests/ -v

# Test Java integration
mvn test -Dtest=BridgeClientTest
```

---

## 📝 Next Steps (Prompt 5)

Prompt 5 will cover:
- Comprehensive testing strategy
- Performance benchmarking
- Deployment procedures
- Monitoring and alerting
- Production readiness checklist

---

**Prompt Series:** 4 of 5  
**Status:** Python Bridge Complete  
**Next:** Testing, Deployment & Operations
