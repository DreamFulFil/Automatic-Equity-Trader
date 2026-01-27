"""
Index Data Service
Fetches market index data from Yahoo Finance for benchmark and arbitrage strategies.

@since 2026-01-26 - Phase 3 Data Improvement Plan
"""
import yfinance as yf
from datetime import datetime, timedelta
from typing import Optional, List
import pandas as pd


# Common index symbols
TAIEX = "^TWII"
TWOII = "^TWOII"
TW50 = "0050.TW"


def get_index_data(symbol: str) -> dict:
    """
    Fetch current index data for a symbol.
    
    Args:
        symbol: Index symbol (e.g., "^TWII", "0050.TW")
        
    Returns:
        Dictionary with index data or error message
    """
    try:
        index = yf.Ticker(symbol)
        info = index.info
        
        if not info or info.get('regularMarketPrice') is None:
            # Try to get from history if info is empty
            hist = index.history(period="5d")
            if hist.empty:
                return {"error": f"No data available for {symbol}"}
            
            # Use last available data from history
            latest = hist.iloc[-1]
            prev_close = hist.iloc[-2]['Close'] if len(hist) > 1 else None
            
            return {
                "symbol": symbol,
                "name": symbol,
                "trade_date": str(hist.index[-1].date()),
                "open": float(latest['Open']) if pd.notna(latest['Open']) else None,
                "high": float(latest['High']) if pd.notna(latest['High']) else None,
                "low": float(latest['Low']) if pd.notna(latest['Low']) else None,
                "close": float(latest['Close']) if pd.notna(latest['Close']) else None,
                "volume": int(latest['Volume']) if pd.notna(latest['Volume']) else None,
                "previous_close": float(prev_close) if prev_close and pd.notna(prev_close) else None,
                "change": float(latest['Close'] - prev_close) if prev_close else None,
                "change_percent": float((latest['Close'] - prev_close) / prev_close) if prev_close and prev_close > 0 else None,
            }
        
        # Extract index data from info
        current_price = info.get('regularMarketPrice')
        prev_close = info.get('regularMarketPreviousClose')
        
        data = {
            "symbol": symbol,
            "name": info.get("longName") or info.get("shortName") or symbol,
            "trade_date": datetime.now().strftime("%Y-%m-%d"),
            
            # Price data
            "open": info.get("regularMarketOpen"),
            "high": info.get("regularMarketDayHigh"),
            "low": info.get("regularMarketDayLow"),
            "close": current_price,
            "previous_close": prev_close,
            
            # Change calculations
            "change": info.get("regularMarketChange"),
            "change_percent": info.get("regularMarketChangePercent"),
            
            # Volume
            "volume": info.get("regularMarketVolume"),
            
            # 52-week range
            "year_high": info.get("fiftyTwoWeekHigh"),
            "year_low": info.get("fiftyTwoWeekLow"),
            
            # Moving averages
            "ma_50": info.get("fiftyDayAverage"),
            "ma_200": info.get("twoHundredDayAverage"),
            
            # Additional info
            "currency": info.get("currency", "TWD"),
            "exchange": info.get("exchange"),
        }
        
        # Calculate change percent if not provided
        if data["change_percent"] is None and current_price and prev_close and prev_close > 0:
            data["change_percent"] = (current_price - prev_close) / prev_close
        
        # Calculate change if not provided
        if data["change"] is None and current_price and prev_close:
            data["change"] = current_price - prev_close
        
        return data
        
    except Exception as e:
        return {"error": str(e)}


def get_index_history(symbol: str, days: int = 252) -> dict:
    """
    Fetch historical index data.
    
    Args:
        symbol: Index symbol
        days: Number of calendar days to fetch (default 252 for ~1 year of trading days)
        
    Returns:
        Dictionary with historical data array or error
    """
    try:
        index = yf.Ticker(symbol)
        
        # Fetch history
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days)
        
        hist = index.history(start=start_date, end=end_date)
        
        if hist.empty:
            return {"error": f"No historical data available for {symbol}"}
        
        # Convert to list of records
        data = []
        for date, row in hist.iterrows():
            record = {
                "date": str(date.date()),
                "open": float(row['Open']) if pd.notna(row['Open']) else None,
                "high": float(row['High']) if pd.notna(row['High']) else None,
                "low": float(row['Low']) if pd.notna(row['Low']) else None,
                "close": float(row['Close']) if pd.notna(row['Close']) else None,
                "volume": int(row['Volume']) if pd.notna(row['Volume']) else None,
            }
            data.append(record)
        
        return {
            "symbol": symbol,
            "count": len(data),
            "start_date": str(hist.index[0].date()),
            "end_date": str(hist.index[-1].date()),
            "data": data
        }
        
    except Exception as e:
        return {"error": str(e)}


def get_multiple_indices(symbols: List[str]) -> dict:
    """
    Fetch data for multiple indices.
    
    Args:
        symbols: List of index symbols
        
    Returns:
        Dictionary with data for each symbol
    """
    results = {}
    for symbol in symbols:
        results[symbol] = get_index_data(symbol)
    return results


def calculate_index_volatility(symbol: str, days: int = 20) -> dict:
    """
    Calculate realized volatility for an index.
    
    Args:
        symbol: Index symbol
        days: Number of trading days for calculation
        
    Returns:
        Dictionary with volatility metrics
    """
    try:
        index = yf.Ticker(symbol)
        hist = index.history(period=f"{days * 2}d")  # Fetch extra for safety
        
        if len(hist) < days:
            return {"error": f"Insufficient data for volatility calculation"}
        
        # Use last N days
        hist = hist.tail(days + 1)
        
        # Calculate daily returns
        returns = hist['Close'].pct_change().dropna()
        
        if len(returns) < days:
            return {"error": f"Insufficient returns data"}
        
        # Calculate metrics
        daily_vol = returns.std()
        annualized_vol = daily_vol * (252 ** 0.5)
        
        return {
            "symbol": symbol,
            "days": days,
            "daily_volatility": float(daily_vol),
            "annualized_volatility": float(annualized_vol),
            "avg_daily_return": float(returns.mean()),
            "max_daily_return": float(returns.max()),
            "min_daily_return": float(returns.min()),
        }
        
    except Exception as e:
        return {"error": str(e)}


def get_index_returns(symbol: str, days: int = 60) -> dict:
    """
    Get daily returns for beta calculation.
    
    Args:
        symbol: Index symbol
        days: Number of trading days
        
    Returns:
        Dictionary with dates and returns
    """
    try:
        index = yf.Ticker(symbol)
        hist = index.history(period=f"{days * 2}d")
        
        if len(hist) < 2:
            return {"error": f"Insufficient data"}
        
        # Calculate returns
        hist['return'] = hist['Close'].pct_change()
        
        # Filter to requested days
        hist = hist.tail(days)
        
        data = []
        for date, row in hist.iterrows():
            if pd.notna(row['return']):
                data.append({
                    "date": str(date.date()),
                    "close": float(row['Close']),
                    "return": float(row['return'])
                })
        
        return {
            "symbol": symbol,
            "count": len(data),
            "data": data
        }
        
    except Exception as e:
        return {"error": str(e)}
