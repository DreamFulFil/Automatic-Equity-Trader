#!/usr/bin/env python3
"""
AI-Powered Daily Insights Generator

Fetches daily performance data and generates beginner-friendly insights
using the AI insights service. Runs after market close to summarize the day.
"""

import os
import sys
import requests
import psycopg2
from datetime import datetime, timedelta
from typing import Dict, Optional

# Database credentials
DB_HOST = os.getenv("POSTGRES_HOST", "localhost")
DB_PORT = os.getenv("POSTGRES_PORT", "5432")
DB_NAME = os.getenv("POSTGRES_DB", "auto_equity_trader")
DB_USER = os.getenv("POSTGRES_USER", "dreamer")
DB_PASS = os.getenv("POSTGRES_PASSWORD", "WSYS1r0PE0Ig0iuNX2aNi5k7")

# AI service endpoint
AI_SERVICE_URL = "http://localhost:8888"


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


def get_daily_performance(conn) -> Optional[Dict]:
    """Get today's performance metrics"""
    cursor = conn.cursor()
    
    query = """
        SELECT 
            strategy_name, 
            symbol,
            total_return_pct, 
            sharpe_ratio,
            max_drawdown_pct, 
            win_rate_pct, 
            total_trades,
            winning_trades,
            avg_win,
            avg_loss,
            performance_mode
        FROM strategy_performance
        WHERE DATE(calculated_at) = CURRENT_DATE
        ORDER BY calculated_at DESC
        LIMIT 50;
    """
    
    cursor.execute(query)
    rows = cursor.fetchall()
    
    if not rows:
        return None
    
    performances = []
    main_strategy = None
    
    for row in rows:
        perf = {
            'strategy_name': row[0],
            'symbol': row[1],
            'total_return_pct': float(row[2]) if row[2] else 0.0,
            'sharpe_ratio': float(row[3]) if row[3] else 0.0,
            'max_drawdown_pct': float(row[4]) if row[4] else 0.0,
            'win_rate_pct': float(row[5]) if row[5] else 0.0,
            'total_trades': int(row[6]) if row[6] else 0,
            'winning_trades': int(row[7]) if row[7] else 0,
            'avg_win': float(row[8]) if row[8] else 0.0,
            'avg_loss': float(row[9]) if row[9] else 0.0,
            'mode': row[10]
        }
        
        performances.append(perf)
        
        if row[10] == 'MAIN':
            main_strategy = perf
    
    return {
        'main_strategy': main_strategy,
        'all_strategies': performances,
        'shadow_strategies': [p for p in performances if p['mode'] == 'SHADOW']
    }


def get_weekly_summary(conn) -> Dict:
    """Get this week's performance summary"""
    cursor = conn.cursor()
    
    query = """
        SELECT 
            AVG(total_return_pct) as avg_return,
            MIN(max_drawdown_pct) as worst_drawdown,
            AVG(win_rate_pct) as avg_win_rate,
            SUM(total_trades) as total_trades
        FROM strategy_performance
        WHERE calculated_at > NOW() - INTERVAL '7 days'
          AND performance_mode = 'MAIN';
    """
    
    cursor.execute(query)
    row = cursor.fetchone()
    
    if not row:
        return {}
    
    return {
        'weekly_return_pct': float(row[0]) if row[0] else 0.0,
        'worst_drawdown_pct': float(row[1]) if row[1] else 0.0,
        'avg_win_rate_pct': float(row[2]) if row[2] else 0.0,
        'total_trades': int(row[3]) if row[3] else 0
    }


def call_ai_insights(endpoint: str, data: dict) -> dict:
    """Call AI insights API"""
    try:
        response = requests.post(f"{AI_SERVICE_URL}{endpoint}", json=data, timeout=30)
        if response.status_code == 200:
            return response.json()
        else:
            print(f"⚠️ AI service returned {response.status_code}: {response.text}")
            return {"error": response.text}
    except Exception as e:
        print(f"❌ Failed to call AI service: {e}")
        return {"error": str(e)}


def save_insights_to_db(conn, insights: Dict):
    """Save AI insights to database for later retrieval"""
    cursor = conn.cursor()
    
    try:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS ai_insights (
                id SERIAL PRIMARY KEY,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                insight_type VARCHAR(50),
                content TEXT,
                risk_level VARCHAR(20),
                action_required BOOLEAN DEFAULT FALSE
            );
        """)
        
        cursor.execute("""
            INSERT INTO ai_insights (insight_type, content, risk_level, action_required)
            VALUES (%s, %s, %s, %s)
        """, (
            insights.get('type', 'daily_summary'),
            insights.get('content', ''),
            insights.get('risk_level', 'UNKNOWN'),
            insights.get('action_required', False)
        ))
        
        conn.commit()
        print("✅ Insights saved to database")
    except Exception as e:
        print(f"⚠️ Failed to save insights: {e}")
        conn.rollback()


def main():
    """Generate daily AI insights"""
    print("=" * 70)
    print("🤖 AI Daily Insights Generator")
    print("=" * 70)
    print(f"📅 Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()
    
    conn = connect_db()
    
    # 1. Get daily performance
    print("📊 Fetching today's performance...")
    daily_perf = get_daily_performance(conn)
    
    if not daily_perf or not daily_perf['main_strategy']:
        print("⚠️ No performance data available for today")
        return
    
    weekly_summary = get_weekly_summary(conn)
    
    # 2. Generate strategy analysis
    print("🔍 Analyzing strategy performance...")
    strategy_insights = call_ai_insights("/ai/analyze-strategies", {
        "performances": daily_perf['all_strategies']
    })
    
    print("\n" + "=" * 70)
    print("📈 STRATEGY ANALYSIS")
    print("=" * 70)
    if 'analysis' in strategy_insights:
        print(strategy_insights['analysis'])
        print(f"\n🏆 Best Strategy: {strategy_insights.get('best_strategy', 'N/A')}")
        print(f"⚠️  Risk Level: {strategy_insights.get('risk_level', 'UNKNOWN')}")
    
    # 3. Generate daily report insights
    print("\n📝 Generating daily summary...")
    report_data = {
        'main_strategy': daily_perf['main_strategy']['strategy_name'],
        'daily_return_pct': daily_perf['main_strategy']['total_return_pct'],
        'weekly_return_pct': weekly_summary.get('weekly_return_pct', 0),
        'total_trades': daily_perf['main_strategy']['total_trades'],
        'win_rate_pct': daily_perf['main_strategy']['win_rate_pct'],
        'max_drawdown_pct': daily_perf['main_strategy']['max_drawdown_pct']
    }
    
    daily_insights = call_ai_insights("/ai/daily-insights", {
        "report_data": report_data
    })
    
    print("\n" + "=" * 70)
    print("📋 DAILY SUMMARY")
    print("=" * 70)
    if 'summary' in daily_insights:
        print(daily_insights['summary'])
        print(f"\n😊 Sentiment: {daily_insights.get('sentiment', 'NEUTRAL')}")
        if daily_insights.get('action_needed'):
            print("⚠️  ACTION REQUIRED: Please review performance")
    
    # 4. Risk analysis
    print("\n🛡️  Analyzing risks...")
    risk_metrics = {
        'max_drawdown_pct': daily_perf['main_strategy']['max_drawdown_pct'],
        'sharpe_ratio': daily_perf['main_strategy']['sharpe_ratio'],
        'volatility': abs(daily_perf['main_strategy']['total_return_pct']) / max(daily_perf['main_strategy']['total_trades'], 1)
    }
    
    risk_insights = call_ai_insights("/ai/risk-analysis", {
        "metrics": risk_metrics
    })
    
    print("\n" + "=" * 70)
    print("🛡️  RISK ASSESSMENT")
    print("=" * 70)
    if 'advice' in risk_insights:
        print(risk_insights['advice'])
        print(f"\n⚠️  Risk Level: {risk_insights.get('risk_level', 'UNKNOWN')}")
        if risk_insights.get('warnings'):
            print("\n⚠️  Warnings:")
            for warning in risk_insights['warnings']:
                print(f"   • {warning}")
    
    # 5. Save insights to database
    combined_insights = {
        'type': 'daily_summary',
        'content': f"{daily_insights.get('summary', '')}\n\n{risk_insights.get('advice', '')}",
        'risk_level': risk_insights.get('risk_level', 'UNKNOWN'),
        'action_required': daily_insights.get('action_needed', False) or risk_insights.get('action_required', False)
    }
    
    save_insights_to_db(conn, combined_insights)
    
    print("\n" + "=" * 70)
    print("✅ Daily insights generation complete!")
    print("=" * 70)
    
    conn.close()


if __name__ == "__main__":
    main()
