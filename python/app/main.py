from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
import json
from contextlib import asynccontextmanager
import sys
import os
import threading
import time
import argparse
from datetime import datetime
from pydantic import BaseModel
import feedparser
import yfinance as yf

# Add parent directory to path to allow imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.config import load_config_with_decryption
from app.services.shioaji_service import ShioajiWrapper, latest_tick, streaming_quotes, streaming_quotes_lock, order_book, order_book_lock
from app.services.ollama_service import OllamaService
from app.services.ai_insights_service import AIInsightsService
from app.services.earnings_service import scrape_earnings_dates
from app.services.fundamental_service import get_fundamental_data, get_fundamental_data_batch
from app.services.news_service import get_news_for_symbol, get_news_for_symbols, get_market_news_tw
from app.services.index_service import get_index_data, get_index_history, get_multiple_indices, calculate_index_volatility, get_index_returns
from app.services.calendar_service import (
    get_holidays, is_trading_day, get_next_trading_day, get_previous_trading_day,
    get_trading_days, count_trading_days, get_futures_expiration, get_next_futures_expiration,
    get_futures_expirations, get_seasonal_strength, get_all_seasonal_strength,
    get_event_risk_level, get_market_calendar_summary
)
from app.services.annual_report_service import (
    AnnualReportRequest,
    AnnualReportResponse,
    AnnualReportDownloadError,
    download_shareholders_annual_report,
)
from app.services.annual_report_rag_service import (
    AnnualReportRagIndexRequest,
    AnnualReportRagIndexResponse,
    AnnualReportRagQueryRequest,
    AnnualReportRagQueryResponse,
    AnnualReportRagError,
    index_annual_report_rag,
    query_annual_report_rag,
)
from app.services.annual_report_summary_service import (
    AnnualReportSummaryRequest,
    AnnualReportSummaryResponse,
    AnnualReportSummaryError,
    summarize_annual_report,
)
from app.strategies.legacy_strategy import get_signal_legacy, notify_exit_order

# Global state
JASYPT_PASSWORD = None
TRADING_MODE = None
config = None
shioaji = None
ollama_service = None
ai_insights_service = None

# News cache to avoid hammering Ollama
news_cache = {"result": None, "timestamp": None, "ttl_seconds": 300}  # 5 minute cache

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
    """Scrape MoneyDJ + UDN RSS feeds - optimized for speed"""
    feeds = [
        "https://www.moneydj.com/rss/RssNews.djhtm",
        "https://udn.com/rssfeed/news/2/6638"
    ]
    headlines = []
    for feed_url in feeds:
        try:
            # Add timeout to prevent hanging
            feed = feedparser.parse(feed_url, timeout=5)
            for entry in feed.entries[:3]:  # Reduced from 5 to 3 per feed
                headlines.append(entry.title)
        except:
            pass
    return headlines[:8]  # Reduced from 15 to 8 total headlines

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
    # Check cache first
    now = time.time()
    if news_cache["result"] and news_cache["timestamp"]:
        age = now - news_cache["timestamp"]
        if age < news_cache["ttl_seconds"]:
            return news_cache["result"]
    
    # Fetch headlines
    headlines = fetch_news_headlines()
    
    news_analysis = {}
    if ollama_service:
        news_analysis = ollama_service.call_llama_news_veto(headlines)
    
    result = {
        "news_veto": news_analysis.get("veto", False),
        "news_score": news_analysis.get("score", 0.5),
        "news_reason": news_analysis.get("reason", ""),
        "headlines_count": len(headlines),
        "timestamp": datetime.now().isoformat()
    }
    
    # Cache the result
    news_cache["result"] = result
    news_cache["timestamp"] = now
    
    return result

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
def scrape_earnings_endpoint(force: bool = False):
    try:
        # If a cached file exists and force is not set, return cached results quickly to avoid long external scrapes in tests and fast health checks.
        script_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.abspath(os.path.join(script_dir, '..', '..'))
        config_dir = os.path.join(project_root, 'config')
        output_file = os.path.join(config_dir, 'earnings-blackout-dates.json')

        if not force and os.path.exists(output_file):
            try:
                with open(output_file, 'r') as f:
                    cached = json.load(f)
                    return cached
            except Exception:
                # Fall through to perform live scrape if cache is unreadable
                pass

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


@app.post("/reports/shareholders/annual", response_model=AnnualReportResponse)
def download_shareholders_annual_report_endpoint(request: AnnualReportRequest):
    try:
        return download_shareholders_annual_report(
            ticker=request.ticker,
            report_year=request.report_year,
            report_type=request.report_type,
            force=request.force,
        )
    except AnnualReportDownloadError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message)


@app.post("/reports/shareholders/annual/rag/index", response_model=AnnualReportRagIndexResponse)
def index_shareholders_annual_report_rag_endpoint(request: AnnualReportRagIndexRequest):
    try:
        return index_annual_report_rag(
            ticker=request.ticker,
            report_year=request.report_year,
            report_type=request.report_type,
            force=request.force,
        )
    except (AnnualReportDownloadError, AnnualReportRagError) as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message)


@app.post("/reports/shareholders/annual/rag/query", response_model=AnnualReportRagQueryResponse)
def query_shareholders_annual_report_rag_endpoint(request: AnnualReportRagQueryRequest):
    try:
        return query_annual_report_rag(
            ticker=request.ticker,
            question=request.question,
            report_year=request.report_year,
            report_type=request.report_type,
            top_k=request.top_k,
            force=request.force,
        )
    except (AnnualReportDownloadError, AnnualReportRagError) as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message)


@app.post("/reports/shareholders/annual/summary", response_model=AnnualReportSummaryResponse)
def summarize_shareholders_annual_report_endpoint(request: AnnualReportSummaryRequest):
    try:
        return summarize_annual_report(
            ticker=request.ticker,
            report_year=request.report_year,
            report_type=request.report_type,
            force=request.force,
        )
    except (AnnualReportDownloadError, AnnualReportSummaryError) as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message)

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
# DATA OPERATIONS ENDPOINTS (DEPRECATED - Now handled by Java)
# These endpoints are kept for backward compatibility but are no longer used.
# All data operations are now handled by Java BacktestService.
# ============================================================================

# NOTE: The following endpoints have been migrated to Java:
# - POST /data/populate -> Java BacktestController.populateHistoricalData()
# - POST /data/backtest -> Java BacktestController.runCombinationalBacktests()
# - POST /data/select-strategy -> Java BacktestController.autoSelectStrategy()
# - POST /data/full-pipeline -> Java BacktestController.runFullPipeline()
#
# The Python DataOperationsService and these endpoints are deprecated.
# They will be removed in a future version.

# ============================================================================
# HISTORICAL DATA DOWNLOAD ENDPOINT (Required by Java HistoryDataService)
# Uses Shioaji kbars API for Taiwan stock historical data
# ============================================================================

class DownloadBatchRequest(BaseModel):
    symbol: str
    start_date: str
    end_date: str
    stocks: list = []  # Optional list of Taiwan stock symbols from Java

@app.post("/data/download-batch")
def download_batch(request: DownloadBatchRequest):
    """
    Download historical OHLCV data for a stock symbol within a date range.
    Acts as a pass-through: receives request, delegates to DataOperationsService.fetch_historical_data,
    and returns the service result directly without modification.
    
    Args:
        request: DownloadBatchRequest with symbol, start_date, end_date, and optional stocks list
        
    Returns:
        Structured response from DataOperationsService.fetch_historical_data
    """
    try:
        stock_code = request.symbol.replace(".TWO", "").replace(".TW", "")
        start = datetime.fromisoformat(request.start_date.replace('Z', '+00:00'))
        end = datetime.fromisoformat(request.end_date.replace('Z', '+00:00'))
        days = (end - start).days + 1
        
        db_config = {
            'host': os.environ.get('POSTGRES_HOST', 'localhost'),
            'port': int(os.environ.get('POSTGRES_PORT', 5432)),
            'database': os.environ.get('POSTGRES_DB', 'auto_equity_trader'),
            'username': os.environ.get('POSTGRES_USER', 'dreamer'),
            'password': os.environ.get('POSTGRES_PASSWORD', 'password')
        }
        
        from app.services.data_operations_service import DataOperationsService
        service = DataOperationsService(db_config)
        
        # Pass-through delegation: return service result directly
        return service.fetch_historical_data(
            stock_code=stock_code,
            days=days,
            symbol=request.symbol,
            start_date=request.start_date,
            end_date=request.end_date
        )
        
    except Exception as e:
        # Return structured error payload with 200 to avoid causing callers to treat
        # this as an infrastructure (HTTP) failure. Java side handles status
        # field and will fallback appropriately.
        return JSONResponse(
            status_code=200,
            content={
                "status": "error",
                "symbol": request.symbol,
                "error": str(e),
                "data": []
            }
        )


@app.get("/data/source-stats")
def get_data_source_stats():
    """
    Get data source statistics for diagnostics.
    
    Returns which data sources (Shioaji, Yahoo, TWSE) supplied data for each symbol,
    success/failure counts, and total records fetched from each source.
    
    This is useful for diagnosing data pipeline issues and understanding
    which sources are actively providing data.
    
    Returns:
        {
            "status": "ok",
            "timestamp": "2025-12-27T10:00:00",
            "sources": {
                "shioaji": {"success": 10, "failed": 2, "records": 500},
                "yahoo": {"success": 8, "failed": 4, "records": 400},
                "twse": {"success": 5, "failed": 7, "records": 250}
            },
            "last_fetch": {
                "2330": {"primary_source": "shioaji", "breakdown": {...}, "total_count": 5, "timestamp": "..."},
                ...
            },
            "total_fetches": 15,
            "shioaji_connected": true,
            "trading_mode": "stock"
        }
    """
    from app.services.data_operations_service import get_source_stats
    
    stats = get_source_stats()
    
    return {
        "status": "ok",
        "timestamp": datetime.now().isoformat(),
        "sources": {
            "shioaji": stats["shioaji"],
            "yahoo": stats["yahoo"],
            "twse": stats["twse"]
        },
        "last_fetch": stats["last_fetch"],
        "total_fetches": stats["total_fetches"],
        "shioaji_connected": shioaji.connected if shioaji else False,
        "trading_mode": TRADING_MODE or "unknown"
    }


@app.post("/data/source-stats/reset")
def reset_data_source_stats():
    """Reset data source statistics counters"""
    from app.services.data_operations_service import reset_source_stats
    reset_source_stats()
    return {"status": "ok", "message": "Source statistics reset", "timestamp": datetime.now().isoformat()}


def _download_with_yfinance(symbol: str, start_str: str, end_str: str, orig_start: str, orig_end: str):
    """
    Download historical data using Yahoo Finance (yfinance) as fallback.
    """
    try:
        # Use the symbol as-is for yfinance (e.g., "2330.TW")
        ticker = yf.Ticker(symbol)
        
        # Download historical data
        hist = ticker.history(start=start_str, end=end_str, interval="1d")
        
        if hist.empty:
            return {
                "status": "success",
                "symbol": symbol,
                "source": "yfinance",
                "data": [],
                "count": 0,
                "message": "No data found for the specified date range"
            }
        
        # Convert to list of dicts
        data_points = []
        for idx, row in hist.iterrows():
            # idx is a pandas Timestamp
            ts = idx.to_pydatetime()
            data_points.append({
                "timestamp": ts.isoformat(),
                "open": float(row["Open"]),
                "high": float(row["High"]),
                "low": float(row["Low"]),
                "close": float(row["Close"]),
                "volume": int(row["Volume"])
            })
        
        return {
            "status": "success",
            "symbol": symbol,
            "source": "yfinance",
            "data": data_points,
            "count": len(data_points),
            "start_date": orig_start,
            "end_date": orig_end
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "source": "yfinance",
                "error": str(e),
                "data": []
            }
        )

# ============================================================================
# FUNDAMENTAL DATA ENDPOINTS
# ============================================================================

@app.get("/api/fundamentals/{symbol}")
def get_fundamentals(symbol: str):
    """
    Get fundamental financial data for a stock symbol.
    
    Args:
        symbol: Stock ticker (e.g., "2330.TW", "TSM")
        
    Returns:
        Comprehensive fundamental metrics including valuation, profitability,
        financial health, dividends, cash flow, growth, and analyst data.
    """
    try:
        data = get_fundamental_data(symbol)
        
        if "error" in data and data.get("error"):
            return JSONResponse(
                status_code=404,
                content={
                    "status": "error",
                    "symbol": symbol,
                    "error": data["error"]
                }
            )
        
        return {
            "status": "success",
            "symbol": symbol,
            "source": "yfinance",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "error": str(e)
            }
        )


class FundamentalsBatchRequest(BaseModel):
    symbols: list[str]
    delay_seconds: float = 3.0


@app.post("/api/fundamentals/batch")
def get_fundamentals_batch(request: FundamentalsBatchRequest):
    """
    Get fundamental data for multiple symbols (with rate limiting).
    
    Args:
        symbols: List of stock tickers
        delay_seconds: Delay between requests (default 3.0)
        
    Returns:
        List of fundamental data for each symbol.
    """
    try:
        if len(request.symbols) > 50:
            return JSONResponse(
                status_code=400,
                content={
                    "status": "error",
                    "error": "Maximum 50 symbols per batch request"
                }
            )
        
        results = get_fundamental_data_batch(request.symbols, request.delay_seconds)
        
        successful = [r for r in results if "error" not in r or not r.get("error")]
        failed = [r for r in results if "error" in r and r.get("error")]
        
        return {
            "status": "success",
            "source": "yfinance",
            "timestamp": datetime.now().isoformat(),
            "total": len(request.symbols),
            "successful": len(successful),
            "failed": len(failed),
            "data": results
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


# ============================================================================
# NEWS DATA ENDPOINTS
# ============================================================================

@app.get("/api/news/{symbol}")
def get_news(symbol: str, max_articles: int = 10):
    """
    Get news articles for a stock symbol.
    
    Args:
        symbol: Stock ticker (e.g., "2330.TW", "TSM")
        max_articles: Maximum number of articles to return (default 10)
        
    Returns:
        List of news articles with headlines, summaries, sources, and timestamps.
    """
    try:
        if max_articles > 50:
            max_articles = 50  # Cap at 50 articles
            
        data = get_news_for_symbol(symbol, max_articles)
        
        if "error" in data and data.get("error"):
            return JSONResponse(
                status_code=404,
                content={
                    "status": "error",
                    "symbol": symbol,
                    "error": data["error"]
                }
            )
        
        return {
            "status": "success",
            "symbol": symbol,
            "source": "yfinance",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "error": str(e)
            }
        )


class NewsBatchRequest(BaseModel):
    symbols: list[str]
    max_articles_per_symbol: int = 5


@app.post("/api/news/batch")
def get_news_batch(request: NewsBatchRequest):
    """
    Get news for multiple symbols.
    
    Args:
        symbols: List of stock tickers
        max_articles_per_symbol: Max articles per symbol (default 5)
        
    Returns:
        News articles grouped by symbol.
    """
    try:
        if len(request.symbols) > 20:
            return JSONResponse(
                status_code=400,
                content={
                    "status": "error",
                    "error": "Maximum 20 symbols per batch request"
                }
            )
        
        results = get_news_for_symbols(request.symbols, request.max_articles_per_symbol)
        
        return {
            "status": "success",
            "source": "yfinance",
            "timestamp": datetime.now().isoformat(),
            "symbols": len(request.symbols),
            "data": results
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


@app.get("/api/news/market/tw")
def get_taiwan_market_news():
    """
    Get general Taiwan market news from RSS feeds.
    
    Returns:
        Recent market news from MoneyDJ, UDN, and CNYES.
    """
    try:
        data = get_market_news_tw()
        
        return {
            "status": "success",
            "source": "RSS_feeds",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


# ============================================================================
# INDEX DATA ENDPOINTS
# ============================================================================

@app.get("/api/index/{symbol}")
def get_index(symbol: str):
    """
    Get current index data for a symbol.
    
    Args:
        symbol: Index symbol (e.g., "^TWII" for TAIEX, "0050.TW")
        
    Returns:
        Current index data including price, change, volume, moving averages.
    """
    try:
        data = get_index_data(symbol)
        
        if "error" in data and data.get("error"):
            return JSONResponse(
                status_code=404,
                content={
                    "status": "error",
                    "symbol": symbol,
                    "error": data["error"]
                }
            )
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "error": str(e)
            }
        )


@app.get("/api/index/{symbol}/history")
def get_index_historical(symbol: str, days: int = 252):
    """
    Get historical index data.
    
    Args:
        symbol: Index symbol
        days: Number of calendar days to fetch (default 252 for ~1 year)
        
    Returns:
        Historical OHLCV data for the index.
    """
    try:
        data = get_index_history(symbol, days)
        
        if "error" in data and data.get("error"):
            return JSONResponse(
                status_code=404,
                content={
                    "status": "error",
                    "symbol": symbol,
                    "error": data["error"]
                }
            )
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "error": str(e)
            }
        )


@app.get("/api/index/{symbol}/returns")
def get_index_daily_returns(symbol: str, days: int = 60):
    """
    Get daily returns for beta calculation.
    
    Args:
        symbol: Index symbol
        days: Number of trading days
        
    Returns:
        Daily returns data.
    """
    try:
        data = get_index_returns(symbol, days)
        
        if "error" in data and data.get("error"):
            return JSONResponse(
                status_code=404,
                content={
                    "status": "error",
                    "symbol": symbol,
                    "error": data["error"]
                }
            )
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "error": str(e)
            }
        )


@app.get("/api/index/{symbol}/volatility")
def get_index_vol(symbol: str, days: int = 20):
    """
    Calculate realized volatility for an index.
    
    Args:
        symbol: Index symbol
        days: Number of trading days for calculation
        
    Returns:
        Volatility metrics (daily and annualized).
    """
    try:
        data = calculate_index_volatility(symbol, days)
        
        if "error" in data and data.get("error"):
            return JSONResponse(
                status_code=404,
                content={
                    "status": "error",
                    "symbol": symbol,
                    "error": data["error"]
                }
            )
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            **data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "symbol": symbol,
                "error": str(e)
            }
        )


class IndexBatchRequest(BaseModel):
    symbols: list[str]


@app.post("/api/index/batch")
def get_indices_batch(request: IndexBatchRequest):
    """
    Get data for multiple indices.
    
    Args:
        symbols: List of index symbols
        
    Returns:
        Data for each index.
    """
    try:
        data = get_multiple_indices(request.symbols)
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            "count": len(data),
            "data": data
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


# ============================================================================
# CALENDAR ENDPOINTS (Phase 4)
# ============================================================================

@app.get("/api/calendar/holidays/{country}/{year}")
def get_market_holidays(country: str, year: int):
    """
    Get market holidays for a country and year.
    
    Args:
        country: Country code (TW, US)
        year: Year
        
    Returns:
        List of holidays with dates and names.
    """
    try:
        holidays = get_holidays(country, year)
        
        return {
            "status": "success",
            "country": country.upper(),
            "year": year,
            "count": len(holidays),
            "holidays": holidays
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "country": country,
                "year": year,
                "error": str(e)
            }
        )


@app.get("/api/calendar/is-trading-day/{date_str}")
def check_trading_day(date_str: str, country: str = "TW"):
    """
    Check if a date is a trading day.
    
    Args:
        date_str: Date in YYYY-MM-DD format
        country: Country code (default: TW)
        
    Returns:
        Boolean indicating if it's a trading day.
    """
    from datetime import date as dt_date
    try:
        check_date = dt_date.fromisoformat(date_str)
        is_trading = is_trading_day(check_date, country)
        
        return {
            "status": "success",
            "date": date_str,
            "country": country.upper(),
            "is_trading_day": is_trading,
            "day_of_week": check_date.strftime("%A")
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "date": date_str,
                "error": str(e)
            }
        )


@app.get("/api/calendar/trading-days")
def get_trading_days_range(start_date: str, end_date: str, country: str = "TW"):
    """
    Get all trading days between two dates.
    
    Args:
        start_date: Start date in YYYY-MM-DD format
        end_date: End date in YYYY-MM-DD format
        country: Country code (default: TW)
        
    Returns:
        List of trading days.
    """
    from datetime import date as dt_date
    try:
        start = dt_date.fromisoformat(start_date)
        end = dt_date.fromisoformat(end_date)
        trading_days = get_trading_days(start, end, country)
        
        return {
            "status": "success",
            "start_date": start_date,
            "end_date": end_date,
            "country": country.upper(),
            "count": len(trading_days),
            "trading_days": trading_days
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


@app.get("/api/calendar/futures/expiration/{year}/{month}")
def get_futures_expiration_date(year: int, month: int):
    """
    Get futures expiration date for a specific month.
    
    Args:
        year: Year
        month: Month (1-12)
        
    Returns:
        Futures expiration date.
    """
    try:
        exp_date = get_futures_expiration(year, month)
        
        return {
            "status": "success",
            "year": year,
            "month": month,
            "expiration_date": exp_date.isoformat(),
            "day_of_week": exp_date.strftime("%A")
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "year": year,
                "month": month,
                "error": str(e)
            }
        )


@app.get("/api/calendar/futures/next")
def get_next_expiration():
    """
    Get the next futures expiration date.
    
    Returns:
        Next expiration date and days until.
    """
    try:
        exp_info = get_next_futures_expiration()
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            **exp_info
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


@app.get("/api/calendar/futures/year/{year}")
def get_year_expirations(year: int):
    """
    Get all futures expiration dates for a year.
    
    Args:
        year: Year
        
    Returns:
        List of expiration dates.
    """
    try:
        expirations = get_futures_expirations(year)
        
        return {
            "status": "success",
            "year": year,
            "count": len(expirations),
            "expirations": expirations
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "year": year,
                "error": str(e)
            }
        )


@app.get("/api/calendar/seasonal")
def get_seasonal_info(month: int = None):
    """
    Get seasonal strength indicator.
    
    Args:
        month: Month (1-12), default: current month
        
    Returns:
        Seasonal strength and classification.
    """
    try:
        if month is not None:
            info = get_seasonal_strength(month)
        else:
            info = get_seasonal_strength()
        
        return {
            "status": "success",
            **info
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


@app.get("/api/calendar/seasonal/all")
def get_all_seasonal_info():
    """
    Get seasonal strength for all months.
    
    Returns:
        Seasonal strength for all 12 months.
    """
    try:
        all_months = get_all_seasonal_strength()
        
        return {
            "status": "success",
            "months": all_months
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


@app.get("/api/calendar/risk/{date_str}")
def get_date_risk(date_str: str):
    """
    Get event risk level for a date.
    
    Args:
        date_str: Date in YYYY-MM-DD format
        
    Returns:
        Risk level and position size recommendation.
    """
    from datetime import date as dt_date
    try:
        check_date = dt_date.fromisoformat(date_str)
        risk_info = get_event_risk_level(check_date)
        
        return {
            "status": "success",
            **risk_info
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "date": date_str,
                "error": str(e)
            }
        )


@app.get("/api/calendar/summary")
def get_calendar_summary(days: int = 30):
    """
    Get comprehensive market calendar summary.
    
    Args:
        days: Number of days to look ahead (default: 30)
        
    Returns:
        Calendar summary with holidays, expirations, seasonal info.
    """
    try:
        summary = get_market_calendar_summary(days=days)
        
        return {
            "status": "success",
            **summary
        }
        
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(e)
            }
        )


if __name__ == "__main__":
    import uvicorn
    print("🐍 Python bridge starting on port 8888...")
    uvicorn.run(app, host="0.0.0.0", port=8888, log_level="info")
