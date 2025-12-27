package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Strategy Configuration for Multi-Strategy Concurrent Execution
 * 
 * Supports:
 * - Intraday trading strategies
 * - Swing trading strategies
 * - Position trading strategies
 * - Long-term investment strategies
 * 
 * Multiple strategies can run independently or in parallel across different markets
 */
@Entity
@Table(name = "strategy_config", indexes = {
    @Index(name = "idx_strategy_name", columnList = "strategy_name", unique = true),
    @Index(name = "idx_strategy_enabled", columnList = "enabled"),
    @Index(name = "idx_strategy_type", columnList = "strategy_type"),
    @Index(name = "idx_strategy_market", columnList = "market_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Strategy unique name/identifier
     */
    @Column(name = "strategy_name", nullable = false, unique = true, length = 100)
    private String strategyName;
    
    /**
     * Strategy display name
     */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    
    /**
     * Strategy type/category
     */
    @Column(name = "strategy_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private StrategyType strategyType;
    
    /**
     * Market this strategy trades (nullable for multi-market)
     */
    @Column(name = "market_code", length = 20)
    private String marketCode;
    
    /**
     * Symbols this strategy trades (comma-separated, nullable for dynamic)
     */
    @Column(name = "symbols", length = 500)
    private String symbols;
    
    /**
     * Whether strategy is enabled
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * Strategy priority (higher = executes first)
     */
    @Column(name = "priority")
    @Builder.Default
    private int priority = 0;
    
    /**
     * Maximum position size (contracts/shares)
     */
    @Column(name = "max_position_size")
    private Integer maxPositionSize;
    
    /**
     * Maximum portfolio allocation (percentage)
     */
    @Column(name = "max_allocation_pct")
    private Double maxAllocationPct;
    
    /**
     * Daily loss limit for this strategy
     */
    @Column(name = "daily_loss_limit")
    private Double dailyLossLimit;
    
    /**
     * Weekly loss limit for this strategy
     */
    @Column(name = "weekly_loss_limit")
    private Double weeklyLossLimit;
    
    /**
     * Signal check interval in seconds
     */
    @Column(name = "signal_interval_sec")
    @Builder.Default
    private int signalIntervalSec = 30;
    
    /**
     * Strategy-specific parameters as JSON
     */
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;
    
    /**
     * Java class implementing this strategy
     */
    @Column(name = "implementation_class", length = 200)
    private String implementationClass;
    
    /**
     * When strategy was created
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Last time strategy was modified
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Last time strategy executed
     */
    @Column(name = "last_execution_at")
    private LocalDateTime lastExecutionAt;
    
    /**
     * Total trades executed by strategy
     */
    @Column(name = "total_trades")
    @Builder.Default
    private long totalTrades = 0;
    
    /**
     * Win rate percentage
     */
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    /**
     * Total P&L for strategy
     */
    @Column(name = "total_pnl")
    @Builder.Default
    private double totalPnl = 0.0;
    
    /**
     * Whether to use LLM for signal enhancement
     */
    @Column(name = "use_llm_enhancement")
    @Builder.Default
    private boolean useLlmEnhancement = false;
    
    /**
     * Description of strategy logic
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * Strategy types supported
     */
    public enum StrategyType {
        /** Intraday trading (minutes to hours) */
        INTRADAY,
        
        /** Swing trading (days to weeks) */
        SWING,
        
        /** Position trading (weeks to months) */
        POSITION,
        
        /** Long-term investment (months to years) */
        LONG_TERM,
        
        /** Market making */
        MARKET_MAKING,
        
        /** Arbitrage */
        ARBITRAGE,
        
        /** Statistical arbitrage */
        STAT_ARB,
        
        /** Options strategies */
        OPTIONS,
        
        /** Hedging */
        HEDGING,
        
        /** Custom/Other */
        CUSTOM
    }
}
