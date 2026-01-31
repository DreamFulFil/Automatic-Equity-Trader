package tw.gc.auto.equity.trader.services;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Data
@RequiredArgsConstructor
public class TradingStateService {

    private final ActiveStrategyService activeStrategyService;

    // Trading mode: "stock" or "futures"
    private String tradingMode = "stock";
    
    // State flags
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    private volatile boolean tradingPaused = false;
    private volatile boolean activePolling = true;
    private volatile boolean multiStrategyExecutionEnabled = false;
    
    // News veto cache
    private final AtomicBoolean cachedNewsVeto = new AtomicBoolean(false);
    private final AtomicReference<String> cachedNewsReason = new AtomicReference<>("");

    /**
     * Get active strategy name from database
     */
    public String getActiveStrategyName() {
        return activeStrategyService.getActiveStrategyName();
    }
    
    /**
     * Set active strategy name (delegates to database)
     */
    public void setActiveStrategyName(String strategyName) {
        activeStrategyService.switchStrategy(
            strategyName, 
            Map.of(), // Empty parameters - will be loaded dynamically
            "Manual switch via Telegram or initialization",
            false // not auto-switched
        );
    }
    
    /**
     * Get active strategy parameters from database
     */
    public Map<String, Object> getActiveStrategyParameters() {
        return activeStrategyService.getActiveStrategyParameters();
    }

    public boolean isNewsVeto() {
        return cachedNewsVeto.get();
    }

    public String getNewsReason() {
        return cachedNewsReason.get();
    }

    public void setNewsVeto(boolean veto, String reason) {
        cachedNewsVeto.set(veto);
        cachedNewsReason.set(reason);
    }
    
    public boolean isActivePolling() {
        return activePolling;
    }
    
    public void setActivePolling(boolean active) {
        this.activePolling = active;
    }

    public boolean isMultiStrategyExecutionEnabled() {
        return multiStrategyExecutionEnabled;
    }

    public void setMultiStrategyExecutionEnabled(boolean enabled) {
        this.multiStrategyExecutionEnabled = enabled;
    }
}
