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
 * NewsRevisionMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Chan, Jegadeesh & Lakonishok (1996) - 'Momentum Strategies'
 * 
 * Logic:
 * Trade on earnings/analyst revision momentum using price action as proxy.
 * Significant price moves on high volume suggest news/revision events.
 */
@Slf4j
public class NewsRevisionMomentumStrategy implements IStrategy {
    
    private final int revisionLookback;
    private final double significanceThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    private final Map<String, Double> eventPrices = new HashMap<>();
    
    public NewsRevisionMomentumStrategy(int revisionLookback, double significanceThreshold) {
        this.revisionLookback = revisionLookback;
        this.significanceThreshold = significanceThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        
        if (prices.size() > revisionLookback + 10) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < revisionLookback) {
            return TradeSignal.neutral("Warming up - need " + revisionLookback + " bars");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        double currentPrice = priceArray[priceArray.length - 1];
        double pastPrice = priceArray[priceArray.length - 2];
        double dailyReturn = (currentPrice - pastPrice) / pastPrice;
        
        // Calculate average volume
        double avgVolume = 0;
        for (int i = 0; i < volumeArray.length - 1; i++) {
            avgVolume += volumeArray[i];
        }
        avgVolume /= (volumeArray.length - 1);
        
        long currentVolume = volumeArray[volumeArray.length - 1];
        double volumeRatio = avgVolume > 0 ? currentVolume / avgVolume : 1;
        
        // Detect significant news/revision event: large price move + high volume
        boolean significantEvent = Math.abs(dailyReturn) > significanceThreshold && volumeRatio > 1.5;
        
        int position = portfolio.getPosition(symbol);
        
        // Positive revision event: large up move + high volume
        if (significantEvent && dailyReturn > 0 && position <= 0) {
            eventPrices.put(symbol, currentPrice);
            return TradeSignal.longSignal(0.80,
                String.format("Positive revision signal: %.2f%% move, %.1fx volume", 
                    dailyReturn * 100, volumeRatio));
        }
        
        // Negative revision event: large down move + high volume
        if (significantEvent && dailyReturn < 0 && position >= 0) {
            eventPrices.put(symbol, currentPrice);
            return TradeSignal.shortSignal(0.75,
                String.format("Negative revision signal: %.2f%% move, %.1fx volume", 
                    dailyReturn * 100, volumeRatio));
        }
        
        // Follow-through: momentum continues in direction of event
        Double eventPrice = eventPrices.get(symbol);
        if (eventPrice != null && position != 0) {
            double pnlFromEvent = (currentPrice - eventPrice) / eventPrice;
            
            // Exit if momentum fades or reverses
            if ((position > 0 && pnlFromEvent < -significanceThreshold / 2) ||
                (position < 0 && pnlFromEvent > significanceThreshold / 2)) {
                eventPrices.remove(symbol);
                return TradeSignal.exitSignal(
                    position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                    0.70,
                    String.format("Revision momentum faded: PnL %.2f%%", pnlFromEvent * 100));
            }
        }
        
        return TradeSignal.neutral(String.format("Return: %.2f%%, Vol ratio: %.1fx", 
            dailyReturn * 100, volumeRatio));
    }

    @Override
    public String getName() {
        return String.format("News Revision Momentum (%d, %.1f%%)", 
            revisionLookback, significanceThreshold * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
        eventPrices.clear();
    }
}
