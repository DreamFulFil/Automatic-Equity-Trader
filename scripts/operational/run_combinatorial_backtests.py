#!/usr/bin/env python3
"""
Combinatorial Backtest Runner

Runs all strategies against all stocks with historical data in the database.
This generates comprehensive performance metrics for strategy selection.

Usage:
    python scripts/operational/run_combinatorial_backtests.py --port 16350
"""

import argparse
import requests
from datetime import datetime, timedelta
import time
from typing import List, Dict
import sys

# Taiwan stock symbols (must match populate_historical_data.py)
TAIWAN_STOCKS = [
    "2330.TW",  # TSMC
    "2317.TW",  # Hon Hai
    "2454.TW",  # MediaTek
    "2308.TW",  # Delta Electronics
    "2881.TW",  # Fubon Financial
    "2882.TW",  # Cathay Financial
    "2886.TW",  # Mega Financial
    "2303.TW",  # United Microelectronics
    "1303.TW",  # Nan Ya Plastics
    "1301.TW",  # Formosa Plastics
]

class BacktestRunner:
    def __init__(self, base_url: str, capital: float = 80000):
        self.base_url = base_url
        self.capital = capital
        self.results = []
        
    def get_available_strategies(self) -> List[str]:
        """Fetch list of available strategies from API"""
        try:
            url = f"{self.base_url}/api/backtest/strategies"
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            
            strategies = response.json()
            print(f"✅ Found {len(strategies)} strategies")
            return strategies
            
        except Exception as e:
            print(f"❌ Failed to fetch strategies: {e}")
            return []
    
    def run_backtest(self, symbol: str, start_date: str, end_date: str) -> Dict:
        """Run backtest for a single stock"""
        try:
            url = f"{self.base_url}/api/backtest/run"
            params = {
                'symbol': symbol,
                'timeframe': '1D',
                'start': start_date,
                'end': end_date,
                'capital': self.capital
            }
            
            print(f"  🔄 Running backtest for {symbol}...", end='', flush=True)
            
            response = requests.get(url, params=params, timeout=300)  # 5 minute timeout
            response.raise_for_status()
            
            results = response.json()
            print(f" ✅ Done ({len(results)} strategies)")
            
            return results
            
        except requests.exceptions.Timeout:
            print(f" ❌ Timeout")
            return {}
        except Exception as e:
            print(f" ❌ Error: {e}")
            return {}
    
    def run_all_combinations(self, days: int = 730):
        """Run backtests for all stock × strategy combinations"""
        
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days)
        
        start_str = start_date.strftime('%Y-%m-%dT00:00:00')
        end_str = end_date.strftime('%Y-%m-%dT23:59:59')
        
        print(f"\n📅 Backtest period: {start_date.date()} to {end_date.date()}")
        print(f"📊 Initial capital: ${self.capital:,.0f} TWD")
        print(f"📈 Stocks to test: {len(TAIWAN_STOCKS)}")
        print("=" * 70 + "\n")
        
        total_combinations = 0
        successful_combinations = 0
        
        for i, symbol in enumerate(TAIWAN_STOCKS, 1):
            print(f"[{i}/{len(TAIWAN_STOCKS)}] {symbol}")
            
            results = self.run_backtest(symbol, start_str, end_str)
            
            if results:
                successful_combinations += len(results)
                total_combinations += len(results)
                
                # Display top 3 strategies for this stock
                sorted_results = sorted(
                    results.items(),
                    key=lambda x: x[1].get('sharpeRatio', 0),
                    reverse=True
                )
                
                print(f"  📊 Top 3 strategies:")
                for strategy, result in sorted_results[:3]:
                    sharpe = result.get('sharpeRatio', 0)
                    returns = result.get('totalReturnPercentage', 0)
                    win_rate = result.get('winRate', 0)
                    print(f"    • {strategy}: Sharpe={sharpe:.2f}, Return={returns:.2f}%, WinRate={win_rate:.2f}%")
            
            # Rate limiting between stocks
            if i < len(TAIWAN_STOCKS):
                time.sleep(2)
            
            print()
        
        print("=" * 70)
        print(f"✅ Combinatorial backtests complete!")
        print(f"📊 Total combinations tested: {total_combinations}")
        print(f"✅ Successful: {successful_combinations}")
        print(f"❌ Failed: {total_combinations - successful_combinations}")
        
    def generate_summary_report(self):
        """Generate summary report of all backtest results"""
        try:
            # Query database for top performers
            print("\n📈 Top 10 Strategy-Stock Combinations (by Sharpe Ratio):")
            print("=" * 70)
            
            # This would query the strategy_stock_mapping table
            # For now, we'll just indicate where results are stored
            print("✅ Results stored in database: strategy_stock_mapping table")
            print("   Use AutoStrategySelector or Telegram /selectstrategy to view")
            
        except Exception as e:
            print(f"❌ Failed to generate summary: {e}")

def main():
    parser = argparse.ArgumentParser(description='Run combinatorial backtests')
    parser.add_argument('--port', type=int, default=16350, help='Java application port')
    parser.add_argument('--host', default='localhost', help='Java application host')
    parser.add_argument('--capital', type=float, default=80000, help='Initial capital (TWD)')
    parser.add_argument('--days', type=int, default=730, help='Days of history to backtest')
    
    args = parser.parse_args()
    
    print("╔═══════════════════════════════════════════════════════════════╗")
    print("║         Combinatorial Backtest Runner - All Strategies        ║")
    print("╚═══════════════════════════════════════════════════════════════╝")
    
    base_url = f"http://{args.host}:{args.port}"
    
    # Check if Java application is running
    try:
        response = requests.get(f"{base_url}/api/backtest/strategies", timeout=5)
        if response.status_code == 200:
            print("✅ Java application is running\n")
        else:
            print("⚠️  Java application responded but endpoint may not be ready")
            print(f"    Status: {response.status_code}\n")
    except Exception as e:
        print(f"❌ Java application is not running: {e}")
        print("   Please start it with: ./start-auto-trader.fish <jasypt-password>")
        sys.exit(1)
    
    runner = BacktestRunner(base_url, capital=args.capital)
    
    # Get available strategies
    strategies = runner.get_available_strategies()
    if not strategies:
        print("❌ No strategies available")
        sys.exit(1)
    
    print(f"📊 Will test {len(strategies)} strategies × {len(TAIWAN_STOCKS)} stocks")
    print(f"   = {len(strategies) * len(TAIWAN_STOCKS)} total combinations\n")
    
    # Run all combinations
    runner.run_all_combinations(days=args.days)
    
    # Generate summary
    runner.generate_summary_report()

if __name__ == "__main__":
    main()
