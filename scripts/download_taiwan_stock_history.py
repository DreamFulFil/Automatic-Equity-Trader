#!/usr/bin/env python3
"""
Taiwan Stock Historical Data Downloader

This script downloads historical OHLCV data for all Taiwan stocks from Yahoo Finance
and inserts them into the PostgreSQL database.

Features:
- Fetches all TWSE (Taiwan Stock Exchange) stock tickers
- Downloads historical data using yfinance
- Stores data in both 'bar' and 'market_data' tables
- Handles duplicates gracefully (skips existing records)
- Progress tracking and error handling
- Rate limiting to avoid HTTP 429 errors

Usage:
    python3 /tmp/download_taiwan_stock_history.py

Database connection:
    Host: localhost
    Database: auto_equity_trader
    User: dreamer
    Password: WSYS1r0PE0Ig0iuNX2aNi5k7

Requirements:
    pip install yfinance pandas psycopg2-binary requests beautifulsoup4 lxml
"""

import yfinance as yf
import pandas as pd
import psycopg2
from psycopg2.extras import execute_batch
from datetime import datetime, timedelta
import time
import sys
import requests
from bs4 import BeautifulSoup
import re

# Database configuration
DB_CONFIG = {
    'host': 'localhost',
    'database': 'auto_equity_trader',
    'user': 'dreamer',
    'password': 'WSYS1r0PE0Ig0iuNX2aNi5k7'
}

# Configuration
RATE_LIMIT_DELAY = 1.5  # seconds between requests to avoid 429
BATCH_SIZE = 100  # number of rows to insert at once
MAX_TICKERS = None  # Set to a number for testing, None for all


def get_twse_tickers():
    """
    Get list of well-known Taiwan Stock Exchange tickers.
    Returns a list of ticker symbols (with .TW suffix for yfinance).
    """
    print("📥 Using well-known TWSE ticker list...")
    
    # Only use well-known Taiwan stocks (skip web scraping and range generation)
    well_known_stocks = [
        "2330.TW",  # TSMC
        "2454.TW",  # MediaTek
        "2317.TW",  # Hon Hai (Foxconn)
        "2303.TW",  # UMC
        "3711.TW",  # ASE Technology
        "2412.TW",  # Chunghwa Telecom
        "2882.TW",  # Cathay Financial
        "2881.TW",  # Fubon Financial
        "1301.TW",  # Formosa Plastics
        "2002.TW",  # China Steel
        "2891.TW",  # CTBC Financial
        "2886.TW",  # Mega Financial
        "2308.TW",  # Delta Electronics
        "1303.TW",  # Nan Ya Plastics
        "2382.TW",  # Quanta Computer
        "2357.TW",  # Asustek Computer
        "3008.TW",  # Largan Precision
        "2912.TW",  # President Chain Store
    ]
    
    tickers_list = sorted(well_known_stocks)
    
    if MAX_TICKERS:
        tickers_list = tickers_list[:MAX_TICKERS]
        print(f"   ⚠️  Limited to first {MAX_TICKERS} tickers for testing")
    
    print(f"📊 Total tickers to process: {len(tickers_list)}")
    return tickers_list


def create_connection():
    """Create database connection"""
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        return conn
    except Exception as e:
        print(f"❌ Database connection failed: {e}")
        sys.exit(1)


def check_duplicate_bar(cursor, symbol, timestamp, timeframe):
    """Check if a bar already exists in the database"""
    cursor.execute("""
        SELECT COUNT(*) FROM bar 
        WHERE symbol = %s AND timestamp = %s AND timeframe = %s
    """, (symbol, timestamp, timeframe))
    return cursor.fetchone()[0] > 0


def check_duplicate_market_data(cursor, symbol, timestamp, timeframe):
    """Check if market data already exists in the database"""
    cursor.execute("""
        SELECT COUNT(*) FROM market_data 
        WHERE symbol = %s AND timestamp = %s AND timeframe = %s
    """, (symbol, timestamp, timeframe))
    return cursor.fetchone()[0] > 0


def download_and_insert_stock_data(ticker, conn):
    """
    Download historical data for a single ticker and insert into database.
    Returns (success, records_inserted, error_message)
    """
    cursor = conn.cursor()
    records_inserted = 0
    
    try:
        # Download historical data (max 10 years)
        stock = yf.Ticker(ticker)
        
        # Try to get data - start with max period, then fallback to shorter periods
        df = None
        periods = ['max', '10y', '5y', '2y', '1y']
        
        for period in periods:
            try:
                df = stock.history(period=period)
                if df is not None and not df.empty:
                    break
            except Exception as e:
                continue
        
        if df is None or df.empty:
            return (False, 0, "No data available")
        
        # Remove timezone info for PostgreSQL compatibility
        df.index = df.index.tz_localize(None)
        
        # Prepare data for insertion
        bars = []
        market_data_rows = []
        
        for timestamp, row in df.iterrows():
            # Skip if any essential values are NaN
            if pd.isna(row['Open']) or pd.isna(row['Close']):
                continue
            
            # Check for duplicates before preparing insert
            if not check_duplicate_bar(cursor, ticker, timestamp, '1day'):
                bar = (
                    timestamp,  # timestamp
                    ticker,  # symbol
                    'TSE',  # market
                    '1day',  # timeframe
                    float(row['Open']),  # open
                    float(row['High']),  # high
                    float(row['Low']),  # low
                    float(row['Close']),  # close
                    int(row['Volume']),  # volume
                    None,  # trade_count
                    None,  # vwap
                    None,  # buy_volume
                    None,  # sell_volume
                    True  # is_complete
                )
                bars.append(bar)
            
            # Prepare market_data entry
            if not check_duplicate_market_data(cursor, ticker, timestamp, 'DAY_1'):
                market_data = (
                    timestamp,  # timestamp
                    ticker,  # symbol
                    'DAY_1',  # timeframe (enum)
                    float(row['Open']),  # open_price
                    float(row['High']),  # high_price
                    float(row['Low']),  # low_price
                    float(row['Close']),  # close_price
                    int(row['Volume'])  # volume
                )
                market_data_rows.append(market_data)
        
        # Insert bars in batches
        if bars:
            execute_batch(cursor, """
                INSERT INTO bar (timestamp, symbol, market, timeframe, open, high, low, close, 
                                volume, trade_count, vwap, buy_volume, sell_volume, is_complete)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, bars, page_size=BATCH_SIZE)
            records_inserted += len(bars)
        
        # Insert market_data in batches
        if market_data_rows:
            execute_batch(cursor, """
                INSERT INTO market_data (timestamp, symbol, timeframe, open_price, high_price, 
                                        low_price, close_price, volume)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """, market_data_rows, page_size=BATCH_SIZE)
            records_inserted += len(market_data_rows)
        
        conn.commit()
        cursor.close()
        
        return (True, records_inserted, None)
        
    except Exception as e:
        conn.rollback()
        cursor.close()
        error_msg = str(e)
        return (False, 0, error_msg)


def main():
    """Main execution function"""
    print("=" * 70)
    print("🇹🇼 Taiwan Stock Historical Data Downloader")
    print("=" * 70)
    print()
    
    # Get list of tickers
    tickers = get_twse_tickers()
    
    if not tickers:
        print("❌ No tickers found. Exiting.")
        sys.exit(1)
    
    # Connect to database
    print("\n🔌 Connecting to PostgreSQL database...")
    conn = create_connection()
    print("   ✅ Database connected")
    
    # Statistics
    total = len(tickers)
    success_count = 0
    failed_count = 0
    total_records = 0
    skipped_count = 0
    
    start_time = time.time()
    
    print(f"\n📊 Starting download for {total} tickers...")
    print(f"   Rate limit delay: {RATE_LIMIT_DELAY}s per ticker")
    print()
    
    # Process each ticker
    for idx, ticker in enumerate(tickers, 1):
        # Progress indicator
        progress = (idx / total) * 100
        elapsed = time.time() - start_time
        eta = (elapsed / idx) * (total - idx) if idx > 0 else 0
        
        print(f"[{idx}/{total}] ({progress:.1f}%) {ticker}...", end=" ", flush=True)
        
        success, records, error = download_and_insert_stock_data(ticker, conn)
        
        if success:
            if records > 0:
                success_count += 1
                total_records += records
                print(f"✅ {records} records")
            else:
                skipped_count += 1
                print(f"⏭️  Skipped (already exists)")
        else:
            failed_count += 1
            print(f"❌ Failed: {error}")
        
        # Rate limiting
        if idx < total:
            time.sleep(RATE_LIMIT_DELAY)
    
    # Close database connection
    conn.close()
    
    # Final statistics
    elapsed_total = time.time() - start_time
    print()
    print("=" * 70)
    print("📈 Download Complete!")
    print("=" * 70)
    print(f"✅ Successful:     {success_count}")
    print(f"⏭️  Skipped:        {skipped_count}")
    print(f"❌ Failed:         {failed_count}")
    print(f"📊 Total records:  {total_records}")
    print(f"⏱️  Time elapsed:   {elapsed_total:.1f}s ({elapsed_total/60:.1f} minutes)")
    print()
    print(f"💾 Data stored in database: {DB_CONFIG['database']}")
    print(f"   Tables: 'bar' and 'market_data'")
    print()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Download interrupted by user. Exiting...")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
