# DATA OPERATIONS API

Complete guide for historical data population, backtesting, and strategy selection.

## Overview

The Data Operations API provides:
- **Historical Data Population**: Load market data for all stocks
- **Combinatorial Backtesting**: Test all strategies against all stocks
- **Strategy Auto-Selection**: Automatically select optimal strategy
- **Full Pipeline**: Run all operations in sequence

## Access Methods

### 1. REST API (Java)

**Base URL**: `http://localhost:16350/api/data-ops`

#### Populate Historical Data
```bash
curl -X POST "http://localhost:16350/api/data-ops/populate?days=730"
```

**Response**:
```json
{
  "status": "success",
  "message": "Historical data populated successfully",
  "total_records": 5220,
  "stocks": 10,
  "days": 730,
  "stock_details": {
    "2330.TW": 522,
    "2317.TW": 522,
    ...
  }
}
```

#### Run Combinatorial Backtests
```bash
curl -X POST "http://localhost:16350/api/data-ops/backtest?capital=80000&days=730"
```

**Response**:
```json
{
  "status": "success",
  "message": "Combinatorial backtests completed",
  "total_combinations": 500,
  "successful": 500,
  "failed": 0,
  "results": [
    {
      "symbol": "2330.TW",
      "strategies_tested": 50,
      "top_strategy": "Balance of Power",
      "top_sharpe": 5.16
    },
    ...
  ]
}
```

#### Auto-Select Best Strategy
```bash
curl -X POST "http://localhost:16350/api/data-ops/select-strategy?minSharpe=0.5&minReturn=10.0&minWinRate=50.0"
```

**Response**:
```json
{
  "status": "success",
  "message": "Strategy selection completed",
  "active_strategy": {
    "symbol": "1303.TW",
    "strategy": "Balance of Power",
    "sharpe": 5.16,
    "return": 68.95,
    "win_rate": 100.0,
    "max_drawdown": 0.0,
    "trades": 2
  },
  "shadow_mode": {
    "count": 10,
    "stocks": [...]
  }
}
```

#### Run Full Pipeline
```bash
curl -X POST "http://localhost:16350/api/data-ops/full-pipeline?days=730"
```

**Response**:
```json
{
  "status": "success",
  "started_at": "2025-12-19T09:00:00",
  "completed_at": "2025-12-19T09:25:00",
  "steps": [
    {"step": "populate_data", "result": {...}},
    {"step": "run_backtests", "result": {...}},
    {"step": "select_strategy", "result": {...}}
  ]
}
```

#### Get Data Status
```bash
curl "http://localhost:16350/api/data-ops/status"
```

**Response**:
```json
{
  "status": "success",
  "market_data_records": 5220,
  "backtest_results": 500,
  "shadow_mode_stocks": 10
}
```

### 2. Python Bridge API

**Base URL**: `http://localhost:8888/data`

#### Populate Data
```bash
curl -X POST "http://localhost:8888/data/populate" \
  -H "Content-Type: application/json" \
  -d '{"days": 730}'
```

#### Run Backtests
```bash
curl -X POST "http://localhost:8888/data/backtest" \
  -H "Content-Type: application/json" \
  -d '{"capital": 80000, "days": 730}'
```

#### Select Strategy
```bash
curl -X POST "http://localhost:8888/data/select-strategy" \
  -H "Content-Type: application/json" \
  -d '{"min_sharpe": 0.5, "min_return": 10.0, "min_win_rate": 50.0}'
```

#### Full Pipeline
```bash
curl -X POST "http://localhost:8888/data/full-pipeline" \
  -H "Content-Type: application/json" \
  -d '{"days": 730}'
```

### 3. Telegram Commands

All operations available via Telegram bot:

#### Populate Historical Data
```
/populate-data
/populate-data 730
```

**Response**:
```
✅ Historical Data Populated

📊 Total Records: 5220
📈 Stocks: 10
📅 Days: 730
```

#### Run Backtests
```
/run-backtests
```

**Response**:
```
🧪 Running combinatorial backtests... This will take 10-20 minutes.

✅ Backtests Complete

📊 Total Combinations: 500
✅ Successful: 500
❌ Failed: 0
```

#### Auto-Select Strategy
```
/select-best-strategy
```

**Response**:
```
✅ Strategy Selection Complete

🎯 *Active Strategy*
Symbol: 1303.TW
Strategy: Balance of Power
Sharpe: 5.16
Return: 68.95%
Win Rate: 100.00%

👻 *Shadow Mode*: 10 stocks configured
```

#### Run Full Pipeline
```
/full-pipeline
/full-pipeline 365
```

**Response**:
```
🚀 Running full data pipeline...

Steps:
1️⃣ Populate data (~5 min)
2️⃣ Run backtests (~15 min)
3️⃣ Select strategy (~1 min)

Total: ~20-25 minutes

✅ Full pipeline complete! Check status with /data-status
```

#### Check Data Status
```
/data-status
```

**Response**:
```
📊 *Data Status*

Historical Data: 5220 bars
Backtest Results: 500 combinations
Shadow Mode: 10 stocks
```

## Parameters

### Historical Data Population

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `days` | int | 730 | Days of historical data to generate |

### Combinatorial Backtesting

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `capital` | double | 80000 | Initial capital in TWD |
| `days` | int | 730 | Days of data to backtest |

### Strategy Selection

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minSharpe` | double | 0.5 | Minimum Sharpe ratio |
| `minReturn` | double | 10.0 | Minimum return percentage |
| `minWinRate` | double | 50.0 | Minimum win rate percentage |

## Error Handling

All endpoints return a consistent error format:

```json
{
  "status": "error",
  "message": "Detailed error message",
  "failed_at": "step_name"  // For pipeline
}
```

### Common Errors

**No Data Available**:
```json
{
  "status": "error",
  "message": "No strategies meet the criteria"
}
```

**Database Connection Failed**:
```json
{
  "status": "error",
  "message": "Failed to populate data: connection refused"
}
```

**Java Application Not Running**:
```json
{
  "status": "error",
  "message": "Failed to run backtests: Connection refused"
}
```

## Workflow

### Recommended Sequence

1. **Start Fresh**:
   ```bash
   # Clear existing data (optional)
   DELETE FROM market_data;
   DELETE FROM strategy_stock_mapping;
   DELETE FROM shadow_mode_stocks;
   ```

2. **Populate Data**:
   ```bash
   curl -X POST "http://localhost:16350/api/data-ops/populate?days=730"
   ```
   Duration: ~2-5 minutes

3. **Run Backtests**:
   ```bash
   curl -X POST "http://localhost:16350/api/data-ops/backtest"
   ```
   Duration: ~10-20 minutes

4. **Select Strategy**:
   ```bash
   curl -X POST "http://localhost:16350/api/data-ops/select-strategy"
   ```
   Duration: ~1 second

5. **Verify**:
   ```bash
   curl "http://localhost:16350/api/data-ops/status"
   ```

### Or Use Full Pipeline

```bash
curl -X POST "http://localhost:16350/api/data-ops/full-pipeline?days=730"
```

Duration: ~20-25 minutes total

## Database Schema

### market_data
- Stores OHLCV historical bars
- Primary key: (symbol, timestamp, timeframe)
- Indexed on symbol + timestamp

### strategy_stock_mapping
- Stores backtest results
- One row per strategy-stock combination
- Includes: sharpe_ratio, total_return_pct, win_rate_pct, max_drawdown_pct

### shadow_mode_stocks
- Stores top 10 strategies for paper trading
- Unique constraint on symbol
- Automatically populated by auto-selection

### active_strategy_config
- Stores the currently active strategy
- Single row (id = 1)
- Updated by auto-selection

## Integration Testing

Run the comprehensive integration test:

```bash
mvn test -Dtest=DataOperationsIntegrationTest
```

**Test Scenarios**:
1. Get initial data status (empty)
2. Populate historical data
3. Run combinatorial backtests
4. Auto-select best strategy
5. Verify updated data status
6. Test full pipeline
7. Error handling with no data

## Troubleshooting

### Python Bridge Not Responding

**Symptom**: `Connection refused` errors

**Solution**:
```bash
# Check if Python bridge is running
curl http://localhost:8888/health

# Restart if needed
./start-auto-trader.fish <jasypt-password>
```

### Java Application Not Running

**Symptom**: REST API returns 404

**Solution**:
```bash
# Check Java app status
curl http://localhost:16350/api/data-ops/status

# Start if needed
./start-auto-trader.fish <jasypt-password>
```

### Backtests Failing

**Symptom**: No backtest results or all failed

**Solution**:
1. Verify historical data exists:
   ```sql
   SELECT COUNT(*) FROM market_data;
   ```

2. Check logs for specific errors:
   ```bash
   tail -f logs/application.log
   ```

3. Ensure enough data points:
   - Minimum 50 days recommended
   - More days = better backtest quality

### No Strategies Meet Criteria

**Symptom**: "No strategies meet the criteria" error

**Solution**:
Lower the thresholds:
```bash
curl -X POST "http://localhost:16350/api/data-ops/select-strategy?minSharpe=0.1&minReturn=5.0&minWinRate=30.0"
```

## Performance

### Expected Durations

| Operation | Duration | Records |
|-----------|----------|---------|
| Populate Data (730 days) | 2-5 min | ~5,220 |
| Run Backtests (50 strategies × 10 stocks) | 10-20 min | 500 |
| Select Strategy | <1 sec | N/A |
| Full Pipeline | 20-25 min | N/A |

### Optimization Tips

1. **Use shorter periods for testing**:
   ```bash
   curl -X POST "http://localhost:16350/api/data-ops/populate?days=100"
   ```

2. **Run operations during off-hours** (no market impact)

3. **Monitor database size**:
   ```sql
   SELECT 
     pg_size_pretty(pg_total_relation_size('market_data')) as size,
     COUNT(*) as records 
   FROM market_data;
   ```

## Security

- All endpoints require Java application to be running
- No authentication on local endpoints (add reverse proxy for production)
- Database credentials managed via environment variables
- Jasypt encryption for sensitive configuration

## See Also

- [TESTING.md](TESTING.md) - Testing guide
- [BEGINNER_GUIDE.md](../usage/BEGINNER_GUIDE.md) - Getting started
- [AUTOMATION_FEATURES.md](../usage/AUTOMATION_FEATURES.md) - Scheduled tasks
