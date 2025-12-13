package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.IStrategy;
import tw.gc.mtxfbot.strategy.Portfolio;
import tw.gc.mtxfbot.strategy.StrategyType;
import tw.gc.mtxfbot.strategy.TradeSignal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * VWAP (Volume-Weighted Average Price) Execution Strategy
 * 
 * Algorithmic execution strategy that aims to execute orders close to the 
 * volume-weighted average price over a trading period. Minimizes market impact
 * by slicing orders based on volume distribution.
 * 
 * Strategy Logic:
 * 1. Calculate current VWAP from price and volume history
 * 2. Execute buy orders when price is below VWAP (good fill)
 * 3. Execute sell orders when price is above VWAP (good fill)
 * 4. Pace execution across the trading window
 * 5. Increase urgency as end of window approaches
 * 
 * Configuration:
 * - targetVolume: Total volume/quantity to execute
 * - executionWindow: Time window for execution (minutes)
 * - vwapDeviationThreshold: Max acceptable deviation from VWAP (default: 0.003 = 0.3%)
 * - sliceSize: Size of each execution slice (default: 1 unit)
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
@Slf4j
public class VWAPExecutionStrategy implements IStrategy {
    
    private final int targetVolume;
    private final int executionWindowMinutes;
    private final double vwapDeviationThreshold;
    private final int sliceSize;
    
    private final Map<String, Deque<VWAPDataPoint>> vwapHistory = new HashMap<>();
    private final Map<String, Integer> executedVolume = new HashMap<>();
    private final Map<String, LocalDateTime> startTime = new HashMap<>();
    
    private static class VWAPDataPoint {
        final double price;
        final long volume;
        
        VWAPDataPoint(double price, long volume) {
            this.price = price;
            this.volume = volume;
        }
    }
    
    public VWAPExecutionStrategy(int targetVolume, int executionWindowMinutes) {
        this(targetVolume, executionWindowMinutes, 0.003, 1);
    }
    
    public VWAPExecutionStrategy(int targetVolume, int executionWindowMinutes, 
                                 double vwapDeviationThreshold, int sliceSize) {
        this.targetVolume = targetVolume;
        this.executionWindowMinutes = executionWindowMinutes;
        this.vwapDeviationThreshold = vwapDeviationThreshold;
        this.sliceSize = sliceSize;
        log.info("[VWAP] Strategy initialized: targetVol={}, window={}min, maxDeviation={}%, slice={}",
                targetVolume, executionWindowMinutes, vwapDeviationThreshold * 100, sliceSize);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            log.trace("[VWAP] No market data available");
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        double currentPrice = data.getClose();
        long currentVolume = data.getVolume();
        LocalDateTime now = LocalDateTime.now();
        
        // Initialize start time on first call
        startTime.putIfAbsent(symbol, now);
        LocalDateTime start = startTime.get(symbol);
        long minutesElapsed = Duration.between(start, now).toMinutes();
        
        // Check if execution window expired
        if (minutesElapsed >= executionWindowMinutes) {
            int executed = executedVolume.getOrDefault(symbol, 0);
            if (executed < targetVolume) {
                log.info("[VWAP] URGENT: Window expiring, execute remaining volume ({}/{})",
                        executed, targetVolume);
                // At window end, execute regardless of price
                return TradeSignal.longSignal(0.95,
                        String.format("Urgent: window closing (%d/%d executed)", executed, targetVolume));
            } else {
                log.info("[VWAP] Target volume reached: {}/{}", executed, targetVolume);
                return TradeSignal.neutral("Target volume reached");
            }
        }
        
        // Update VWAP history
        Deque<VWAPDataPoint> history = vwapHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        history.addLast(new VWAPDataPoint(currentPrice, currentVolume));
        if (history.size() > 60) { // Keep last 60 data points
            history.removeFirst();
        }
        
        // Need at least 3 data points for meaningful VWAP
        if (history.size() < 3) {
            log.trace("[VWAP] Insufficient data: {} / 3 required", history.size());
            return TradeSignal.neutral("Warming up VWAP calculation");
        }
        
        // Calculate VWAP
        double totalPV = 0.0;
        long totalVol = 0L;
        for (VWAPDataPoint dp : history) {
            totalPV += dp.price * dp.volume;
            totalVol += dp.volume;
        }
        double vwap = totalVol > 0 ? totalPV / totalVol : currentPrice;
        double deviation = (currentPrice - vwap) / vwap;
        
        log.trace("[VWAP] price={}, vwap={}, deviation={}%, elapsed={}min",
                currentPrice, vwap, deviation * 100, minutesElapsed);
        
        int executed = executedVolume.getOrDefault(symbol, 0);
        int remaining = targetVolume - executed;
        
        if (remaining <= 0) {
            return TradeSignal.neutral("Target volume reached");
        }
        
        // Calculate pace: how much should be executed by now
        double expectedPace = (double) minutesElapsed / executionWindowMinutes;
        int expectedExecuted = (int) (targetVolume * expectedPace);
        boolean behindPace = executed < expectedExecuted;
        
        // Good price = below VWAP (for buy orders)
        boolean goodPrice = deviation <= 0 && Math.abs(deviation) <= vwapDeviationThreshold;
        
        // Execute if: good price OR behind pace OR near end of window
        double urgencyFactor = (double) minutesElapsed / executionWindowMinutes;
        boolean shouldExecute = goodPrice || behindPace || urgencyFactor > 0.7;
        
        if (shouldExecute) {
            int execSize = Math.min(sliceSize, remaining);
            executedVolume.put(symbol, executed + execSize);
            
            double confidence = goodPrice ? 0.85 : (0.60 + urgencyFactor * 0.30);
            log.info("[VWAP] EXECUTE: slice={}, price={} vs vwap={}, progress={}/{}",
                    execSize, currentPrice, vwap, executed + execSize, targetVolume);
            
            String reason = String.format("VWAP execution: price %.2f vs vwap %.2f (progress: %d/%d)",
                    currentPrice, vwap, executed + execSize, targetVolume);
            return TradeSignal.longSignal(confidence, reason);
        }
        
        return TradeSignal.neutral(String.format("Waiting for better price (current: %.2f, vwap: %.2f)",
                currentPrice, vwap));
    }
    
    @Override
    public String getName() {
        return "VWAP Execution Algorithm";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }
    
    @Override
    public void reset() {
        log.info("[VWAP] Resetting strategy state");
        vwapHistory.clear();
        executedVolume.clear();
        startTime.clear();
    }
    
    // Package-private for testing
    Deque<VWAPDataPoint> getVwapHistory(String symbol) {
        return vwapHistory.get(symbol);
    }
    
    Integer getExecutedVolume(String symbol) {
        return executedVolume.get(symbol);
    }
}
