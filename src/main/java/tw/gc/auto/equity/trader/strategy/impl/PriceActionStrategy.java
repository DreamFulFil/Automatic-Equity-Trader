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

@Slf4j
public class PriceActionStrategy implements IStrategy {
    
    private final Map<String, Deque<MarketData>> barHistory = new HashMap<>();
    
    public PriceActionStrategy() {
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<MarketData> bars = barHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        bars.addLast(data);
        if (bars.size() > 3) bars.removeFirst();
        
        if (bars.size() < 3) {
            return TradeSignal.neutral("Warming up Price Action");
        }
        
        MarketData[] b = bars.toArray(new MarketData[0]);
        
        // Bullish engulfing
        if (b[1].getClose() < b[1].getOpen() && 
            b[2].getClose() > b[2].getOpen() &&
            b[2].getOpen() < b[1].getClose() &&
            b[2].getClose() > b[1].getOpen()) {
            if (portfolio.getPosition(symbol) <= 0) {
                return TradeSignal.longSignal(0.8, "Bullish engulfing pattern");
            }
        }
        
        // Bearish engulfing
        if (b[1].getClose() > b[1].getOpen() && 
            b[2].getClose() < b[2].getOpen() &&
            b[2].getOpen() > b[1].getClose() &&
            b[2].getClose() < b[1].getOpen()) {
            if (portfolio.getPosition(symbol) >= 0) {
                return TradeSignal.shortSignal(0.8, "Bearish engulfing pattern");
            }
        }
        
        // Morning star
        if (b[0].getClose() < b[0].getOpen() &&
            Math.abs(b[1].getClose() - b[1].getOpen()) < (b[0].getOpen() - b[0].getClose()) * 0.3 &&
            b[2].getClose() > b[2].getOpen() &&
            b[2].getClose() > (b[0].getOpen() + b[0].getClose()) / 2) {
            if (portfolio.getPosition(symbol) <= 0) {
                return TradeSignal.longSignal(0.85, "Morning star pattern");
            }
        }
        
        return TradeSignal.neutral("No pattern detected");
    }

    @Override
    public String getName() {
        return "Price Action Patterns";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        barHistory.clear();
    }
}
