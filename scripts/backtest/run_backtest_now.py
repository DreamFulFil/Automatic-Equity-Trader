#!/usr/bin/env python3
import requests
import time
from datetime import datetime, timedelta

STOCKS = ["1216.TW", "1301.TW", "1303.TW", "1326.TW", "1402.TW", "2002.TW", "2105.TW", "2301.TW", "2303.TW", "2308.TW", "2317.TW", "2324.TW", "2327.TW", "2330.TW", "2357.TW", "2377.TW", "2379.TW", "2382.TW", "2395.TW", "2408.TW", "2409.TW", "2412.TW", "2454.TW", "2474.TW", "2609.TW", "2610.TW", "2615.TW", "2801.TW", "2834.TW", "2845.TW", "2880.TW", "2881.TW", "2882.TW", "2883.TW", "2884.TW", "2885.TW", "2886.TW", "2887.TW", "2891.TW", "2892.TW", "2912.TW", "3008.TW", "3034.TW", "3037.TW", "3045.TW", "3711.TW", "6505.TW"]

end_date = datetime.now()
start_date = end_date - timedelta(days=365)

print("=" * 70)
print("RUNNING BACKTESTS")
print("=" * 70)
print("Period:", start_date.strftime('%Y-%m-%d'), "to", end_date.strftime('%Y-%m-%d'))
print("Stocks:", len(STOCKS))
print("=" * 70)

successful = 0
failed = 0

for i, symbol in enumerate(STOCKS, 1):
    print("[{}/{}] {}...".format(i, len(STOCKS), symbol), end=" ", flush=True)
    
    try:
        params = {
            'symbol': symbol,
            'timeframe': '1D',
            'start': start_date.isoformat(),
            'end': end_date.isoformat(),
            'capital': 80000
        }
        
        response = requests.get("http://localhost:16350/api/backtest/run", params=params, timeout=300)
        
        if response.status_code == 200:
            results = response.json()
            num = len(results)
            print("OK", num, "strategies")
            successful += 1
        else:
            print("FAIL", response.status_code)
            failed += 1
            
    except Exception as e:
        print("ERROR", str(e)[:50])
        failed += 1
    
    time.sleep(1)

print()
print("DONE:", successful, "/", len(STOCKS), "successful")
