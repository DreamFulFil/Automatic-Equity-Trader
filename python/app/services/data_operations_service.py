"""
Data Operations Service

Handles historical data population, backtesting orchestration, and strategy selection.
All operations are exposed via REST API for Java integration.
"""

import os
import psycopg2
from psycopg2.extras import execute_batch
from datetime import datetime, timedelta
from typing import List, Dict, Optional
import random
import math
import requests


class DataOperationsService:
    """Service for data population and backtest operations"""
    
    # Taiwan stock symbols (50 stocks for comprehensive backtesting)
    TAIWAN_STOCKS = [
        # Technology & Electronics
        "2330", "2454", "2317", "2382", "2308", "2303", "2357", "3008", "2344", "2345",
        "2347", "2353", "3711", "2356", "2377", "2379", "2408", "3034", "6505", "2301",
        "2498", "5269", "2395", "3037", "3231", "3443", "4938", "6669", "2327", "3105",
        "2412", "6770",
        # Financial Services
        "2881", "2882", "2886", "2891", "2892", "2884", "2883", "2885", "5880",
        # Petrochemicals & Materials
        "1303", "1301", "2002", "1216",
        # Retail & Consumer
        "2603", "2609", "2615", "2610", "9910"
    ]
    
    # Realistic base prices for Taiwan stocks (TWD)
    BASE_PRICES = {
        # Technology & Electronics
        "2330": 600.0, "2454": 1100.0, "2317": 100.0, "2382": 180.0, "2308": 300.0,
        "2303": 45.0, "2357": 350.0, "3008": 2500.0, "2344": 120.0, "2345": 280.0,
        "2347": 45.0, "2353": 25.0, "3711": 95.0, "2356": 25.0, "2377": 120.0,
        "2379": 350.0, "2408": 65.0, "3034": 450.0, "6505": 300.0, "2301": 70.0,
        "2498": 50.0, "5269": 950.0, "2395": 380.0, "3037": 110.0, "3231": 950.0,
        "3443": 450.0, "4938": 80.0, "6669": 95.0, "2327": 550.0, "3105": 250.0,
        "2412": 120.0, "6770": 15.0,
        # Financial Services
        "2881": 70.0, "2882": 50.0, "2886": 35.0, "2891": 32.0, "2892": 28.0,
        "2884": 28.0, "2883": 14.0, "2885": 22.0, "5880": 23.0,
        # Petrochemicals & Materials
        "1303": 65.0, "1301": 95.0, "2002": 28.0, "1216": 75.0,
        # Retail & Consumer
        "2603": 180.0, "2609": 65.0, "2615": 75.0, "2610": 20.0, "9910": 180.0
    }
    
    STOCK_NAMES = {
        # Technology & Electronics
        "2330": "Taiwan Semiconductor Manufacturing",
        "2454": "MediaTek",
        "2317": "Hon Hai Precision Industry",
        "2382": "Quanta Computer",
        "2308": "Delta Electronics",
        "2303": "United Microelectronics",
        "2357": "Asustek Computer",
        "3008": "Largan Precision",
        "2344": "Advanced Semiconductor Engineering",
        "2345": "Accton Technology",
        "2347": "Synnex Technology",
        "2353": "Acer",
        "3711": "ASE Technology Holding",
        "2356": "Inventec",
        "2377": "Micro-Star International",
        "2379": "Realtek Semiconductor",
        "2408": "Nanya Technology",
        "3034": "Novatek Microelectronics",
        "6505": "Taiwan Mask",
        "2301": "Lite-On Technology",
        "2498": "HTC Corporation",
        "5269": "Asmedia Technology",
        "2395": "Advantech",
        "3037": "Unimicron Technology",
        "3231": "Wiwynn",
        "3443": "Global Unichip",
        "4938": "Pegatron",
        "6669": "Wistron NeWeb",
        "2327": "Yageo",
        "3105": "Walsin Technology",
        "2412": "Chunghwa Telecom",
        "6770": "Gintech Energy",
        # Financial Services
        "2881": "Fubon Financial Holding",
        "2882": "Cathay Financial Holding",
        "2886": "Mega Financial Holding",
        "2891": "CTBC Financial Holding",
        "2892": "First Financial Holding",
        "2884": "E.Sun Financial Holding",
        "2883": "China Development Financial",
        "2885": "Yuanta Financial Holding",
        "5880": "Taiwan Cooperative Bank",
        # Petrochemicals & Materials
        "1303": "Nan Ya Plastics",
        "1301": "Formosa Plastics",
        "2002": "China Steel",
        "1216": "Uni-President Enterprises",
        # Retail & Consumer
        "2603": "Evergreen Marine",
        "2609": "Yang Ming Marine Transport",
        "2615": "Wan Hai Lines",
        "2610": "China Airlines",
        "9910": "Feng TAY Enterprise",
    }
    
    def __init__(self, db_config: Dict[str, str], java_base_url: str = "http://localhost:16350"):
        self.db_config = db_config
        self.java_base_url = java_base_url
        
    def get_db_connection(self):
        """Get PostgreSQL database connection"""
        return psycopg2.connect(
            host=self.db_config.get('host', 'localhost'),
            port=self.db_config.get('port', 5432),
            database=self.db_config['database'],
            user=self.db_config['username'],
            password=self.db_config['password']
        )
    
    def fetch_historical_data(self, stock_code: str, days: int, symbol: str = None, start_date: str = None, end_date: str = None) -> dict:
        """
        Fetch historical OHLCV data for a Taiwan stock, merging TWSE, Shioaji, Yahoo Finance to fill all available days.
        Returns a structured object in the exact format expected by /data/download-batch endpoint.
        
        Args:
            stock_code: Taiwan stock code (e.g., "2330")
            days: Number of days of historical data to fetch
            symbol: Full Yahoo Finance symbol (e.g., "2330.TW"), defaults to {stock_code}.TW
            start_date: ISO format start date (optional, for response metadata)
            end_date: ISO format end date (optional, for response metadata)
            
        Returns:
            dict: Structured response object with status, symbol, source, data, count, etc.
                {
                    "status": "success",
                    "symbol": "2330.TW",
                    "source": "merged",
                    "data": [...],  # List of OHLCV data points
                    "count": 100,
                    "start_date": "2025-01-01",
                    "end_date": "2025-12-21"
                }
        """
        from datetime import datetime
        import pandas as pd
        import yfinance as yf
        today = datetime.now()
        start = today - timedelta(days=days-1)
        # Helper to normalize date to date only
        def norm_date(d):
            if isinstance(d, datetime):
                return d.date()
            return d
        # Fetch from all sources
        twse = self._fetch_twse(stock_code, start, today)
        shioaji = self._fetch_shioaji(stock_code, start, today)
        yahoo = self._fetch_yahoo(stock_code, start, today)
        # Merge by date, priority: TWSE > Shioaji > Yahoo
        merged = {}
        for src in (twse, shioaji, yahoo):
            for bar in src:
                d = norm_date(bar['date'])
                if d not in merged:
                    merged[d] = bar
        # Format for endpoint
        data_points = []
        for d in sorted(merged.keys()):
            bar = merged[d]
            data_points.append({
                "timestamp": bar["date"].isoformat() if hasattr(bar["date"], "isoformat") else str(bar["date"]),
                "open": bar["open"],
                "high": bar["high"],
                "low": bar["low"],
                "close": bar["close"],
                "volume": bar["volume"]
            })
        return {
            "status": "success",
            "symbol": symbol or f"{stock_code}.TW",
            "source": "merged",
            "data": data_points,
            "count": len(data_points),
            "start_date": start_date,
            "end_date": end_date
        }

    def _fetch_twse(self, stock_code: str, start, end) -> List[Dict]:
        import pandas as pd
        from datetime import datetime
        ohlcv = []
        for year in range(start.year, end.year + 1):
            for month in range(1, 13):
                if year == end.year and month > end.month:
                    break
                date_str = f"{year}{month:02d}01"
                url = f"https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=csv&date={date_str}&stockNo={stock_code}"
                try:
                    resp = requests.get(url, timeout=10)
                    if resp.status_code != 200:
                        continue
                    lines = resp.text.splitlines()
                    csv_lines = [l for l in lines if l and l[0].isdigit()]
                    if not csv_lines:
                        continue
                    df = pd.read_csv(pd.compat.StringIO('\n'.join(csv_lines)), header=None)
                    for _, row in df.iterrows():
                        try:
                            d = datetime.strptime(str(row[0]).strip(), "%Y/%m/%d").date()
                            if d < start.date() or d > end.date():
                                continue
                            ohlcv.append({
                                'date': d,
                                'open': float(row[3]),
                                'high': float(row[4]),
                                'low': float(row[5]),
                                'close': float(row[6]),
                                'volume': int(str(row[1]).replace(',', ''))
                            })
                        except Exception:
                            continue
                except Exception:
                    continue
        return ohlcv

    def _fetch_shioaji(self, stock_code: str, start, end) -> List[Dict]:
        try:
            from app.services.shioaji_service import ShioajiWrapper
            sjw = ShioajiWrapper(self.db_config)
            bars = sjw.fetch_ohlcv(stock_code, (end - start).days + 1)
            # Filter to date range
            return [bar for bar in bars if start.date() <= bar['date'] <= end.date()]
        except Exception:
            return []

    def _fetch_yahoo(self, stock_code: str, start, end) -> List[Dict]:
        import yfinance as yf
        symbol = f"{stock_code}.TW"
        try:
            df = yf.download(symbol, start=start.date(), end=(end+timedelta(days=1)).date())
            if df.empty:
                return []
            ohlcv = []
            for idx, row in df.iterrows():
                d = idx.date() if hasattr(idx, 'date') else idx
                ohlcv.append({
                    'date': d,
                    'open': float(row['Open']),
                    'high': float(row['High']),
                    'low': float(row['Low']),
                    'close': float(row['Close']),
                    'volume': int(row['Volume'])
                })
            return ohlcv
        except Exception:
            return []

    
    def populate_historical_data(self, days: int = 730) -> Dict:
        """
        Populate historical data for all stocks using TWSE, Shioaji, Yahoo fallback order.
        Returns:
            Dict with status and stats
        """
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()
            total_records = 0
            stock_results = {}
            for stock_code in self.TAIWAN_STOCKS:
                data = self.fetch_historical_data(stock_code, days)
                if not data:
                    stock_results[f"{stock_code}.TW"] = 0
                    continue
                records = []
                for bar in data:
                    records.append((
                        f"{stock_code}.TW",
                        bar['date'],
                        bar['open'],
                        bar['high'],
                        bar['low'],
                        bar['close'],
                        bar['volume'],
                        'DAY_1'
                    ))
                insert_query = """
                    INSERT INTO market_data 
                    (symbol, timestamp, open_price, high_price, low_price, close_price, volume, timeframe)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """
                execute_batch(cursor, insert_query, records, page_size=100)
                conn.commit()
                total_records += len(records)
                stock_results[f"{stock_code}.TW"] = len(records)
            cursor.close()
            conn.close()
            return {
                "status": "success",
                "message": "Historical data populated successfully",
                "total_records": total_records,
                "stocks": len(self.TAIWAN_STOCKS),
                "days": days,
                "stock_details": stock_results
            }
        except Exception as e:
            return {
                "status": "error",
                "message": f"Failed to populate data: {str(e)}"
            }
    
    def run_combinatorial_backtests(self, capital: float = 80000, days: int = 730) -> Dict:
        """
        Run backtests for all strategy-stock combinations via Java REST API
        
        Returns:
            Dict with status and results
        """
        try:
            end_date = datetime.now()
            start_date = end_date - timedelta(days=days)
            start_str = start_date.strftime('%Y-%m-%dT00:00:00')
            end_str = end_date.strftime('%Y-%m-%dT23:59:59')
            
            results = []
            successful = 0
            failed = 0
            
            for stock_code in self.TAIWAN_STOCKS:
                symbol = f"{stock_code}.TW"
                
                try:
                    # Call Java backtest endpoint
                    url = f"{self.java_base_url}/api/backtest/run"
                    params = {
                        'symbol': symbol,
                        'timeframe': '1D',
                        'start': start_str,
                        'end': end_str,
                        'capital': capital
                    }
                    
                    response = requests.get(url, params=params, timeout=300)
                    response.raise_for_status()
                    
                    stock_results = response.json()
                    successful += len(stock_results)
                    
                    # Get top 3 for this stock
                    sorted_results = sorted(
                        stock_results.items(),
                        key=lambda x: x[1].get('sharpeRatio', 0),
                        reverse=True
                    )[:3]
                    
                    results.append({
                        "symbol": symbol,
                        "strategies_tested": len(stock_results),
                        "top_strategy": sorted_results[0][0] if sorted_results else None,
                        "top_sharpe": sorted_results[0][1].get('sharpeRatio', 0) if sorted_results else 0
                    })
                    
                except Exception as e:
                    failed += 1
                    results.append({
                        "symbol": symbol,
                        "error": str(e)
                    })
            
            return {
                "status": "success",
                "message": "Combinatorial backtests completed",
                "total_combinations": successful + failed,
                "successful": successful,
                "failed": failed,
                "results": results
            }
            
        except Exception as e:
            return {
                "status": "error",
                "message": f"Failed to run backtests: {str(e)}"
            }
    
    def auto_select_best_strategy(self, min_sharpe: float = 0.5, min_return: float = 10.0, 
                                  min_win_rate: float = 50.0) -> Dict:
        """
        Auto-select best strategy by calling Java AutoStrategySelector endpoint
        
        Returns:
            Dict with selected strategies
        """
        try:
            # Call Java AutoStrategySelector endpoint
            response = requests.post(f"{self.java_base_url}/api/backtest/select-strategy", timeout=60)
            
            if response.status_code == 200:
                return {
                    "status": "success",
                    "message": "Strategy selection delegated to Java AutoStrategySelector",
                    "java_response": response.json()
                }
            else:
                return {
                    "status": "error",
                    "message": f"Java AutoStrategySelector returned status {response.status_code}: {response.text}"
                }
                
        except Exception as e:
            return {
                "status": "error",
                "message": f"Failed to call Java AutoStrategySelector: {str(e)}"
            }
    

