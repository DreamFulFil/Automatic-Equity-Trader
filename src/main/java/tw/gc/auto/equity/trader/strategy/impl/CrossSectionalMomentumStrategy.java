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
 * CrossSectionalMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Jegadeesh & Titman (1993) - 'Returns to Buying Winners'
 * 
 * Logic:
 * Rank stocks by past returns. Buy winners (top N percentile), avoid/short losers.
 * Simplified single-stock version: compare stock's momentum to a threshold percentile.
 */
@Slf4j
public class CrossSectionalMomentumStrategy implements IStrategy {
    
    private final int rankPeriod;
    private final int topN;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> returnHistory = new HashMap<>();
    
    public CrossSectionalMomentumStrategy(int rankPeriod, int topN) {
        this.rankPeriod = rankPeriod;
        this.topN = topN;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> returns = returnHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double currentPrice = data.getClose();
        
        if (!prices.isEmpty()) {
            double lastPrice = prices.getLast();
            double periodReturn = (currentPrice - lastPrice) / lastPrice;
            returns.addLast(periodReturn);
            if (returns.size() > rankPeriod * 2) {
                returns.removeFirst();
            }
        }
        
        prices.addLast(currentPrice);
        if (prices.size() > rankPeriod + 10) {
            prices.removeFirst();
        }
        
        if (prices.size() < rankPeriod) {
            return TradeSignal.neutral("Warming up - need " + rankPeriod + " prices");
        }
        
        // Calculate cumulative return over rank period
        Double[] priceArray = prices.toArray(new Double[0]);
        double pastPrice = priceArray[priceArray.length - 1 - rankPeriod];
        double momentum = (currentPrice - pastPrice) / pastPrice;
        
        // Calculate historical return distribution (for ranking threshold)
        Double[] retArray = returns.toArray(new Double[0]);
        if (retArray.length < 10) {
            return TradeSignal.neutral("Need more return history for ranking");
        }
        
        // Sort returns to find percentile thresholds
        double[] sortedReturns = new double[retArray.length];
        for (int i = 0; i < retArray.length; i++) {
            sortedReturns[i] = retArray[i];
        }
        java.util.Arrays.sort(sortedReturns);
        
        // Top N percentile threshold
        int topIndex = (int) ((100 - topN) / 100.0 * sortedReturns.length);
        double topThreshold = sortedReturns[Math.min(topIndex, sortedReturns.length - 1)];
        
        // Bottom N percentile threshold  
        int bottomIndex = (int) (topN / 100.0 * sortedReturns.length);
        double bottomThreshold = sortedReturns[Math.max(bottomIndex, 0)];
        
        int position = portfolio.getPosition(symbol);
        double dailyReturn = returns.isEmpty() ? 0 : returns.getLast();
        
        // Buy winners (in top N percentile)
        if (dailyReturn > topThreshold && momentum > 0 && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Cross-sectional winner: ret=%.2f%% > %.2f%% (top %d%%)", 
                    dailyReturn * 100, topThreshold * 100, topN));
        }
        
        // Short losers (in bottom N percentile)
        if (dailyReturn < bottomThreshold && momentum < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.70,
                String.format("Cross-sectional loser: ret=%.2f%% < %.2f%% (bottom %d%%)", 
                    dailyReturn * 100, bottomThreshold * 100, topN));
        }
        
        // Exit positions when momentum reverses
        if (position > 0 && momentum < 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Momentum reversal: %.2f%%", momentum * 100));
        }
        
        if (position < 0 && momentum > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.65,
                String.format("Momentum reversal: %.2f%%", momentum * 100));
        }
        
        return TradeSignal.neutral(String.format("Momentum: %.2f%%", momentum * 100));
    }

    @Override
    public String getName() {
        return String.format("Cross-Sectional Momentum (%d, top%d)", rankPeriod, topN);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        returnHistory.clear();
    }
}
