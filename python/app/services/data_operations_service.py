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
    
    # Taiwan stock symbols (top 10 liquid stocks)
    TAIWAN_STOCKS = [
        "2330", "2317", "2454", "2308", "2881",
        "2882", "2886", "2303", "1303", "1301"
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
    
    def generate_mock_ohlcv(self, stock_code: str, days: int) -> List[Dict]:
        """Generate realistic OHLCV data with trends and volatility"""
        base_price = self.BASE_PRICES.get(stock_code, 100.0)
        data = []
        current_date = datetime.now() - timedelta(days=days)
        price = base_price
        trend = random.uniform(-0.0001, 0.0003)
        
        for _ in range(days):
            # Skip weekends
            if current_date.weekday() >= 5:
                current_date += timedelta(days=1)
                continue
            
            # Daily volatility and price movement
            daily_volatility = random.uniform(0.02, 0.04)
            change_pct = random.gauss(trend, daily_volatility)
            price *= (1 + change_pct)
            
            # Generate OHLC
            daily_range = price * random.uniform(0.015, 0.04)
            open_price = price + random.uniform(-daily_range/2, daily_range/2)
            high = max(open_price, price) + random.uniform(0, daily_range/2)
            low = min(open_price, price) - random.uniform(0, daily_range/2)
            close = price
            
            # Ensure OHLC relationships
            high = max(high, open_price, close)
            low = min(low, open_price, close)
            
            # Volume (log-normal distribution)
            volume = int(1_000_000 * random.lognormvariate(0, 1.5))
            
            data.append({
                'date': current_date,
                'open': round(open_price, 2),
                'high': round(high, 2),
                'low': round(low, 2),
                'close': round(close, 2),
                'volume': volume
            })
            
            current_date += timedelta(days=1)
        
        return data
    
    def populate_historical_data(self, days: int = 730) -> Dict:
        """
        Populate historical data for all stocks
        
        Returns:
            Dict with status and stats
        """
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()
            
            total_records = 0
            stock_results = {}
            
            for stock_code in self.TAIWAN_STOCKS:
                # Generate data
                data = self.generate_mock_ohlcv(stock_code, days)
                
                # Prepare records
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
                
                # Batch insert
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
        Auto-select best strategy for main trading and top 10 for shadow mode
        
        Returns:
            Dict with selected strategies
        """
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()
            
            # Query best strategy
            cursor.execute("""
                SELECT symbol, strategy_name, sharpe_ratio, total_return_pct, 
                       win_rate_pct, max_drawdown_pct, total_trades
                FROM strategy_stock_mapping
                WHERE sharpe_ratio > %s 
                  AND total_return_pct > %s
                  AND win_rate_pct > %s
                ORDER BY sharpe_ratio DESC
                LIMIT 1
            """, (min_sharpe, min_return, min_win_rate))
            
            best = cursor.fetchone()
            
            if not best:
                cursor.close()
                conn.close()
                return {
                    "status": "error",
                    "message": "No strategies meet the criteria"
                }
            
            symbol, strategy_name, sharpe, total_return, win_rate, max_dd, trades = best
            
            # Update active strategy
            cursor.execute("""
                UPDATE active_strategy_config
                SET strategy_name = %s,
                    switch_reason = %s,
                    auto_switched = TRUE,
                    sharpe_ratio = %s,
                    max_drawdown_pct = %s,
                    total_return_pct = %s,
                    win_rate_pct = %s,
                    last_updated = NOW()
                WHERE id = 1
            """, (strategy_name, 
                  f'Auto-selected: {strategy_name} (Sharpe={sharpe:.2f})',
                  sharpe, max_dd, total_return, win_rate))
            
            # Update stock settings
            stock_name = self.STOCK_NAMES.get(symbol.replace('.TW', ''), symbol)
            cursor.execute("""
                INSERT INTO stock_settings (id, symbol, name, reason, updated_at)
                VALUES (1, %s, %s, %s, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    symbol = %s,
                    name = %s,
                    reason = %s,
                    updated_at = NOW()
            """, (symbol, stock_name, f'Auto-selected: {strategy_name}',
                  symbol, stock_name, f'Auto-selected: {strategy_name}'))
            
            # Get top 10 for shadow mode (excluding the active one)
            cursor.execute("""
                SELECT symbol, strategy_name, sharpe_ratio, total_return_pct, 
                       win_rate_pct, max_drawdown_pct
                FROM strategy_stock_mapping
                WHERE sharpe_ratio > %s 
                  AND total_return_pct > %s
                  AND win_rate_pct > %s
                  AND NOT (symbol = %s AND strategy_name = %s)
                ORDER BY sharpe_ratio DESC
                LIMIT 10
            """, (min_sharpe, min_return, min_win_rate, symbol, strategy_name))
            
            shadow_stocks = cursor.fetchall()
            
            # Clear and insert shadow mode stocks
            cursor.execute("DELETE FROM shadow_mode_stocks")
            
            shadow_results = []
            for s_symbol, s_strategy, s_sharpe, s_return, s_win_rate, s_max_dd in shadow_stocks:
                s_name = self.STOCK_NAMES.get(s_symbol.replace('.TW', ''), s_symbol)
                
                cursor.execute("""
                    INSERT INTO shadow_mode_stocks 
                    (symbol, stock_name, strategy_name, expected_return_percentage, 
                     sharpe_ratio, win_rate_pct, max_drawdown_pct, enabled, created_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, TRUE, NOW())
                """, (s_symbol, s_name, s_strategy, s_return, s_sharpe, s_win_rate, s_max_dd))
                
                shadow_results.append({
                    "symbol": s_symbol,
                    "strategy": s_strategy,
                    "sharpe": round(s_sharpe, 2),
                    "return": round(s_return, 2),
                    "win_rate": round(s_win_rate, 2)
                })
            
            conn.commit()
            cursor.close()
            conn.close()
            
            return {
                "status": "success",
                "message": "Strategy selection completed",
                "active_strategy": {
                    "symbol": symbol,
                    "strategy": strategy_name,
                    "sharpe": round(sharpe, 2),
                    "return": round(total_return, 2),
                    "win_rate": round(win_rate, 2),
                    "max_drawdown": round(max_dd, 2),
                    "trades": trades
                },
                "shadow_mode": {
                    "count": len(shadow_results),
                    "stocks": shadow_results
                }
            }
            
        except Exception as e:
            return {
                "status": "error",
                "message": f"Failed to select strategy: {str(e)}"
            }
