#!/usr/bin/env python3
"""
Historical Data Population Script

Fetches 2 years of daily historical data for Taiwan stocks via Shioaji API
and stores them in PostgreSQL for backtesting.

Usage:
    python scripts/operational/populate_historical_data.py --jasypt-password <password>
"""

import os
import sys
import argparse
from datetime import datetime, timedelta
from typing import List
import time
import psycopg2
from psycopg2.extras import execute_batch

# Add python directory to path
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(project_root, 'python'))

from app.core.config import load_config_with_decryption

# Taiwan stock symbols (top 10 liquid stocks for backtesting)
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

def connect_shioaji(config):
    """Connect to Shioaji API"""
    try:
        import shioaji as sj
        
        person_id = config['sinopac']['person_id']
        password = config['sinopac']['password']
        
        api = sj.Shioaji()
        api.login(person_id=person_id, passwd=password)
        
        print("✅ Connected to Shioaji API")
        return api
        
    except Exception as e:
        print(f"❌ Failed to connect to Shioaji: {e}")
        return None

def fetch_kbars(api, stock_code: str, start_date: str, end_date: str):
    """Fetch historical kbars from Shioaji"""
    try:
        contract = api.Contracts.Stocks[stock_code]
        kbars = api.kbars(contract=contract, start=start_date, end=end_date)
        
        print(f"  ✅ Fetched {len(kbars)} kbars for {stock_code}")
        return kbars
        
    except Exception as e:
        print(f"  ❌ Failed to fetch kbars for {stock_code}: {e}")
        return []

def store_to_postgres(stock_code: str, kbars: List, db_config):
    """Store kbars to PostgreSQL database"""
    try:
        conn = psycopg2.connect(
            host=db_config.get('host', 'localhost'),
            port=db_config.get('port', 5432),
            database=db_config['database'],
            user=db_config['username'],
            password=db_config['password']
        )
        cursor = conn.cursor()
        
        # Prepare data for batch insert
        records = []
        for kbar in kbars:
            # Parse timestamp
            ts = datetime.fromisoformat(str(kbar['ts']).replace('Z', '+00:00'))
            
            records.append((
                f"{stock_code}.TW",  # symbol
                ts,  # timestamp
                float(kbar['Open']),  # open
                float(kbar['High']),  # high
                float(kbar['Low']),  # low
                float(kbar['Close']),  # close
                int(kbar['Volume']),  # volume
                'DAY_1'  # timeframe
            ))
        
        # Batch insert with ON CONFLICT DO UPDATE
        insert_query = """
            INSERT INTO market_data (symbol, timestamp, open_price, high_price, low_price, close_price, volume, timeframe)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (symbol, timestamp, timeframe) 
            DO UPDATE SET
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume
        """
        
        execute_batch(cursor, insert_query, records, page_size=100)
        conn.commit()
        
        print(f"  ✅ Stored {len(records)} records for {stock_code}")
        
        cursor.close()
        conn.close()
        
        return len(records)
        
    except Exception as e:
        print(f"  ❌ Failed to store data for {stock_code}: {e}")
        return 0

def main():
    parser = argparse.ArgumentParser(description='Populate historical market data')
    parser.add_argument('--jasypt-password', required=True, help='Jasypt password')
    parser.add_argument('--days', type=int, default=730, help='Days of history (default: 730)')
    
    args = parser.parse_args()
    
    print("╔═══════════════════════════════════════════════════════════════╗")
    print("║      Historical Data Population - Taiwan Stocks (Daily)       ║")
    print("╚═══════════════════════════════════════════════════════════════╝\n")
    
    # Load configuration
    config = load_config_with_decryption(args.jasypt_password)
    
    # Connect to Shioaji
    api = connect_shioaji(config)
    if not api:
        sys.exit(1)
    
    # Calculate date range
    end_date = datetime.now()
    start_date = end_date - timedelta(days=args.days)
    start_str = start_date.strftime('%Y-%m-%d')
    end_str = end_date.strftime('%Y-%m-%d')
    
    print(f"📅 Date range: {start_str} to {end_str}")
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
        print(f"🔄 Processing {stock_code}...")
        
        kbars = fetch_kbars(api, stock_code, start_str, end_str)
        
        if kbars:
            count = store_to_postgres(stock_code, kbars, db_config)
            total_records += count
        
        # Rate limiting
        time.sleep(0.5)
    
    print("\n" + "=" * 60)
    print(f"✅ Historical data population complete!")
    print(f"📊 Total records: {total_records}")
    print(f"📈 Stocks processed: {len(TAIWAN_STOCKS)}")
    
    api.logout()
    print("✅ Logged out from Shioaji")

if __name__ == "__main__":
    main()
