package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * IndexArbitrageStrategy
 * Type: Statistical Arbitrage
 * 
 * Academic Foundation:
 * - Pontiff (1996) - 'Costly Arbitrage: Evidence from Closed-End Funds'
 * 
 * Logic:
 * Trade deviation from fair value (moving average) when price diverges.
 */
@Slf4j
public class IndexArbitrageStrategy implements IStrategy {
    
    private final String indexSymbol;
    private final double threshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public IndexArbitrageStrategy(String indexSymbol, double threshold) {
        this.indexSymbol = indexSymbol;
        this.threshold = threshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) prices.removeFirst();
        
        if (prices.size() < 20) {
            return TradeSignal.neutral("Warming up index arb");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate fair value (moving average as index proxy)
        double fairValue = 0;
        for (Double p : priceArray) fairValue += p;
        fairValue /= priceArray.length;
        
        double deviation = (currentPrice - fairValue) / fairValue;
        
        int position = portfolio.getPosition(symbol);
        
        // Stock undervalued vs index
        if (deviation < -threshold && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Index arb long: %.2f%% below fair value", Math.abs(deviation) * 100));
        }
        
        // Stock overvalued vs index
        if (deviation > threshold && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Index arb exit: %.2f%% above fair value", deviation * 100));
        }
        
        // Short overvalued
        if (deviation > threshold * 2 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Index arb short: %.2f%% overvalued", deviation * 100));
        }
        
        return TradeSignal.neutral(String.format("Index deviation: %.2f%%", deviation * 100));
    }

    @Override
    public String getName() {
        return String.format("Index Arbitrage (%s, %.1f%%)", indexSymbol, threshold * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
