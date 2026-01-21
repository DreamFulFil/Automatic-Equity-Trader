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
 * BidAskSpreadStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Roll (1984) - 'A Simple Implicit Measure of the Bid-Ask Spread'
 * 
 * Logic:
 * Exploit temporary liquidity imbalances using implied spread from price reversals.
 */
@Slf4j
public class BidAskSpreadStrategy implements IStrategy {
    
    private final double normalSpread;
    private final double wideSpreadThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public BidAskSpreadStrategy(double normalSpread, double wideSpreadThreshold) {
        this.normalSpread = normalSpread;
        this.wideSpreadThreshold = wideSpreadThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) {
            prices.removeFirst();
        }
        
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up spread calculation");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate implied spread using Roll's measure
        // Spread = 2 * sqrt(-cov(rt, rt-1)) where rt is return
        double sumCov = 0;
        int n = 0;
        for (int i = 2; i < priceArray.length; i++) {
            double r1 = (priceArray[i] - priceArray[i-1]) / priceArray[i-1];
            double r0 = (priceArray[i-1] - priceArray[i-2]) / priceArray[i-2];
            sumCov += r1 * r0;
            n++;
        }
        // n is guaranteed > 0 here because prices.size() >= 10 (loop runs at least 8 times)
        double cov = sumCov / n;
        double impliedSpread = cov < 0 ? 2 * Math.sqrt(-cov) : normalSpread;
        
        double currentPrice = priceArray[priceArray.length - 1];
        double prevPrice = priceArray[priceArray.length - 2];
        double priceChange = (currentPrice - prevPrice) / prevPrice;
        
        int position = portfolio.getPosition(symbol);
        
        // Wide spread + down move = buy opportunity (liquidity providing)
        if (impliedSpread > wideSpreadThreshold && priceChange < -impliedSpread / 2 && position <= 0) {
            return TradeSignal.longSignal(0.65,
                String.format("Wide spread buy: spread=%.3f%%, change=%.2f%%", 
                    impliedSpread * 100, priceChange * 100));
        }
        
        // Wide spread + up move = sell opportunity
        if (impliedSpread > wideSpreadThreshold && priceChange > impliedSpread / 2 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Wide spread sell: spread=%.3f%%", impliedSpread * 100));
        }
        
        // Normal spread = mean reversion trades
        if (impliedSpread <= normalSpread && Math.abs(priceChange) > normalSpread * 2) {
            if (priceChange < 0 && position <= 0) {
                return TradeSignal.longSignal(0.60,
                    String.format("Spread reversion long: change=%.2f%%", priceChange * 100));
            }
        }
        
        return TradeSignal.neutral(String.format("Implied spread: %.3f%%", impliedSpread * 100));
    }

    @Override
    public String getName() {
        return String.format("Bid-Ask Spread (%.2f%%/%.2f%%)", normalSpread * 100, wideSpreadThreshold * 100);
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
