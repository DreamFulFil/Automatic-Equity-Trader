#!/usr/bin/env python3
"""
Automation Watchdog - Monitors system health and sends alerts

Runs continuously to ensure:
- All services are running
- Trading is happening as expected
- Risks are within acceptable limits
- User gets timely notifications

This is your "set it and forget it" monitor.
"""

import os
import sys
import time
import requests
import psycopg2
from datetime import datetime, timedelta
from typing import Dict, Optional

# Configuration
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID")
CHECK_INTERVAL_MINUTES = 15

# Service URLs
JAVA_SERVICE = "http://localhost:16350"
PYTHON_BRIDGE = "http://localhost:8888"

# Database credentials
DB_HOST = os.getenv("POSTGRES_HOST", "localhost")
DB_NAME = os.getenv("POSTGRES_DB", "auto_equity_trader")
DB_USER = os.getenv("POSTGRES_USER", "dreamer")
DB_PASS = os.getenv("POSTGRES_PASSWORD", "WSYS1r0PE0Ig0iuNX2aNi5k7")


def send_telegram_alert(message: str):
    """Send alert to Telegram"""
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        print(f"⚠️ Telegram not configured: {message}")
        return
    
    try:
        url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
        payload = {
            "chat_id": TELEGRAM_CHAT_ID,
            "text": f"🤖 Watchdog Alert\n\n{message}",
            "parse_mode": "Markdown"
        }
        requests.post(url, json=payload, timeout=10)
    except Exception as e:
        print(f"❌ Failed to send Telegram alert: {e}")


def check_service_health(url: str, name: str) -> bool:
    """Check if a service is responsive"""
    try:
        response = requests.get(f"{url}/health", timeout=5)
        if response.status_code == 200:
            return True
    except:
        pass
    
    send_telegram_alert(f"🚨 *{name} is DOWN*\n\nPlease check the service immediately.")
    return False


def check_database_connection() -> bool:
    """Verify database is accessible"""
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASS,
            connect_timeout=5
        )
        conn.close()
        return True
    except Exception as e:
        send_telegram_alert(f"🚨 *Database Connection Lost*\n\n{str(e)}")
        return False


def check_recent_trading_activity(conn) -> Dict:
    """Check if trading is happening as expected"""
    cursor = conn.cursor()
    
    # Check for recent trades (within last 2 hours during market hours)
    now = datetime.now()
    if 9 <= now.hour < 14:  # Market hours
        cursor.execute("""
            SELECT COUNT(*) 
            FROM trade 
            WHERE created_at > NOW() - INTERVAL '2 hours';
        """)
        recent_trades = cursor.fetchone()[0]
        
        if recent_trades == 0:
            return {
                "status": "WARNING",
                "message": "No trades in last 2 hours during market hours"
            }
    
    # Check today's performance
    cursor.execute("""
        SELECT 
            strategy_name,
            total_return_pct,
            total_trades,
            win_rate_pct,
            max_drawdown_pct
        FROM strategy_performance
        WHERE performance_mode = 'MAIN'
          AND DATE(calculated_at) = CURRENT_DATE
        ORDER BY calculated_at DESC
        LIMIT 1;
    """)
    
    row = cursor.fetchone()
    if not row:
        return {
            "status": "INFO",
            "message": "No performance data yet today"
        }
    
    strategy, return_pct, trades, win_rate, drawdown = row
    
    # Check for concerning metrics
    concerns = []
    if return_pct and return_pct < -3:
        concerns.append(f"Daily loss: {return_pct:.2f}%")
    if drawdown and abs(drawdown) > 12:
        concerns.append(f"High drawdown: {abs(drawdown):.2f}%")
    if win_rate and win_rate < 40:
        concerns.append(f"Low win rate: {win_rate:.1f}%")
    
    if concerns:
        return {
            "status": "ALERT",
            "message": f"*Performance Concerns*\n" + "\n".join(f"• {c}" for c in concerns),
            "strategy": strategy
        }
    
    return {
        "status": "OK",
        "strategy": strategy,
        "return_pct": return_pct,
        "trades": trades
    }


def check_position_risk(conn) -> Optional[str]:
    """Check if positions are within risk limits"""
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT 
            symbol,
            quantity,
            entry_price,
            current_price,
            unrealized_pnl
        FROM position
        WHERE status = 'OPEN'
          AND quantity > 0;
    """)
    
    positions = cursor.fetchall()
    
    # Get current equity
    cursor.execute("""
        SELECT value 
        FROM system_settings 
        WHERE key = 'current_equity';
    """)
    equity_row = cursor.fetchone()
    equity = float(equity_row[0]) if equity_row else 80000.0
    
    warnings = []
    for symbol, qty, entry, current, pnl in positions:
        position_value = qty * (current or entry)
        position_pct = (position_value / equity) * 100
        
        if position_pct > 15:
            warnings.append(f"• {symbol}: {position_pct:.1f}% of equity (>15% risk!)")
        
        if pnl and pnl < 0:
            loss_pct = (pnl / (qty * entry)) * 100
            if loss_pct < -5:
                warnings.append(f"• {symbol}: {loss_pct:.1f}% loss on position")
    
    if warnings:
        return "*Position Risk Warnings*\n" + "\n".join(warnings)
    
    return None


def get_ai_daily_summary(conn) -> Optional[str]:
    """Get latest AI insights if available"""
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT content, risk_level
        FROM ai_insights
        WHERE DATE(created_at) = CURRENT_DATE
        ORDER BY created_at DESC
        LIMIT 1;
    """)
    
    row = cursor.fetchone()
    if row:
        content, risk_level = row
        return f"📊 *Today's AI Summary*\nRisk: {risk_level}\n\n{content[:200]}..."
    
    return None


def run_watchdog_check():
    """Run complete system check"""
    print(f"\n{'='*60}")
    print(f"🔍 Watchdog Check - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}\n")
    
    # 1. Check services
    java_ok = check_service_health(JAVA_SERVICE, "Java Trading Service")
    python_ok = check_service_health(PYTHON_BRIDGE, "Python Bridge")
    
    if not (java_ok and python_ok):
        print("❌ Critical services are down!")
        return
    
    print("✅ All services running")
    
    # 2. Check database
    if not check_database_connection():
        print("❌ Database connection failed!")
        return
    
    print("✅ Database connected")
    
    # 3. Detailed checks
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASS
        )
        
        # Check trading activity
        activity = check_recent_trading_activity(conn)
        print(f"📊 Trading: {activity['status']}")
        
        if activity['status'] in ['WARNING', 'ALERT']:
            send_telegram_alert(activity['message'])
        
        # Check position risk
        risk_warning = check_position_risk(conn)
        if risk_warning:
            print("⚠️ Position risk warnings detected")
            send_telegram_alert(risk_warning)
        else:
            print("✅ Positions within risk limits")
        
        # Get AI summary if available
        ai_summary = get_ai_daily_summary(conn)
        if ai_summary and datetime.now().hour == 15:  # Send once at 3 PM
            send_telegram_alert(ai_summary)
        
        conn.close()
        
    except Exception as e:
        print(f"❌ Check failed: {e}")
        send_telegram_alert(f"🚨 Watchdog check failed:\n\n{str(e)}")


def main():
    """Main watchdog loop"""
    print("🐕 Automation Watchdog Starting...")
    print(f"Check interval: {CHECK_INTERVAL_MINUTES} minutes")
    print("Press Ctrl+C to stop\n")
    
    if not TELEGRAM_BOT_TOKEN:
        print("⚠️ TELEGRAM_BOT_TOKEN not set - alerts will not be sent")
    
    while True:
        try:
            run_watchdog_check()
            print(f"\n😴 Sleeping for {CHECK_INTERVAL_MINUTES} minutes...\n")
            time.sleep(CHECK_INTERVAL_MINUTES * 60)
            
        except KeyboardInterrupt:
            print("\n\n🛑 Watchdog stopped by user")
            break
        except Exception as e:
            print(f"\n❌ Unexpected error: {e}")
            time.sleep(60)  # Sleep 1 minute on error


if __name__ == "__main__":
    main()
