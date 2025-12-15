package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Active Strategy Configuration
 * 
 * Stores the currently active main strategy and its optimized parameters.
 * Only one record should exist at a time (enforced by unique constraint on id=1).
 */
@Entity
@Table(name = "active_strategy_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveStrategyConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Name of the currently active strategy
     */
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    /**
     * Optimized parameters for the active strategy (stored as JSON)
     * Example: {"period": 14, "threshold": 0.7}
     */
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;
    
    /**
     * Last time the strategy was switched
     */
    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    /**
     * Reason for the strategy switch
     */
    @Column(name = "switch_reason", length = 500)
    private String switchReason;
    
    /**
     * Performance metrics snapshot at time of selection
     */
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    /**
     * Whether this was an automated switch (true) or manual (false)
     */
    @Column(name = "auto_switched")
    @Builder.Default
    private boolean autoSwitched = false;
}
