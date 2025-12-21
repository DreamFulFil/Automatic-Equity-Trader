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
 * FinancialDistressStrategy
 * Type: Risk Factor
 * 
 * Academic Foundation:
 * - Campbell, Hilscher & Szilagyi (2008) - 'In Search of Distress Risk'
 * 
 * Logic:
 * Avoid financially distressed firms using volatility and drawdown as proxy.
 */
@Slf4j
public class FinancialDistressStrategy implements IStrategy {
    
    private final double maxDistressProbability;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public FinancialDistressStrategy(double maxDistressProbability) {
        this.maxDistressProbability = maxDistressProbability;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 120) prices.removeFirst();
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up distress indicators");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate max drawdown (distress indicator)
        double peak = priceArray[0];
        double maxDrawdown = 0;
        for (Double p : priceArray) {
            if (p > peak) peak = p;
            double drawdown = (peak - p) / peak;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
        }
        
        // Calculate volatility
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double volatility = Math.sqrt(variance / priceArray.length) / avgPrice;
        
        // Distress probability proxy: high drawdown + high volatility
        double distressProb = maxDrawdown * 0.5 + volatility * 2;
        
        int position = portfolio.getPosition(symbol);
        
        // Low distress = good quality, go long
        if (distressProb < maxDistressProbability && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Low distress: prob=%.2f%% (dd=%.1f%%, vol=%.1f%%)", 
                    distressProb * 100, maxDrawdown * 100, volatility * 100));
        }
        
        // Exit if distress increases
        if (position > 0 && distressProb > maxDistressProbability * 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.75,
                String.format("Distress warning: prob=%.2f%%", distressProb * 100));
        }
        
        // Short high distress stocks
        if (distressProb > maxDistressProbability * 3 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("High distress short: prob=%.2f%%", distressProb * 100));
        }
        
        return TradeSignal.neutral(String.format("Distress prob: %.2f%%", distressProb * 100));
    }

    @Override
    public String getName() {
        return String.format("Financial Distress (max=%.0f%%)", maxDistressProbability * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
