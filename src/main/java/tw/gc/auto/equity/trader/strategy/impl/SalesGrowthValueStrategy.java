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
 * SalesGrowthValueStrategy
 * Type: Growth at Reasonable Price
 * 
 * Academic Foundation:
 * - Barbee, Mukherji & Raines (1996) - 'Do Sales-Price Ratios Have Explanatory Power?'
 * 
 * Logic:
 * Low price-to-sales with positive growth trajectory.
 */
@Slf4j
public class SalesGrowthValueStrategy implements IStrategy {
    
    private final double maxPriceToSales;
    private final double minGrowthRate;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public SalesGrowthValueStrategy(double maxPriceToSales, double minGrowthRate) {
        this.maxPriceToSales = maxPriceToSales;
        this.minGrowthRate = minGrowthRate;
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
            return TradeSignal.neutral("Warming up sales growth");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Price-to-sales proxy: current price vs volume-weighted average
        double vwap = 0;
        long totalVol = 0;
        for (int i = 0; i < priceArray.length; i++) {
            vwap += priceArray[i] * volumeArray[i];
            totalVol += volumeArray[i];
        }
        vwap = totalVol > 0 ? vwap / totalVol : currentPrice;
        double priceToSalesProxy = currentPrice / vwap;
        
        // Growth rate
        int half = priceArray.length / 2;
        double pastAvg = 0, recentAvg = 0;
        for (int i = 0; i < half; i++) pastAvg += priceArray[i];
        for (int i = half; i < priceArray.length; i++) recentAvg += priceArray[i];
        pastAvg /= half;
        recentAvg /= (priceArray.length - half);
        double growthRate = (recentAvg - pastAvg) / pastAvg;
        
        int position = portfolio.getPosition(symbol);
        
        // Low P/S with growth
        if (priceToSalesProxy < maxPriceToSales && growthRate > minGrowthRate && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Sales Growth Value: P/S=%.2f, growth=%.1f%%", 
                    priceToSalesProxy, growthRate * 100));
        }
        
        // Exit if valuation expands or growth stalls
        if (position > 0 && (priceToSalesProxy > maxPriceToSales * 1.5 || growthRate < 0)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("SGV exit: P/S=%.2f, growth=%.1f%%", priceToSalesProxy, growthRate * 100));
        }
        
        return TradeSignal.neutral(String.format("P/S=%.2f, growth=%.1f%%", 
            priceToSalesProxy, growthRate * 100));
    }

    @Override
    public String getName() {
        return String.format("Sales Growth Value (P/S<%.1f, growth>%.0f%%)", 
            maxPriceToSales, minGrowthRate * 100);
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
