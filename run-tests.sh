#!/usr/bin/env bash
###############################################################################
# MTXF Lunch Bot - Complete Test Suite Runner
#
# Usage: ./run-tests.sh <jasypt-password>
#
# Runs comprehensive test suite:
#   1. Java Unit Tests
#   2. Python Unit Tests
#   3. Full System Startup (Java + Python + Ollama)
#   4. Java Integration Tests
#   5. Python Integration Tests
#   6. E2E Tests
#   7. Graceful Shutdown
###############################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "$1" ]; then
    echo -e "${RED}Error: Missing Jasypt password${NC}"
    echo "Usage: ./run-tests.sh <jasypt-password>"
    exit 1
fi

JASYPT_PASSWORD="$1"
export JASYPT_PASSWORD

# Setup Java/Maven
if [ -z "$JAVA_HOME" ]; then
  if command -v jenv >/dev/null 2>&1; then
    export JAVA_HOME="$(jenv javahome 2>/dev/null || echo '')"
  fi
  if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo '')
  fi
fi

if command -v jenv >/dev/null 2>&1; then
  MVN_CMD="jenv exec mvn"
  JAVA_CMD="jenv exec java"
else
  MVN_CMD="mvn"
  JAVA_CMD="java"
fi

cd "$SCRIPT_DIR"

# PIDs for cleanup
OLLAMA_PID=""
BRIDGE_PID=""
BOT_PID=""

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         MTXF Lunch Bot - Complete Test Suite                  ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "Project: ${SCRIPT_DIR}"
echo -e "Started: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

###############################################################################
# Helper Functions
###############################################################################

check_ollama() {
    curl -s http://localhost:11434/api/tags > /dev/null 2>&1
}

check_bridge() {
    curl -s http://localhost:8888/health > /dev/null 2>&1
}

check_bot() {
    pgrep -f "java.*mtxf-bot.*jar" > /dev/null 2>&1
}

start_ollama() {
    echo -e "${YELLOW}🤖 Starting Ollama...${NC}"
    if pgrep -f "ollama serve" >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Ollama already running${NC}"
        return 0
    fi
    
    nohup ollama serve > /tmp/ollama.log 2>&1 &
    OLLAMA_PID=$!
    sleep 3
    
    if ! ollama list | grep -q "llama3.1:8b-instruct-q5_K_M"; then
        echo "Pulling model..."
        ollama pull llama3.1:8b-instruct-q5_K_M
    fi
    
    for i in {1..30}; do
        check_ollama && echo -e "${GREEN}✅ Ollama ready (PID: $OLLAMA_PID)${NC}" && return 0
        sleep 1
    done
    
    echo -e "${RED}❌ Ollama failed to start${NC}"
    return 1
}

start_bridge() {
    echo -e "${YELLOW}🐍 Starting Python Bridge...${NC}"
    
    cd "$SCRIPT_DIR/python"
    if [ -x "venv/bin/python" ]; then
        JASYPT_PASSWORD="$JASYPT_PASSWORD" venv/bin/python bridge.py > /tmp/bridge.log 2>&1 &
    else
        JASYPT_PASSWORD="$JASYPT_PASSWORD" python3 bridge.py > /tmp/bridge.log 2>&1 &
    fi
    BRIDGE_PID=$!
    cd "$SCRIPT_DIR"
    
    for i in {1..30}; do
        check_bridge && echo -e "${GREEN}✅ Bridge ready (PID: $BRIDGE_PID)${NC}" && return 0
        sleep 1
    done
    
    echo -e "${RED}❌ Bridge failed to start${NC}"
    tail -50 /tmp/bridge.log
    return 1
}

start_bot() {
    echo -e "${YELLOW}☕ Starting Java Bot...${NC}"
    
    # Build JAR
    $MVN_CMD clean package -DskipTests -q
    
    cd "$SCRIPT_DIR"
    
    # Use CI profile to disable Telegram when in CI
    if [ "$CI" = "true" ]; then
        SPRING_PROFILE="--spring.profiles.active=ci"
        echo "Starting bot with CI profile (Telegram disabled)"
    else
        SPRING_PROFILE=""
    fi
    
    JASYPT_PASSWORD="$JASYPT_PASSWORD" $JAVA_CMD -jar target/mtxf-bot-*.jar \
        --jasypt.encryptor.password="$JASYPT_PASSWORD" $SPRING_PROFILE > /tmp/bot.log 2>&1 &
    BOT_PID=$!
    
    sleep 5
    if check_bot; then
        echo -e "${GREEN}✅ Bot ready (PID: $BOT_PID)${NC}"
        return 0
    fi
    
    echo -e "${RED}❌ Bot failed to start${NC}"
    tail -50 /tmp/bot.log
    return 1
}

shutdown_gracefully() {
    echo ""
    if [ "$CI" = "true" ]; then
        echo -e "${YELLOW}🛑 Graceful Shutdown (CI mode - no Telegram messages)${NC}"
    else
        echo -e "${YELLOW}🛑 Graceful Shutdown (Telegram messages e,f,g)${NC}"
    fi
    
    # Trigger daily summary and shutdown via REST API (SKIP IN CI)
    if [ "$CI" != "true" ] && pgrep -f "java.*mtxf-bot.*jar" >/dev/null 2>&1; then
        echo "Triggering autoFlatten via REST API..."
        curl -s -X POST http://localhost:16350/api/shutdown >/dev/null 2>&1 || true
        sleep 25  # Wait for autoFlatten to complete and app to exit gracefully
    fi
    
    # Don't force kill - let it exit gracefully
    # Only kill if it's still running after 25 seconds
    if pgrep -f "java.*mtxf-bot.*jar" >/dev/null 2>&1; then
        echo "Java bot still running after 25s, force stopping..."
        /bin/pkill -9 -f "java.*mtxf-bot.*jar" 2>/dev/null || true
    fi
    
    # Shutdown bridge
    if [ -n "$BRIDGE_PID" ] && kill -0 $BRIDGE_PID 2>/dev/null; then
        echo "Shutting down Python bridge..."
        curl -s -X POST http://localhost:8888/shutdown >/dev/null 2>&1 || true
        sleep 2
        if kill -0 $BRIDGE_PID 2>/dev/null; then
            /bin/kill -TERM $BRIDGE_PID 2>/dev/null || true
            sleep 2
        fi
        if kill -0 $BRIDGE_PID 2>/dev/null; then
            /bin/kill -9 $BRIDGE_PID 2>/dev/null || true
        fi
    fi
    
    # Shutdown Ollama
    if [ -n "$OLLAMA_PID" ] && kill -0 $OLLAMA_PID 2>/dev/null; then
        echo "Shutting down Ollama..."
        ollama stop llama3.1:8b-instruct-q5_K_M 2>/dev/null || true
        sleep 10
        if kill -0 $OLLAMA_PID 2>/dev/null; then
            /bin/kill -9 $OLLAMA_PID 2>/dev/null || true
        fi
    fi
    
    echo -e "${GREEN}✅ All processes stopped${NC}"
}

trap shutdown_gracefully EXIT

###############################################################################
# Phase 1: Java Unit Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📝 Phase 1: Java Unit Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

JAVA_UNIT_OUTPUT=$($MVN_CMD test -DexcludedGroups=integration -Dspring.profiles.active=ci 2>&1) || true
JAVA_UNIT_SUMMARY=$(echo "$JAVA_UNIT_OUTPUT" | grep -E "Tests run:" | tail -1)

if echo "$JAVA_UNIT_OUTPUT" | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✅ Java unit tests passed${NC}"
    JAVA_UNIT_RESULT="PASSED"
else
    echo -e "${RED}❌ Java unit tests failed${NC}"
    JAVA_UNIT_RESULT="FAILED"
fi
echo "   $JAVA_UNIT_SUMMARY"
echo ""

###############################################################################
# Phase 2: Python Unit Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🐍 Phase 2: Python Unit Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Ensure venv is ready
if [ ! -d "python/venv" ]; then
    echo "Creating Python venv..."
    python3 -m venv python/venv
    python/venv/bin/pip install -q --upgrade pip
    python/venv/bin/pip install -q -r python/requirements.txt
fi

PYTHON_UNIT_OUTPUT=$(JASYPT_PASSWORD="$JASYPT_PASSWORD" python/venv/bin/pytest \
    python/tests/test_bridge.py \
    python/tests/test_contract.py \
    python/tests/test_shioaji_simulation.py \
    python/tests/test_shioaji_api.py \
    -v 2>&1) || true

PYTHON_UNIT_SUMMARY=$(echo "$PYTHON_UNIT_OUTPUT" | grep -E "[0-9]+ passed" | tail -1)

if echo "$PYTHON_UNIT_OUTPUT" | grep -qE "passed"; then
    echo -e "${GREEN}✅ Python unit tests passed${NC}"
    PYTHON_UNIT_RESULT="PASSED"
else
    echo -e "${RED}❌ Python unit tests failed${NC}"
    PYTHON_UNIT_RESULT="FAILED"
fi
echo "   $PYTHON_UNIT_SUMMARY"
echo ""

###############################################################################
# Phase 3: Start Full System (with scraper - Telegram messages a-b, skipped in CI)
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🚀 Phase 3: Starting Full System${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Ensure earnings blackout file exists
mkdir -p "$SCRIPT_DIR/config"

# Run earnings scraper (Telegram messages a-b) - SKIP IN CI
if [ "$CI" = "true" ]; then
    echo "Skipping earnings scraper in CI (would send Telegram messages a-b)"
    # Create empty file to prevent errors
    echo "{}" > "$SCRIPT_DIR/config/earnings-blackout-dates.json"
else
    echo "Running earnings scraper (Telegram messages a-b)..."
    cd "$SCRIPT_DIR/python"
    if [ -x "venv/bin/python3" ]; then
        venv/bin/python3 bridge.py --scrape-earnings --jasypt-password "$JASYPT_PASSWORD"
    else
        python3 bridge.py --scrape-earnings --jasypt-password "$JASYPT_PASSWORD"
    fi
    cd "$SCRIPT_DIR"
fi
sleep 3

start_ollama || exit 1
start_bridge || exit 1
start_bot || exit 1

echo ""
echo -e "${GREEN}✅ Full system running${NC}"
echo ""

###############################################################################
# Phase 4: Java Integration Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔗 Phase 4: Java Integration Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

JAVA_INT_OUTPUT=$(BRIDGE_URL=http://localhost:8888 $MVN_CMD verify -DskipTests=false -Dspring.profiles.active=ci 2>&1) || true
JAVA_INT_SUMMARY=$(echo "$JAVA_INT_OUTPUT" | grep -E "Tests run:" | tail -1)

if echo "$JAVA_INT_OUTPUT" | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✅ Java integration tests passed${NC}"
    JAVA_INT_RESULT="PASSED"
else
    echo -e "${RED}❌ Java integration tests failed${NC}"
    JAVA_INT_RESULT="FAILED"
fi
echo "   $JAVA_INT_SUMMARY"
echo ""

###############################################################################
# Phase 5: Python Integration Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔗 Phase 5: Python Integration Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

PYTHON_INT_OUTPUT=$(BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest \
    python/tests/test_integration.py -v 2>&1) || true

PYTHON_INT_SUMMARY=$(echo "$PYTHON_INT_OUTPUT" | grep -E "[0-9]+ passed" | tail -1)

if echo "$PYTHON_INT_OUTPUT" | grep -qE "passed"; then
    echo -e "${GREEN}✅ Python integration tests passed${NC}"
    PYTHON_INT_RESULT="PASSED"
else
    echo -e "${RED}❌ Python integration tests failed${NC}"
    PYTHON_INT_RESULT="FAILED"
fi
echo "   $PYTHON_INT_SUMMARY"
echo ""

###############################################################################
# Phase 6: E2E Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🎯 Phase 6: E2E Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

E2E_OUTPUT=$(BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest \
    tests/e2e/test_full_session.py -v 2>&1)
E2E_EXIT_CODE=$?

E2E_SUMMARY=$(echo "$E2E_OUTPUT" | grep -E "[0-9]+ passed" | tail -1)

if [ $E2E_EXIT_CODE -eq 0 ] && echo "$E2E_OUTPUT" | grep -qE "passed" && ! echo "$E2E_OUTPUT" | grep -qE "failed|error"; then
    echo -e "${GREEN}✅ E2E tests passed${NC}"
    E2E_RESULT="PASSED"
else
    echo -e "${RED}❌ E2E tests failed${NC}"
    E2E_RESULT="FAILED"
fi
echo "   $E2E_SUMMARY"
echo ""

###############################################################################
# Phase 7: Graceful Shutdown (triggered by trap, Telegram messages e,f,g skipped in CI)
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 Test Summary${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "┌────────────────────────────────────────────┐"
echo -e "│ ${BLUE}Test Results${NC}                               │"
echo -e "├────────────────────────────────────────────┤"
printf "│ Java Unit Tests:       %-20s │\n" "$([ "$JAVA_UNIT_RESULT" = "PASSED" ] && echo -e "${GREEN}✅ PASSED${NC}" || echo -e "${RED}❌ FAILED${NC}")"
printf "│ Python Unit Tests:     %-20s │\n" "$([ "$PYTHON_UNIT_RESULT" = "PASSED" ] && echo -e "${GREEN}✅ PASSED${NC}" || echo -e "${RED}❌ FAILED${NC}")"
printf "│ Java Integration:      %-20s │\n" "$([ "$JAVA_INT_RESULT" = "PASSED" ] && echo -e "${GREEN}✅ PASSED${NC}" || echo -e "${RED}❌ FAILED${NC}")"
printf "│ Python Integration:    %-20s │\n" "$([ "$PYTHON_INT_RESULT" = "PASSED" ] && echo -e "${GREEN}✅ PASSED${NC}" || echo -e "${RED}❌ FAILED${NC}")"
printf "│ E2E Tests:             %-20s │\n" "$([ "$E2E_RESULT" = "PASSED" ] && echo -e "${GREEN}✅ PASSED${NC}" || echo -e "${RED}❌ FAILED${NC}")"
echo -e "└────────────────────────────────────────────┘"
echo ""

ALL_PASSED=true
for result in "$JAVA_UNIT_RESULT" "$PYTHON_UNIT_RESULT" "$JAVA_INT_RESULT" "$PYTHON_INT_RESULT" "$E2E_RESULT"; do
    if [ "$result" != "PASSED" ]; then
        ALL_PASSED=false
        break
    fi
done

echo -e "Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

if [ "$ALL_PASSED" = true ]; then
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  ✅ ALL TESTS PASSED - System ready for production           ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════╝${NC}"
    exit 0
else
    echo -e "${RED}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  ❌ TESTS FAILED - Fix issues before deploying                ║${NC}"
    echo -e "${RED}╚═══════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi
