# Automatic Equity Trading System - Prompt 2
## Data Layer & Services

**Prerequisite:** Complete Prompt 1 (Foundation)  
**Next:** Prompt 3 (Strategy Framework)  
**Estimated Time:** 3-4 hours

---

## 🎯 Objective

Implement the complete data persistence layer with 20 entities, repositories, and core service infrastructure. This prompt establishes the database schema, JPA mappings, and foundational services needed for trading operations.

---

## 📊 Complete Entity Model (20 Entities)

### Category 1: Trading Operations (6 entities)

#### 1. Trade Entity
```java
package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trade_timestamp", columnList = "timestamp"),
    @Index(name = "idx_trade_symbol", columnList = "symbol"),
    @Index(name = "idx_trade_mode", columnList = "mode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeDirection direction;  // LONG, SHORT, FLAT
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(name = "entry_price")
    private Double entryPrice;
    
    @Column(name = "exit_price")
    private Double exitPrice;
    
    @Column(name = "realized_pnl")
    private Double realizedPnl;
    
    @Column(length = 100)
    private String strategy;
    
    @Column(name = "entry_reason", length = 500)
    private String entryReason;
    
    @Column(name = "exit_reason", length = 500)
    private String exitReason;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradingMode mode;  // SIMULATION, LIVE
    
    @Column(name = "market_code", length = 20)
    private String marketCode;
    
    public enum TradeDirection {
        LONG, SHORT, FLAT
    }
    
    public enum TradingMode {
        SIMULATION, LIVE
    }
}
```

#### 2. Signal Entity
```java
@Entity
@Table(name = "signals", indexes = {
    @Index(name = "idx_signal_timestamp", columnList = "timestamp"),
    @Index(name = "idx_signal_strategy", columnList = "strategy")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(length = 20)
    private String symbol;
    
    @Column(length = 100)
    private String strategy;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SignalDirection direction;  // LONG, SHORT, NEUTRAL
    
    private Double confidence;
    
    @Column(length = 500)
    private String reason;
    
    @Column(name = "was_executed")
    private Boolean wasExecuted;
    
    public enum SignalDirection {
        LONG, SHORT, NEUTRAL
    }
}
```

#### 3-6. Position, Quote, Bar, MarketData
```java
@Entity @Table(name = "positions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Position {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 20)
    private String symbol;
    
    private Integer quantity;
    private Double avgPrice;
    private LocalDateTime entryTime;
    private Double unrealizedPnl;
    
    @Enumerated(EnumType.STRING)
    private Trade.TradeDirection direction;
}

@Entity @Table(name = "quotes")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Quote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime timestamp;
    
    @Column(length = 20)
    private String symbol;
    
    private Double bid;
    private Double ask;
    private Integer bidVolume;
    private Integer askVolume;
    private Double last;
}

@Entity @Table(name = "bars")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Bar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime timestamp;
    
    @Column(length = 20)
    private String symbol;
    
    @Column(length = 10)
    private String timeframe;  // "1m", "5m", "1h"
    
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
}

@Entity @Table(name = "market_data")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime timestamp;
    
    @Column(length = 20)
    private String symbol;
    
    private Double price;
    private Integer volume;
    
    @Column(length = 20)
    private String dataType;  // "tick", "snapshot"
    
    @Column(length = 1000)
    private String metadata;  // JSON for additional fields
}
```

### Category 2: Configuration (6 entities)

#### 7. MarketConfig
```java
@Entity @Table(name = "market_configs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 20)
    private String marketCode;  // "TSE", "TAIFEX", "NYSE"
    
    @Column(nullable = false, length = 50)
    private String timezone;
    
    @Column(nullable = false, length = 10)
    private String tradingStart;
    
    @Column(nullable = false, length = 10)
    private String tradingEnd;
    
    private Boolean enabled;
    
    @Column(length = 200)
    private String holidays;  // JSON array of dates
}
```

#### 8-12. StrategyConfig, StockSettings, RiskSettings, ShioajiSettings, BotSettings
```java
@Entity @Table(name = "strategy_configs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StrategyConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    private StrategyType type;
    
    private Boolean enabled;
    
    @Column(columnDefinition = "TEXT")
    private String parameters;  // JSON parameters
    
    public enum StrategyType {
        INTRADAY, SWING, POSITION, LONG_TERM
    }
}

// Similar entities for StockSettings, RiskSettings, ShioajiSettings, BotSettings
// (See full source for complete implementations)
```

### Category 3: Intelligence & Analytics (5 entities)

#### 13. LlmInsight
```java
@Entity @Table(name = "llm_insights")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LlmInsight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime timestamp;
    
    @Column(length = 50)
    private String insightType;  // "news_veto", "signal_enhancement", "risk_assessment"
    
    @Column(columnDefinition = "TEXT")
    private String prompt;
    
    @Column(columnDefinition = "TEXT")
    private String response;
    
    private Double confidence;
    
    @Column(length = 20)
    private String decision;  // "VETO", "APPROVE", "NEUTRAL"
    
    @Column(length = 500)
    private String reason;
    
    private Integer tokensUsed;
    private Double latencyMs;
}
```

#### 14-17. VetoEvent, EconomicNews, DailyStatistics, Event
```java
// See full source for complete implementations
// VetoEvent: Records veto decisions (system, manual, LLM)
// EconomicNews: News articles with sentiment scores
// DailyStatistics: End-of-day performance metrics
// Event: System events and audit log
```

### Category 4: Agents & Compliance (3 entities)

#### 18-20. Agent, AgentInteraction, EarningsBlackoutDate
```java
@Entity @Table(name = "earnings_blackout_dates")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EarningsBlackoutDate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDate blackoutDate;
    
    @Column(length = 100)
    private String source;
    
    private LocalDateTime createdAt;
    
    @Column(unique = true)
    private String uniqueKey;  // symbol_date for constraint
}
```

---

## 🗃 Repository Layer

### Base Repository Pattern
```java
package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.Trade;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    // Custom queries
    List<Trade> findBySymbolAndTimestampBetween(
        String symbol, 
        LocalDateTime start, 
        LocalDateTime end
    );
    
    List<Trade> findByModeAndTimestampAfter(
        Trade.TradingMode mode, 
        LocalDateTime since
    );
    
    // P&L calculation
    @Query("SELECT SUM(t.realizedPnl) FROM Trade t WHERE t.timestamp >= :since")
    Double sumRealizedPnlSince(@Param("since") LocalDateTime since);
}
```

### Create repositories for all 20 entities
- TradeRepository
- SignalRepository
- PositionRepository
- QuoteRepository
- BarRepository
- MarketDataRepository
- MarketConfigRepository
- StrategyConfigRepository
- StockSettingsRepository
- RiskSettingsRepository
- ShioajiSettingsRepository
- BotSettingsRepository
- LlmInsightRepository
- VetoEventRepository
- EconomicNewsRepository
- DailyStatisticsRepository
- EventRepository
- AgentRepository
- AgentInteractionRepository
- EarningsBlackoutDateRepository

---

## 🛠 Core Services

### 1. Data Logging Service
```java
package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.*;
import tw.gc.auto.equity.trader.repositories.*;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataLoggingService {
    
    private final TradeRepository tradeRepository;
    private final SignalRepository signalRepository;
    private final QuoteRepository quoteRepository;
    
    public void logTrade(
        String symbol,
        Trade.TradeDirection direction,
        int quantity,
        double price,
        String strategy,
        Trade.TradingMode mode
    ) {
        Trade trade = Trade.builder()
            .timestamp(LocalDateTime.now())
            .symbol(symbol)
            .direction(direction)
            .quantity(quantity)
            .entryPrice(price)
            .strategy(strategy)
            .mode(mode)
            .build();
        
        tradeRepository.save(trade);
        log.info("Trade logged: {} {} {} @ {}", direction, quantity, symbol, price);
    }
    
    public void logSignal(
        String symbol,
        String strategy,
        Signal.SignalDirection direction,
        double confidence,
        String reason
    ) {
        Signal signal = Signal.builder()
            .timestamp(LocalDateTime.now())
            .symbol(symbol)
            .strategy(strategy)
            .direction(direction)
            .confidence(confidence)
            .reason(reason)
            .wasExecuted(false)
            .build();
        
        signalRepository.save(signal);
        log.debug("Signal logged: {} {} (confidence: {})", symbol, direction, confidence);
    }
    
    public void closeTrade(Long tradeId, double exitPrice, String exitReason) {
        tradeRepository.findById(tradeId).ifPresent(trade -> {
            trade.setExitPrice(exitPrice);
            trade.setExitReason(exitReason);
            
            // Calculate P&L
            if (trade.getEntryPrice() != null) {
                double pnl = (exitPrice - trade.getEntryPrice()) * trade.getQuantity();
                if (trade.getDirection() == Trade.TradeDirection.SHORT) {
                    pnl = -pnl;
                }
                trade.setRealizedPnl(pnl);
            }
            
            tradeRepository.save(trade);
            log.info("Trade closed: ID={}, P&L={}", tradeId, trade.getRealizedPnl());
        });
    }
}
```

### 2. Risk Management Service (Enhanced)
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskManagementService {
    
    private final TradeRepository tradeRepository;
    private final RiskSettingsRepository riskSettingsRepository;
    
    private double dailyPnl = 0.0;
    private double weeklyPnl = 0.0;
    private boolean emergencyShutdown = false;
    
    public void initialize() {
        // Load P&L from database
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        dailyPnl = tradeRepository.sumRealizedPnlSince(todayStart);
        
        LocalDateTime weekStart = todayStart.minusDays(todayStart.getDayOfWeek().getValue());
        weeklyPnl = tradeRepository.sumRealizedPnlSince(weekStart);
        
        log.info("Risk initialized: Daily P&L={}, Weekly P&L={}", dailyPnl, weeklyPnl);
    }
    
    public boolean isDailyLossLimitExceeded() {
        RiskSettings settings = getRiskSettings();
        return dailyPnl < -settings.getDailyLossLimit();
    }
    
    public boolean isWeeklyLossLimitExceeded() {
        RiskSettings settings = getRiskSettings();
        return weeklyPnl < -settings.getWeeklyLossLimit();
    }
    
    public void recordPnl(double pnl) {
        dailyPnl += pnl;
        weeklyPnl += pnl;
        
        if (isDailyLossLimitExceeded()) {
            emergencyShutdown = true;
            log.error("DAILY LOSS LIMIT HIT: {} TWD", dailyPnl);
        }
    }
    
    private RiskSettings getRiskSettings() {
        // Load from database or return default
        return riskSettingsRepository.findById(1L)
            .orElse(RiskSettings.builder()
                .dailyLossLimit(1500.0)
                .weeklyLossLimit(7000.0)
                .maxPosition(4)
                .build());
    }
}
```

### 3. LLM Insight Service
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmInsightService {
    
    private final LlmInsightRepository insightRepository;
    private final OllamaClient ollamaClient;
    
    public LlmInsight generateInsight(
        String insightType,
        String prompt,
        String context
    ) {
        long startTime = System.currentTimeMillis();
        
        String response = ollamaClient.generate(prompt, context);
        
        long latency = System.currentTimeMillis() - startTime;
        
        LlmInsight insight = LlmInsight.builder()
            .timestamp(LocalDateTime.now())
            .insightType(insightType)
            .prompt(prompt)
            .response(response)
            .latencyMs((double) latency)
            .build();
        
        // Parse response for structured fields
        parseInsightResponse(insight, response);
        
        insightRepository.save(insight);
        log.info("LLM insight generated: type={}, latency={}ms", insightType, latency);
        
        return insight;
    }
    
    private void parseInsightResponse(LlmInsight insight, String response) {
        // Parse JSON response for confidence, decision, reason
        // Implementation omitted for brevity
    }
}
```

---

## 🔧 Configuration Properties

### application.yml (Enhanced)
```yaml
spring:
  application:
    name: auto-equity-trader
  
  datasource:
    url: jdbc:sqlite:data/trading.db
    driver-class-name: org.sqlite.JDBC
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    properties:
      hibernate:
        format_sql: true

server:
  port: 16350

# Trading Configuration
trading:
  window:
    start: "11:30"
    end: "13:00"
  bridge:
    url: "http://localhost:8888"
    timeout-ms: 3000
  
  risk:
    daily-loss-limit: 1500.0
    weekly-loss-limit: 7000.0
    max-position: 4
    max-hold-time-minutes: 45

# Ollama LLM
ollama:
  url: "http://localhost:11434"
  model: "llama3.1:8b-instruct-q5_K_M"
  timeout-seconds: 30

# Telegram
telegram:
  bot-token: "ENC(...)"
  chat-id: "ENC(...)"
  enabled: true

# Logging
logging:
  level:
    tw.gc.auto.equity.trader: INFO
    org.hibernate.SQL: DEBUG
  file:
    name: logs/trading-engine.log
```

---

## ✅ Verification Checklist

After implementing this prompt:

- [ ] All 20 entities compile without errors
- [ ] Database schema creates successfully
- [ ] All 20 repositories created with custom queries
- [ ] DataLoggingService logs trades and signals
- [ ] RiskManagementService tracks P&L correctly
- [ ] LlmInsightService integrates with Ollama
- [ ] application.yml configuration loads properly
- [ ] Application starts without errors
- [ ] Database file created at `data/trading.db`

### Test Commands
```bash
# Compile
mvn clean compile

# Run application (should start successfully)
mvn spring-boot:run

# Verify database
sqlite3 data/trading.db ".schema"

# Run unit tests
mvn test
```

---

## 📝 Next Steps (Prompt 3)

Prompt 3 will implement:
- Complete strategy framework (11 strategies)
- Strategy execution engine
- Market context provider
- Portfolio management service

---

**Prompt Series:** 2 of 5  
**Status:** Data Layer Complete  
**Next:** Strategy Framework & Risk Management
