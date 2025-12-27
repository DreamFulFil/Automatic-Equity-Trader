# Automatic Equity Trading System - Prompt 3
## Strategy Framework & Risk Management

**Prerequisite:** Complete Prompt 2 (Data Layer)  
**Next:** Prompt 4 (Python Bridge & LLM)  
**Estimated Time:** 4-5 hours

---

## 🎯 Objective

Implement the complete strategy framework with 11 concurrent strategies, market context provider, and advanced risk management. This prompt establishes the multi-strategy execution engine that enables parallel trading across different time horizons.

---

## 🏗 Strategy Pattern Architecture

### Base Strategy Interface
```java
package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.StrategyConfig.StrategyType;

/**
 * Strategy Pattern Interface
 * 
 * All trading strategies must implement this interface.
 * Strategies are completely independent and never control engine flow.
 * 
 * Design Principles:
 * - Stateless: No mutable state between calls
 * - Pure functions: Output depends only on input (MarketContext)
 * - No side effects: Strategies generate signals, don't execute trades
 * - Composable: Can run 11+ strategies concurrently
 */
public interface IStrategy {
    
    /**
     * Get strategy unique name
     */
    String getName();
    
    /**
     * Get strategy type/category
     */
    StrategyType getType();
    
    /**
     * Generate trading signal based on market context
     * 
     * @param context Current market conditions with price history,
volume, indicators
     * @return Trade signal (LONG/SHORT/NEUTRAL) with confidence and reason
     */
    TradeSignal generateSignal(MarketContext context);
    
    /**
     * Reset strategy state (called at end of day or on error)
     */
    void reset();
    
    /**
     * Get strategy configuration (optional, for tuning)
     */
    default Map<String, Object> getConfig() {
        return Collections.emptyMap();
    }
}
```

### Supporting Classes
```java
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeSignal {
    private SignalDirection direction;  // LONG, SHORT, NEUTRAL
    private double confidence;          // 0.0 to 1.0
    private String reason;
    private boolean exitSignal;
    private Map<String, Object> metadata;  // Additional context
    
    public enum SignalDirection {
        LONG, SHORT, NEUTRAL
    }
    
    public boolean isActionable() {
        return direction != SignalDirection.NEUTRAL && confidence >= 0.65;
    }
}

@Data
@Builder
public class MarketContext {
    private String symbol;
    private double currentPrice;
    private LocalDateTime timestamp;
    
    // Price history
    private List<Double> priceHistory;     // Last 600 ticks
    private List<Integer> volumeHistory;   // Last 600 volumes
    
    // Calculated indicators
    private Double sma5;
    private Double sma20;
    private Double rsi;
    private Double vwap;
    private Double bollingerUpper;
    private Double bollingerLower;
    
    // Session data
    private Double sessionOpen;
    private Double sessionHigh;
    private Double sessionLow;
    
    // Position info
    private Integer currentPosition;
    private Double avgEntryPrice;
    
    // Market state
    private Trade.TradingMode mode;
    private boolean newsVeto;
}
```

---

## 📈 11 Strategy Implementations

### 1. DCA Strategy (Long-Term)
```java
package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DCAStrategy implements IStrategy {
    
    private static final int DEFAULT_INTERVAL_MINUTES = 30;
    private static final int DEFAULT_TARGET = Integer.MAX_VALUE;
    
    private LocalDateTime lastPurchase;
    private int currentPosition;
    private int targetPosition;
    private int intervalMinutes;
    
    public DCAStrategy() {
        this.intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        this.targetPosition = DEFAULT_TARGET;
        this.currentPosition = 0;
        log.info("[DCA] Strategy initialized: interval={}min, target={}", 
            intervalMinutes, targetPosition);
    }
    
    @Override
    public String getName() {
        return "DCA";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }
    
    @Override
    public TradeSignal generateSignal(MarketContext context) {
        // DCA: Buy fixed amount at regular intervals
        LocalDateTime now = context.getTimestamp();
        
        if (currentPosition >= targetPosition) {
            return TradeSignal.builder()
                .direction(SignalDirection.NEUTRAL)
                .confidence(0.0)
                .reason("Target position reached")
                .build();
        }
        
        if (lastPurchase == null || 
            Duration.between(lastPurchase, now).toMinutes() >= intervalMinutes) {
            
            lastPurchase = now;
            currentPosition++;
            
            log.info("[DCA] BUY SIGNAL: {} @ {} TWD (position: {}/{})",
                context.getSymbol(), context.getCurrentPrice(), 
                currentPosition, targetPosition);
            
            return TradeSignal.builder()
                .direction(SignalDirection.LONG)
                .confidence(0.95)  // Very high confidence (it's DCA)
                .reason(String.format("DCA interval reached (%d min)", intervalMinutes))
                .build();
        }
        
        return TradeSignal.builder()
            .direction(SignalDirection.NEUTRAL)
            .confidence(0.0)
            .reason("Waiting for next DCA interval")
            .build();
    }
    
    @Override
    public void reset() {
        lastPurchase = null;
        currentPosition = 0;
        log.info("[DCA] Resetting strategy state");
    }
}
```

### 2. Moving Average Crossover (Short-Term)
```java
@Component
@Slf4j
public class MovingAverageCrossoverStrategy implements IStrategy {
    
    private static final int FAST_PERIOD = 5;
    private static final int SLOW_PERIOD = 20;
    private static final double MIN_CONFIDENCE = 0.001;  // 0.1%
    
    @Override
    public TradeSignal generateSignal(MarketContext context) {
        if (context.getSma5() == null || context.getSma20() == null) {
            return TradeSignal.builder()
                .direction(SignalDirection.NEUTRAL)
                .confidence(0.0)
                .reason("Insufficient data for MA calculation")
                .build();
        }
        
        double fast = context.getSma5();
        double slow = context.getSma20();
        double divergence = (fast - slow) / slow;
        
        // Golden cross: fast MA crosses above slow MA
        if (divergence > MIN_CONFIDENCE) {
            return TradeSignal.builder()
                .direction(SignalDirection.LONG)
                .confidence(Math.min(0.95, 0.60 + Math.abs(divergence) * 100))
                .reason(String.format("Golden cross: SMA5 %.2f > SMA20 %.2f", fast, slow))
                .build();
        }
        
        // Death cross: fast MA crosses below slow MA
        if (divergence < -MIN_CONFIDENCE) {
            return TradeSignal.builder()
                .direction(SignalDirection.SHORT)
                .confidence(Math.min(0.95, 0.60 + Math.abs(divergence) * 100))
                .reason(String.format("Death cross: SMA5 %.2f < SMA20 %.2f", fast, slow))
                .exitSignal(true)
                .build();
        }
        
        return TradeSignal.builder()
            .direction(SignalDirection.NEUTRAL)
            .confidence(0.0)
            .reason("No significant MA crossover")
            .build();
    }
    
    @Override
    public String getName() {
        return "MA Crossover";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }
    
    @Override
    public void reset() {
        log.info("[MA Crossover] Resetting strategy state");
    }
}
```

### 3-11. Additional Strategies (Abbreviated)

```java
// 3. Bollinger Band Strategy (Mean Reversion)
@Component
public class BollingerBandStrategy implements IStrategy {
    // Buy when price touches lower band
    // Sell when price touches upper band
}

// 4. VWAP Execution Strategy
@Component
public class VWAPExecutionStrategy implements IStrategy {
    // Execute orders near VWAP to minimize market impact
}

// 5. Momentum Trading Strategy
@Component
public class MomentumTradingStrategy implements IStrategy {
    // Trade based on price momentum (rate of change)
}

// 6. Arbitrage Strategy (Pairs Trading)
@Component
public class ArbitrageStrategy implements IStrategy {
    // Exploit price discrepancies between related assets
}

// 7. News/Sentiment Strategy
@Component
public class NewsSentimentStrategy implements IStrategy {
    // Trade based on LLM-analyzed news sentiment
}

// 8. TWAP Execution Strategy
@Component
public class TWAPExecutionStrategy implements IStrategy {
    // Time-Weighted Average Price execution
}

// 9. Rebalancing Strategy
@Component
public class RebalancingStrategy implements IStrategy {
    // Periodic portfolio rebalancing
}

// 10. DRIP Strategy
@Component
public class DRIPStrategy implements IStrategy {
    // Dividend Reinvestment Plan automation
}

// 11. Tax-Loss Harvesting Strategy
@Component
public class TaxLossHarvestingStrategy implements IStrategy {
    // Optimize tax liabilities through strategic selling
}
```

---

## 🎮 Strategy Manager

```java
package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.strategy.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyManager {
    
    private final List<IStrategy> strategies;
    private final StrategyConfigRepository strategyConfigRepository;
    
    /**
     * Execute all enabled strategies and aggregate signals
     */
    public List<TradeSignal> generateSignals(MarketContext context) {
        List<TradeSignal> signals = new ArrayList<>();
        
        for (IStrategy strategy : getEnabledStrategies()) {
            try {
                TradeSignal signal = strategy.generateSignal(context);
                
                if (signal != null && signal.isActionable()) {
                    signals.add(signal);
                    log.debug("Strategy {} generated signal: {}", 
                        strategy.getName(), signal.getDirection());
                }
            } catch (Exception e) {
                log.error("Strategy {} failed: {}", strategy.getName(), e.getMessage());
            }
        }
        
        return signals;
    }
    
    /**
     * Aggregate signals using weighted voting
     */
    public TradeSignal aggregateSignals(List<TradeSignal> signals) {
        if (signals.isEmpty()) {
            return TradeSignal.builder()
                .direction(SignalDirection.NEUTRAL)
                .confidence(0.0)
                .reason("No actionable signals")
                .build();
        }
        
        // Count votes weighted by confidence
        double longScore = signals.stream()
            .filter(s -> s.getDirection() == SignalDirection.LONG)
            .mapToDouble(TradeSignal::getConfidence)
            .sum();
        
        double shortScore = signals.stream()
            .filter(s -> s.getDirection() == SignalDirection.SHORT)
            .mapToDouble(TradeSignal::getConfidence)
            .sum();
        
        SignalDirection finalDirection;
        double confidence;
        String reason;
        
        if (longScore > shortScore && longScore > 0.65) {
            finalDirection = SignalDirection.LONG;
            confidence = Math.min(0.95, longScore / signals.size());
            reason = String.format("%d strategies vote LONG (score: %.2f)", 
                countDirection(signals, SignalDirection.LONG), longScore);
        } else if (shortScore > longScore && shortScore > 0.65) {
            finalDirection = SignalDirection.SHORT;
            confidence = Math.min(0.95, shortScore / signals.size());
            reason = String.format("%d strategies vote SHORT (score: %.2f)", 
                countDirection(signals, SignalDirection.SHORT), shortScore);
        } else {
            finalDirection = SignalDirection.NEUTRAL;
            confidence = 0.0;
            reason = "Insufficient consensus among strategies";
        }
        
        return TradeSignal.builder()
            .direction(finalDirection)
            .confidence(confidence)
            .reason(reason)
            .build();
    }
    
    private List<IStrategy> getEnabledStrategies() {
        // Filter based on database config
        return strategies.stream()
            .filter(s -> isStrategyEnabled(s.getName()))
            .collect(Collectors.toList());
    }
    
    private boolean isStrategyEnabled(String name) {
        return strategyConfigRepository.findByName(name)
            .map(StrategyConfig::getEnabled)
            .orElse(true);  // Default to enabled
    }
    
    private long countDirection(List<TradeSignal> signals, SignalDirection direction) {
        return signals.stream()
            .filter(s -> s.getDirection() == direction)
            .count();
    }
}
```

---

## 📊 Market Context Provider

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketContextProvider {
    
    private final BridgeClient bridgeClient;
    private final PositionRepository positionRepository;
    
    /**
     * Build comprehensive market context for strategy evaluation
     */
    public MarketContext buildContext(String symbol) {
        // Fetch latest market data from Python bridge
        JsonNode signalData = bridgeClient.getSignal();
        
        if (signalData == null) {
            log.warn("Failed to get signal data from bridge");
            return null;
        }
        
        // Extract price and indicators
        double currentPrice = signalData.path("current_price").asDouble(0.0);
        double momentum3min = signalData.path("momentum_3min").asDouble(0.0);
        double momentum5min = signalData.path("momentum_5min").asDouble(0.0);
        double rsi = signalData.path("rsi").asDouble(50.0);
        double volumeRatio = signalData.path("volume_ratio").asDouble(1.0);
        
        // Get position info
        Position position = positionRepository.findBySymbol(symbol).orElse(null);
        
        return MarketContext.builder()
            .symbol(symbol)
            .currentPrice(currentPrice)
            .timestamp(LocalDateTime.now())
            .rsi(rsi)
            // Additional indicators populated from bridge
            .currentPosition(position != null ? position.getQuantity() : 0)
            .avgEntryPrice(position != null ? position.getAvgPrice() : null)
            .build();
    }
}
```

---

## 🛡 Advanced Risk Management

### Enhanced Risk Service
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskManagementService {
    
    private final TradeRepository tradeRepository;
    private final PositionRepository positionRepository;
    private final RiskSettingsRepository riskSettingsRepository;
    private final TelegramService telegram;
    
    private double dailyPnl = 0.0;
    private double weeklyPnl = 0.0;
    private boolean emergencyShutdown = false;
    private LocalDateTime lastTradeTime = null;
    
    /**
     * Pre-trade risk check - MUST pass before order submission
     */
    public boolean canTrade(MarketContext context, TradeSignal signal) {
        if (emergencyShutdown) {
            log.error("❌ Trading blocked: Emergency shutdown active");
            return false;
        }
        
        if (isDailyLossLimitExceeded()) {
            log.error("❌ Trading blocked: Daily loss limit hit");
            triggerEmergencyShutdown("Daily loss limit");
            return false;
        }
        
        if (isWeeklyLossLimitExceeded()) {
            log.error("❌ Trading blocked: Weekly loss limit hit");
            return false;
        }
        
        if (isPositionLimitExceeded(context)) {
            log.warn("⚠️ Trading blocked: Max position limit reached");
            return false;
        }
        
        if (isInEarningsBlackout(context.getSymbol())) {
            log.warn("⚠️ Trading blocked: Earnings blackout period");
            return false;
        }
        
        if (context.isNewsVeto()) {
            log.warn("⚠️ Trading blocked: News veto active");
            return false;
        }
        
        return true;
    }
    
    /**
     * Post-trade P&L update and limit checking
     */
    public void recordTrade(Trade trade) {
        if (trade.getRealizedPnl() != null) {
            dailyPnl += trade.getRealizedPnl();
            weeklyPnl += trade.getRealizedPnl();
            
            log.info("P&L updated: Daily={}, Weekly={}", dailyPnl, weeklyPnl);
            
            if (isDailyLossLimitExceeded()) {
                triggerEmergencyShutdown("Daily loss limit");
            }
        }
        
        lastTradeTime = trade.getTimestamp();
    }
    
    private void triggerEmergencyShutdown(String reason) {
        emergencyShutdown = true;
        log.error("🚨 EMERGENCY SHUTDOWN: {}", reason);
        telegram.sendMessage(String.format(
            "🚨 <b>EMERGENCY SHUTDOWN</b>\n" +
            "Reason: %s\n" +
            "Daily P&L: %.2f TWD", 
            reason, dailyPnl
        ));
    }
    
    private boolean isPositionLimitExceeded(MarketContext context) {
        int currentPos = Math.abs(context.getCurrentPosition());
        int maxPos = getRiskSettings().getMaxPosition();
        return currentPos >= maxPos;
    }
    
    private boolean isInEarningsBlackout(String symbol) {
        // Check database for blackout dates
        // Implementation omitted for brevity
        return false;
    }
    
    // Additional helper methods...
}
```

---

## ✅ Verification Checklist

- [ ] All 11 strategies compile and implement IStrategy
- [ ] StrategyManager aggregates signals correctly
- [ ] MarketContext builds complete indicator set
- [ ] Risk checks block trades when limits hit
- [ ] Strategies reset cleanly at end of day
- [ ] Database stores strategy configs
- [ ] Concurrent strategy execution works

### Test Commands
```bash
# Test strategy execution
mvn test -Dtest=StrategyManagerTest

# Test risk management
mvn test -Dtest=RiskManagementServiceTest

# Test all strategies
mvn test -Dtest=AllStrategiesTest
```

---

## 📝 Next Steps (Prompt 4)

Prompt 4 will implement:
- Python FastAPI bridge
- Shioaji market data integration
- Ollama LLM integration
- Real-time streaming data

---

**Prompt Series:** 3 of 5  
**Status:** Strategy Framework Complete  
**Next:** Python Bridge & LLM Integration
