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
public class EnvelopeStrategy implements IStrategy {
    
    private final int period;
    private final double percentage;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public EnvelopeStrategy() {
        this(20, 0.025);
    }
    
    public EnvelopeStrategy(int period, double percentage) {
        this.period = period;
        this.percentage = percentage;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period) prices.removeFirst();
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up Envelope");
        }
        
        double sma = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double upper = sma * (1 + percentage);
        double lower = sma * (1 - percentage);
        
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        if (currentPrice < lower && position <= 0) {
            return TradeSignal.longSignal(0.75, "Price below envelope lower band");
        } else if (currentPrice > upper && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Price above envelope upper band");
        } else if (Math.abs(currentPrice - sma) / sma < 0.005 && position != 0) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.75, "Price returned to mean");
        }
        
        return TradeSignal.neutral("Price within envelope");
    }

    @Override
    public String getName() {
        return "Envelope Channel";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
