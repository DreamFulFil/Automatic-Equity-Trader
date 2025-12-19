from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from contextlib import asynccontextmanager
import sys
import os
import threading
import time
import argparse
from datetime import datetime
from pydantic import BaseModel
import feedparser

# Add parent directory to path to allow imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.config import load_config_with_decryption
from app.services.shioaji_service import ShioajiWrapper, latest_tick, streaming_quotes, streaming_quotes_lock, order_book, order_book_lock
from app.services.ollama_service import OllamaService
from app.services.ai_insights_service import AIInsightsService
from app.services.earnings_service import scrape_earnings_dates
from app.strategies.legacy_strategy import get_signal_legacy, notify_exit_order

# Global state
JASYPT_PASSWORD = None
TRADING_MODE = None
config = None
shioaji = None
ollama_service = None
ai_insights_service = None

# Lifespan event handler
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Run trading init in background
    def _init_bg():
        try:
            init_trading_mode()
        except Exception as e:
            print(f"⚠️ Background init error: {e}")
    t = threading.Thread(target=_init_bg, daemon=True)
    t.start()
    yield
    # Shutdown
    if shioaji:
        shioaji.logout()

app = FastAPI(lifespan=lifespan)

def init_trading_mode():
    """Initialize Shioaji and config"""
    global JASYPT_PASSWORD, TRADING_MODE, config, shioaji, ollama_service, ai_insights_service
    
    JASYPT_PASSWORD = os.environ.get('JASYPT_PASSWORD')
    if not JASYPT_PASSWORD:
        print("❌ JASYPT_PASSWORD environment variable not set!")
        return
    
    TRADING_MODE = os.environ.get('TRADING_MODE', 'stock')
    print(f"📈 Trading Mode: {TRADING_MODE.upper()}")
    
    config = load_config_with_decryption(JASYPT_PASSWORD)
    print("✅ Configuration loaded and decrypted")
    
    shioaji = ShioajiWrapper(config, trading_mode=TRADING_MODE)
    if not shioaji.connect():
        print("⚠️ Failed to connect to Shioaji after all retries; running in degraded mode")
    
    ollama_url = config['ollama']['url']
    ollama_model = config['ollama']['model']
    ollama_service = OllamaService(ollama_url, ollama_model)
    ai_insights_service = AIInsightsService(ollama_service)

# ============================================================================
# ERROR HANDLING
# ============================================================================

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    error_type = type(exc).__name__
    error_message = str(exc)
    
    llm_explanation = {}
    if ollama_service:
        llm_explanation = ollama_service.call_llama_error_explanation(
            error_type=error_type,
            error_message=error_message,
            context=f"Endpoint: {request.url.path}"
        )
    
    return JSONResponse(
        status_code=500,
        content={
            "status": "error",
            "error_type": error_type,
            "error": error_message,
            "explanation": llm_explanation.get("explanation", error_message),
            "suggestion": llm_explanation.get("suggestion", "Please check logs"),
            "severity": llm_explanation.get("severity", "medium"),
            "timestamp": datetime.now().isoformat()
        }
    )

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    error_details = exc.errors()
    error_summary = "; ".join([f"{e['loc'][-1]}: {e['msg']}" for e in error_details])
    
    llm_explanation = {}
    if ollama_service:
        llm_explanation = ollama_service.call_llama_error_explanation(
            error_type="ValidationError",
            error_message=error_summary,
            context=f"Endpoint: {request.url.path}"
        )
    
    return JSONResponse(
        status_code=422,
        content={
            "status": "error",
            "error_type": "ValidationError",
            "error": error_summary,
            "details": error_details,
            "explanation": llm_explanation.get("explanation", "Invalid request data"),
            "suggestion": llm_explanation.get("suggestion", "Check request parameters"),
            "severity": "low",
            "timestamp": datetime.now().isoformat()
        }
    )

# ============================================================================
# ENDPOINTS
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

@app.get("/health")
def health():
    return {
        "status": "ok",
        "shioaji_connected": shioaji.connected if shioaji else False,
        "trading_mode": TRADING_MODE or "unknown",
        "time": datetime.now().isoformat()
    }

@app.post("/mode")
def set_trading_mode(request: Request, mode_data: dict):
    global TRADING_MODE, shioaji
    new_mode = mode_data.get("mode")
    if new_mode not in ["stock", "futures"]:
        return JSONResponse(status_code=400, content={"error": "Invalid mode"})
    
    if new_mode == TRADING_MODE:
        return {"status": "ok", "message": f"Already in {new_mode} mode", "mode": new_mode}
    
    print(f"🔄 Switching trading mode: {TRADING_MODE} -> {new_mode}")
    if shioaji:
        shioaji.logout()
    
    TRADING_MODE = new_mode
    shioaji = ShioajiWrapper(config, trading_mode=TRADING_MODE)
    if shioaji.connect():
        return {"status": "ok", "message": f"Switched to {new_mode} mode", "mode": new_mode}
    else:
        return JSONResponse(status_code=500, content={"status": "error", "error": "Failed to connect in new mode", "mode": new_mode})

@app.get("/account")
def get_account():
    try:
        account_info = shioaji.get_account_info()
        return {
            "equity": account_info.get("equity", 0),
            "available_margin": account_info.get("available_margin", 0),
            "status": account_info.get("status", "error"),
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {"equity": 0, "available_margin": 0, "status": "error", "error": str(e), "timestamp": datetime.now().isoformat()}

@app.get("/account/profit-history")
def get_profit_history(days: int = 30):
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
        return {"total_pnl": 0, "days": days, "record_count": 0, "status": "error", "error": str(e), "timestamp": datetime.now().isoformat()}

@app.post("/shutdown")
def shutdown():
    def do_shutdown():
        time.sleep(1)
        print("🛑 Shutdown requested - cleaning up...")
        if shioaji:
            shioaji.logout()
        os._exit(0)
    threading.Thread(target=do_shutdown).start()
    return {"status": "shutting_down", "message": "Python bridge shutting down gracefully"}

@app.get("/signal")
def get_signal():
    return get_signal_legacy()

@app.get("/signal/news")
def get_news_veto():
    # Fetch headlines
    headlines = fetch_news_headlines()
    
    news_analysis = {}
    if ollama_service:
        news_analysis = ollama_service.call_llama_news_veto(headlines)
    
    return {
        "news_veto": news_analysis.get("veto", False),
        "news_score": news_analysis.get("score", 0.5),
        "news_reason": news_analysis.get("reason", ""),
        "headlines_count": len(headlines),
        "timestamp": datetime.now().isoformat()
    }

class OrderRequest(BaseModel):
    action: str
    quantity: int
    price: float
    is_exit: bool = False
    strategy: str = "Unknown"

@app.post("/order")
def place_order(order: OrderRequest):
    print(f"🤖 [Strategy: {order.strategy}] Placing {order.action} order: {order.quantity} @ {order.price}")
    result = shioaji.place_order(order.action, order.quantity, order.price)
    
    if order.is_exit:
        cooldown_info = notify_exit_order()
        result.update(cooldown_info)
        print(f"🤖 [Strategy: {order.strategy}] Exit order - cooldown started")
    
    return result

@app.post("/order/dry-run")
def place_order_dry_run(order: OrderRequest):
    if order.action not in ("BUY", "SELL"):
        return JSONResponse(status_code=400, content={"error": f"Invalid action: {order.action}"})
    if order.quantity <= 0:
        return JSONResponse(status_code=400, content={"error": f"Invalid quantity: {order.quantity}"})
    if order.price <= 0:
        return JSONResponse(status_code=400, content={"error": f"Invalid price: {order.price}"})
    
    return {
        "status": "validated",
        "dry_run": True,
        "order": {"action": order.action, "quantity": order.quantity, "price": order.price},
        "message": "Order would be accepted (dry-run mode)",
        "timestamp": datetime.now().isoformat()
    }

@app.get("/earnings/scrape")
def scrape_earnings_endpoint():
    try:
        jasypt_password = os.environ.get('JASYPT_PASSWORD')
        if not jasypt_password:
            return JSONResponse(status_code=500, content={"error": "JASYPT_PASSWORD not set"})
        
        dates = scrape_earnings_dates(jasypt_password)
        
        return {
            "last_updated": datetime.now().isoformat(),
            "source": "Yahoo Finance (yfinance)",
            "tickers_checked": [
                'TSM', '2454.TW', '2317.TW', 'UMC', '2303.TW', 
                'ASX', '3711.TW', '2412.TW', '2882.TW', '2881.TW', 
                '1301.TW', '2002.TW'
            ],
            "dates": dates
        }
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.get("/stream/quotes")
def get_streaming_quotes(limit: int = 50):
    try:
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
    except Exception as e:
        return JSONResponse(status_code=500, content={"status": "error", "error": str(e), "timestamp": datetime.now().isoformat()})

@app.get("/orderbook/{symbol}")
def get_order_book(symbol: str):
    try:
        if shioaji and not hasattr(shioaji, '_bidask_subscribed'):
            shioaji.subscribe_bidask()
            shioaji._bidask_subscribed = True
        
        with order_book_lock:
            current_book = dict(order_book)
        
        if current_book.get("symbol") != symbol and current_book.get("symbol") is not None:
            return JSONResponse(status_code=400, content={"status": "error", "error": f"Symbol mismatch: requested {symbol}, subscribed to {current_book.get('symbol')}", "timestamp": datetime.now().isoformat()})
        
        return {
            "status": "ok",
            "symbol": symbol,
            "bids": current_book.get("bids", []),
            "asks": current_book.get("asks", []),
            "last_update": current_book.get("timestamp"),
            "trading_mode": TRADING_MODE or "unknown",
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return JSONResponse(status_code=500, content={"status": "error", "error": str(e), "timestamp": datetime.now().isoformat()})

@app.post("/stream/subscribe")
def subscribe_streaming():
    try:
        if not shioaji or not shioaji.connected:
            return JSONResponse(status_code=503, content={"status": "error", "error": "Shioaji not connected", "timestamp": datetime.now().isoformat()})
        
        success = shioaji.subscribe_bidask()
        return {
            "status": "ok" if success else "error",
            "subscribed": success,
            "symbol": shioaji.contract.symbol if shioaji.contract else "UNKNOWN",
            "trading_mode": TRADING_MODE or "unknown",
            "message": "Level 2 order book streaming enabled" if success else "Subscription failed",
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return JSONResponse(status_code=500, content={"status": "error", "error": str(e), "timestamp": datetime.now().isoformat()})

# ============================================================================
# AI INSIGHTS ENDPOINTS - For Beginner-Friendly Analysis
# ============================================================================

class OllamaRequest(BaseModel):
    prompt: str
    options: dict = None

class StrategyPerformanceRequest(BaseModel):
    performances: list

class StockPerformanceRequest(BaseModel):
    stock_data: list

class DailyReportRequest(BaseModel):
    report_data: dict

class PositionSizingRequest(BaseModel):
    capital: float
    stock_price: float
    risk_level: str
    equity: float

class RiskMetricsRequest(BaseModel):
    metrics: dict

class StrategyExplainRequest(BaseModel):
    current_strategy: str
    recommended_strategy: str
    reason: str

@app.post("/ai/analyze-strategies")
def analyze_strategies(request: StrategyPerformanceRequest):
    """Analyze strategy performance and provide beginner-friendly insights"""
    if not ai_insights_service:
        return JSONResponse(status_code=503, content={"error": "AI insights service not initialized"})
    
    try:
        result = ai_insights_service.analyze_strategy_performance(request.performances)
        return result
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.post("/ai/analyze-stocks")
def analyze_stocks(request: StockPerformanceRequest):
    """Analyze stock performance and suggest best stocks to trade"""
    if not ai_insights_service:
        return JSONResponse(status_code=503, content={"error": "AI insights service not initialized"})
    
    try:
        result = ai_insights_service.analyze_stock_performance(request.stock_data)
        return result
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.post("/ai/daily-insights")
def daily_insights(request: DailyReportRequest):
    """Generate daily performance insights in simple language"""
    if not ai_insights_service:
        return JSONResponse(status_code=503, content={"error": "AI insights service not initialized"})
    
    try:
        result = ai_insights_service.analyze_daily_report(request.report_data)
        return result
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.post("/ai/position-sizing")
def position_sizing_advice(request: PositionSizingRequest):
    """Get beginner-friendly position sizing advice"""
    if not ai_insights_service:
        return JSONResponse(status_code=503, content={"error": "AI insights service not initialized"})
    
    try:
        result = ai_insights_service.generate_position_sizing_advice(
            request.capital, 
            request.stock_price,
            request.risk_level,
            request.equity
        )
        return result
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.post("/ai/risk-analysis")
def risk_analysis(request: RiskMetricsRequest):
    """Analyze risk metrics and provide warnings"""
    if not ai_insights_service:
        return JSONResponse(status_code=503, content={"error": "AI insights service not initialized"})
    
    try:
        result = ai_insights_service.analyze_risk_metrics(request.metrics)
        return result
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.post("/ai/explain-strategy-switch")
def explain_strategy_switch(request: StrategyExplainRequest):
    """Explain strategy switch in beginner-friendly terms"""
    if not ai_insights_service:
        return JSONResponse(status_code=503, content={"error": "AI insights service not initialized"})
    
    try:
        result = ai_insights_service.explain_strategy_switch(
            request.current_strategy,
            request.recommended_strategy,
            request.reason
        )
        return {"explanation": result}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

@app.post("/ollama/generate")
def ollama_generate(request: OllamaRequest):
    """Generic Ollama generation endpoint for other strategies"""
    if not ollama_service:
        return JSONResponse(status_code=503, content={"error": "Ollama service not initialized"})
    
    result = ollama_service.generate(request.prompt, request.options)
    if "error" in result:
        return JSONResponse(status_code=500, content=result)
    return result

# ============================================================================
# DATA OPERATIONS ENDPOINTS
# ============================================================================

from app.services.data_operations_service import DataOperationsService

# Initialize data operations service (lazy)
data_ops_service = None

def get_data_ops_service():
    global data_ops_service
    if data_ops_service is None:
        db_config = {
            'host': 'localhost',
            'port': 5432,
            'database': os.environ.get('POSTGRES_DB', 'auto_equity_trader'),
            'username': os.environ.get('POSTGRES_USER', 'dreamer'),
            'password': os.environ.get('POSTGRES_PASSWORD', 'password')
        }
        data_ops_service = DataOperationsService(db_config, java_base_url="http://localhost:16350")
    return data_ops_service

class DataPopulationRequest(BaseModel):
    days: int = 730

class BacktestRequest(BaseModel):
    capital: float = 80000
    days: int = 730

class StrategySelectionRequest(BaseModel):
    min_sharpe: float = 0.5
    min_return: float = 10.0
    min_win_rate: float = 50.0

@app.post("/data/populate")
def populate_historical_data_endpoint(request: DataPopulationRequest):
    """Populate historical data for all stocks"""
    service = get_data_ops_service()
    result = service.populate_historical_data(days=request.days)
    return result

@app.post("/data/backtest")
def run_combinatorial_backtests_endpoint(request: BacktestRequest):
    """Run combinatorial backtests via Java REST API"""
    service = get_data_ops_service()
    result = service.run_combinatorial_backtests(capital=request.capital, days=request.days)
    return result

@app.post("/data/select-strategy")
def auto_select_strategy_endpoint(request: StrategySelectionRequest):
    """Auto-select best strategy for main and shadow mode"""
    service = get_data_ops_service()
    result = service.auto_select_best_strategy(
        min_sharpe=request.min_sharpe,
        min_return=request.min_return,
        min_win_rate=request.min_win_rate
    )
    return result

@app.post("/data/full-pipeline")
def run_full_data_pipeline(request: DataPopulationRequest):
    """
    Run complete data pipeline: populate data, run backtests, select strategy
    """
    service = get_data_ops_service()
    
    results = {
        "started_at": datetime.now().isoformat(),
        "steps": []
    }
    
    # Step 1: Populate data
    populate_result = service.populate_historical_data(days=request.days)
    results["steps"].append({"step": "populate_data", "result": populate_result})
    
    if populate_result.get("status") != "success":
        results["status"] = "failed"
        results["failed_at"] = "populate_data"
        return results
    
    # Step 2: Run backtests
    backtest_result = service.run_combinatorial_backtests(capital=80000, days=request.days)
    results["steps"].append({"step": "run_backtests", "result": backtest_result})
    
    if backtest_result.get("status") != "success":
        results["status"] = "failed"
        results["failed_at"] = "run_backtests"
        return results
    
    # Step 3: Select strategy
    selection_result = service.auto_select_best_strategy()
    results["steps"].append({"step": "select_strategy", "result": selection_result})
    
    if selection_result.get("status") != "success":
        results["status"] = "failed"
        results["failed_at"] = "select_strategy"
        return results
    
    results["status"] = "success"
    results["completed_at"] = datetime.now().isoformat()
    return results

if __name__ == "__main__":
    import uvicorn
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
