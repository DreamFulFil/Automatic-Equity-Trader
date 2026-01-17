import os
import json
import time
from datetime import datetime, timedelta
import yfinance as yf
from app.services.telegram_service import send_telegram_message

def scrape_earnings_dates(jasypt_password: str = None):
    """
    Scrapes earnings dates with yfinance and writes to legacy JSON for bootstrapping.
    Sends Telegram notification on start and completion.
    """
    
    # Write under the current working directory so callers/tests can control the output location.
    config_dir = os.path.join(os.getcwd(), 'config')
    output_file = os.path.join(config_dir, 'earnings-blackout-dates.json')
    
    # Major Taiwan stocks to track (Yahoo Finance tickers)
    TICKERS = [
        'TSM',      # TSMC (ADR)
        '2454.TW',  # MediaTek (Taiwan)
        '2454.TW',  # MediaTek
        '2317.TW',  # Hon Hai (Foxconn)
        'UMC',      # UMC (ADR)
        '2303.TW',  # UMC (Taiwan)
        'ASX',      # ASE Technology (ADR)
        '3711.TW',  # ASE (Taiwan)
        '2412.TW',  # Chunghwa Telecom
        '2882.TW',  # Cathay Financial
        '2881.TW',  # Fubon Financial
        '1301.TW',  # Formosa Plastics
        '2002.TW',  # China Steel
    ]
    
    print(f"📅 Scraping earnings dates for {len(TICKERS)} stocks (using yfinance)...")
    if jasypt_password:
        send_telegram_message(f"📅 <b>Earnings Scraper Started</b>\nChecking {len(TICKERS)} tickers...", jasypt_password)
    
    earnings_dates = set()
    today = datetime.now().date()
    one_year_later = today + timedelta(days=365)
    
    new_dates_found = []
    
    for ticker in TICKERS:
        try:
            stock = yf.Ticker(ticker)
            calendar = stock.calendar
            
            if calendar is None or 'Earnings Date' not in calendar:
                print(f"  ⚠️ {ticker}: No earnings date available")
                continue
            
            earnings_list = calendar.get('Earnings Date', [])
            if not isinstance(earnings_list, list):
                earnings_list = [earnings_list]
            
            for dt in earnings_list:
                if isinstance(dt, datetime):
                    dt = dt.date()
                if isinstance(dt, type(today)) and today <= dt <= one_year_later:
                    date_str = dt.isoformat()
                    earnings_dates.add(date_str)
                    new_dates_found.append(f"{ticker}: {date_str}")
                    print(f"  ✅ {ticker}: {date_str}")
            
            # Rate limit: 15 seconds between requests to avoid 429 errors
            time.sleep(5)
            
        except Exception as e:
            print(f"  ❌ {ticker}: {e}")
            continue
    
    # Load existing dates (graceful: keep old if scrape fails)
    existing_dates = set()
    if os.path.exists(output_file):
        try:
            with open(output_file, 'r') as f:
                existing = json.load(f)
                existing_dates = set(existing.get('dates', []))
        except:
            pass
    
    # Merge new dates with existing (never lose data)
    all_dates = earnings_dates.union(existing_dates)
    
    # Filter to only future dates
    future_dates = sorted([d for d in all_dates if d >= today.isoformat()])
    
    # Save to JSON
    os.makedirs(config_dir, exist_ok=True)
    result = {
        "last_updated": datetime.now().isoformat(),
        "source": "Yahoo Finance (yfinance)",
        "tickers_checked": TICKERS,
        "dates": future_dates
    }
    
    with open(output_file, 'w') as f:
        json.dump(result, f, indent=2)
    
    print(f"\n✅ Saved {len(future_dates)} blackout dates to {output_file}")
    print(f"   Next dates: {future_dates[:5]}...")
    
    if jasypt_password:
        msg = f"✅ <b>Earnings Scraper Finished</b>\nTotal dates: {len(future_dates)}\n"
        if new_dates_found:
            msg += "New/Upcoming:\n" + "\n".join(new_dates_found[:5])
            if len(new_dates_found) > 5:
                msg += f"\n...and {len(new_dates_found)-5} more"
        else:
            msg += "No new dates found."
        send_telegram_message(msg, jasypt_password)
    
    return future_dates
