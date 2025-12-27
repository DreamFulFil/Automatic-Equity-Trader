package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.*;
import java.time.*;
import java.util.*;

@Slf4j
public class TWAPExecutionStrategy implements IStrategy {
    private final int targetVolume;
    private final int executionWindowMinutes;
    private final Map<String, Integer> executedVolume = new HashMap<>();
    private final Map<String, LocalDateTime> startTime = new HashMap<>();
    
    public TWAPExecutionStrategy(int targetVolume, int executionWindowMinutes) {
        this.targetVolume = targetVolume;
        this.executionWindowMinutes = executionWindowMinutes;
        log.info("[TWAP] Strategy initialized: target={}, window={}min", targetVolume, executionWindowMinutes);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        LocalDateTime now = LocalDateTime.now();
        startTime.putIfAbsent(symbol, now);
        
        long minutesElapsed = Duration.between(startTime.get(symbol), now).toMinutes();
        int executed = executedVolume.getOrDefault(symbol, 0);
        int remaining = targetVolume - executed;
        
        if (remaining <= 0) {
            return TradeSignal.neutral("Target volume reached");
        }
        
        if (minutesElapsed >= executionWindowMinutes) {
            log.info("[TWAP] URGENT: Execute remaining {}", remaining);
            return TradeSignal.longSignal(0.95, "Urgent: window closing");
        }
        
        double expectedPace = (double) minutesElapsed / executionWindowMinutes;
        int expectedExecuted = (int) (targetVolume * expectedPace);
        
        if (executed < expectedExecuted) {
            executedVolume.put(symbol, executed + 1);
            log.info("[TWAP] EXECUTE: progress={}/{}", executed + 1, targetVolume);
            return TradeSignal.longSignal(0.80, "TWAP slice execution");
        }
        
        return TradeSignal.neutral("On pace");
    }
    
    @Override
    public String getName() { return "TWAP Execution Algorithm"; }
    
    @Override
    public StrategyType getType() { return StrategyType.SHORT_TERM; }
    
    @Override
    public void reset() {
        executedVolume.clear();
        startTime.clear();
    }
}
