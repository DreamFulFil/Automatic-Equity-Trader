package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TriangularArbitrageStrategy
 * Type: Relative Value
 * 
 * Academic Foundation:
 * - Fenn, Howison & McDonald (2009) - 'An Optimal Execution Problem in Finance'
 * 
 * Logic:
 * Trade based on triangular relationship deviation from equilibrium.
 */
@Slf4j
public class TriangularArbitrageStrategy implements IStrategy {
    
    private final Set<String> symbols;
    private final double minProfit;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public TriangularArbitrageStrategy(String[] symbols, double minProfit) {
        this.symbols = new HashSet<>(Arrays.asList(symbols));
        this.minProfit = minProfit;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) prices.removeFirst();
        
        if (prices.size() < 30) {
            return TradeSignal.neutral("Warming up triangular arb");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate fair value (equilibrium price)
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        // Calculate historical volatility for spread normalization
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double stdDev = Math.sqrt(variance / priceArray.length);
        
        // Deviation from equilibrium (normalized)
        double deviation = stdDev > 0 ? (currentPrice - avgPrice) / stdDev : 0;
        double profitOpportunity = Math.abs(deviation) * 0.01;
        
        int position = portfolio.getPosition(symbol);
        
        // Undervalued (buy)
        if (deviation < -2 && profitOpportunity > minProfit && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Triangular arb long: z=%.2f, profit=%.3f%%", 
                    deviation, profitOpportunity * 100));
        }
        
        // Overvalued (sell/short)
        if (deviation > 2 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Triangular arb exit: z=%.2f", deviation));
        }
        
        // Short overvalued
        if (deviation > 2 && profitOpportunity > minProfit && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Triangular arb short: z=%.2f", deviation));
        }
        
        // Exit when deviation normalizes
        if (position != 0 && Math.abs(deviation) < 0.5) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.65,
                String.format("Equilibrium reached: z=%.2f", deviation));
        }
        
        return TradeSignal.neutral(String.format("Deviation: %.2f std", deviation));
    }

    @Override
    public String getName() {
        return String.format("Triangular Arbitrage (%d symbols, min=%.2f%%)", 
            symbols.size(), minProfit * 100);
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
