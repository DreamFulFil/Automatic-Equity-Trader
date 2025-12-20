#!/usr/bin/env bash
###############################################################################
# Native Image Build Script
#
# This script builds both the standard JAR and GraalVM native executable.
# It ensures all tests pass before native compilation.
#
# Usage: ./scripts/build-native.sh [--skip-tests] <jasypt-password>
#
# Requirements:
#   - GraalVM 21 with native-image (run ./scripts/setup/install-graalvm.sh)
#   - All unit tests must pass
#
# Output:
#   - target/auto-equity-trader.jar (56MB, JVM mode)
#   - target/auto-equity-trader (native executable, ~100MB)
###############################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Parse arguments
SKIP_TESTS=false
JASYPT_PASSWORD=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -h|--help)
            echo "Usage: ./scripts/build-native.sh [--skip-tests] <jasypt-password>"
            echo ""
            echo "Options:"
            echo "  --skip-tests  Skip unit tests (not recommended)"
            echo ""
            echo "Requirements:"
            echo "  - GraalVM 21 with native-image installed"
            echo "  - Run ./scripts/setup/install-graalvm.sh if needed"
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
else
  MVN_CMD="mvn"
fi

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         Native Image Build - Automatic Equity Trader         ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BOLD}Project:${NC}      $PROJECT_ROOT"
echo -e "${BOLD}Started:${NC}      $(date '+%Y-%m-%d %H:%M:%S')"
echo -e "${BOLD}Skip Tests:${NC}   $SKIP_TESTS"
echo ""

# Step 1: Verify GraalVM installation
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 1/5: Verifying GraalVM Installation${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if ! command -v native-image >/dev/null 2>&1; then
    echo -e "${RED}❌ native-image not found!${NC}"
    echo ""
    echo "GraalVM is not installed or not in PATH."
    echo "Run: ./scripts/setup/install-graalvm.sh"
    echo ""
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1)
NATIVE_VERSION=$(native-image --version 2>&1 | head -1)

if ! echo "$JAVA_VERSION" | grep -q "GraalVM"; then
    echo -e "${RED}❌ Current Java is not GraalVM!${NC}"
    echo ""
    echo "Current: $JAVA_VERSION"
    echo "Expected: GraalVM 21+"
    echo ""
    echo "Fix: Set JAVA_HOME to GraalVM installation"
    exit 1
fi

echo -e "${GREEN}✅ GraalVM verified${NC}"
echo "   Java: $JAVA_VERSION"
echo "   Native: $NATIVE_VERSION"
echo ""

# Step 2: Run unit tests (unless skipped)
if [ "$SKIP_TESTS" = false ]; then
    if [ -z "$JASYPT_PASSWORD" ]; then
        echo -e "${RED}❌ Jasypt password required for tests${NC}"
        echo "Usage: ./scripts/build-native.sh <jasypt-password>"
        exit 1
    fi
    
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Step 2/5: Running Unit Tests${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    TEST_START=$(date +%s)
    if ! ./run-tests.sh --unit "$JASYPT_PASSWORD"; then
        echo -e "${RED}❌ Unit tests failed!${NC}"
        echo "Fix tests before building native image."
        exit 1
    fi
    TEST_END=$(date +%s)
    TEST_DURATION=$((TEST_END - TEST_START))
    
    echo -e "${GREEN}✅ All tests passed (${TEST_DURATION}s)${NC}"
    echo ""
else
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Step 2/5: Skipping Tests (--skip-tests)${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}⚠️  Tests skipped - not recommended for production builds${NC}"
    echo ""
fi

# Step 3: Build standard JAR
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 3/5: Building Standard JAR${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

JAR_START=$(date +%s)
$MVN_CMD clean package -DskipTests -q
JAR_END=$(date +%s)
JAR_DURATION=$((JAR_END - JAR_START))

if [ ! -f "target/auto-equity-trader.jar" ]; then
    echo -e "${RED}❌ JAR build failed!${NC}"
    exit 1
fi

JAR_SIZE=$(du -h target/auto-equity-trader.jar | cut -f1)
echo -e "${GREEN}✅ JAR built successfully${NC}"
echo "   File: target/auto-equity-trader.jar"
echo "   Size: $JAR_SIZE"
echo "   Time: ${JAR_DURATION}s"
echo ""

# Step 4: Build native executable
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 4/5: Building Native Executable (5-10 minutes)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}⏳ This will take 5-10 minutes depending on your CPU...${NC}"
echo ""

NATIVE_START=$(date +%s)

# Show progress indicators during build
$MVN_CMD -Pnative native:compile -DskipTests 2>&1 | while IFS= read -r line; do
    if echo "$line" | grep -q "Building native image"; then
        echo -e "${CYAN}[Native Build] Compiling...${NC}"
    elif echo "$line" | grep -q "Finished generating"; then
        echo -e "${CYAN}[Native Build] Linking...${NC}"
    elif echo "$line" | grep -q "ERROR"; then
        echo -e "${RED}$line${NC}"
    elif echo "$line" | grep -q "WARNING"; then
        echo -e "${YELLOW}$line${NC}"
    fi
done

NATIVE_EXIT_CODE=$?
NATIVE_END=$(date +%s)
NATIVE_DURATION=$((NATIVE_END - NATIVE_START))

if [ $NATIVE_EXIT_CODE -ne 0 ] || [ ! -f "target/auto-equity-trader" ]; then
    echo ""
    echo -e "${RED}❌ Native build failed!${NC}"
    echo ""
    echo "Check build logs for errors. Common issues:"
    echo "  - Missing reflection hints"
    echo "  - Unsupported dynamic features"
    echo "  - Insufficient memory (requires 4-8GB RAM)"
    echo ""
    exit 1
fi

NATIVE_SIZE=$(du -h target/auto-equity-trader | cut -f1)
echo ""
echo -e "${GREEN}✅ Native executable built successfully${NC}"
echo "   File: target/auto-equity-trader"
echo "   Size: $NATIVE_SIZE"
echo "   Time: ${NATIVE_DURATION}s ($(($NATIVE_DURATION / 60))m $(($NATIVE_DURATION % 60))s)"
echo ""

# Step 5: Performance comparison
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 5/5: Build Summary${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""
echo -e "${BOLD}Artifacts:${NC}"
echo "  📦 JAR:    target/auto-equity-trader.jar ($JAR_SIZE)"
echo "  🚀 Native: target/auto-equity-trader ($NATIVE_SIZE)"
echo ""
echo -e "${BOLD}Build Times:${NC}"
echo "  ⏱️  JAR:    ${JAR_DURATION}s"
echo "  ⏱️  Native: ${NATIVE_DURATION}s ($(($NATIVE_DURATION / 60))m)"
echo ""
echo -e "${BOLD}Expected Performance:${NC}"
echo "  🚀 Startup: ~200-500ms (vs 3-5s for JVM)"
echo "  💾 Memory:  ~150-300MB RSS (vs 500MB-1GB for JVM)"
echo ""

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ✅ Native Build Complete - Ready for Production             ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}Next steps:${NC}"
echo "  1. Test native executable: ./target/auto-equity-trader --help"
echo "  2. Run production: ./start-auto-trader.fish <jasypt-password>"
echo "  3. Validate: ./mvnw -PnativeTest test (optional)"
echo ""
