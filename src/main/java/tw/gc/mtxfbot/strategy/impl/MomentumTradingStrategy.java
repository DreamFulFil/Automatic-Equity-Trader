package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.*;
import java.util.*;

@Slf4j
public class MomentumTradingStrategy implements IStrategy {
    private final double momentumThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public MomentumTradingStrategy() {
        this(0.02);
        log.info("[Momentum] Strategy initialized: threshold={}%", momentumThreshold * 100);
    }
    
    public MomentumTradingStrategy(double momentumThreshold) {
        this.momentumThreshold = momentumThreshold;
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(data.getClose());
        if (prices.size() > 10) prices.removeFirst();
        
        if (prices.size() < 5) {
            return TradeSignal.neutral("Warming up");
        }
        
        double firstPrice = prices.peekFirst();
        double lastPrice = prices.peekLast();
        double momentum = (lastPrice - firstPrice) / firstPrice;
        
        if (momentum > momentumThreshold && portfolio.getPosition(symbol) == 0) {
            log.info("[Momentum] BUY signal: momentum={}%", momentum * 100);
            return TradeSignal.longSignal(0.75, String.format("Strong momentum: %.2f%%", momentum * 100));
        } else if (momentum < -momentumThreshold && portfolio.getPosition(symbol) == 0) {
            log.info("[Momentum] SHORT signal: momentum={}%", momentum * 100);
            return TradeSignal.shortSignal(0.75, String.format("Negative momentum: %.2f%%", momentum * 100));
        }
        
        return TradeSignal.neutral("No momentum signal");
    }
    
    @Override
    public String getName() { return "Momentum Trading"; }
    
    @Override
    public StrategyType getType() { return StrategyType.SHORT_TERM; }
    
    @Override
    public void reset() { priceHistory.clear(); }
}
