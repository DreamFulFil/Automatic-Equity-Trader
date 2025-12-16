#!/usr/bin/env python3
"""
Daily Performance Report Generator

Generates a comprehensive daily performance summary with:
- Main strategy performance vs shadow strategies
- Best/worst performers of the day
- Risk metrics comparison
- Actionable recommendations

Run daily at market close (14:30) or before next trading day starts.
"""

import os
import sys
import psycopg2
from datetime import datetime, timedelta
from typing import Dict, List, Tuple, Optional

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


def get_main_strategy_performance(conn, lookback_hours=24) -> Optional[Dict]:
    """Get main strategy performance for the past day"""
    cursor = conn.cursor()
    
    query = """
        SELECT 
            strategy_name, symbol, total_return_pct, sharpe_ratio,
            max_drawdown_pct, win_rate_pct, total_trades, winning_trades,
            calculated_at, avg_win, avg_loss
        FROM strategy_performance
        WHERE performance_mode = 'MAIN'
          AND calculated_at > NOW() - INTERVAL '%s hours'
        ORDER BY calculated_at DESC
        LIMIT 1;
    """
    
    cursor.execute(query, (lookback_hours,))
    row = cursor.fetchone()
    
    if not row:
        return None
    
    return {
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
    }


def get_shadow_performances(conn, lookback_hours=24) -> List[Dict]:
    """Get all shadow strategy performances for the past day"""
    cursor = conn.cursor()
    
    query = """
        SELECT 
            strategy_name, symbol, total_return_pct, sharpe_ratio,
            max_drawdown_pct, win_rate_pct, total_trades, winning_trades,
            calculated_at, avg_win, avg_loss
        FROM strategy_performance
        WHERE performance_mode = 'SHADOW'
          AND calculated_at > NOW() - INTERVAL '%s hours'
        ORDER BY sharpe_ratio DESC NULLS LAST;
    """
    
    cursor.execute(query, (lookback_hours,))
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


def get_current_active_stock(conn) -> str:
    """Get currently active trading stock"""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT config_value FROM system_config 
        WHERE config_key = 'CURRENT_ACTIVE_STOCK';
    """)
    row = cursor.fetchone()
    return row[0] if row else "2454.TW"


def calculate_recommendation_score(perf: Dict) -> float:
    """
    Calculate recommendation score based on multiple factors
    Higher is better
    """
    sharpe_weight = 0.35
    return_weight = 0.25
    winrate_weight = 0.20
    drawdown_weight = 0.20  # Inverted (lower is better)
    
    sharpe_score = perf['sharpe_ratio'] * sharpe_weight
    return_score = (perf['total_return_pct'] / 10.0) * return_weight
    winrate_score = (perf['win_rate_pct'] / 100.0) * winrate_weight
    drawdown_score = (1.0 - abs(perf['max_drawdown_pct']) / 20.0) * drawdown_weight
    
    return sharpe_score + return_score + winrate_score + max(0, drawdown_score)


def generate_recommendations(main_perf: Dict, shadow_perfs: List[Dict], current_stock: str) -> List[str]:
    """Generate actionable recommendations"""
    recommendations = []
    
    if not shadow_perfs:
        recommendations.append("⚠️ No shadow mode data available - enable shadow trading for better insights")
        return recommendations
    
    # Find best shadow performer
    best_shadow = max(shadow_perfs, key=lambda x: calculate_recommendation_score(x))
    
    # Compare main vs best shadow
    if main_perf:
        main_score = calculate_recommendation_score(main_perf)
        shadow_score = calculate_recommendation_score(best_shadow)
        
        sharpe_diff = best_shadow['sharpe_ratio'] - main_perf['sharpe_ratio']
        return_diff = best_shadow['total_return_pct'] - main_perf['total_return_pct']
        
        # Stock change recommendation
        if best_shadow['symbol'] != current_stock and sharpe_diff > 0.5:
            recommendations.append(
                f"🔄 STOCK CHANGE RECOMMENDED\n"
                f"   Current: {current_stock} (Sharpe: {main_perf['sharpe_ratio']:.2f})\n"
                f"   Suggested: {best_shadow['symbol']} (Sharpe: {best_shadow['sharpe_ratio']:.2f})\n"
                f"   Command: /change-stock {best_shadow['symbol']}"
            )
        
        # Strategy change recommendation
        if best_shadow['strategy_name'] != main_perf['strategy_name'] and sharpe_diff > 0.3:
            recommendations.append(
                f"📊 STRATEGY CHANGE RECOMMENDED\n"
                f"   Current: {main_perf['strategy_name']} (Sharpe: {main_perf['sharpe_ratio']:.2f})\n"
                f"   Suggested: {best_shadow['strategy_name']} (Sharpe: {best_shadow['sharpe_ratio']:.2f})\n"
                f"   Command: /set-main-strategy {best_shadow['strategy_name']}"
            )
        
        # Risk warnings
        if main_perf['max_drawdown_pct'] < -15.0:
            recommendations.append(
                f"⚠️ HIGH DRAWDOWN ALERT\n"
                f"   Main strategy MDD: {main_perf['max_drawdown_pct']:.2f}%\n"
                f"   Consider switching to lower-risk strategy"
            )
        
        # Performance alerts
        if main_perf['win_rate_pct'] < 45.0 and main_perf['total_trades'] > 10:
            recommendations.append(
                f"⚠️ LOW WIN RATE\n"
                f"   Current: {main_perf['win_rate_pct']:.1f}%\n"
                f"   Review strategy parameters or switch strategies"
            )
        
    else:
        recommendations.append("ℹ️ No main strategy performance data available")
    
    # General advice
    if not recommendations:
        recommendations.append("✅ Current configuration performing well - no changes needed")
    
    return recommendations


def format_performance_table(perf: Dict, label: str) -> str:
    """Format a single performance record as a table"""
    return f"""
{label}
{'=' * 60}
Strategy:        {perf['strategy_name']}
Symbol:          {perf['symbol']}
Total Return:    {perf['total_return_pct']:>8.2f}%
Sharpe Ratio:    {perf['sharpe_ratio']:>8.2f}
Max Drawdown:    {perf['max_drawdown_pct']:>8.2f}%
Win Rate:        {perf['win_rate_pct']:>8.1f}%
Total Trades:    {perf['total_trades']:>8d}
Winning Trades:  {perf['winning_trades']:>8d}
Avg Win:         {perf['avg_win']:>8.2f}
Avg Loss:        {perf['avg_loss']:>8.2f}
Last Updated:    {perf['calculated_at'].strftime('%Y-%m-%d %H:%M:%S')}
"""


def main():
    """Generate and print daily performance report"""
    print("\n" + "=" * 70)
    print("📊 DAILY PERFORMANCE REPORT")
    print("=" * 70)
    print(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    
    conn = connect_db()
    
    try:
        # Get current active stock
        current_stock = get_current_active_stock(conn)
        print(f"\n🎯 Current Active Stock: {current_stock}\n")
        
        # Get main strategy performance
        main_perf = get_main_strategy_performance(conn, lookback_hours=24)
        
        if main_perf:
            print(format_performance_table(main_perf, "📈 MAIN STRATEGY PERFORMANCE (Last 24h)"))
        else:
            print("\n⚠️ No main strategy performance data available for the past 24 hours\n")
        
        # Get shadow performances
        shadow_perfs = get_shadow_performances(conn, lookback_hours=24)
        
        if shadow_perfs:
            print("\n" + "=" * 70)
            print("👥 TOP 5 SHADOW STRATEGIES (Last 24h)")
            print("=" * 70)
            
            for i, perf in enumerate(shadow_perfs[:5], 1):
                print(f"\n#{i} - {perf['strategy_name']} ({perf['symbol']})")
                print(f"    Sharpe: {perf['sharpe_ratio']:>6.2f} | "
                      f"Return: {perf['total_return_pct']:>6.2f}% | "
                      f"Win Rate: {perf['win_rate_pct']:>5.1f}% | "
                      f"MDD: {perf['max_drawdown_pct']:>6.2f}%")
        else:
            print("\n⚠️ No shadow strategy performance data available\n")
        
        # Generate recommendations
        print("\n" + "=" * 70)
        print("💡 RECOMMENDATIONS")
        print("=" * 70)
        
        recommendations = generate_recommendations(main_perf, shadow_perfs, current_stock)
        for rec in recommendations:
            print(f"\n{rec}")
        
        print("\n" + "=" * 70)
        print("📝 SUMMARY")
        print("=" * 70)
        print(f"Main strategy records:   {1 if main_perf else 0}")
        print(f"Shadow strategy records: {len(shadow_perfs)}")
        print(f"Recommendations:         {len(recommendations)}")
        print("=" * 70 + "\n")
        
    finally:
        conn.close()


if __name__ == "__main__":
    main()
