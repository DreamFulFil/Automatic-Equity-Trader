package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.IStrategy;
import tw.gc.mtxfbot.strategy.Portfolio;
import tw.gc.mtxfbot.strategy.StrategyType;
import tw.gc.mtxfbot.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Moving Average Crossover Strategy
 * 
 * Classic momentum strategy that generates signals based on moving average crossovers.
 * When fast MA crosses above slow MA = BUY signal
 * When fast MA crosses below slow MA = SELL signal
 * 
 * Strategy Logic:
 * 1. Calculate fast and slow moving averages from price history
 * 2. Detect crossover events
 * 3. Generate LONG signal on golden cross (fast > slow)
 * 4. Generate SHORT signal on death cross (fast < slow)
 * 5. Exit position on opposite crossover
 * 
 * Configuration:
 * - fastPeriod: Fast MA period (default: 5)
 * - slowPeriod: Slow MA period (default: 20)
 * - minCrossoverConfidence: Minimum price movement to confirm crossover (default: 0.001 = 0.1%)
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
@Slf4j
public class MovingAverageCrossoverStrategy implements IStrategy {
    
    private final int fastPeriod;
    private final int slowPeriod;
    private final double minCrossoverConfidence;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Boolean> previousGoldenCross = new HashMap<>();
    
    public MovingAverageCrossoverStrategy() {
        this(5, 20, 0.001);
    }
    
    public MovingAverageCrossoverStrategy(int fastPeriod, int slowPeriod, double minCrossoverConfidence) {
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.minCrossoverConfidence = minCrossoverConfidence;
        log.info("[MA Crossover] Strategy initialized: fast={}, slow={}, minConfidence={}%",
                fastPeriod, slowPeriod, minCrossoverConfidence * 100);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            log.trace("[MA Crossover] No market data available");
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        double currentPrice = data.getClose();
        
        // Update price history
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(currentPrice);
        if (prices.size() > slowPeriod) {
            prices.removeFirst();
        }
        
        // Need enough data for slow MA
        if (prices.size() < slowPeriod) {
            log.trace("[MA Crossover] Insufficient data: {} / {} required", prices.size(), slowPeriod);
            return TradeSignal.neutral(String.format("Warming up (%d/%d)", prices.size(), slowPeriod));
        }
        
        // Calculate MAs
        double fastMA = calculateMA(prices, fastPeriod);
        double slowMA = calculateMA(prices, slowPeriod);
        double spread = (fastMA - slowMA) / slowMA;
        
        log.trace("[MA Crossover] Analysis: price={}, fastMA={}, slowMA={}, spread={}%",
                currentPrice, fastMA, slowMA, spread * 100);
        
        // Detect crossover
        boolean currentGoldenCross = fastMA > slowMA;
        Boolean prevCross = previousGoldenCross.get(symbol);
        previousGoldenCross.put(symbol, currentGoldenCross);
        
        // Need previous state to detect crossover
        if (prevCross == null) {
            return TradeSignal.neutral("Establishing baseline");
        }
        
        int currentPosition = portfolio.getPosition(symbol);
        
        // Golden cross: fast MA crosses above slow MA
        if (!prevCross && currentGoldenCross && Math.abs(spread) >= minCrossoverConfidence) {
            if (currentPosition <= 0) {
                log.info("[MA Crossover] GOLDEN CROSS: BUY signal (fast={}, slow={}, spread=+{}%)",
                        fastMA, slowMA, spread * 100);
                return TradeSignal.longSignal(0.75 + Math.min(spread * 10, 0.2),
                        String.format("Golden cross: fast MA crossed above slow MA (spread: +%.2f%%)", spread * 100));
            } else {
                return TradeSignal.neutral("Already long");
            }
        }
        
        // Death cross: fast MA crosses below slow MA
        if (prevCross && !currentGoldenCross && Math.abs(spread) >= minCrossoverConfidence) {
            if (currentPosition > 0) {
                log.info("[MA Crossover] DEATH CROSS: EXIT long (fast={}, slow={}, spread={}%)",
                        fastMA, slowMA, spread * 100);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.75,
                        String.format("Death cross: fast MA crossed below slow MA (spread: %.2f%%)", spread * 100));
            } else if (currentPosition == 0) {
                log.info("[MA Crossover] DEATH CROSS: SHORT signal (fast={}, slow={}, spread={}%)",
                        fastMA, slowMA, spread * 100);
                return TradeSignal.shortSignal(0.75 + Math.min(Math.abs(spread) * 10, 0.2),
                        String.format("Death cross: fast MA crossed below slow MA (spread: %.2f%%)", spread * 100));
            } else {
                return TradeSignal.neutral("Already short");
            }
        }
        
        return TradeSignal.neutral("No crossover detected");
    }
    
    private double calculateMA(Deque<Double> prices, int period) {
        return prices.stream()
                .skip(Math.max(0, prices.size() - period))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }
    
    @Override
    public String getName() {
        return "Moving Average Crossover";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }
    
    @Override
    public void reset() {
        log.info("[MA Crossover] Resetting strategy state");
        priceHistory.clear();
        previousGoldenCross.clear();
    }
    
    // Package-private for testing
    Deque<Double> getPriceHistory(String symbol) {
        return priceHistory.get(symbol);
    }
    
    Boolean getPreviousGoldenCross(String symbol) {
        return previousGoldenCross.get(symbol);
    }
}
