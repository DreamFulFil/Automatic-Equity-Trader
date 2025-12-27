#!/usr/bin/env bash
###############################################################################
# MTXF Lunch Bot - Complete Test Suite Runner
#
# Usage: ./run-tests.sh <jasypt-password>
#
# This script runs all tests:
# - Java unit tests (JUnit 5)
# - Java integration tests (requires Python bridge)
# - Python unit tests (pytest)
# - Python integration tests (requires Python bridge)
# - E2E tests (requires Python bridge + Ollama)
# - Fish shell tests
#
# Prerequisites:
# - Java 21
# - Maven
# - Python 3.10+ with venv
# - Ollama (for news veto tests)
###############################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
JAVA_UNIT_PASSED=0
JAVA_UNIT_FAILED=0
JAVA_UNIT_SKIPPED=0
JAVA_INT_PASSED=0
JAVA_INT_FAILED=0
JAVA_INT_SKIPPED=0
PYTHON_UNIT_PASSED=0
PYTHON_UNIT_FAILED=0
PYTHON_UNIT_SKIPPED=0
PYTHON_INT_PASSED=0
PYTHON_INT_FAILED=0
PYTHON_INT_SKIPPED=0
E2E_PASSED=0
E2E_FAILED=0
E2E_SKIPPED=0
FISH_PASSED=0
FISH_FAILED=0
FISH_SKIPPED=0

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check for required password argument
if [ -z "$1" ]; then
    echo -e "${RED}❌ Usage: ./run-tests.sh <jasypt-password>${NC}"
    echo "   The Jasypt password is required to decrypt credentials for integration tests."
    exit 1
fi

JASYPT_PASSWORD="$1"
export JASYPT_PASSWORD

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         MTXF Lunch Bot - Complete Test Suite                  ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "📁 Project directory: ${SCRIPT_DIR}"
echo -e "🕐 Started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

###############################################################################
# Helper Functions
###############################################################################

parse_maven_results() {
    local output="$1"
    # Extract from line like: "Tests run: 138, Failures: 0, Errors: 0, Skipped: 30"
    local summary_line=$(echo "$output" | grep "Tests run:" | tail -1)
    local passed=$(echo "$summary_line" | sed -E 's/.*Tests run: ([0-9]+).*/\1/')
    local failed=$(echo "$summary_line" | sed -E 's/.*Failures: ([0-9]+).*/\1/')
    local skipped=$(echo "$summary_line" | sed -E 's/.*Skipped: ([0-9]+).*/\1/')
    echo "${passed:-0} ${failed:-0} ${skipped:-0}"
}

parse_pytest_results() {
    local output="$1"
    # Extract from line like: "58 passed, 5 skipped in 0.47s" or "24 passed in 15.64s"
    local summary_line=$(echo "$output" | grep -E "[0-9]+ passed" | tail -1)
    local passed=$(echo "$summary_line" | sed -E 's/.*[^0-9]([0-9]+) passed.*/\1/' | grep -E '^[0-9]+$' || echo "0")
    local failed=$(echo "$summary_line" | sed -E 's/.*[^0-9]([0-9]+) failed.*/\1/' | grep -E '^[0-9]+$' || echo "0")
    local skipped=$(echo "$summary_line" | sed -E 's/.*[^0-9]([0-9]+) skipped.*/\1/' | grep -E '^[0-9]+$' || echo "0")
    # Handle case where pattern doesn't match
    if ! echo "$passed" | grep -qE '^[0-9]+$'; then passed=0; fi
    if ! echo "$failed" | grep -qE '^[0-9]+$'; then failed=0; fi
    if ! echo "$skipped" | grep -qE '^[0-9]+$'; then skipped=0; fi
    echo "${passed} ${failed} ${skipped}"
}

parse_fish_results() {
    local output="$1"
    # Extract from lines like "✅ Passed: 15", "❌ Failed: 0", "⏭️ Skipped: 0"
    local passed=$(echo "$output" | grep "Passed:" | sed -E 's/.*Passed: ([0-9]+).*/\1/' | tail -1)
    local failed=$(echo "$output" | grep "Failed:" | sed -E 's/.*Failed: ([0-9]+).*/\1/' | tail -1)
    local skipped=$(echo "$output" | grep "Skipped:" | sed -E 's/.*Skipped: ([0-9]+).*/\1/' | tail -1)
    echo "${passed:-0} ${failed:-0} ${skipped:-0}"
}

check_bridge() {
    curl -s http://localhost:8888/health > /dev/null 2>&1
    return $?
}

check_ollama() {
    curl -s http://localhost:11434/api/tags > /dev/null 2>&1
    return $?
}

start_ollama() {
    echo -e "${YELLOW}🦙 Starting Ollama...${NC}"
    ollama serve > /tmp/ollama.log 2>&1 &
    OLLAMA_PID=$!
    
    # Wait for Ollama to be ready
    local attempts=0
    while [ $attempts -lt 30 ]; do
        if check_ollama; then
            echo -e "${GREEN}✅ Ollama started (PID: $OLLAMA_PID)${NC}"
            return 0
        fi
        attempts=$((attempts + 1))
        sleep 1
    done
    
    echo -e "${RED}❌ Failed to start Ollama${NC}"
    return 1
}

stop_ollama() {
    if [ -n "$OLLAMA_PID" ]; then
        echo -e "${YELLOW}🛑 Stopping Ollama (PID: $OLLAMA_PID)...${NC}"
        kill $OLLAMA_PID 2>/dev/null || true
        wait $OLLAMA_PID 2>/dev/null || true
    fi
}

start_bridge() {
    echo -e "${YELLOW}🐍 Starting Python bridge...${NC}"
    cd "$SCRIPT_DIR/python"
    source venv/bin/activate
    JASYPT_PASSWORD="$JASYPT_PASSWORD" python3 bridge.py > /tmp/mtxf-bridge.log 2>&1 &
    BRIDGE_PID=$!
    cd "$SCRIPT_DIR"
    
    # Wait for bridge to be ready
    local attempts=0
    while [ $attempts -lt 30 ]; do
        if check_bridge; then
            echo -e "${GREEN}✅ Python bridge started (PID: $BRIDGE_PID)${NC}"
            return 0
        fi
        attempts=$((attempts + 1))
        sleep 1
    done
    
    echo -e "${RED}❌ Failed to start Python bridge${NC}"
    return 1
}

stop_bridge() {
    if [ -n "$BRIDGE_PID" ]; then
        echo -e "${YELLOW}🛑 Stopping Python bridge (PID: $BRIDGE_PID)...${NC}"
        kill $BRIDGE_PID 2>/dev/null || true
        wait $BRIDGE_PID 2>/dev/null || true
    fi
}

###############################################################################
# Phase 1: Java Unit Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📦 Phase 1: Java Unit Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

JAVA_UNIT_OUTPUT=$(mvn test -DexcludedGroups=integration 2>&1) || true
JAVA_UNIT_SUMMARY=$(echo "$JAVA_UNIT_OUTPUT" | grep -E "Tests run:" | tail -1)

if echo "$JAVA_UNIT_OUTPUT" | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✅ Java unit tests passed${NC}"
    # Parse results
    read JAVA_UNIT_PASSED JAVA_UNIT_FAILED JAVA_UNIT_SKIPPED <<< $(parse_maven_results "$JAVA_UNIT_OUTPUT")
else
    echo -e "${RED}❌ Java unit tests failed${NC}"
    JAVA_UNIT_FAILED=1
fi
echo "   $JAVA_UNIT_SUMMARY"
echo ""

###############################################################################
# Phase 2: Python Unit Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🐍 Phase 2: Python Unit Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

PYTHON_UNIT_OUTPUT=$(JASYPT_PASSWORD="$JASYPT_PASSWORD" python/venv/bin/pytest \
    python/tests/test_bridge.py \
    python/tests/test_contract.py \
    python/tests/test_shioaji_simulation.py \
    -v 2>&1) || true

if echo "$PYTHON_UNIT_OUTPUT" | grep -qE "passed|failed"; then
    read PYTHON_UNIT_PASSED PYTHON_UNIT_FAILED PYTHON_UNIT_SKIPPED <<< $(parse_pytest_results "$PYTHON_UNIT_OUTPUT")
    if [ "${PYTHON_UNIT_FAILED:-0}" -eq 0 ]; then
        echo -e "${GREEN}✅ Python unit tests passed${NC}"
    else
        echo -e "${RED}❌ Python unit tests failed${NC}"
    fi
    echo "   ${PYTHON_UNIT_PASSED} passed, ${PYTHON_UNIT_FAILED} failed, ${PYTHON_UNIT_SKIPPED} skipped"
else
    echo -e "${RED}❌ Python unit tests failed to run${NC}"
    PYTHON_UNIT_FAILED=1
fi
echo ""

###############################################################################
# Phase 3: Fish Shell Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🐟 Phase 3: Fish Shell Tests${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

FISH_OUTPUT=$(fish tests/fish/test_start_script.fish 2>&1) || true

read FISH_PASSED FISH_FAILED FISH_SKIPPED <<< $(parse_fish_results "$FISH_OUTPUT")

if [ "${FISH_FAILED:-0}" -eq 0 ]; then
    echo -e "${GREEN}✅ Fish shell tests passed${NC}"
else
    echo -e "${RED}❌ Fish shell tests failed${NC}"
fi
echo "   ${FISH_PASSED} passed, ${FISH_FAILED} failed, ${FISH_SKIPPED} skipped"
echo ""

###############################################################################
# Phase 4: Start Ollama and Python Bridge for Integration Tests
###############################################################################

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔌 Phase 4: Integration Tests Setup${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Check/Start Ollama first (needed for news veto tests)
OLLAMA_WAS_RUNNING=false
if check_ollama; then
    echo -e "${GREEN}✅ Ollama already running${NC}"
    OLLAMA_WAS_RUNNING=true
else
    start_ollama || {
        echo -e "${YELLOW}⚠️ Ollama not available - news veto tests may be skipped${NC}"
    }
fi

# Check/Start Python Bridge
BRIDGE_WAS_RUNNING=false
if check_bridge; then
    echo -e "${GREEN}✅ Python bridge already running${NC}"
    BRIDGE_WAS_RUNNING=true
else
    start_bridge || {
        echo -e "${RED}❌ Cannot run integration tests without bridge${NC}"
        JAVA_INT_SKIPPED=30
        PYTHON_INT_SKIPPED=24
        E2E_SKIPPED=18
    }
fi
echo ""

###############################################################################
# Phase 5: Java Integration Tests
###############################################################################

if check_bridge; then
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}☕ Phase 5: Java Integration Tests${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    JAVA_INT_OUTPUT=$(BRIDGE_URL=http://localhost:8888 mvn test \
        -Dtest=OrderEndpointIntegrationTest,SystemIntegrationTest 2>&1) || true
    
    if echo "$JAVA_INT_OUTPUT" | grep -q "BUILD SUCCESS"; then
        echo -e "${GREEN}✅ Java integration tests passed${NC}"
        read JAVA_INT_PASSED JAVA_INT_FAILED JAVA_INT_SKIPPED <<< $(parse_maven_results "$JAVA_INT_OUTPUT")
    else
        echo -e "${RED}❌ Java integration tests failed${NC}"
        JAVA_INT_FAILED=1
    fi
    JAVA_INT_SUMMARY=$(echo "$JAVA_INT_OUTPUT" | grep -E "Tests run:" | tail -1)
    echo "   $JAVA_INT_SUMMARY"
    echo ""
fi

###############################################################################
# Phase 6: Python Integration Tests
###############################################################################

if check_bridge; then
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}🐍 Phase 6: Python Integration Tests${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    PYTHON_INT_OUTPUT=$(BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest \
        python/tests/test_integration.py -v 2>&1) || true
    
    if echo "$PYTHON_INT_OUTPUT" | grep -qE "passed|failed"; then
        read PYTHON_INT_PASSED PYTHON_INT_FAILED PYTHON_INT_SKIPPED <<< $(parse_pytest_results "$PYTHON_INT_OUTPUT")
        if [ "${PYTHON_INT_FAILED:-0}" -eq 0 ]; then
            echo -e "${GREEN}✅ Python integration tests passed${NC}"
        else
            echo -e "${RED}❌ Python integration tests failed${NC}"
        fi
        echo "   ${PYTHON_INT_PASSED} passed, ${PYTHON_INT_FAILED} failed, ${PYTHON_INT_SKIPPED} skipped"
    else
        echo -e "${RED}❌ Python integration tests failed to run${NC}"
        PYTHON_INT_FAILED=1
    fi
    echo ""
fi

###############################################################################
# Phase 7: E2E Tests
###############################################################################

if check_bridge; then
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}🚀 Phase 7: E2E Tests${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    E2E_OUTPUT=$(BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest \
        tests/e2e/test_full_session.py -v 2>&1) || true
    
    if echo "$E2E_OUTPUT" | grep -qE "passed|failed"; then
        read E2E_PASSED E2E_FAILED E2E_SKIPPED <<< $(parse_pytest_results "$E2E_OUTPUT")
        if [ "${E2E_FAILED:-0}" -eq 0 ]; then
            echo -e "${GREEN}✅ E2E tests passed${NC}"
        else
            echo -e "${RED}❌ E2E tests failed${NC}"
        fi
        echo "   ${E2E_PASSED} passed, ${E2E_FAILED} failed, ${E2E_SKIPPED} skipped"
    else
        echo -e "${RED}❌ E2E tests failed to run${NC}"
        E2E_FAILED=1
    fi
    echo ""
fi

###############################################################################
# Cleanup
###############################################################################

if [ "$BRIDGE_WAS_RUNNING" = false ] && [ -n "$BRIDGE_PID" ]; then
    stop_bridge
fi

if [ "$OLLAMA_WAS_RUNNING" = false ] && [ -n "$OLLAMA_PID" ]; then
    stop_ollama
fi

###############################################################################
# Summary
###############################################################################

echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                  FULL TEST RESULTS SUMMARY                    ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Calculate totals
TOTAL_PASSED=$((JAVA_UNIT_PASSED + JAVA_INT_PASSED + PYTHON_UNIT_PASSED + PYTHON_INT_PASSED + E2E_PASSED + FISH_PASSED))
TOTAL_FAILED=$((JAVA_UNIT_FAILED + JAVA_INT_FAILED + PYTHON_UNIT_FAILED + PYTHON_INT_FAILED + E2E_FAILED + FISH_FAILED))
TOTAL_SKIPPED=$((JAVA_UNIT_SKIPPED + JAVA_INT_SKIPPED + PYTHON_UNIT_SKIPPED + PYTHON_INT_SKIPPED + E2E_SKIPPED + FISH_SKIPPED))
TOTAL_TESTS=$((TOTAL_PASSED + TOTAL_FAILED + TOTAL_SKIPPED))

JAVA_TOTAL_PASSED=$((JAVA_UNIT_PASSED + JAVA_INT_PASSED))
JAVA_TOTAL_FAILED=$((JAVA_UNIT_FAILED + JAVA_INT_FAILED))
JAVA_TOTAL_SKIPPED=$((JAVA_UNIT_SKIPPED + JAVA_INT_SKIPPED))

PYTHON_TOTAL_PASSED=$((PYTHON_UNIT_PASSED + PYTHON_INT_PASSED + E2E_PASSED))
PYTHON_TOTAL_FAILED=$((PYTHON_UNIT_FAILED + PYTHON_INT_FAILED + E2E_FAILED))
PYTHON_TOTAL_SKIPPED=$((PYTHON_UNIT_SKIPPED + PYTHON_INT_SKIPPED + E2E_SKIPPED))

echo -e "┌─────────────────────────────────────────────────────────────────┐"
echo -e "│ ${BLUE}Java Tests${NC}                                                      │"
echo -e "├─────────────────────────────────────────────────────────────────┤"
printf "│   Unit Tests:        %3d passed, %3d failed, %3d skipped        │\n" "$JAVA_UNIT_PASSED" "$JAVA_UNIT_FAILED" "$JAVA_UNIT_SKIPPED"
printf "│   Integration Tests: %3d passed, %3d failed, %3d skipped        │\n" "$JAVA_INT_PASSED" "$JAVA_INT_FAILED" "$JAVA_INT_SKIPPED"
printf "│   ${BLUE}Subtotal:          %3d passed, %3d failed, %3d skipped${NC}        │\n" "$JAVA_TOTAL_PASSED" "$JAVA_TOTAL_FAILED" "$JAVA_TOTAL_SKIPPED"
echo -e "├─────────────────────────────────────────────────────────────────┤"
echo -e "│ ${BLUE}Python Tests${NC}                                                    │"
echo -e "├─────────────────────────────────────────────────────────────────┤"
printf "│   Unit Tests:        %3d passed, %3d failed, %3d skipped        │\n" "$PYTHON_UNIT_PASSED" "$PYTHON_UNIT_FAILED" "$PYTHON_UNIT_SKIPPED"
printf "│   Integration Tests: %3d passed, %3d failed, %3d skipped        │\n" "$PYTHON_INT_PASSED" "$PYTHON_INT_FAILED" "$PYTHON_INT_SKIPPED"
printf "│   E2E Tests:         %3d passed, %3d failed, %3d skipped        │\n" "$E2E_PASSED" "$E2E_FAILED" "$E2E_SKIPPED"
printf "│   ${BLUE}Subtotal:          %3d passed, %3d failed, %3d skipped${NC}        │\n" "$PYTHON_TOTAL_PASSED" "$PYTHON_TOTAL_FAILED" "$PYTHON_TOTAL_SKIPPED"
echo -e "├─────────────────────────────────────────────────────────────────┤"
echo -e "│ ${BLUE}Fish Shell Tests${NC}                                                │"
echo -e "├─────────────────────────────────────────────────────────────────┤"
printf "│   Shell Tests:       %3d passed, %3d failed, %3d skipped        │\n" "$FISH_PASSED" "$FISH_FAILED" "$FISH_SKIPPED"
echo -e "├─────────────────────────────────────────────────────────────────┤"
echo -e "│ ${BLUE}GRAND TOTAL${NC}                                                     │"
echo -e "├─────────────────────────────────────────────────────────────────┤"

if [ "$TOTAL_FAILED" -eq 0 ]; then
    printf "│   ${GREEN}✅ %3d tests passed, %3d failed, %3d skipped${NC}                 │\n" "$TOTAL_PASSED" "$TOTAL_FAILED" "$TOTAL_SKIPPED"
else
    printf "│   ${RED}❌ %3d tests passed, %3d failed, %3d skipped${NC}                 │\n" "$TOTAL_PASSED" "$TOTAL_FAILED" "$TOTAL_SKIPPED"
fi

echo -e "└─────────────────────────────────────────────────────────────────┘"
echo ""
echo -e "🕐 Completed at: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Exit with appropriate code
if [ "$TOTAL_FAILED" -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}💥 Some tests failed!${NC}"
    exit 1
fi
