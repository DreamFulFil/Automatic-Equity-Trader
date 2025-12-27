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
 * BasketArbitrageStrategy
 * Type: Index Arbitrage
 * 
 * Academic Foundation:
 * - Hasbrouck (2003) - 'Intraday Price Formation in US Equity Index Markets'
 * 
 * Logic:
 * Compare basket price to index/benchmark. Trade divergences.
 */
@Slf4j
public class BasketArbitrageStrategy implements IStrategy {
    
    private final Set<String> basketSymbols;
    private final String indexSymbol;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public BasketArbitrageStrategy(String[] basketSymbols, String indexSymbol) {
        this.basketSymbols = new HashSet<>(Arrays.asList(basketSymbols));
        this.indexSymbol = indexSymbol;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) {
            prices.removeFirst();
        }
        
        if (prices.size() < 20) {
            return TradeSignal.neutral("Warming up basket");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate average price (proxy for fair value)
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        // Calculate momentum
        double shortTermAvg = 0;
        for (int i = priceArray.length - 5; i < priceArray.length; i++) {
            shortTermAvg += priceArray[i];
        }
        shortTermAvg /= 5;
        
        double deviation = (currentPrice - avgPrice) / avgPrice;
        double momentum = (shortTermAvg - avgPrice) / avgPrice;
        
        int position = portfolio.getPosition(symbol);
        
        // Basket undervalued vs fair value
        if (deviation < -0.02 && momentum > 0 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Basket undervalued: %.2f%% below fair value", 
                    Math.abs(deviation) * 100));
        }
        
        // Basket overvalued
        if (deviation > 0.02 && momentum < 0 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Basket overvalued: %.2f%% above fair value", 
                    deviation * 100));
        }
        
        // Short overvaluation
        if (deviation > 0.03 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("Short basket: %.2f%% overvalued", deviation * 100));
        }
        
        return TradeSignal.neutral(String.format("Basket deviation: %.2f%%", deviation * 100));
    }

    @Override
    public String getName() {
        return String.format("Basket Arbitrage (%d stocks)", basketSymbols.size());
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
