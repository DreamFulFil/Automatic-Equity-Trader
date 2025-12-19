#!/usr/bin/env python3
"""
Generate Mock Historical Data

Creates synthetic historical data for backtesting when Shioaji API is not available.
Generates realistic OHLCV data with trends, volatility, and volume patterns.

Usage:
    python scripts/operational/generate_mock_data.py --days 730
"""

import os
import sys
import argparse
import psycopg2
from psycopg2.extras import execute_batch
from datetime import datetime, timedelta
import random
import math

# Taiwan stock symbols (same as populate_historical_data.py)
TAIWAN_STOCKS = [
    "2330",  # TSMC
    "2317",  # Hon Hai
    "2454",  # MediaTek
    "2308",  # Delta Electronics
    "2881",  # Fubon Financial
    "2882",  # Cathay Financial
    "2886",  # Mega Financial
    "2303",  # United Microelectronics
    "1303",  # Nan Ya Plastics
    "1301",  # Formosa Plastics
]

# Realistic base prices for Taiwan stocks (approximate TWD)
BASE_PRICES = {
    "2330": 600.0,  # TSMC
    "2317": 100.0,  # Hon Hai
    "2454": 1100.0,  # MediaTek
    "2308": 300.0,  # Delta
    "2881": 70.0,   # Fubon
    "2882": 50.0,   # Cathay
    "2886": 35.0,   # Mega
    "2303": 45.0,   # UMC
    "1303": 65.0,   # Nan Ya
    "1301": 95.0,   # Formosa
}

def generate_ohlcv(stock_code: str, days: int):
    """Generate realistic OHLCV data with trends and volatility"""
    base_price = BASE_PRICES.get(stock_code, 100.0)
    data = []
    
    # Start from days ago
    current_date = datetime.now() - timedelta(days=days)
    price = base_price
    
    # Generate trend (slight upward bias for Taiwan market)
    trend = random.uniform(-0.0001, 0.0003)
    
    for day in range(days):
        # Skip weekends
        if current_date.weekday() >= 5:  # Saturday or Sunday
            current_date += timedelta(days=1)
            continue
        
        # Daily volatility (2-4%)
        daily_volatility = random.uniform(0.02, 0.04)
        
        # Price movement with trend and randomness
        change_pct = random.gauss(trend, daily_volatility)
        price *= (1 + change_pct)
        
        # Generate OHLC with realistic relationships
        daily_range = price * random.uniform(0.015, 0.04)
        
        open_price = price + random.uniform(-daily_range/2, daily_range/2)
        high = max(open_price, price) + random.uniform(0, daily_range/2)
        low = min(open_price, price) - random.uniform(0, daily_range/2)
        close = price
        
        # Ensure OHLC relationships
        high = max(high, open_price, close)
        low = min(low, open_price, close)
        
        # Volume (100k to 10M shares, log-normal distribution)
        base_volume = 1_000_000
        volume = int(base_volume * random.lognormvariate(0, 1.5))
        
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

def store_to_postgres(stock_code: str, data: list, db_config):
    """Store generated data to PostgreSQL"""
    try:
        conn = psycopg2.connect(
            host=db_config.get('host', 'localhost'),
            port=db_config.get('port', 5432),
            database=db_config['database'],
            user=db_config['username'],
            password=db_config['password']
        )
        cursor = conn.cursor()
        
        # Prepare records
        records = []
        for bar in data:
            records.append((
                f"{stock_code}.TW",  # symbol
                bar['date'],  # timestamp
                bar['open'],
                bar['high'],
                bar['low'],
                bar['close'],
                bar['volume'],
                'DAY_1'  # timeframe
            ))
        
        # Batch insert (simple - assume clean database)
        insert_query = """
            INSERT INTO market_data (symbol, timestamp, open_price, high_price, low_price, close_price, volume, timeframe)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        execute_batch(cursor, insert_query, records, page_size=100)
        conn.commit()
        
        print(f"  ✅ Stored {len(records)} bars for {stock_code}")
        
        cursor.close()
        conn.close()
        
        return len(records)
        
    except Exception as e:
        print(f"  ❌ Failed to store data for {stock_code}: {e}")
        return 0

def main():
    parser = argparse.ArgumentParser(description='Generate mock historical data')
    parser.add_argument('--days', type=int, default=730, help='Days of history (default: 730)')
    
    args = parser.parse_args()
    
    print("╔═══════════════════════════════════════════════════════════════╗")
    print("║         Mock Historical Data Generator - Taiwan Stocks        ║")
    print("╚═══════════════════════════════════════════════════════════════╝\n")
    
    print(f"📅 Generating {args.days} days of history")
    print(f"📈 Stocks: {len(TAIWAN_STOCKS)}")
    print("=" * 60 + "\n")
    
    # Database config
    db_config = {
        'host': 'localhost',
        'port': 5432,
        'database': os.environ.get('POSTGRES_DB', 'auto_equity_trader'),
        'username': os.environ.get('POSTGRES_USER', 'dreamer'),
        'password': os.environ.get('POSTGRES_PASSWORD', 'password')
    }
    
    total_records = 0
    
    for stock_code in TAIWAN_STOCKS:
        print(f"🔄 Generating data for {stock_code}...")
        
        data = generate_ohlcv(stock_code, args.days)
        count = store_to_postgres(stock_code, data, db_config)
        total_records += count
    
    print("\n" + "=" * 60)
    print(f"✅ Mock data generation complete!")
    print(f"📊 Total bars: {total_records}")
    print(f"📈 Stocks: {len(TAIWAN_STOCKS)}")
    print(f"📅 Date range: ~{args.days} days of trading data")

if __name__ == "__main__":
    main()
