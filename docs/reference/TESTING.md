# MTXF Lunch Bot - Comprehensive Testing Suite

**Version:** November 2025 Final  
**Repository:** https://github.com/DreamFulFil/Lunch-Investor-Bot  
**Last Updated:** 2025-11-27  

This document is the **single source of truth** for all testing in the MTXF Lunch Bot project.

---

## Table of Contents

1. [Quick Start](#1-quick-start)
2. [Test Architecture](#2-test-architecture)
3. [Contract Tests (API Boundaries)](#3-contract-tests-api-boundaries)
4. [Unit Tests](#4-unit-tests)
5. [Integration Tests](#5-integration-tests)
6. [Component Tests](#6-component-tests)
7. [E2E Tests](#7-e2e-tests)
8. [Defensive Coding Requirements](#8-defensive-coding-requirements)
9. [Test Data Requirements](#9-test-data-requirements)
10. [CI/CD Gate Requirements](#10-cicd-gate-requirements)
11. [Test Execution Commands](#11-test-execution-commands)
12. [Definition of Done Checklist](#12-definition-of-done-checklist)
13. [Test Failure Response Protocol](#13-test-failure-response-protocol)
14. [Folder Structure](#14-folder-structure)

---

## 1. Quick Start

### One-Liner: Run Everything
```bash
# Full test suite (requires bridge + Ollama running)
mvn test && BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest python/tests/ -v && fish tests/fish/test_start_script.fish
```

### Fast Tests Only (No External Services)
```bash
# Java unit tests only
mvn test -DexcludedGroups=integration

# Python unit tests only
python/venv/bin/pytest python/tests/test_bridge.py -v
```

### Prerequisites
```bash
# 1. Java 21
java -version  # Should show 21.x

# 2. Python venv
cd python && python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt -r tests/requirements-test.txt

# 3. Start Python bridge (for integration tests)
JASYPT_PASSWORD=<secret> python3 bridge.py &

# 4. Start Ollama (for news/AI tests)
ollama serve &
ollama pull mistral:7b-instruct-v0.2-q5_K_M
```

---

## 2. Test Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        E2E Tests                                │
│     (Full journey: cron → signal → trade → flatten → Telegram)  │
├─────────────────────────────────────────────────────────────────┤
│                    Integration Tests                            │
│  (Java ↔ Python bridge, Python ↔ Ollama, Shioaji simulation)   │
├─────────────────────────────────────────────────────────────────┤
│                     Contract Tests                              │
│        (API boundary validation, request/response schemas)      │
├─────────────────────────────────────────────────────────────────┤
│                       Unit Tests                                │
│  (TradingEngine, RiskManagement, ContractScaling, bridge.py)    │
└─────────────────────────────────────────────────────────────────┘
```

### Test Distribution Target
| Layer | Coverage Target | Runtime |
|-------|-----------------|---------|
| Unit | 100% on core logic | &lt;30s |
| Contract | 100% on APIs | &lt;10s |
| Integration | All endpoints | &lt;60s |
| E2E | Critical paths | &lt;5min |

---

## 3. Contract Tests (API Boundaries)

Contract tests verify that Java and Python speak the same language at HTTP boundaries.

### 3.1 Java → Python Bridge Contract

**File:** `src/test/java/tw/gc/mtxfbot/OrderEndpointIntegrationTest.java`

Key test cases:
- `healthEndpoint_shouldReturnOk` - Health contract
- `orderDryRun_withMapPayload_shouldReturnValidated` - THE 422 BUG FIX
- `orderDryRun_withIntegerPrice_shouldWork` - Type coercion
- `orderDryRun_withInvalidAction_shouldReturn400` - Validation
- `signalEndpoint_shouldReturnValidJson` - Signal contract
- `newsEndpoint_shouldReturnValidJson` - News contract

### 3.2 Python → Java Contract

**File:** `python/tests/test_contract.py` (NEW)

Tests that Python responses match what Java expects:
- OrderRequest model validation
- Signal response schema
- Health response schema
- News response schema
- Account response schema

---

## 4. Unit Tests

### 4.1 Java Unit Tests (JUnit 5 + Mockito)

#### TradingEngine Tests
**File:** `src/test/java/tw/gc/mtxfbot/TradingEngineTest.java`

Key test cases:
- `initialize_whenBridgeConnected_shouldSetMarketDataConnected`
- `tradingLoop_whenEmergencyShutdown_shouldReturnImmediately`
- `checkRiskLimits_whenDailyLossLimitHit_shouldTriggerEmergencyShutdown`
- `evaluateEntry_whenNewsVetoActive_shouldNotEnterPosition`
- `evaluateEntry_whenLowConfidence_shouldNotEnterPosition`
- `evaluateEntry_whenHighConfidenceLong_shouldExecuteBuyOrder`
- `evaluateExit_whenStopLossHit_shouldFlattenPosition`
- `evaluateExit_whenProfitRunning_shouldNotExit` (NO PROFIT CAPS!)

#### RiskManagementService Tests
**File:** `src/test/java/tw/gc/mtxfbot/RiskManagementServiceTest.java`

Key test cases:
- `recordPnL_shouldUpdateDailyAndWeekly`
- `isDailyLimitExceeded_whenAtLimit_shouldReturnTrue`
- `checkWeeklyLossLimit_whenHit_shouldSetFlag`
- `loadEarningsBlackoutDates_whenFileNotExists_shouldNotThrow`

#### ContractScalingService Tests
**File:** `src/test/java/tw/gc/mtxfbot/ContractScalingServiceTest.java`

Key test cases:
- `calculateContractSize_whenEquityBelow250k_shouldReturn1`
- `calculateContractSize_whenEquity250kAndProfit80k_shouldReturn2`
- `updateContractSizing_whenBridgeFails_shouldDefaultTo1`

#### TelegramService Tests
**File:** `src/test/java/tw/gc/mtxfbot/TelegramServiceTest.java`

Key test cases:
- `sendMessage_whenEnabled_shouldCallTelegramApi`
- `pollUpdates_withStatusCommand_shouldCallStatusHandler`
- `pollUpdates_withUnauthorizedChat_shouldIgnoreCommand`

### 4.2 Python Unit Tests (pytest)

**File:** `python/tests/test_bridge.py`

Key test classes:
- `TestJasyptDecryption` - Credential decryption
- `TestOrderRequest` - Pydantic model (THE 422 BUG)
- `TestNewsAnalysis` - Ollama integration
- `TestMomentumCalculation` - Signal logic
- `TestShioajiWrapper` - Connection retry logic

### 4.3 Fish Shell Tests

**File:** `tests/fish/test_start_script.fish` (NEW)

Tests:
- Script requires secret argument
- Python venv exists
- activate.fish exists
- Config files exist

---

## 5. Integration Tests

### 5.1 Java Integration Tests

**File:** `src/test/java/tw/gc/mtxfbot/SystemIntegrationTest.java`

Run: `BRIDGE_URL=http://localhost:8888 mvn test -Dtest=SystemIntegrationTest`

Key test cases:
- `pythonBridge_shouldBeHealthy`
- `signalEndpoint_shouldReturnValidSignal`
- `orderDryRun_shouldAcceptMapPayload`
- `fullTradingCycle_shouldWork`
- `contractScalingFlow_shouldWork`

### 5.2 Python Integration Tests

**File:** `python/tests/test_integration.py`

Run: `BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest python/tests/test_integration.py -v`

Key test classes:
- `TestBridgeEndpoints` - All HTTP endpoints
- `TestOllamaIntegration` - News veto with real Ollama
- `TestJavaPythonInteraction` - Simulates Java client
- `TestAccountEndpoints` - Contract scaling data

### 5.3 Shioaji Simulation Tests

**File:** `python/tests/test_shioaji_simulation.py` (NEW)

Tests with real Shioaji credentials (simulation mode):
- Config loading with decryption
- CA path resolution
- Connection retry logic

---

## 6. Component Tests

### 6.1 Telegram MarkdownV2 Escaping

**File:** `src/test/java/tw/gc/mtxfbot/TelegramMarkdownTest.java` (NEW)

Tests:
- P&L formatting
- Emoji handling
- Special character escaping
- Multiline messages

### 6.2 Log File Rolling

**File:** `src/test/java/tw/gc/mtxfbot/LogFileRollingTest.java` (NEW)

Tests:
- Log file name pattern
- Log directory existence
- File writability

---

## 7. E2E Tests

### 7.1 Full Trading Session Simulation

**File:** `tests/e2e/test_full_session.py` (NEW)

Test scenarios:
1. **Startup Sequence**
   - Bridge health check
   - Pre-market dry-run
   - Contract scaling update

2. **Signal to Entry Flow**
   - Get signal
   - Evaluate confidence
   - Validate order

3. **News Veto Check**
   - Ollama integration
   - Veto decision handling

4. **Earnings Blackout**
   - Blackout file loaded
   - Date format validation

5. **Weekly Loss Limit**
   - P&L file format
   - Limit enforcement

---

## 8. Defensive Coding Requirements

### 8.1 Java: Null-Safety

```java
// ✅ GOOD: Use path() with defaults
String direction = signal.path("direction").asText("NEUTRAL");
double confidence = signal.path("confidence").asDouble(0.0);

// ❌ BAD: Direct access
String direction = signal.get("direction").asText();  // NPE if missing!
```

### 8.2 Python: .get() Defaults

```python
# ✅ GOOD: Use .get() with defaults
veto = news_analysis.get("veto", False)
score = news_analysis.get("score", 0.5)

# ❌ BAD: Direct access
veto = news_analysis["veto"]  # KeyError if missing!
```

### 8.3 Fish: Path Checks

```fish
# ✅ GOOD: Check before using
if test -f "$PROJECT_ROOT/python/venv/bin/activate.fish"
    source "$PROJECT_ROOT/python/venv/bin/activate.fish"
else
    echo "Error: venv not found"
    exit 1
end
```

---

## 9. Test Data Requirements

### 9.1 Captured Shioaji Responses

**File:** `tests/fixtures/shioaji_responses.json`

Contains real tick data, margin info, and P&L records for offline testing.

### 9.2 Ollama Veto Responses

**File:** `tests/fixtures/ollama_responses.json`

Contains sample veto decisions: neutral, crisis, bullish.

### 9.3 MoneyDJ RSS Snippets

**File:** `tests/fixtures/moneydj_rss.xml`

Contains sample RSS feed data for news parsing tests.

---

## 10. CI/CD Gate Requirements

### 10.1 Local Validation (Pre-commit)

```bash
# .git/hooks/pre-commit
mvn compile -q || exit 1
mvn test -DexcludedGroups=integration -q || exit 1
python/venv/bin/pytest python/tests/test_bridge.py -q || exit 1
```

### 10.2 Coverage Requirements

| Component | Minimum Coverage |
|-----------|-----------------|
| TradingEngine.java | 100% |
| RiskManagementService.java | 100% |
| ContractScalingService.java | 100% |
| TelegramService.java | 90% |
| bridge.py (core functions) | 100% |

---

## 11. Test Execution Commands

### All Tests (Full Suite)
```bash
./run-tests.sh <jasypt-password>    # Run all tests
./run-tests.sh help                 # Show help and usage info
```

### Fast Tests Only (Unit Tests)
```bash
mvn test -DexcludedGroups=integration && \
python/venv/bin/pytest python/tests/test_bridge.py python/tests/test_contract.py -v
```

### Integration Tests Only
```bash
BRIDGE_URL=http://localhost:8888 mvn test -Dtest=OrderEndpointIntegrationTest,SystemIntegrationTest && \
BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest python/tests/test_integration.py -v
```

### Coverage Report
```bash
# Java
mvn test jacoco:report && open target/site/jacoco/index.html

# Python
python/venv/bin/pytest python/tests/ --cov=bridge --cov-report=html && open htmlcov/index.html
```

---

## 12. Definition of Done Checklist

Before marking ANY feature complete:

### Code Quality
- [ ] Code compiles without errors: `mvn compile`
- [ ] No new warnings introduced
- [ ] Follows existing code style

### Unit Tests
- [ ] All existing unit tests pass: `mvn test`
- [ ] New code has unit tests with 100% coverage on core logic
- [ ] Python unit tests pass: `pytest python/tests/test_bridge.py`

### Contract Tests
- [ ] API contracts verified: `pytest python/tests/test_contract.py`
- [ ] Request/response formats unchanged (or documented)

### Integration Tests
- [ ] Java-Python integration passes
- [ ] Python integration passes

### Manual Verification
- [ ] `/order/dry-run` endpoint works
- [ ] Telegram messages send correctly
- [ ] Logs write to correct location

---

## 13. Test Failure Response Protocol

### When Tests Fail

1. **Identify Scope**
   ```bash
   mvn test 2>&amp;1 | grep -A 5 "FAILURE"
   ```

2. **Isolate Failure**
   ```bash
   mvn test -Dtest=TradingEngineTest#evaluateEntry_whenHighConfidenceLong
   ```

3. **Check Dependencies**
   ```bash
   curl -s http://localhost:8888/health
   curl -s http://localhost:11434/api/tags
   ```

### Common Failure Patterns

| Error | Likely Cause | Fix |
|-------|--------------|-----|
| 422 Unprocessable Entity | JSON serialization mismatch | Check OrderRequest model |
| Connection refused | Bridge not running | Start bridge first |
| Timeout on /signal/news | Ollama slow | Start Ollama |
| NPE in TradingEngine | Missing null check | Add .path().asText("default") |
| KeyError in bridge.py | Missing .get() | Use .get(key, default) |

---

## 14. Folder Structure

```
mtxf-bot/
├── docs/
│   └── TESTING.md              # This file
├── src/
│   ├── main/java/tw/gc/mtxfbot/
│   └── test/java/tw/gc/mtxfbot/
│       ├── TradingEngineTest.java
│       ├── RiskManagementServiceTest.java
│       ├── ContractScalingServiceTest.java
│       ├── TelegramServiceTest.java
│       ├── TelegramMarkdownTest.java        # NEW
│       ├── LogFileRollingTest.java          # NEW
│       ├── OrderEndpointIntegrationTest.java
│       └── SystemIntegrationTest.java
├── python/
│   ├── bridge.py
│   └── tests/
│       ├── test_bridge.py
│       ├── test_contract.py                  # NEW
│       ├── test_integration.py
│       ├── test_shioaji_simulation.py        # NEW
│       └── requirements-test.txt
├── tests/
│   ├── e2e/
│   │   └── test_full_session.py              # NEW
│   ├── fish/
│   │   └── test_start_script.fish            # NEW
│   └── fixtures/
│       ├── shioaji_responses.json            # NEW
│       ├── ollama_responses.json             # NEW
│       └── moneydj_rss.xml                   # NEW
├── config/
│   └── earnings-blackout-dates.json         # Legacy seed only (DB holds runtime data)
└── pom.xml
```

---

**Document Version:** 1.1  
**Last Updated:** 2025-12-08
