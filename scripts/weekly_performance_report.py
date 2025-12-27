#!/usr/bin/env python3
"""
Weekly Performance Report Generator

Generates a comprehensive weekly performance analysis with:
- 7-day performance trends
- Stock/strategy stability metrics
- Consistency analysis
- Strategic recommendations for the week ahead

Run weekly on Monday morning before market opens.
"""

import os
import sys
import psycopg2
from datetime import datetime, timedelta
from typing import Dict, List, Optional
from collections import defaultdict

# Database credentials from environment or defaults
DB_HOST = os.getenv("POSTGRES_HOST", "localhost")
DB_PORT = os.getenv("POSTGRES_PORT", "5432")
DB_NAME = os.getenv("POSTGRES_DB", "auto_equity_trader")
DB_USER = os.getenv("POSTGRES_USER", "dreamer")
DB_PASS = os.getenv("POSTGRES_PASSWORD", "WSYS1r0PE0Ig0iuNX2aNi5k7")


def connect_db():
    """Connect to PostgreSQL database"""
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASS
        )
        return conn
    except Exception as e:
        print(f"❌ Database connection failed: {e}")
        sys.exit(1)


def get_weekly_performances(conn, mode: str, days: int = 7) -> List[Dict]:
    """Get strategy performances for the past N days"""
    cursor = conn.cursor()
    
    query = """
        SELECT 
            strategy_name, symbol, total_return_pct, sharpe_ratio,
            max_drawdown_pct, win_rate_pct, total_trades, winning_trades,
            calculated_at, avg_win, avg_loss
        FROM strategy_performance
        WHERE performance_mode = %s
          AND calculated_at > NOW() - INTERVAL '%s days'
        ORDER BY calculated_at DESC;
    """
    
    cursor.execute(query, (mode, days))
    rows = cursor.fetchall()
    
    results = []
    for row in rows:
        results.append({
            'strategy_name': row[0],
            'symbol': row[1],
            'total_return_pct': row[2] or 0.0,
            'sharpe_ratio': row[3] or 0.0,
            'max_drawdown_pct': row[4] or 0.0,
            'win_rate_pct': row[5] or 0.0,
            'total_trades': row[6] or 0,
            'winning_trades': row[7] or 0,
            'calculated_at': row[8],
            'avg_win': row[9] or 0.0,
            'avg_loss': row[10] or 0.0
        })
    
    return results


def aggregate_by_strategy_symbol(perfs: List[Dict]) -> Dict[tuple, List[Dict]]:
    """Group performances by (strategy_name, symbol)"""
    grouped = defaultdict(list)
    for perf in perfs:
        key = (perf['strategy_name'], perf['symbol'])
        grouped[key].append(perf)
    return dict(grouped)


def calculate_consistency_score(perfs: List[Dict]) -> float:
    """
    Calculate consistency score (0-1)
    Based on variance of daily returns and win rates
    """
    if len(perfs) < 2:
        return 0.0
    
    returns = [p['total_return_pct'] for p in perfs]
    win_rates = [p['win_rate_pct'] for p in perfs]
    
    # Lower variance = higher consistency
    return_var = sum((r - sum(returns)/len(returns))**2 for r in returns) / len(returns)
    winrate_var = sum((w - sum(win_rates)/len(win_rates))**2 for w in win_rates) / len(win_rates)
    
    # Normalize to 0-1 (lower variance = higher score)
    consistency = 1.0 / (1.0 + (return_var + winrate_var) / 100.0)
    return consistency


def calculate_weekly_aggregates(perfs: List[Dict]) -> Dict:
    """Calculate aggregate metrics from multiple performance records"""
    if not perfs:
        return None
    
    avg_return = sum(p['total_return_pct'] for p in perfs) / len(perfs)
    avg_sharpe = sum(p['sharpe_ratio'] for p in perfs) / len(perfs)
    worst_drawdown = min(p['max_drawdown_pct'] for p in perfs)
    avg_winrate = sum(p['win_rate_pct'] for p in perfs) / len(perfs)
    total_trades = sum(p['total_trades'] for p in perfs)
    total_wins = sum(p['winning_trades'] for p in perfs)
    
    return {
        'strategy_name': perfs[0]['strategy_name'],
        'symbol': perfs[0]['symbol'],
        'avg_return_pct': avg_return,
        'avg_sharpe_ratio': avg_sharpe,
        'worst_drawdown_pct': worst_drawdown,
        'avg_win_rate_pct': avg_winrate,
        'total_trades': total_trades,
        'total_wins': total_wins,
        'consistency_score': calculate_consistency_score(perfs),
        'data_points': len(perfs)
    }


def calculate_trend(perfs: List[Dict]) -> str:
    """Determine if performance is improving, declining, or stable"""
    if len(perfs) < 3:
        return "INSUFFICIENT_DATA"
    
    # Sort by date (oldest first)
    sorted_perfs = sorted(perfs, key=lambda x: x['calculated_at'])
    
    # Split into first half and second half
    mid = len(sorted_perfs) // 2
    first_half = sorted_perfs[:mid]
    second_half = sorted_perfs[mid:]
    
    avg_first = sum(p['sharpe_ratio'] for p in first_half) / len(first_half)
    avg_second = sum(p['sharpe_ratio'] for p in second_half) / len(second_half)
    
    diff = avg_second - avg_first
    
    if diff > 0.2:
        return "IMPROVING ⬆️"
    elif diff < -0.2:
        return "DECLINING ⬇️"
    else:
        return "STABLE ➡️"


def generate_weekly_recommendations(main_agg: Optional[Dict], shadow_aggs: List[Dict]) -> List[str]:
    """Generate strategic recommendations for the week"""
    recommendations = []
    
    if not shadow_aggs:
        recommendations.append("⚠️ No shadow mode data - enable shadow trading for comparative analysis")
        return recommendations
    
    # Find most consistent high performer
    scored_shadows = [
        (agg, agg['avg_sharpe_ratio'] * agg['consistency_score'])
        for agg in shadow_aggs
        if agg['data_points'] >= 3  # At least 3 days of data
    ]
    
    if not scored_shadows:
        recommendations.append("⚠️ Insufficient shadow data for reliable recommendations")
        return recommendations
    
    best_shadow = max(scored_shadows, key=lambda x: x[1])[0]
    
    if main_agg:
        # Stock change recommendation
        if best_shadow['symbol'] != main_agg['symbol']:
            sharpe_improvement = best_shadow['avg_sharpe_ratio'] - main_agg['avg_sharpe_ratio']
            consistency_improvement = best_shadow['consistency_score'] - main_agg['consistency_score']
            
            if sharpe_improvement > 0.3 and best_shadow['consistency_score'] > 0.6:
                recommendations.append(
                    f"🎯 STOCK CHANGE RECOMMENDED FOR WEEK\n"
                    f"   Current: {main_agg['symbol']} "
                    f"(Sharpe: {main_agg['avg_sharpe_ratio']:.2f}, "
                    f"Consistency: {main_agg['consistency_score']:.2f})\n"
                    f"   Suggested: {best_shadow['symbol']} "
                    f"(Sharpe: {best_shadow['avg_sharpe_ratio']:.2f}, "
                    f"Consistency: {best_shadow['consistency_score']:.2f})\n"
                    f"   Expected improvement: {sharpe_improvement:.2f} Sharpe points\n"
                    f"   Command: /change-stock {best_shadow['symbol']}"
                )
        
        # Strategy change recommendation
        if best_shadow['strategy_name'] != main_agg['strategy_name']:
            if best_shadow['avg_sharpe_ratio'] > main_agg['avg_sharpe_ratio'] + 0.2:
                recommendations.append(
                    f"📊 STRATEGY CHANGE RECOMMENDED\n"
                    f"   Current: {main_agg['strategy_name']}\n"
                    f"   Suggested: {best_shadow['strategy_name']}\n"
                    f"   7-day avg Sharpe improvement: {best_shadow['avg_sharpe_ratio'] - main_agg['avg_sharpe_ratio']:.2f}\n"
                    f"   Command: /set-main-strategy {best_shadow['strategy_name']}"
                )
        
        # Consistency warnings
        if main_agg['consistency_score'] < 0.4:
            recommendations.append(
                f"⚠️ LOW CONSISTENCY DETECTED\n"
                f"   Current strategy showing high variance\n"
                f"   Consistency score: {main_agg['consistency_score']:.2f}\n"
                f"   Consider switching to more stable strategy"
            )
        
        # Risk warnings
        if main_agg['worst_drawdown_pct'] < -12.0:
            recommendations.append(
                f"⚠️ SIGNIFICANT DRAWDOWN THIS WEEK\n"
                f"   Worst MDD: {main_agg['worst_drawdown_pct']:.2f}%\n"
                f"   Review risk parameters or consider strategy change"
            )
    
    # General strategy advice
    top_3_consistent = sorted(
        [agg for agg in shadow_aggs if agg['data_points'] >= 3],
        key=lambda x: x['consistency_score'],
        reverse=True
    )[:3]
    
    if top_3_consistent:
        recommendations.append(
            f"📌 MOST CONSISTENT STRATEGIES THIS WEEK:\n" +
            "\n".join([
                f"   {i+1}. {agg['strategy_name']} ({agg['symbol']}) - "
                f"Consistency: {agg['consistency_score']:.2f}, Sharpe: {agg['avg_sharpe_ratio']:.2f}"
                for i, agg in enumerate(top_3_consistent)
            ])
        )
    
    return recommendations


def main():
    """Generate and print weekly performance report"""
    print("\n" + "=" * 70)
    print("📅 WEEKLY PERFORMANCE REPORT")
    print("=" * 70)
    print(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Period: Last 7 days")
    print("=" * 70)
    
    conn = connect_db()
    
    try:
        # Get weekly performances
        main_perfs = get_weekly_performances(conn, 'MAIN', days=7)
        shadow_perfs = get_weekly_performances(conn, 'SHADOW', days=7)
        
        print(f"\n📊 Data Summary:")
        print(f"   Main performance records: {len(main_perfs)}")
        print(f"   Shadow performance records: {len(shadow_perfs)}")
        
        # Aggregate by strategy/symbol
        main_grouped = aggregate_by_strategy_symbol(main_perfs)
        shadow_grouped = aggregate_by_strategy_symbol(shadow_perfs)
        
        # Calculate weekly aggregates
        main_aggregates = {k: calculate_weekly_aggregates(v) for k, v in main_grouped.items()}
        shadow_aggregates = {k: calculate_weekly_aggregates(v) for k, v in shadow_grouped.items()}
        
        # Display main strategy performance
        if main_aggregates:
            print("\n" + "=" * 70)
            print("📈 MAIN STRATEGY - WEEKLY AGGREGATE")
            print("=" * 70)
            
            for (strategy, symbol), agg in main_aggregates.items():
                trend = calculate_trend(main_grouped[(strategy, symbol)])
                print(f"""
Strategy:         {strategy}
Symbol:           {symbol}
Avg Return:       {agg['avg_return_pct']:>8.2f}%
Avg Sharpe:       {agg['avg_sharpe_ratio']:>8.2f}
Worst Drawdown:   {agg['worst_drawdown_pct']:>8.2f}%
Avg Win Rate:     {agg['avg_win_rate_pct']:>8.1f}%
Total Trades:     {agg['total_trades']:>8d}
Consistency:      {agg['consistency_score']:>8.2f}
Trend:            {trend}
Data Points:      {agg['data_points']:>8d} days
""")
        else:
            print("\n⚠️ No main strategy data available for the past 7 days\n")
        
        # Display top shadow strategies
        if shadow_aggregates:
            print("\n" + "=" * 70)
            print("👥 TOP 10 SHADOW STRATEGIES - WEEKLY AGGREGATE")
            print("=" * 70)
            
            # Sort by combined score (sharpe * consistency)
            ranked_shadows = sorted(
                shadow_aggregates.values(),
                key=lambda x: x['avg_sharpe_ratio'] * x['consistency_score'],
                reverse=True
            )[:10]
            
            for i, agg in enumerate(ranked_shadows, 1):
                key = (agg['strategy_name'], agg['symbol'])
                trend = calculate_trend(shadow_grouped[key])
                print(f"""
#{i} - {agg['strategy_name']} ({agg['symbol']})
     Avg Sharpe: {agg['avg_sharpe_ratio']:>6.2f} | 
     Avg Return: {agg['avg_return_pct']:>6.2f}% | 
     Consistency: {agg['consistency_score']:>5.2f} | 
     Trend: {trend}
     Win Rate: {agg['avg_win_rate_pct']:>5.1f}% | 
     MDD: {agg['worst_drawdown_pct']:>6.2f}% | 
     Trades: {agg['total_trades']:>4d}
""")
        else:
            print("\n⚠️ No shadow strategy data available\n")
        
        # Generate recommendations
        print("\n" + "=" * 70)
        print("💡 WEEKLY RECOMMENDATIONS")
        print("=" * 70)
        
        main_agg = list(main_aggregates.values())[0] if main_aggregates else None
        shadow_agg_list = list(shadow_aggregates.values())
        
        recommendations = generate_weekly_recommendations(main_agg, shadow_agg_list)
        
        for rec in recommendations:
            print(f"\n{rec}")
        
        print("\n" + "=" * 70)
        print("📝 WEEKLY SUMMARY")
        print("=" * 70)
        print(f"Main strategies analyzed:   {len(main_aggregates)}")
        print(f"Shadow strategies analyzed: {len(shadow_aggregates)}")
        print(f"Recommendations generated:  {len(recommendations)}")
        print("=" * 70 + "\n")
        
    finally:
        conn.close()


if __name__ == "__main__":
    main()
