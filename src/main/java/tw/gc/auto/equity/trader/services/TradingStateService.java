package tw.gc.auto.equity.trader.services;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Data
public class TradingStateService {

    // Trading mode: "stock" or "futures"
    private String tradingMode = "stock";
    
    // State flags
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    private volatile boolean tradingPaused = false;
    
    // News veto cache
    private final AtomicBoolean cachedNewsVeto = new AtomicBoolean(false);
    private final AtomicReference<String> cachedNewsReason = new AtomicReference<>("");
    
    // Strategy Selection
    private String activeStrategyName;

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
}
