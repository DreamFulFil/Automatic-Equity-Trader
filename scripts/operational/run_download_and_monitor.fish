#!/usr/bin/env fish
# Non-interactive runner for history download + monitoring
# Export JASYPT_PASSWORD and run as: fish scripts/operational/run_download_and_monitor.fish

# Requires JASYPT_PASSWORD exported
if not set -q JASYPT_PASSWORD
    echo "❌ MISSING: JASYPT_PASSWORD - please set it (e.g. 'set -x JASYPT_PASSWORD mysecret')";
    exit 1
end

cd (dirname (status --current-filename))
cd ..
cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
mkdir -p logs
set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)

# Kill Java if running
if pgrep -f "auto-equity-trader.jar" >/dev/null 2>&1
    echo "Killing existing Java process...";
    pkill -9 -f "auto-equity-trader.jar" || true;
    sleep 2;
end

# Kill Python bridge if running
if lsof -i :8888 >/dev/null 2>&1
    echo "Killing existing Python bridge on :8888...";
    lsof -i :8888 -t | xargs -r kill -9 || true;
    sleep 2;
end

# Build JAR if missing
if not test -f target/auto-equity-trader.jar
    echo "Building Java artifact..."
    jenv exec mvn -q package -DskipTests || { echo "❌ Maven build failed"; exit 1 }
end

# Start Java
echo "Starting Java (logs/java-$LOG_TS.log)..."
nohup jenv exec java -Djasypt.encryptor.password="$JASYPT_PASSWORD" -jar target/auto-equity-trader.jar > "logs/java-$LOG_TS.log" 2>&1 &
sleep 2

# Start Python bridge
if test -x python/venv/bin/uvicorn
    echo "Starting Python bridge (logs/bridge-$LOG_TS.log)..."
    cd python
    set -x TRADING_MODE stock
    ../python/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8888 > "../logs/bridge-$LOG_TS.log" 2>&1 &
    cd ..
else
    echo "❌ Python venv or uvicorn missing: run 'python/venv/bin/pip install -r python/requirements.txt'";
end

# Sleep and health check
echo "Sleeping 30s to allow services to boot..."; sleep 30

set -l java_code (curl -s -o /dev/null -w "%{http_code}" http://localhost:16350/actuator/health || echo "000")
set -l py_code (curl -s -o /dev/null -w "%{http_code}" http://localhost:8888/health || echo "000")

echo "Java health HTTP code: $java_code"
echo "Python bridge health HTTP code: $py_code"

if test "$java_code" -ne 200 -o "$py_code" -ne 200
    echo "❌ One or both services failed to respond. Tail last 200 lines for debugging:";
    tail -n 200 logs/java-*.log 2>/dev/null | sed -n '1,200p'
    tail -n 200 logs/bridge-*.log 2>/dev/null | sed -n '1,200p'
    echo "Stopping any started processes..."
    pkill -9 -f "auto-equity-trader.jar" || true
    lsof -i :8888 -t | xargs -r kill -9 || true
    exit 2
end

echo "✅ Both services responded; proceeding to invoke history download endpoint"

# Sample initial counts BEFORE triggering download
set -l prev_bar (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COALESCE(COUNT(*),0) FROM bar;" 2>/dev/null)
set -l prev_md (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COALESCE(COUNT(*),0) FROM market_data;" 2>/dev/null)
set -l prev_map (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COALESCE(COUNT(*),0) FROM strategy_stock_mapping;" 2>/dev/null)

echo "Pre-download counts: bar=$prev_bar market_data=$prev_md mapping=$prev_map"

# Trigger history download
set -l DL_TS (date -u +%Y%m%dT%H%M%SZ)
curl -s -X POST "http://localhost:16350/api/history/download?years=5" -o "logs/history-download-$DL_TS.json" || echo "Failed to POST /api/history/download?years=5"

echo "Saved history download trigger response to logs/history-download-$DL_TS.json"

# quick check: if Java logs already show completion, consider success and exit
sleep 3
if tail -n 200 logs/java-*.log 2>/dev/null | rg -q "Historical data download completed|Multi-stock download complete"; or true
    echo "Detected immediate completion in logs (download already completed). Leaving services running.";
    exit 0
end

# Begin monitoring
set -l checks 0
set -l max_checks 10
set -l sleep_secs 30

echo "Starting monitoring: bar=$prev_bar market_data=$prev_md mapping=$prev_map (checking every $sleep_secs s up to $max_checks times)"

while test $checks -lt $max_checks
    sleep $sleep_secs
    set -l checks (math $checks + 1)

    set -l cur_bar (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COALESCE(COUNT(*),0) FROM bar;" 2>/dev/null)
    set -l cur_md (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COALESCE(COUNT(*),0) FROM market_data;" 2>/dev/null)
    set -l cur_map (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COALESCE(COUNT(*),0) FROM strategy_stock_mapping;" 2>/dev/null)

    echo "Check #$checks — bar: $cur_bar (prev $prev_bar), market_data: $cur_md (prev $prev_md), mapping: $cur_map (prev $prev_map)"

    if test $cur_bar -gt $prev_bar -o $cur_md -gt $prev_md -o $cur_map -gt $prev_map
        echo "📥 Data is being inserted (progress observed on check #$checks)."
        set -l prev_bar $cur_bar; set -l prev_md $cur_md; set -l prev_map $cur_map
        if test $checks -ge $max_checks
            echo "✅ Completed $max_checks monitoring checks with observed inserts. Leaving services running."
            exit 0
        end
        continue
    else
        echo "⚠️ No new inserts detected on check #$checks — stopping services to prevent wasted runs."
        echo "Tailing last 200 lines of logs for debugging:";
        tail -n 200 logs/java-*.log 2>/dev/null | sed -n '1,200p'
        tail -n 200 logs/bridge-*.log 2>/dev/null | sed -n '1,200p'
        echo "Stopping Java and Python bridge..."
        pkill -9 -f "auto-equity-trader.jar" || true
        lsof -i :8888 -t | xargs -r kill -9 || true
        exit 3
    end
end

echo "Monitoring finished after $max_checks checks. Services left running."; exit 0
