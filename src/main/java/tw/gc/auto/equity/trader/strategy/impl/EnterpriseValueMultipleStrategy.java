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
 * EnterpriseValueMultipleStrategy
 * Type: Value Factor
 * 
 * Academic Foundation:
 * - Piotroski & So (2012) - 'Identifying Expectation Errors'
 * 
 * Logic:
 * Buy stocks with low EV/EBITDA proxy (trading significantly below average).
 */
@Slf4j
public class EnterpriseValueMultipleStrategy implements IStrategy {
    
    private final double maxEVToEBITDA;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public EnterpriseValueMultipleStrategy(double maxEVToEBITDA) {
        this.maxEVToEBITDA = maxEVToEBITDA;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > 120) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up EV multiple");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate average market cap proxy
        double avgMktCap = 0;
        for (int i = 0; i < priceArray.length; i++) {
            avgMktCap += priceArray[i] * volumeArray[i];
        }
        avgMktCap /= priceArray.length;
        
        double currentMktCap = currentPrice * volumeArray[volumeArray.length - 1];
        
        // EV/EBITDA proxy: price relative to "earnings" (volume-weighted average)
        double evMultiple = avgMktCap > 0 ? (currentMktCap / avgMktCap) * maxEVToEBITDA : maxEVToEBITDA;
        
        // Also consider price-to-average as value indicator
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        double priceRatio = currentPrice / avgPrice;
        
        double impliedMultiple = evMultiple * priceRatio;
        
        int position = portfolio.getPosition(symbol);
        
        // Low multiple = value (undervalued)
        if (impliedMultiple < maxEVToEBITDA && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Low EV multiple: %.1fx < %.1fx", impliedMultiple, maxEVToEBITDA));
        }
        
        // Exit when multiple expands
        if (position > 0 && impliedMultiple > maxEVToEBITDA * 1.5) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("EV multiple expanded: %.1fx", impliedMultiple));
        }
        
        // Short very high multiples
        if (impliedMultiple > maxEVToEBITDA * 2 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("High EV multiple: %.1fx", impliedMultiple));
        }
        
        return TradeSignal.neutral(String.format("EV multiple: %.1fx", impliedMultiple));
    }

    @Override
    public String getName() {
        return String.format("EV Multiple (max=%.0fx)", maxEVToEBITDA);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
    }
}
