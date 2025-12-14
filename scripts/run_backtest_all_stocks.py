#!/usr/bin/env python3
"""
Comprehensive Backtest Runner for All 18 Major Taiwan Stocks
Generates detailed report with strategy recommendations for 80k TWD capital
"""

import requests
import json
import time
from datetime import datetime, timedelta
from typing import Dict, List
import sys

# Configuration
API_BASE_URL = "http://localhost:16350/api/backtest"
CAPITAL = 80000  # 80k TWD
OUTPUT_FILE = "/tmp/backtest-result-20251214.md"

# 18 Major Taiwan Stocks
STOCKS = [
    ("2330.TW", "TSMC"),
    ("2454.TW", "MediaTek"),
    ("2317.TW", "Hon Hai (Foxconn)"),
    ("2303.TW", "UMC"),
    ("3711.TW", "ASE Technology"),
    ("2412.TW", "Chunghwa Telecom"),
    ("2882.TW", "Cathay Financial"),
    ("2881.TW", "Fubon Financial"),
    ("1301.TW", "Formosa Plastics"),
    ("2002.TW", "China Steel"),
    ("2891.TW", "CTBC Financial"),
    ("2886.TW", "Mega Financial"),
    ("2308.TW", "Delta Electronics"),
    ("1303.TW", "Nan Ya Plastics"),
    ("2382.TW", "Quanta Computer"),
    ("2357.TW", "Asustek Computer"),
    ("3008.TW", "Largan Precision"),
    ("2912.TW", "President Chain Store"),
]

def run_backtest_for_stock(symbol: str, stock_name: str) -> Dict:
    """Run backtest for a single stock"""
    # Use last 90 days of data
    end_date = datetime.now()
    start_date = end_date - timedelta(days=90)
    
    params = {
        'symbol': symbol,
        'timeframe': '1day',
        'start': start_date.strftime('%Y-%m-%dT00:00:00'),
        'end': end_date.strftime('%Y-%m-%dT23:59:59'),
        'capital': CAPITAL
    }
    
    print(f"  Running backtest for {stock_name} ({symbol})...")
    
    try:
        response = requests.get(f"{API_BASE_URL}/run", params=params, timeout=300)
        
        if response.status_code == 200:
            results = response.json()
            return {
                'success': True,
                'results': results,
                'symbol': symbol,
                'name': stock_name
            }
        else:
            return {
                'success': False,
                'error': f"HTTP {response.status_code}: {response.text[:200]}",
                'symbol': symbol,
                'name': stock_name
            }
    except Exception as e:
        return {
            'success': False,
            'error': str(e),
            'symbol': symbol,
            'name': stock_name
        }

def find_best_strategies(all_results: List[Dict], capital: float = 80000) -> Dict:
    """Analyze all results and find best strategies for each stock"""
    
    recommendations = {}
    
    for stock_data in all_results:
        if not stock_data['success']:
            continue
            
        symbol = stock_data['symbol']
        name = stock_data['name']
        results = stock_data['results']
        
        # Rank strategies by profitability and other metrics
        strategy_rankings = []
        
        for strategy_name, metrics in results.items():
            total_return = metrics.get('totalReturnPercentage', 0)
            sharpe_ratio = metrics.get('sharpeRatio', 0)
            win_rate = metrics.get('winRate', 0)
            total_trades = metrics.get('totalTrades', 0)
            max_drawdown = metrics.get('maxDrawdownPercentage', 0)
            final_equity = metrics.get('finalEquity', capital)
            profit = final_equity - capital
            
            # Composite score (weighted)
            score = (
                (total_return * 0.4) +  # 40% weight on returns
                (sharpe_ratio * 20 * 0.2) +  # 20% weight on Sharpe
                (win_rate * 0.2) +  # 20% weight on win rate
                (-max_drawdown * 0.1) +  # 10% weight on low drawdown
                ((total_trades / 10) * 0.1)  # 10% weight on activity
            )
            
            strategy_rankings.append({
                'name': strategy_name,
                'score': score,
                'return_pct': total_return,
                'profit': profit,
                'sharpe': sharpe_ratio,
                'win_rate': win_rate,
                'trades': total_trades,
                'max_dd': max_drawdown,
                'final_equity': final_equity
            })
        
        # Sort by score
        strategy_rankings.sort(key=lambda x: x['score'], reverse=True)
        
        recommendations[symbol] = {
            'name': name,
            'top_strategies': strategy_rankings[:5],  # Top 5
            'all_strategies': strategy_rankings
        }
    
    return recommendations

def generate_markdown_report(all_results: List[Dict], recommendations: Dict) -> str:
    """Generate comprehensive markdown report"""
    
    report = []
    report.append("# Backtest Results for 18 Major Taiwan Stocks")
    report.append(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append(f"**Capital:** {CAPITAL:,} TWD")
    report.append(f"**Period:** Last 90 days")
    report.append("")
    
    # Executive Summary
    report.append("## 📊 Executive Summary")
    report.append("")
    
    # Overall best strategies across all stocks
    all_strategy_performances = {}
    for stock_sym, data in recommendations.items():
        for strat in data['all_strategies']:
            strat_name = strat['name']
            if strat_name not in all_strategy_performances:
                all_strategy_performances[strat_name] = []
            all_strategy_performances[strat_name].append(strat['return_pct'])
    
    # Calculate average performance per strategy
    strategy_avg = {
        name: sum(returns) / len(returns) 
        for name, returns in all_strategy_performances.items()
    }
    
    top_overall = sorted(strategy_avg.items(), key=lambda x: x[1], reverse=True)[:10]
    
    report.append("### 🏆 Top 10 Strategies Overall (by average return)")
    report.append("")
    report.append("| Rank | Strategy | Avg Return % |")
    report.append("|------|----------|--------------|")
    for idx, (name, avg_ret) in enumerate(top_overall, 1):
        report.append(f"| {idx} | {name} | {avg_ret:.2f}% |")
    report.append("")
    
    # Stock-specific recommendations
    report.append("## 💼 Stock-Specific Strategy Recommendations")
    report.append("")
    
    for symbol, data in sorted(recommendations.items()):
        stock_name = data['name']
        top_strats = data['top_strategies']
        
        if not top_strats:
            continue
        
        best = top_strats[0]
        
        report.append(f"### {stock_name} ({symbol})")
        report.append("")
        report.append(f"**🥇 Best Strategy:** {best['name']}")
        report.append(f"- **Return:** {best['return_pct']:.2f}% ({best['profit']:+,.0f} TWD)")
        report.append(f"- **Sharpe Ratio:** {best['sharpe']:.3f}")
        report.append(f"- **Win Rate:** {best['win_rate']:.1f}%")
        report.append(f"- **Total Trades:** {best['trades']}")
        report.append(f"- **Max Drawdown:** {best['max_dd']:.2f}%")
        report.append(f"- **Final Equity:** {best['final_equity']:,.0f} TWD")
        report.append("")
        
        # Show top 5 strategies
        if len(top_strats) > 1:
            report.append("**Top 5 Strategies:**")
            report.append("")
            report.append("| Rank | Strategy | Return % | Profit (TWD) | Sharpe | Win Rate |")
            report.append("|------|----------|----------|--------------|--------|----------|")
            for idx, strat in enumerate(top_strats[:5], 1):
                report.append(
                    f"| {idx} | {strat['name']} | {strat['return_pct']:.2f}% | "
                    f"{strat['profit']:+,.0f} | {strat['sharpe']:.3f} | {strat['win_rate']:.1f}% |"
                )
            report.append("")
    
    # Overall recommendations for 80k capital
    report.append("## 🎯 Portfolio Recommendations (80k TWD Capital)")
    report.append("")
    
    # Find stocks with best expected returns
    stock_expected_returns = []
    for symbol, data in recommendations.items():
        if data['top_strategies']:
            best = data['top_strategies'][0]
            stock_expected_returns.append({
                'symbol': symbol,
                'name': data['name'],
                'strategy': best['name'],
                'expected_return': best['return_pct'],
                'profit': best['profit'],
                'sharpe': best['sharpe'],
                'win_rate': best['win_rate']
            })
    
    stock_expected_returns.sort(key=lambda x: x['expected_return'], reverse=True)
    
    report.append("### 🌟 Top 5 Stock + Strategy Combinations")
    report.append("")
    report.append("Based on backtested performance, here are the top recommendations:")
    report.append("")
    
    for idx, rec in enumerate(stock_expected_returns[:5], 1):
        report.append(f"**{idx}. {rec['name']} ({rec['symbol']})**")
        report.append(f"   - Strategy: **{rec['strategy']}**")
        report.append(f"   - Expected Return: **{rec['expected_return']:.2f}%** ({rec['profit']:+,.0f} TWD)")
        report.append(f"   - Sharpe Ratio: {rec['sharpe']:.3f}")
        report.append(f"   - Win Rate: {rec['win_rate']:.1f}%")
        report.append("")
    
    # Risk warning
    report.append("## ⚠️ Important Disclaimers")
    report.append("")
    report.append("1. **Past Performance ≠ Future Results**: Backtested results are based on historical data and do not guarantee future performance.")
    report.append("2. **Market Conditions Change**: The strategies that performed well in the past may not work in different market conditions.")
    report.append("3. **Simulation Limitations**: Backtests do not account for slippage, commissions, or market impact.")
    report.append("4. **Diversification**: Consider diversifying across multiple stocks and strategies to manage risk.")
    report.append("5. **Risk Management**: Always use stop-losses and position sizing appropriate for your risk tolerance.")
    report.append("")
    
    # Failed stocks
    failed_stocks = [r for r in all_results if not r['success']]
    if failed_stocks:
        report.append("## ❌ Failed Backtests")
        report.append("")
        for stock in failed_stocks:
            report.append(f"- **{stock['name']} ({stock['symbol']})**: {stock['error']}")
        report.append("")
    
    report.append("---")
    report.append("*Generated by Automatic Equity Trader Backtest System*")
    
    return "\n".join(report)

def main():
    print("=" * 70)
    print("🚀 Running Comprehensive Backtest for 18 Major Taiwan Stocks")
    print("=" * 70)
    print()
    
    # Check if API is available
    try:
        requests.get(f"{API_BASE_URL}/strategies", timeout=5)
    except Exception as e:
        print(f"❌ Error: Cannot connect to backtest API at {API_BASE_URL}")
        print(f"   Make sure the Java application is running.")
        print(f"   Error: {e}")
        sys.exit(1)
    
    all_results = []
    
    print(f"\n📊 Running backtests for {len(STOCKS)} stocks...")
    print(f"   Capital: {CAPITAL:,} TWD")
    print(f"   Period: Last 90 days")
    print()
    
    for idx, (symbol, name) in enumerate(STOCKS, 1):
        print(f"[{idx}/{len(STOCKS)}] {name} ({symbol})")
        result = run_backtest_for_stock(symbol, name)
        all_results.append(result)
        
        if result['success']:
            num_strategies = len(result['results'])
            print(f"   ✅ Completed ({num_strategies} strategies tested)")
        else:
            print(f"   ❌ Failed: {result['error']}")
        
        # Small delay to avoid overwhelming the API
        time.sleep(0.5)
    
    print()
    print("📈 Analyzing results...")
    
    # Find best strategies
    recommendations = find_best_strategies(all_results, CAPITAL)
    
    # Generate report
    report = generate_markdown_report(all_results, recommendations)
    
    # Write to file
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)
    
    print()
    print("=" * 70)
    print("✅ Backtest Complete!")
    print("=" * 70)
    print(f"📄 Report saved to: {OUTPUT_FILE}")
    print()
    
    # Show quick summary
    successful = len([r for r in all_results if r['success']])
    failed = len([r for r in all_results if not r['success']])
    
    print(f"✅ Successful: {successful}/{len(STOCKS)}")
    if failed > 0:
        print(f"❌ Failed: {failed}/{len(STOCKS)}")
    
    print()
    print("🎯 Top 3 Recommendations (Stock + Strategy):")
    stock_expected_returns = []
    for symbol, data in recommendations.items():
        if data['top_strategies']:
            best = data['top_strategies'][0]
            stock_expected_returns.append({
                'symbol': symbol,
                'name': data['name'],
                'strategy': best['name'],
                'return': best['return_pct'],
                'profit': best['profit']
            })
    
    stock_expected_returns.sort(key=lambda x: x['return'], reverse=True)
    
    for idx, rec in enumerate(stock_expected_returns[:3], 1):
        print(f"   {idx}. {rec['name']} ({rec['symbol']}) + {rec['strategy']}")
        print(f"      Expected Return: {rec['return']:.2f}% ({rec['profit']:+,.0f} TWD)")
    
    print()

if __name__ == "__main__":
    main()
