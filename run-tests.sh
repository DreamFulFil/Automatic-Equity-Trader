#!/usr/bin/env bash
###############################################################################
# Automatic-Equity-Trader - Complete Test Suite Runner
#
# Usage: ./run-tests.sh [--unit|--integration|--full] <jasypt-password>
#
# Execution Tiers:
#   --unit        : Fast unit tests only (no containers/external services)
#   --integration : Unit + Integration tests (mocked & container-based)
#   --full        : Unit + Integration + E2E (default)
#
# Features:
#   - ANSI color-coded status (Green=PASS, Red=FAIL, Yellow=RUNNING)
#   - Real-time progress bar with percentage tracking
#   - Tiered execution for development workflow optimization
###############################################################################

set -e

# ANSI Color Codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

# Progress bar characters
PROGRESS_FILLED='█'
PROGRESS_EMPTY='░'
PROGRESS_WIDTH=40

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

###############################################################################
# Parse Arguments
###############################################################################
TEST_TIER="full"
JASYPT_PASSWORD=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --unit)
            TEST_TIER="unit"
            shift
            ;;
        --integration)
            TEST_TIER="integration"
            shift
            ;;
        --full)
            TEST_TIER="full"
            shift
            ;;
        -h|--help)
            echo "Usage: ./run-tests.sh [--unit|--integration|--full] <jasypt-password>"
            echo ""
            echo "Execution Tiers:"
            echo "  --unit        Fast unit tests only (no containers/external services)"
            echo "  --integration Unit + Integration tests (mocked & container-based)"
            echo "  --full        Unit + Integration + E2E (default)"
            echo ""
            exit 0
            ;;
        *)
            if [ -z "$JASYPT_PASSWORD" ]; then
                JASYPT_PASSWORD="$1"
            fi
            shift
            ;;
    esac
done

if [ -z "$JASYPT_PASSWORD" ]; then
    echo -e "${RED}Error: Missing Jasypt password${NC}"
    echo "Usage: ./run-tests.sh [--unit|--integration|--full] <jasypt-password>"
    exit 1
fi

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

# Test counts for progress tracking
TOTAL_PHASES=0
COMPLETED_PHASES=0
CURRENT_PHASE=""

###############################################################################
# Progress Bar Functions
###############################################################################

draw_progress_bar() {
    local current=$1
    local total=$2
    local phase_name=$3
    local status=$4  # RUNNING, PASSED, FAILED
    
    if [ $total -eq 0 ]; then
        local percent=0
    else
        local percent=$((current * 100 / total))
    fi
    
    local filled=$((current * PROGRESS_WIDTH / total))
    local empty=$((PROGRESS_WIDTH - filled))
    
    local bar=""
    for ((i=0; i<filled; i++)); do bar+="$PROGRESS_FILLED"; done
    for ((i=0; i<empty; i++)); do bar+="$PROGRESS_EMPTY"; done
    
    local status_color=""
    local status_icon=""
    case $status in
        RUNNING)
            status_color="$YELLOW"
            status_icon="⏳"
            ;;
        PASSED)
            status_color="$GREEN"
            status_icon="✅"
            ;;
        FAILED)
            status_color="$RED"
            status_icon="❌"
            ;;
    esac
    
    printf "\r${CYAN}[${bar}]${NC} ${BOLD}%3d%%${NC} ${status_color}${status_icon} %s${NC}    " "$percent" "$phase_name"
}

print_phase_header() {
    local phase_num=$1
    local phase_name=$2
    local total=$3
    
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}📝 Phase ${phase_num}/${total}: ${phase_name}${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_phase_result() {
    local result=$1
    local summary=$2
    local duration=$3
    
    if [ "$result" = "PASSED" ]; then
        echo -e "${GREEN}✅ PASSED${NC} - ${summary}"
    else
        echo -e "${RED}❌ FAILED${NC} - ${summary}"
    fi
    echo -e "${DIM}⏱️  Duration: ${duration}s${NC}"
}

set_phase_count() {
    case $TEST_TIER in
        unit)
            TOTAL_PHASES=2  # Java Unit + Python Unit
            ;;
        integration)
            TOTAL_PHASES=5  # Unit tests + System startup + Integration tests
            ;;
        full)
            TOTAL_PHASES=6  # Unit + Integration + E2E + System startup
            ;;
    esac
}

###############################################################################
# Header Display
###############################################################################

set_phase_count

# Convert test tier to uppercase for display
TEST_TIER_UPPER=$(echo "$TEST_TIER" | tr '[:lower:]' '[:upper:]')

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         Automatic-Equity-Trader - Test Suite Runner           ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BOLD}Project:${NC}    ${SCRIPT_DIR}"
echo -e "${BOLD}Started:${NC}    $(date '+%Y-%m-%d %H:%M:%S')"
echo -e "${BOLD}Test Tier:${NC}  ${MAGENTA}${TEST_TIER_UPPER}${NC}"
echo -e "${BOLD}Phases:${NC}     ${TOTAL_PHASES}"
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
    pgrep -f "java.*auto-equity-trader.jar" > /dev/null 2>&1
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
    
    # Kill any existing bridge on port 8888
    if lsof -i :8888 -t >/dev/null 2>&1; then
        echo "Killing existing process on port 8888..."
        kill -9 $(lsof -i :8888 -t) 2>/dev/null || true
        sleep 2
    fi
    
    cd "$SCRIPT_DIR/python"
    mkdir -p ../logs/python
    JASYPT_PASSWORD="$JASYPT_PASSWORD" venv/bin/python bridge.py > ../logs/python/shioaji.log 2>&1 &
    BRIDGE_PID=$!
    cd "$SCRIPT_DIR"
    
    for i in {1..30}; do
        check_bridge && echo -e "${GREEN}✅ Bridge ready (PID: $BRIDGE_PID)${NC}" && return 0
        sleep 1
    done
    
    echo -e "${RED}❌ Bridge failed to start${NC}"
    tail -50 logs/python/shioaji.log
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
    
    JASYPT_PASSWORD="$JASYPT_PASSWORD" $JAVA_CMD -jar target/auto-equity-trader.jar \
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
        echo -e "${YELLOW}🛑 Graceful Shutdown (Telegram messages)${NC}"
    fi
    
    # Trigger daily summary and shutdown via REST API (SKIP IN CI)
    if [ "$CI" != "true" ] && pgrep -f "java.*auto-equity-trader.jar" >/dev/null 2>&1; then
        echo "Triggering autoFlatten via REST API..."
        curl -s -X POST http://localhost:16350/api/shutdown >/dev/null 2>&1 || true
        sleep 25  # Wait for autoFlatten to complete and app to exit gracefully
    fi
    
    # Don't force kill - let it exit gracefully
    # Only kill if it's still running after 25 seconds
    if [ -n "$BOT_PID" ] && kill -0 $BOT_PID 2>/dev/null; then
        echo "Java bot still running after 25s, force stopping..."
        kill -9 $BOT_PID 2>/dev/null || true
    elif pgrep -f "java.*auto-equity-trader.jar" >/dev/null 2>&1; then
        # Fallback: find and kill by pattern if PID not available
        echo "Finding bot process by pattern..."
        BOT_FALLBACK_PID=$(pgrep -f "java.*auto-equity-trader.jar" | head -1)
        if [ -n "$BOT_FALLBACK_PID" ]; then
            kill -9 $BOT_FALLBACK_PID 2>/dev/null || true
        fi
    fi
    
    # Shutdown bridge
    if [ -n "$BRIDGE_PID" ] && kill -0 $BRIDGE_PID 2>/dev/null; then
        echo "Shutting down Python bridge..."
        curl -s -X POST http://localhost:8888/shutdown >/dev/null 2>&1 || true
        sleep 2
        if kill -0 $BRIDGE_PID 2>/dev/null; then
            kill -TERM $BRIDGE_PID 2>/dev/null || true
            sleep 2
        fi
        if kill -0 $BRIDGE_PID 2>/dev/null; then
            kill -9 $BRIDGE_PID 2>/dev/null || true
        fi
    fi
    
    # Shutdown Ollama
    if [ -n "$OLLAMA_PID" ] && kill -0 $OLLAMA_PID 2>/dev/null; then
        echo "Shutting down Ollama..."
        ollama stop llama3.1:8b-instruct-q5_K_M 2>/dev/null || true
        sleep 10
        if kill -0 $OLLAMA_PID 2>/dev/null; then
            kill -9 $OLLAMA_PID 2>/dev/null || true
        fi
    fi
    
    echo -e "${GREEN}✅ All processes stopped${NC}"
}

trap shutdown_gracefully EXIT

###############################################################################
# Phase 1: Java Unit Tests
###############################################################################

COMPLETED_PHASES=0
print_phase_header 1 "Java Unit Tests" $TOTAL_PHASES
draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Java Unit Tests" "RUNNING"

JAVA_UNIT_START=$(date +%s)
JAVA_UNIT_OUTPUT=$($MVN_CMD test -DexcludedGroups=integration -Dspring.profiles.active=ci 2>&1) || true
JAVA_UNIT_END=$(date +%s)
JAVA_UNIT_DURATION=$((JAVA_UNIT_END - JAVA_UNIT_START))
JAVA_UNIT_SUMMARY=$(echo "$JAVA_UNIT_OUTPUT" | grep -E "Tests run:" | tail -1)

if echo "$JAVA_UNIT_OUTPUT" | grep -q "BUILD SUCCESS"; then
    JAVA_UNIT_RESULT="PASSED"
    COMPLETED_PHASES=$((COMPLETED_PHASES + 1))
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Java Unit Tests" "PASSED"
else
    JAVA_UNIT_RESULT="FAILED"
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Java Unit Tests" "FAILED"
fi
echo ""
if [ "$JAVA_UNIT_RESULT" = "FAILED" ]; then
    echo "Java unit test output:"
    echo "$JAVA_UNIT_OUTPUT"
fi
print_phase_result "$JAVA_UNIT_RESULT" "$JAVA_UNIT_SUMMARY" "$JAVA_UNIT_DURATION"

###############################################################################
# Phase 2: Python Unit Tests
###############################################################################

print_phase_header 2 "Python Unit Tests" $TOTAL_PHASES
draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Python Unit Tests" "RUNNING"

# Ensure venv is ready and uses python3.12
PYTHON_BIN="python3.12"
if ! command -v $PYTHON_BIN >/dev/null 2>&1; then
    echo -e "${RED}Error: python3.12 is not installed. Please install Python 3.12.${NC}"
    exit 1
fi

VENV_OK=false
if [ -d "python/venv" ]; then
    VENV_PY=$(python/venv/bin/python -c 'import sys; print(f"python{sys.version_info.major}.{sys.version_info.minor}")' 2>/dev/null || echo "broken")
    PYTEST_OK=true
    if [ -f "python/venv/bin/pytest" ]; then
        if ! python/venv/bin/pytest --version >/dev/null 2>&1; then
            PYTEST_OK=false
        fi
    else
        PYTEST_OK=false
    fi
    if [ "$VENV_PY" = "python3.12" ] && [ "$PYTEST_OK" = true ]; then
        VENV_OK=true
    else
        echo "Removing old venv (wrong Python version or broken pytest)..."
        rm -rf python/venv
    fi
fi

if [ "$VENV_OK" != true ]; then
    echo "Creating Python venv with python3.12..."
    $PYTHON_BIN -m venv python/venv
    python/venv/bin/pip install -q --upgrade pip
    python/venv/bin/pip install -q -r python/requirements.txt
    python/venv/bin/pip install pytest
    VENV_OK=true
fi

PYTHON_UNIT_START=$(date +%s)
PYTHON_UNIT_OUTPUT=$(JASYPT_PASSWORD="$JASYPT_PASSWORD" python/venv/bin/pytest \
    python/tests/test_bridge.py \
    python/tests/test_contract.py \
    python/tests/test_shioaji_simulation.py \
    python/tests/test_shioaji_api.py \
    python/tests/test_edge_cases.py \
    -v 2>&1) || true
PYTHON_UNIT_END=$(date +%s)
PYTHON_UNIT_DURATION=$((PYTHON_UNIT_END - PYTHON_UNIT_START))
PYTHON_UNIT_SUMMARY=$(echo "$PYTHON_UNIT_OUTPUT" | grep -E "[0-9]+ passed" | tail -1)

if echo "$PYTHON_UNIT_OUTPUT" | grep -qE "passed"; then
    PYTHON_UNIT_RESULT="PASSED"
    COMPLETED_PHASES=$((COMPLETED_PHASES + 1))
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Python Unit Tests" "PASSED"
else
    PYTHON_UNIT_RESULT="FAILED"
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Python Unit Tests" "FAILED"
fi
echo ""
if [ "$PYTHON_UNIT_RESULT" = "FAILED" ]; then
    echo "Python unit test output:"
    echo "$PYTHON_UNIT_OUTPUT"
fi
print_phase_result "$PYTHON_UNIT_RESULT" "$PYTHON_UNIT_SUMMARY" "$PYTHON_UNIT_DURATION"

###############################################################################
# Exit early if --unit tier
###############################################################################
if [ "$TEST_TIER" = "unit" ]; then
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}📊 Unit Test Tier Complete${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    # Skip directly to summary
    JAVA_INT_RESULT="SKIPPED"
    JAVA_INT_SUMMARY="Skipped (--unit tier)"
    PYTHON_INT_RESULT="SKIPPED"
    PYTHON_INT_SUMMARY="Skipped (--unit tier)"
    E2E_RESULT="SKIPPED"
    E2E_SUMMARY="Skipped (--unit tier)"
    
    # Jump to summary section
    goto_summary=true
fi

###############################################################################
# Phase 3: Start Full System (integration and full tiers only)
###############################################################################

if [ "$TEST_TIER" != "unit" ]; then
    print_phase_header 3 "System Startup" $TOTAL_PHASES
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "System Startup" "RUNNING"
    
    echo "Earnings blackout data is now managed by EarningsBlackoutService (DB-backed)."
    echo "Use /admin/earnings-blackout/seed to migrate legacy JSON if needed."
    
    start_ollama || exit 1
    start_bridge || exit 1
    start_bot || exit 1
    
    COMPLETED_PHASES=$((COMPLETED_PHASES + 1))
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "System Startup" "PASSED"
    echo ""
    echo -e "${GREEN}✅ Full system running${NC}"
fi

###############################################################################
# Phase 4: Java Integration Tests
###############################################################################

if [ "$TEST_TIER" != "unit" ]; then
    print_phase_header 4 "Java Integration Tests" $TOTAL_PHASES
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Java Integration Tests" "RUNNING"
    
    JAVA_INT_START=$(date +%s)
    # Skip unit tests (surefire) but run integration tests (failsafe)
    JAVA_INT_OUTPUT=$(BRIDGE_URL=http://localhost:8888 $MVN_CMD verify -DskipTests=true -DskipITs=false -Dspring.profiles.active=ci 2>&1) || true
    JAVA_INT_END=$(date +%s)
    JAVA_INT_DURATION=$((JAVA_INT_END - JAVA_INT_START))
    JAVA_INT_SUMMARY=$(echo "$JAVA_INT_OUTPUT" | grep -E "Tests run:" | tail -1)
    
    if echo "$JAVA_INT_OUTPUT" | grep -q "BUILD SUCCESS"; then
        JAVA_INT_RESULT="PASSED"
        COMPLETED_PHASES=$((COMPLETED_PHASES + 1))
        draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Java Integration Tests" "PASSED"
    else
        JAVA_INT_RESULT="FAILED"
        draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Java Integration Tests" "FAILED"
    fi
    echo ""
    if [ "$JAVA_INT_RESULT" = "FAILED" ]; then
        echo "Java integration test output:"
        echo "$JAVA_INT_OUTPUT"
    fi
    print_phase_result "$JAVA_INT_RESULT" "$JAVA_INT_SUMMARY" "$JAVA_INT_DURATION"
fi

###############################################################################
# Phase 5: Python Integration Tests (integration and full tiers only)
###############################################################################

if [ "$TEST_TIER" != "unit" ]; then
    print_phase_header 5 "Python Integration Tests" $TOTAL_PHASES
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Python Integration Tests" "RUNNING"
    
    PYTHON_INT_START=$(date +%s)
    PYTHON_INT_OUTPUT=$(BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest \
        python/tests/test_integration.py -v 2>&1) || true
    PYTHON_INT_END=$(date +%s)
    PYTHON_INT_DURATION=$((PYTHON_INT_END - PYTHON_INT_START))
    
    PYTHON_INT_SUMMARY=$(echo "$PYTHON_INT_OUTPUT" | grep -E "[0-9]+ passed" | tail -1)
    
    if echo "$PYTHON_INT_OUTPUT" | grep -qE "passed"; then
        PYTHON_INT_RESULT="PASSED"
        COMPLETED_PHASES=$((COMPLETED_PHASES + 1))
        draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Python Integration Tests" "PASSED"
    else
        PYTHON_INT_RESULT="FAILED"
        draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Python Integration Tests" "FAILED"
    fi
    echo ""
    if [ "$PYTHON_INT_RESULT" = "FAILED" ]; then
        echo "Python integration test output:"
        echo "$PYTHON_INT_OUTPUT"
    fi
    print_phase_result "$PYTHON_INT_RESULT" "$PYTHON_INT_SUMMARY" "$PYTHON_INT_DURATION"
fi

###############################################################################
# Exit early if --integration tier
###############################################################################
if [ "$TEST_TIER" = "integration" ]; then
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}📊 Integration Test Tier Complete${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    E2E_RESULT="SKIPPED"
    E2E_SUMMARY="Skipped (--integration tier)"
fi

###############################################################################
# Phase 6: E2E Tests (full tier only)
###############################################################################

if [ "$TEST_TIER" = "full" ]; then
    print_phase_header 6 "E2E Tests" $TOTAL_PHASES
    draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "E2E Tests" "RUNNING"
    
    E2E_START=$(date +%s)
    E2E_OUTPUT=$(BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest \
        tests/e2e/test_full_session.py -v 2>&1) || true
    E2E_EXIT_CODE=$?
    E2E_END=$(date +%s)
    E2E_DURATION=$((E2E_END - E2E_START))
    
    E2E_SUMMARY=$(echo "$E2E_OUTPUT" | grep -E "[0-9]+ passed" | tail -1)
    
    if [ $E2E_EXIT_CODE -eq 0 ] && echo "$E2E_OUTPUT" | grep -qE "passed" && ! echo "$E2E_OUTPUT" | grep -qE "failed|error"; then
        E2E_RESULT="PASSED"
        COMPLETED_PHASES=$((COMPLETED_PHASES + 1))
        draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "E2E Tests" "PASSED"
    else
        E2E_RESULT="FAILED"
        draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "E2E Tests" "FAILED"
    fi
    echo ""
    if [ "$E2E_RESULT" = "FAILED" ]; then
        echo "E2E test output:"
        echo "$E2E_OUTPUT"
    fi
    print_phase_result "$E2E_RESULT" "$E2E_SUMMARY" "$E2E_DURATION"
fi

###############################################################################
# Summary Table - Enhanced with color-coded results
###############################################################################

echo ""
echo -e "${BOLD}${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║                    TEST RESULTS SUMMARY                       ║${NC}"
echo -e "${BOLD}${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Helper: parse JUnit-style summary line like: "Tests run: 240, Failures: 0, Errors: 0, Skipped: 0"
parse_java_counts() {
    local s="$1"
    local tests failures errors skipped
    tests=$(echo "$s" | grep -oE 'Tests run: *[0-9]+' | grep -oE '[0-9]+' || echo "0")
    failures=$(echo "$s" | grep -oE 'Failures: *[0-9]+' | grep -oE '[0-9]+' || echo "0")
    errors=$(echo "$s" | grep -oE 'Errors: *[0-9]+' | grep -oE '[0-9]+' || echo "0")
    skipped=$(echo "$s" | grep -oE 'Skipped: *[0-9]+' | grep -oE '[0-9]+' || echo "0")
    echo "$tests $failures $errors $skipped"
}

# Helper: parse pytest-style summary lines like: "65 passed in 1.56s", or "16 passed, 1 failed"
parse_py_counts() {
    local s="$1"
    local passed failed skipped total
    passed=$(echo "$s" | grep -oE '[0-9]+ passed' | grep -oE '[0-9]+' || echo "0")
    failed=$(echo "$s" | grep -oE '[0-9]+ failed' | grep -oE '[0-9]+' || echo "0")
    skipped=$(echo "$s" | grep -oE '[0-9]+ skipped' | grep -oE '[0-9]+' || echo "0")
    total=$((passed + failed + skipped))
    echo "$total $failed 0 $skipped"
}

# Format result with color
format_result() {
    local result="$1"
    case "$result" in
        PASSED)  echo -e "${GREEN}PASSED${NC}" ;;
        FAILED)  echo -e "${RED}FAILED${NC}" ;;
        SKIPPED) echo -e "${DIM}SKIPPED${NC}" ;;
        *)       echo "$result" ;;
    esac
}

# Extract counts for each suite
read JAVA_UNIT_TESTS JAVA_UNIT_FAILURES JAVA_UNIT_ERRORS JAVA_UNIT_SKIPPED < <(parse_java_counts "$JAVA_UNIT_SUMMARY")
read PYTHON_UNIT_TESTS PYTHON_UNIT_FAILURES PYTHON_UNIT_ERRORS PYTHON_UNIT_SKIPPED < <(parse_py_counts "$PYTHON_UNIT_SUMMARY")

if [ "$TEST_TIER" != "unit" ]; then
    read JAVA_INT_TESTS JAVA_INT_FAILURES JAVA_INT_ERRORS JAVA_INT_SKIPPED < <(parse_java_counts "$JAVA_INT_SUMMARY")
    read PYTHON_INT_TESTS PYTHON_INT_FAILURES PYTHON_INT_ERRORS PYTHON_INT_SKIPPED < <(parse_py_counts "$PYTHON_INT_SUMMARY")
else
    JAVA_INT_TESTS="-"; JAVA_INT_FAILURES="-"; JAVA_INT_ERRORS="-"; JAVA_INT_SKIPPED="-"
    PYTHON_INT_TESTS="-"; PYTHON_INT_FAILURES="-"; PYTHON_INT_ERRORS="-"; PYTHON_INT_SKIPPED="-"
fi

if [ "$TEST_TIER" = "full" ]; then
    read E2E_TESTS E2E_FAILURES E2E_ERRORS E2E_SKIPPED < <(parse_py_counts "$E2E_SUMMARY")
else
    E2E_TESTS="-"; E2E_FAILURES="-"; E2E_ERRORS="-"; E2E_SKIPPED="-"
fi

# Table layout
sep="+-------------------------+---------+-----------+----------+--------+---------+"
printf "%s\n" "$sep"
printf "| %-23s | %-7s | %-9s | %-8s | %-6s | %-7s |\n" "Test Suite" "Result" "Tests run" "Failures" "Errors" "Skipped"
printf "%s\n" "$sep"
printf "| %-23s | %-7s | %9s | %8s | %6s | %7s |\n" "Java Unit Tests" "$JAVA_UNIT_RESULT" "$JAVA_UNIT_TESTS" "$JAVA_UNIT_FAILURES" "$JAVA_UNIT_ERRORS" "$JAVA_UNIT_SKIPPED"
printf "| %-23s | %-7s | %9s | %8s | %6s | %7s |\n" "Python Unit Tests" "$PYTHON_UNIT_RESULT" "$PYTHON_UNIT_TESTS" "$PYTHON_UNIT_FAILURES" "$PYTHON_UNIT_ERRORS" "$PYTHON_UNIT_SKIPPED"
printf "| %-23s | %-7s | %9s | %8s | %6s | %7s |\n" "Java Integration" "$JAVA_INT_RESULT" "$JAVA_INT_TESTS" "$JAVA_INT_FAILURES" "$JAVA_INT_ERRORS" "$JAVA_INT_SKIPPED"
printf "| %-23s | %-7s | %9s | %8s | %6s | %7s |\n" "Python Integration" "$PYTHON_INT_RESULT" "$PYTHON_INT_TESTS" "$PYTHON_INT_FAILURES" "$PYTHON_INT_ERRORS" "$PYTHON_INT_SKIPPED"
printf "| %-23s | %-7s | %9s | %8s | %6s | %7s |\n" "E2E Tests" "$E2E_RESULT" "$E2E_TESTS" "$E2E_FAILURES" "$E2E_ERRORS" "$E2E_SKIPPED"
printf "%s\n" "$sep"

# Print durations summary
echo ""
echo -e "${DIM}Execution times: Java Unit: ${JAVA_UNIT_DURATION}s | Python Unit: ${PYTHON_UNIT_DURATION}s${NC}"
echo -e "${DIM}Test Tier: ${TEST_TIER_UPPER}${NC}"
echo ""

# Determine final result based on tier
ALL_PASSED=true
case $TEST_TIER in
    unit)
        for result in "$JAVA_UNIT_RESULT" "$PYTHON_UNIT_RESULT"; do
            if [ "$result" != "PASSED" ]; then
                ALL_PASSED=false
                break
            fi
        done
        ;;
    integration)
        for result in "$JAVA_UNIT_RESULT" "$PYTHON_UNIT_RESULT" "$JAVA_INT_RESULT" "$PYTHON_INT_RESULT"; do
            if [ "$result" != "PASSED" ] && [ "$result" != "SKIPPED" ]; then
                ALL_PASSED=false
                break
            fi
        done
        ;;
    full)
        for result in "$JAVA_UNIT_RESULT" "$PYTHON_UNIT_RESULT" "$JAVA_INT_RESULT" "$PYTHON_INT_RESULT" "$E2E_RESULT"; do
            if [ "$result" != "PASSED" ] && [ "$result" != "SKIPPED" ]; then
                ALL_PASSED=false
                break
            fi
        done
        ;;
esac

echo -e "Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Final progress bar
draw_progress_bar $COMPLETED_PHASES $TOTAL_PHASES "Complete" "$([ "$ALL_PASSED" = true ] && echo "PASSED" || echo "FAILED")"
echo ""
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
