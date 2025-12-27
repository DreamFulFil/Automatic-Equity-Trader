# Automatic Equity Trading System - Re-Creation Prompt Series
## Prompt 1 of 5: Architecture & Core Foundation

---

## 🎯 Objective

Create the foundational architecture for an enterprise-grade, multi-market, multi-strategy automated trading platform with AI-powered analytics and full regulatory compliance.

---

## 📋 System Overview

You are building an **Automatic Equity Trading System** - a production-ready trading platform that:
- Supports **multiple global markets** simultaneously
- Executes **11 different trading strategies** concurrently
- Uses **local LLM** (Ollama Llama 3.1 8B) for market analysis
- Maintains **full audit trail** for regulatory compliance
- Operates in both **simulation** and **live** trading modes

---

## 🏗 Architecture Decisions

### Technology Stack

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Core Engine** | Java 21 + Spring Boot 3.4 | Enterprise-grade, type-safe, excellent concurrency |
| **API Layer** | Python 3.12 + FastAPI | Rapid broker SDK integration, async support |
| **Database** | SQLite | Zero-config, perfect for single-instance deployment |
| **AI/ML** | Ollama (local) + Llama 3.1 8B | Complete data privacy, low latency, no API costs |
| **Build** | Maven 3.9 | Industry standard, excellent dependency management |
| **Testing** | JUnit 5 + Mockito + pytest | Comprehensive test coverage without complexity |

### Design Patterns

1. **Strategy Pattern** - All trading strategies implement `IStrategy` interface
2. **Repository Pattern** - Data access abstraction via Spring Data JPA
3. **Service Layer** - Business logic separation from controllers
4. **Event-Driven** - Spring scheduling for time-based events
5. **Bridge Pattern** - Python FastAPI bridges Java to broker SDKs

---

## 📁 Project Structure

```
automatic-equity-trader/
├── src/
│   ├── main/
│   │   ├── java/tw/gc/auto/equity/trader/
│   │   │   ├── AutoEquityTraderApplication.java    # Main entry point
│   │   │   ├── config/                             # Configuration classes
│   │   │   │   ├── TradingProperties.java
│   │   │   │   ├── OllamaProperties.java
│   │   │   │   ├── TelegramProperties.java
│   │   │   │   └── WebConfig.java
│   │   │   ├── entities/                           # JPA entities (20)
│   │   │   │   ├── MarketConfig.java
│   │   │   │   ├── StrategyConfig.java
│   │   │   │   ├── Trade.java
│   │   │   │   ├── Signal.java
│   │   │   │   ├── LlmInsight.java
│   │   │   │   ├── Quote.java
│   │   │   │   ├── Bar.java
│   │   │   │   ├── VetoEvent.java
│   │   │   │   └── ... (12 more)
│   │   │   ├── repositories/                       # Spring Data repos
│   │   │   ├── services/                          # Business logic
│   │   │   │   ├── TradingEngine.java             # Core orchestrator
│   │   │   │   ├── StrategyManager.java           # Strategy execution
│   │   │   │   ├── RiskManagementService.java     # Risk controls
│   │   │   │   ├── LlmService.java                # AI integration
│   │   │   │   └── TelegramService.java           # User interface
│   │   │   ├── strategy/                          # Strategy framework
│   │   │   │   ├── IStrategy.java                 # Strategy interface
│   │   │   │   ├── TradeSignal.java               # Signal DTO
│   │   │   │   └── impl/                          # 11 strategies
│   │   │   └── utils/                             # Utilities
│   │   └── resources/
│   │       ├── application.yml                    # Main config
│   │       └── logback-spring.xml                # Logging
│   └── test/java/...                             # 240 unit tests
├── python/
│   ├── bridge.py                                  # FastAPI bridge
│   ├── requirements.txt
│   └── tests/                                     # 90 Python tests
├── pom.xml                                        # Maven build
├── start-auto-trader.fish                        # Startup script
├── run-tests.sh                                   # Test runner
└── README.md                                      # Documentation
```

---

## 🎯 Step 1: Create Maven Project

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.0</version>
    </parent>

    <groupId>tw.gc</groupId>
    <artifactId>auto-equity-trader</artifactId>
    <version>2.0.0</version>
    <name>Automatic Equity Trader</name>
    <description>Enterprise-grade multi-market trading platform</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.43.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-community-dialects</artifactId>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Security -->
        <dependency>
            <groupId>com.github.ulisesbocchio</groupId>
            <artifactId>jasypt-spring-boot-starter</artifactId>
            <version>3.0.5</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 🎯 Step 2: Main Application Class

### AutoEquityTraderApplication.java

```java
package tw.gc.auto.equity.trader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Automatic Equity Trading System
 * 
 * Enterprise-grade multi-market trading platform with:
 * - 11 concurrent strategies
 * - Multi-market support (Taiwan, US, futures)
 * - Local LLM analytics
 * - Full regulatory compliance
 * 
 * @version 2.0
 * @author System Architecture Team
 */
@SpringBootApplication
@EnableScheduling
public class AutoEquityTraderApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutoEquityTraderApplication.class, args);
    }
}
```

---

## 🎯 Step 3: Core Configuration

### application.yml

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

server:
  port: 16350

# Trading Configuration
trading:
  # Window times (Taiwan default)
  window:
    start: "11:30"
    end: "13:00"
  
  # Python bridge
  bridge:
    url: "http://localhost:8888"
    timeout-ms: 3000

# Telegram Bot
telegram:
  bot-token: "ENC(...)"  # Jasypt encrypted
  chat-id: "ENC(...)"
  enabled: true

# Ollama LLM
ollama:
  url: "http://localhost:11434"
  model: "mistral:7b-instruct-v0.2-q5_K_M"
  timeout-seconds: 30

# Logging
logging:
  level:
    tw.gc.auto.equity.trader: INFO
  file:
    name: logs/trading-engine.log
```

---

## 🎯 Step 4: Base Entity Classes

### Trade.java (Core Entity)

```java
package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trade_timestamp", columnList = "timestamp"),
    @Index(name = "idx_trade_symbol", columnList = "symbol")
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

---

## 🎯 Step 5: Strategy Interface

### IStrategy.java

```java
package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.StrategyConfig.StrategyType;

/**
 * Strategy Pattern Interface
 * 
 * All trading strategies must implement this interface.
 * Strategies are completely independent and never control engine flow.
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
     * @param context Current market conditions
     * @return Trade signal (LONG/SHORT/NEUTRAL)
     */
    TradeSignal generateSignal(MarketContext context);
    
    /**
     * Reset strategy state (called at end of day)
     */
    void reset();
}
```

### TradeSignal.java

```java
package tw.gc.auto.equity.trader.strategy;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeSignal {
    private SignalDirection direction;  // LONG, SHORT, NEUTRAL
    private double confidence;          // 0.0 to 1.0
    private String reason;
    private boolean exitSignal;
    
    public enum SignalDirection {
        LONG, SHORT, NEUTRAL
    }
}
```

---

## 🎯 Step 6: Core Trading Engine (Skeleton)

### TradingEngine.java

```java
package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.config.TradingProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Core Trading Engine
 * 
 * Orchestrates:
 * - Strategy execution
 * - Risk management
 * - Order execution
 * - Position tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngine {
    
    private final TradingProperties properties;
    private final RiskManagementService riskManager;
    private final TelegramService telegram;
    
    @PostConstruct
    public void initialize() {
        log.info("🚀 Automatic Equity Trader starting...");
        telegram.sendMessage("🚀 Trading system initialized");
    }
    
    /**
     * Main trading loop - runs every 30 seconds during trading window
     */
    @Scheduled(fixedDelay = 30000)
    public void tradingLoop() {
        if (!isWithinTradingWindow()) {
            return;
        }
        
        log.debug("Trading loop executing...");
        // Strategy execution will be added in Prompt 2
    }
    
    private boolean isWithinTradingWindow() {
        // Implementation in next prompt
        return false;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Trading engine shutting down...");
        telegram.sendMessage("🛑 System shutdown complete");
    }
}
```

---

## 🎯 Verification Steps

After implementing this foundation:

1. **Compile:** `mvn clean compile`
2. **Run Tests:** `mvn test` (should have 0 tests initially)
3. **Start Application:** `mvn spring-boot:run`
4. **Verify Startup:** Check logs for "Trading system initialized"

---

## 📝 Next Steps (Prompt 2)

Prompt 2 will cover:
- Complete entity model (20 entities)
- Repository layer (Spring Data JPA)
- Risk management service
- LLM integration
- Python bridge skeleton

---

**Prompt Series:** 1 of 5  
**Status:** Foundation Complete  
**Next:** Data Layer & Services
