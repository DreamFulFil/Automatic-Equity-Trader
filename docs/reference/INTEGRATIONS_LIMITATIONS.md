# Integrations & Data Limitations (Shioaji, Yahoo, Python Bridge, Ollama) 🔧

**Document Version**: 1.1  
**Last Updated**: 2025-12-28  
**Author**: AI Agent / GitHub Copilot

## Executive Summary

This document consolidates known limitations and operational caveats for external integrations used by Automatic-Equity-Trader (historical data providers, broker APIs, Python bridge, LLM service). It also documents the fixes we implemented to make integration tests deterministic and a new fallback behavior that fills missing stock names on data ingestion.

> TL;DR: Yahoo Finance remains the best primary source for multi-year history; Shioaji is reliable for recent and real-time data. Test stability issues were fixed by providing test property overrides and lightweight test beans; missing names are now filled from a local lookup as a final fallback.

## Key External Limitations & Recent Issues we fixed

This section summarizes the concrete problems we hit during integration testing and production runs, and how we fixed them.

### 1) Jasypt / Encrypted properties caused ApplicationContext startup failures 🔐

Problem:
- `application.yml` included encrypted values (Jasypt ENC(...) format) such as `shioaji.ca-password`, `telegram.bot-token`, `spring.datasource.password`. Tests failed at context startup with DecryptionException when Jasypt password was not configured for test runs.

Fix:
- Provide safe test overrides in `@SpringBootTest(properties = {...})`, including `jasypt.encryptor.password=dummy` and dummy values for `shioaji.*`, `telegram.*`, `spring.datasource.password`.
- Add a small Test `@TestConfiguration` that provides primary beans for `ShioajiProperties` and `TelegramProperties` so binding doesn't depend on encrypted production secrets.

Why it matters:
- This prevents context initialization errors when running integration tests in CI where production secrets are intentionally absent.

---

### 2) Noisy startup due to external services and scheduled jobs (Ollama, Python bridge, scheduled TradingEngine) 🚨

Problem:
- Tests were slow and flaky because application startup executed many scheduled jobs and attempted live external calls (Ollama LLM /api/generate, Python bridge /health & /signal, earnings refresh jobs, trading loops).

Fixes implemented in tests:
- Disable scheduling via test property: `spring.task.scheduling.enabled=false` and `earnings.refresh.enabled=false`.
- Provide a primary no-op `TradingEngineService` (overrides `initialize()` and `tradingLoop()` to no-op) injected in test context.
- Replace in-test `restTemplate` override (which collided with app `restTemplate` bean) by binding a `MockRestServiceServer` to the application's `RestTemplate` and register canned responses for:
  - POST .../api/generate → `{"response":"{}"}`
  - GET .../health → `ok`
  - GET .../signal → `{"current_price":0.0}`

Why it matters:
- Makes integration tests deterministic and fast (no network calls or long-running scheduled loops during test startup).

---

### 3) Missing numeric fields or DB constraints in test fixtures caused H2 insert failures ⚠️

Problem:
- Integration tests failed because test `Bar` entities saved to H2 had null numeric fields (e.g., `close`), violating not-null constraints.

Fix:
- Update `BacktestIntegrationTest` to populate required numeric fields: `open`, `close`, `high`, `low`, `volume` before saving test `Bar`.

---

### 4) History download sometimes returned points without `name` (stock name) or Python bridge returned null responses 🐍

Problem:
- The Python bridge occasionally returned null or incomplete data; as a result some `bar` and `market_data` rows had empty `name` fields, and `BacktestService` then persisted results with `stock_name = null`.

Fix (new fallback behavior):
- Implemented `HistoryDataService.fillMissingNamesIfMissing(List<Bar>, List<MarketData>, String symbol)`.
  - When a flushing batch contains missing `name` fields, the helper checks `TaiwanStockNameService.hasStockName(symbol)` and sets missing names to `TaiwanStockNameService.getStockName(symbol)` where available.
  - The helper is called before every bulk flush and before the final flush, ensuring names are filled (when available) before insertion.
- Added unit test: `fillMissingNamesIfMissing_shouldFillFromTaiwanService()`.

Why it matters:
- Backtest results now almost always have a `stock_name` populated (either from history or from the local mapping), improving downstream selection UIs and reports.

### Impact on Multi-Year Backtesting

For strategies requiring 7-10 years of historical data:
- **Shioaji Coverage**: 5 years (2020-03-02 to present)
- **Data Gap**: Missing 2-5 years of earlier historical data
- **Business Impact**: Cannot perform comprehensive long-term backtesting using Shioaji alone

## Data Source Comparison

### Yahoo Finance

**Advantages:**
- ✅ Historical data availability: **2018 and earlier** for many Taiwan stocks
- ✅ No rate limiting observed in production testing (50 stocks × 7 years)
- ✅ Reliable and consistent data quality
- ✅ Free API access without authentication
- ✅ Covers data gaps before Shioaji's 2020-03-02 limit

**Limitations:**
- ⚠️ Occasional "No timezone found" or missing `name` errors for some symbols (handled by fallback mechanism in `HistoryDataService`).
- ⚠️ Requires proper date range formatting
- ⚠️ May have slight discrepancies in OHLCV values compared to official exchange data

**Production Validation:**
From actual historical download (2025-12-27):
```
Date Range: 2018-12-07 to 2025-12-26
Total Records: 139,024
Coverage: 50 stocks
Yahoo-sourced Pre-2020 Data: Confirmed for multiple stocks (e.g., 3045.TW, 6505.TW, 2498.TW)
Rate Limiting Incidents: 0
```

**Note:** If Python bridge responses are null or missing the `name` field, the system will attempt to fill the name using `TaiwanStockNameService` before inserting the rows (see 'Fallback behavior' below).

### Shioaji

**Advantages:**
- ✅ Official Taiwan broker API
- ✅ Real-time and recent historical data
- ✅ Integrated with trading functionality
- ✅ Reliable within its documented date range (2020-03-02 onwards)

**Limitations:**
- ❌ Historical data limited to **2020-03-02 onwards**
- ❌ Cannot provide pre-2020 data for long-term backtesting
- ❌ Requires authentication and active broker account

### TWSE (Taiwan Stock Exchange)

**Advantages:**
- ✅ Official exchange data
- ✅ Free public access

**Limitations:**
- ⚠️ Limited historical range
- ⚠️ More complex API integration
- ⚠️ Used primarily as supplementary source

## Why Some Stocks Have Missing Data

### Reason 1: Historical Availability

Different stocks have different historical data availability based on:

1. **Listing Date**: Stocks listed after 2018 will have less historical data
2. **Data Provider Coverage**: Not all stocks are covered equally by Yahoo Finance
3. **Corporate Actions**: Mergers, delistings, or ticker changes can cause data gaps

### Reason 2: Data Source Prioritization

Current configuration (as of 2025-12-27):
```
Priority Order: Yahoo Finance → Shioaji → TWSE
```

**How it works:**
1. **Yahoo Finance** attempts to fetch full date range (up to 10 years)
2. **Shioaji** fills gaps from 2020-03-02 onwards
3. **TWSE** provides supplementary data where available

### Reason 3: Date Range Segmentation

The system downloads in batches of 365 days per year:
- Batch 1: 2024-12-27 to 2025-12-26 (most recent year)
- Batch 2: 2023-12-28 to 2024-12-27
- ...
- Batch 10: 2015-12-29 to 2016-12-28 (oldest requested year)

If a data source cannot provide data for an older batch, that period will have missing data.

## Rate Limiting Analysis

### Test Methodology

**Test Date**: 2025-12-27  
**Test Scope**: 50 Taiwan stocks × 7 years of historical data  
**Total Requests**: 50 API calls  

### Results

```
Stocks Tested: 50/50
Successful: Varied (data availability issue, not rate limiting)
Rate Limited (HTTP 429): 0
```

**Conclusion**: ✅ **No rate limiting detected** in Yahoo Finance API for Taiwan stock historical data downloads.

### Production Validation

Actual download session (2025-12-27):
- **Duration**: ~10 minutes for 50 stocks × 10 years
- **Total Records**: 139,024
- **HTTP 429 Errors**: 0
- **Yahoo Finance Failures**: Some "No timezone" errors (data unavailability, not rate limiting)
- **Overall Success Rate**: High (sufficient data obtained from combined sources)

## Recommended Data Source Priority

Based on the analysis:

### ✅ APPROVED: Yahoo Finance as Primary Source

**Rationale:**
1. No rate limiting observed in production
2. Provides historical data beyond Shioaji's 2020-03-02 limit
3. Successfully populated 7 years of historical data (2018-2025)
4. Fallback mechanism handles Yahoo failures gracefully

**Configuration:**
```python
# Priority Order (Current)
1. Yahoo Finance  # Primary: Best historical coverage
2. Shioaji        # Supplement: 2020+ data, validation
3. TWSE           # Supplement: Gap filling
```

### Data Merge Strategy

```python
# Pseudo-code
merged_data = {}

for bar in yahoo_data:
    if date not in merged_data:
        merged_data[date] = bar  # Yahoo takes precedence

for bar in shioaji_data:
    if date not in merged_data:
        merged_data[date] = bar  # Shioaji fills gaps

for bar in twse_data:
    if date not in merged_data:
        merged_data[date] = bar  # TWSE fills remaining gaps
```

## Production Evidence

### Actual Download Results (2025-12-27)

```sql
-- Overall Coverage
SELECT 
    MIN(timestamp::date) as earliest, 
    MAX(timestamp::date) as latest,
    COUNT(DISTINCT symbol) as symbols,
    COUNT(*) as total_records
FROM bar;
```

**Output:**
```
 earliest  |   latest   | symbols | total_records
-----------+------------+---------+---------------
2018-12-07 | 2025-12-26 |      50 |        139024
```

**Years of Data**: ~7 years  
**Expected for 10-year request**: Stocks not trading before 2018, or data unavailable

### Per-Stock Analysis Sample

```sql
SELECT symbol, COUNT(*) as records, 
       MIN(timestamp::date) as earliest, 
       MAX(timestamp::date) as latest
FROM bar 
GROUP BY symbol 
ORDER BY earliest
LIMIT 5;
```

**Output:**
```
 symbol  | records |  earliest  |   latest
---------+---------+------------+------------
3045.TW  |   1776  | 2018-12-07 | 2025-12-26  -- 7 years (Yahoo)
6505.TW  |   1776  | 2018-12-07 | 2025-12-26  -- 7 years (Yahoo)
2498.TW  |   1776  | 2018-12-07 | 2025-12-26  -- 7 years (Yahoo)
2303.TW  |   3893  | 2020-12-28 | 2025-12-26  -- 5 years (Shioaji)
2308.TW  |   3893  | 2020-12-28 | 2025-12-26  -- 5 years (Shioaji)
```

**Analysis**: 
- Some stocks have data from 2018 (Yahoo Finance successful)
- Some stocks only from 2020 (Yahoo unavailable, Shioaji provided data)
- Combined sources provide comprehensive coverage

## Troubleshooting Guide

### Issue: Missing Historical Data for Specific Stocks

**Symptoms**: Some stocks have less than expected years of data

**Root Causes:**
1. Stock was not trading during earlier years (e.g., listed after 2020)
2. Yahoo Finance doesn't have historical data for this specific stock
3. Data source failures during that time period batch

**Solution**: Review per-stock date ranges and verify against stock listing dates

### Issue: All Data Limited to 2020 Onwards

**Symptoms**: No data before 2020-03-02 for any stock

**Root Cause**: Yahoo Finance primary source is not working, Shioaji is only source

**Solution**: 
1. Check Python logs: `tail -f /tmp/bridge.log | rg -i "yahoo"`
2. Verify Yahoo Finance can be accessed
3. Check if date range is being properly passed to `_fetch_yahoo`

### Issue: Backtests missing `stock_name` (null) in results

**Symptoms**: `BacktestResult.stock_name` is null for some persisted results

**Root Causes:**
- History ingestion created `bar` / `market_data` rows with missing `name`, usually due to Python bridge response missing the `name` field or returning null.

**Solution & Verification**:
- A new helper `fillMissingNamesIfMissing(...)` runs before bulk insert and final flush. It tries to populate empty `name` values using `TaiwanStockNameService`.
- Verify by checking logs for: `🔧 Filled missing names for <SYMBOL> using fallback '<NAME>'`.
- DB check:
```sql
SELECT COUNT(*) AS missing_backtest_names FROM backtest_result WHERE stock_name IS NULL;
```
- If > 0, inspect `bar`/`market_data` rows for that symbol to see if history was missing or if fallback mapping doesn't exist.

## Recommendations

### For Long-Term Backtesting (7-10 years)

1. ✅ **Use Yahoo Finance as primary source** (currently configured)
2. Use Shioaji as validation and gap-filling source
3. Accept that some stocks may have <10 years if they weren't trading earlier
4. Consider manual data acquisition for critical missing periods

### For Recent Data (Last 5 Years)

1. Either Yahoo Finance or Shioaji is sufficient
2. Shioaji may provide slightly better data quality for Taiwan stocks
3. Consider using Shioaji as primary for recent data strategies

### For Real-Time Trading

1. **Use Shioaji exclusively** for real-time market data
2. Yahoo Finance is not suitable for real-time data
3. TWSE can be used as backup real-time source

## Maintenance Notes

- Review Yahoo Finance reliability monthly
- Monitor for any future rate limiting (unlikely based on current testing)
- Update this document if Shioaji extends historical data range
- Consider adding data quality metrics dashboard

## References

1. [Shioaji Official Documentation - Historical Data](https://sinotrade.github.io/tutor/market_data/historical/)
2. Production test results: `/tmp/yahoo_finance_rate_limit_test.json`
3. [yfinance library documentation](https://github.com/ranaroussi/yfinance)

## Appendix: Code References

### Date Range Passing Fix (2025-12-27)

**Issue**: Shioaji's `fetch_ohlcv` was calculating dates from "today" instead of using explicit start/end dates from Java.

**Fix**: Modified to accept `start_date` and `end_date` parameters:

```python
# python/app/services/shioaji_service.py
def fetch_ohlcv(self, stock_code: str, start_date=None, end_date=None, days: int = None):
    # Prefer explicit start_date/end_date over days parameter
    if start_date and end_date:
        start = start_date
        end = end_date
    elif days:
        end = datetime.now()
        start = end - timedelta(days=days)
```

**Impact**: Enabled proper multi-year historical downloads spanning 2018-2025.
