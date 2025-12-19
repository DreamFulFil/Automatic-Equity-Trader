#!/bin/bash
#
# Run All Operational Tasks
#
# This script executes:
# 1. Historical data population (2 years)
# 2. Combinatorial backtests (all strategies × all stocks)
#
# Usage:
#   ./scripts/operational/run_all_operational_tasks.sh <jasypt-password>
#

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

if [ -z "$1" ]; then
    echo -e "${RED}❌ Error: Jasypt password required${NC}"
    echo "Usage: $0 <jasypt-password>"
    exit 1
fi

JASYPT_PASSWORD="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║           Operational Tasks Runner - Full Pipeline            ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "📂 Project: $PROJECT_ROOT"
echo "⏰ Started: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "📊 Task 1: Historical Data Population"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Set environment variables
export JASYPT_PASSWORD="$JASYPT_PASSWORD"
export POSTGRES_DB="${POSTGRES_DB:-auto_equity_trader}"
export POSTGRES_USER="${POSTGRES_USER:-dreamer}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD}"

# Ensure PostgreSQL is running
if ! docker ps | grep -q psql; then
    echo -e "${YELLOW}⚠️  PostgreSQL container not running. Starting...${NC}"
    docker start psql || {
        echo -e "${RED}❌ Failed to start PostgreSQL${NC}"
        exit 1
    }
    sleep 3
fi

# Task 1: Populate historical data
cd "$PROJECT_ROOT"
python3 scripts/operational/populate_historical_data.py \
    --jasypt-password "$JASYPT_PASSWORD" \
    --days 730

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}✅ Task 1 complete: Historical data populated${NC}\n"
else
    echo -e "\n${RED}❌ Task 1 failed: Historical data population${NC}\n"
    exit 1
fi

echo "═══════════════════════════════════════════════════════════════"
echo "📊 Task 2: Combinatorial Backtests"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Check if Java application is running
if ! curl -s http://localhost:16350/actuator/health > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Java application not running. Starting...${NC}\n"
    
    # Start Java application in background
    ./start-auto-trader.fish "$JASYPT_PASSWORD" &
    JAVA_PID=$!
    
    echo "⏳ Waiting for application to start..."
    sleep 30
    
    # Wait for health check
    for i in {1..30}; do
        if curl -s http://localhost:16350/actuator/health > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Application started${NC}\n"
            break
        fi
        echo -n "."
        sleep 2
    done
else
    echo -e "${GREEN}✅ Java application already running${NC}\n"
    JAVA_PID=""
fi

# Task 2: Run combinatorial backtests
python3 scripts/operational/run_combinatorial_backtests.py \
    --port 16350 \
    --capital 80000 \
    --days 730

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}✅ Task 2 complete: Combinatorial backtests finished${NC}\n"
else
    echo -e "\n${RED}❌ Task 2 failed: Combinatorial backtests${NC}\n"
    [ -n "$JAVA_PID" ] && kill $JAVA_PID
    exit 1
fi

echo "═══════════════════════════════════════════════════════════════"
echo "🎉 All Operational Tasks Complete!"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "✅ Historical data: PostgreSQL market_data table"
echo "✅ Backtest results: PostgreSQL strategy_stock_mapping table"
echo ""
echo "📊 Next steps:"
echo "   1. Use /selectstrategy in Telegram to auto-select best strategy"
echo "   2. Review backtest results in strategy_stock_mapping table"
echo "   3. Start live trading when ready"
echo ""
echo "⏰ Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Cleanup: Stop Java if we started it
if [ -n "$JAVA_PID" ]; then
    echo "🛑 Stopping Java application..."
    kill $JAVA_PID
    wait $JAVA_PID 2>/dev/null
    echo "✅ Java application stopped"
fi
