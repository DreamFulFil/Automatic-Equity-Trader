"""
Fundamental Data Service
Fetches financial metrics from Yahoo Finance for fundamental analysis strategies.

@since 2026-01-26 - Phase 1 Data Improvement Plan
"""
import yfinance as yf
from typing import Optional
import time


def get_fundamental_data(symbol: str) -> dict:
    """
    Fetch comprehensive fundamental data for a stock symbol.
    
    Args:
        symbol: Stock ticker (e.g., "2330.TW", "TSM")
        
    Returns:
        Dictionary with fundamental metrics or error message
    """
    try:
        stock = yf.Ticker(symbol)
        info = stock.info
        
        if not info or info.get('regularMarketPrice') is None:
            return {"error": f"No data available for {symbol}"}
        
        # Extract fundamental data with safe access
        data = {
            # Basic Info
            "symbol": symbol,
            "name": info.get("longName") or info.get("shortName"),
            "currency": info.get("currency", "TWD"),
            "exchange": info.get("exchange"),
            "sector": info.get("sector"),
            "industry": info.get("industry"),
            
            # Valuation Metrics
            "eps": info.get("trailingEps"),
            "pe_ratio": info.get("trailingPE"),
            "forward_pe": info.get("forwardPE"),
            "pb_ratio": info.get("priceToBook"),
            "ps_ratio": info.get("priceToSalesTrailing12Months"),
            "book_value": info.get("bookValue"),
            "market_cap": info.get("marketCap"),
            "enterprise_value": info.get("enterpriseValue"),
            "ev_to_ebitda": info.get("enterpriseToEbitda"),
            "ev_to_revenue": info.get("enterpriseToRevenue"),
            "peg_ratio": info.get("pegRatio"),
            
            # Profitability Metrics
            "roe": _safe_percentage(info.get("returnOnEquity")),
            "roa": _safe_percentage(info.get("returnOnAssets")),
            "roic": None,  # Not directly available, calculated separately if needed
            "gross_margin": _safe_percentage(info.get("grossMargins")),
            "operating_margin": _safe_percentage(info.get("operatingMargins")),
            "net_margin": _safe_percentage(info.get("profitMargins")),
            "ebitda_margin": _safe_percentage(info.get("ebitdaMargins")),
            
            # Financial Health Metrics
            "debt_to_equity": _safe_ratio(info.get("debtToEquity")),
            "current_ratio": info.get("currentRatio"),
            "quick_ratio": info.get("quickRatio"),
            "total_debt": info.get("totalDebt"),
            "total_cash": info.get("totalCash"),
            "total_cash_per_share": info.get("totalCashPerShare"),
            
            # Dividend Metrics
            "dividend_yield": _safe_percentage(info.get("dividendYield")),
            "payout_ratio": _safe_percentage(info.get("payoutRatio")),
            "dividend_rate": info.get("dividendRate"),
            "trailing_annual_dividend_rate": info.get("trailingAnnualDividendRate"),
            "five_year_avg_dividend_yield": _safe_percentage(info.get("fiveYearAvgDividendYield")),
            "ex_dividend_date": _safe_timestamp(info.get("exDividendDate")),
            "dividend_years": None,  # Would require historical analysis
            
            # Cash Flow Metrics
            "operating_cash_flow": info.get("operatingCashflow"),
            "free_cash_flow": info.get("freeCashflow"),
            "fcf_per_share": _calculate_fcf_per_share(info),
            
            # Growth Metrics
            "revenue": info.get("totalRevenue"),
            "revenue_growth": _safe_percentage(info.get("revenueGrowth")),
            "earnings_growth": _safe_percentage(info.get("earningsGrowth")),
            "earnings_quarterly_growth": _safe_percentage(info.get("earningsQuarterlyGrowth")),
            "revenue_per_share": info.get("revenuePerShare"),
            "total_assets": None,  # Fetched from balance sheet if needed
            "asset_growth": None,  # Calculated from historical data if needed
            
            # Quality / Accrual Metrics
            "accruals_ratio": _calculate_accruals_ratio(stock),
            "shares_outstanding": info.get("sharesOutstanding"),
            "float_shares": info.get("floatShares"),
            "net_stock_issuance": None,  # Would require historical analysis
            
            # Analyst Data
            "analyst_count": info.get("numberOfAnalystOpinions"),
            "target_price": info.get("targetMeanPrice"),
            "target_high_price": info.get("targetHighPrice"),
            "target_low_price": info.get("targetLowPrice"),
            "recommendation_mean": info.get("recommendationMean"),
            "recommendation_key": info.get("recommendationKey"),
            
            # Beta & Range
            "beta": info.get("beta"),
            "fifty_two_week_high": info.get("fiftyTwoWeekHigh"),
            "fifty_two_week_low": info.get("fiftyTwoWeekLow"),
            "fifty_day_average": info.get("fiftyDayAverage"),
            "two_hundred_day_average": info.get("twoHundredDayAverage"),
            
            # Current Price Data
            "current_price": info.get("regularMarketPrice") or info.get("currentPrice"),
            "previous_close": info.get("previousClose"),
            "open": info.get("regularMarketOpen"),
            "day_high": info.get("regularMarketDayHigh"),
            "day_low": info.get("regularMarketDayLow"),
            "volume": info.get("regularMarketVolume"),
            "avg_volume": info.get("averageVolume"),
            "avg_volume_10d": info.get("averageVolume10days"),
        }
        
        # Try to fetch additional data from financials
        data = _enrich_with_financials(stock, data)
        
        return data
        
    except Exception as e:
        return {"error": str(e), "symbol": symbol}


def _safe_percentage(value) -> Optional[float]:
    """Convert percentage value, handling None and NaN."""
    if value is None:
        return None
    try:
        val = float(value)
        if val != val:  # NaN check
            return None
        return val
    except (ValueError, TypeError):
        return None


def _safe_ratio(value) -> Optional[float]:
    """Convert ratio value, dividing by 100 if needed (yfinance quirk)."""
    if value is None:
        return None
    try:
        val = float(value)
        if val != val:  # NaN check
            return None
        # yfinance sometimes returns D/E as percentage (e.g., 50 instead of 0.5)
        if val > 10:
            val = val / 100
        return val
    except (ValueError, TypeError):
        return None


def _safe_timestamp(value) -> Optional[str]:
    """Convert timestamp to ISO string."""
    if value is None:
        return None
    try:
        from datetime import datetime
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(value).isoformat()
        return str(value)
    except Exception:
        return None


def _calculate_fcf_per_share(info: dict) -> Optional[float]:
    """Calculate free cash flow per share."""
    fcf = info.get("freeCashflow")
    shares = info.get("sharesOutstanding")
    if fcf is not None and shares is not None and shares > 0:
        return fcf / shares
    return None


def _calculate_accruals_ratio(stock) -> Optional[float]:
    """
    Calculate accruals ratio (earnings quality indicator).
    Accruals = Net Income - Operating Cash Flow
    Accruals Ratio = Accruals / Total Assets
    Lower is better (more cash-based earnings).
    """
    try:
        info = stock.info
        financials = stock.financials
        balance_sheet = stock.balance_sheet
        cashflow = stock.cashflow
        
        if financials is None or financials.empty:
            return None
        if cashflow is None or cashflow.empty:
            return None
        if balance_sheet is None or balance_sheet.empty:
            return None
            
        # Get most recent values
        net_income = None
        ocf = None
        total_assets = None
        
        # Try to get Net Income
        for col in ['Net Income', 'Net Income From Continuing Operations']:
            if col in financials.index:
                net_income = financials.loc[col].iloc[0]
                break
        
        # Try to get Operating Cash Flow
        for col in ['Operating Cash Flow', 'Total Cash From Operating Activities']:
            if col in cashflow.index:
                ocf = cashflow.loc[col].iloc[0]
                break
        
        # Try to get Total Assets
        if 'Total Assets' in balance_sheet.index:
            total_assets = balance_sheet.loc['Total Assets'].iloc[0]
        
        if net_income is not None and ocf is not None and total_assets is not None and total_assets != 0:
            accruals = net_income - ocf
            return accruals / total_assets
            
        return None
        
    except Exception:
        return None


def _enrich_with_financials(stock, data: dict) -> dict:
    """Enrich data with values from financial statements."""
    try:
        balance_sheet = stock.balance_sheet
        if balance_sheet is not None and not balance_sheet.empty:
            if 'Total Assets' in balance_sheet.index:
                data['total_assets'] = balance_sheet.loc['Total Assets'].iloc[0]
                
            # Calculate asset growth if we have historical data
            if 'Total Assets' in balance_sheet.index and len(balance_sheet.columns) >= 2:
                current_assets = balance_sheet.loc['Total Assets'].iloc[0]
                prev_assets = balance_sheet.loc['Total Assets'].iloc[1]
                if prev_assets and prev_assets != 0:
                    data['asset_growth'] = (current_assets - prev_assets) / prev_assets
                    
    except Exception:
        pass  # Silently fail, data enrichment is optional
        
    return data


def get_fundamental_data_batch(symbols: list[str], delay_seconds: float = 3.0) -> list[dict]:
    """
    Fetch fundamental data for multiple symbols with rate limiting.
    
    Args:
        symbols: List of stock tickers
        delay_seconds: Delay between requests to avoid rate limiting
        
    Returns:
        List of fundamental data dictionaries
    """
    results = []
    for i, symbol in enumerate(symbols):
        data = get_fundamental_data(symbol)
        results.append(data)
        
        # Rate limiting (skip delay for last item)
        if i < len(symbols) - 1:
            time.sleep(delay_seconds)
            
    return results
