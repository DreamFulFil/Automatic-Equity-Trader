#!/usr/bin/env python3
"""
Update strategy configurations with per-strategy position sizing parameters

This script initializes base_shares and share_increment for each strategy
based on their risk profile and target returns.
"""

import os
import sys
import psycopg2
from typing import Dict

# Database credentials
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


def get_strategy_risk_profile(strategy_name: str) -> Dict:
    """
    Determine risk profile and position sizing for each strategy type
    
    Conservative strategies: Lower base, slower scaling
    Aggressive strategies: Higher base, faster scaling
    """
    
    # Long-term conservative strategies
    if any(x in strategy_name.lower() for x in ['dca', 'dividend', 'rebalancing']):
        return {
            'base_shares': 50,
            'share_increment': 20,
            'max_risk_pct': 8.0,
            'target_monthly_return_pct': 3.0
        }
    
    # Mean reversion strategies (moderate risk)
    if any(x in strategy_name.lower() for x in ['rsi', 'bollinger', 'stochastic', 'williams']):
        return {
            'base_shares': 70,
            'share_increment': 27,
            'max_risk_pct': 10.0,
            'target_monthly_return_pct': 5.0
        }
    
    # Trend following strategies (moderate-aggressive)
    if any(x in strategy_name.lower() for x in ['macd', 'moving average', 'adx', 'supertrend', 'parabolic']):
        return {
            'base_shares': 60,
            'share_increment': 25,
            'max_risk_pct': 12.0,
            'target_monthly_return_pct': 6.0
        }
    
    # Volatility-based strategies (higher risk)
    if any(x in strategy_name.lower() for x in ['atr', 'volatility', 'deviation', 'envelope']):
        return {
            'base_shares': 55,
            'share_increment': 22,
            'max_risk_pct': 10.0,
            'target_monthly_return_pct': 5.5
        }
    
    # Momentum strategies (aggressive)
    if any(x in strategy_name.lower() for x in ['momentum', 'force', 'trix', 'price rate']):
        return {
            'base_shares': 65,
            'share_increment': 30,
            'max_risk_pct': 15.0,
            'target_monthly_return_pct': 7.0
        }
    
    # Intraday strategies (very selective)
    if any(x in strategy_name.lower() for x in ['vwap', 'twap']):
        return {
            'base_shares': 40,
            'share_increment': 15,
            'max_risk_pct': 5.0,
            'target_monthly_return_pct': 4.0
        }
    
    # Default (balanced approach)
    return {
        'base_shares': 70,
        'share_increment': 27,
        'max_risk_pct': 10.0,
        'target_monthly_return_pct': 5.0
    }


def update_strategy_configs(conn):
    """Update all strategy configs with position sizing parameters"""
    cursor = conn.cursor()
    
    # Add columns if they don't exist
    try:
        cursor.execute("""
            ALTER TABLE strategy_config 
            ADD COLUMN IF NOT EXISTS base_shares INTEGER,
            ADD COLUMN IF NOT EXISTS share_increment INTEGER,
            ADD COLUMN IF NOT EXISTS max_risk_pct DOUBLE PRECISION DEFAULT 10.0,
            ADD COLUMN IF NOT EXISTS target_monthly_return_pct DOUBLE PRECISION DEFAULT 5.0;
        """)
        conn.commit()
        print("✅ Added new columns to strategy_config table")
    except Exception as e:
        print(f"⚠️ Columns might already exist: {e}")
        conn.rollback()
    
    # Get all strategies
    cursor.execute("SELECT id, strategy_name FROM strategy_config;")
    strategies = cursor.fetchall()
    
    if not strategies:
        print("⚠️ No strategies found in database")
        return
    
    updated_count = 0
    
    for strategy_id, strategy_name in strategies:
        profile = get_strategy_risk_profile(strategy_name)
        
        try:
            cursor.execute("""
                UPDATE strategy_config
                SET base_shares = %s,
                    share_increment = %s,
                    max_risk_pct = %s,
                    target_monthly_return_pct = %s
                WHERE id = %s;
            """, (
                profile['base_shares'],
                profile['share_increment'],
                profile['max_risk_pct'],
                profile['target_monthly_return_pct'],
                strategy_id
            ))
            
            updated_count += 1
            print(f"✅ Updated {strategy_name}: base={profile['base_shares']}, "
                  f"increment={profile['share_increment']}, risk={profile['max_risk_pct']}%")
            
        except Exception as e:
            print(f"❌ Failed to update {strategy_name}: {e}")
            conn.rollback()
    
    conn.commit()
    print(f"\n✅ Updated {updated_count} strategy configurations")


def main():
    """Main execution"""
    print("=" * 70)
    print("🔧 Strategy Configuration Updater")
    print("=" * 70)
    print()
    
    conn = connect_db()
    update_strategy_configs(conn)
    conn.close()
    
    print("\n" + "=" * 70)
    print("✅ Strategy configurations updated successfully!")
    print("=" * 70)


if __name__ == "__main__":
    main()
