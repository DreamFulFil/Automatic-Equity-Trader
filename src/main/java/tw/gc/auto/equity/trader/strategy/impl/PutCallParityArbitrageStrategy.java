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
 * PutCallParityArbitrageStrategy
 * Type: Options Arbitrage
 * 
 * Academic Foundation:
 * - Stoll (1969) - 'The Relationship Between Put and Call Option Prices'
 * 
 * Logic:
 * Trade deviations from fair value using implied volatility proxy.
 */
@Slf4j
public class PutCallParityArbitrageStrategy implements IStrategy {
    
    private final double minDeviation;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public PutCallParityArbitrageStrategy(double minDeviation) {
        this.minDeviation = minDeviation;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) prices.removeFirst();
        
        if (prices.size() < 30) {
            return TradeSignal.neutral("Warming up parity arb");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate fair value (moving average)
        double fairValue = 0;
        for (Double p : priceArray) fairValue += p;
        fairValue /= priceArray.length;
        
        // Calculate implied volatility proxy
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - fairValue, 2);
        double impliedVol = Math.sqrt(variance / priceArray.length) / fairValue;
        
        // Deviation from parity
        double deviation = (currentPrice - fairValue) / fairValue;
        double adjustedDeviation = deviation / Math.max(impliedVol, 0.01);
        
        int position = portfolio.getPosition(symbol);
        
        // Undervalued (buy)
        if (adjustedDeviation < -minDeviation && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Parity arb long: dev=%.2f%% (adj=%.2f)", 
                    deviation * 100, adjustedDeviation));
        }
        
        // Overvalued (sell)
        if (adjustedDeviation > minDeviation && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Parity arb exit: dev=%.2f%%", deviation * 100));
        }
        
        // Short overvalued
        if (adjustedDeviation > minDeviation * 2 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Parity arb short: adj dev=%.2f", adjustedDeviation));
        }
        
        return TradeSignal.neutral(String.format("Parity dev: %.2f%%", deviation * 100));
    }

    @Override
    public String getName() {
        return String.format("Put-Call Parity Arb (%.1f%%)", minDeviation * 100);
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
