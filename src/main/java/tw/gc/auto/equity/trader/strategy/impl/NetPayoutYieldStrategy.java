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
 * NetPayoutYieldStrategy
 * Type: Income / Value
 * 
 * Academic Foundation:
 * - Boudoukh et al. (2007) - 'On the Importance of Measuring Payout Yield'
 * 
 * Logic:
 * Total shareholder yield proxy (dividends + buybacks) using price stability.
 */
@Slf4j
public class NetPayoutYieldStrategy implements IStrategy {
    
    private final double minTotalYield;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public NetPayoutYieldStrategy(double minTotalYield) {
        this.minTotalYield = minTotalYield;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 252) prices.removeFirst();
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up payout yield");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate average and stability
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double volatility = Math.sqrt(variance / priceArray.length) / avgPrice;
        
        // Net payout yield proxy: stable price, trading below average
        double valueComponent = (avgPrice - currentPrice) / avgPrice;
        double stabilityComponent = 1 / (1 + volatility * 5);
        double impliedYield = (valueComponent > 0 ? valueComponent * 0.3 : 0) + stabilityComponent * 0.05;
        
        int position = portfolio.getPosition(symbol);
        
        // High payout yield
        if (impliedYield > minTotalYield && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("High net payout: yield=%.2f%% (stability=%.2f)", 
                    impliedYield * 100, stabilityComponent));
        }
        
        // Exit if yield deteriorates
        if (position > 0 && impliedYield < minTotalYield / 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Payout yield exit: %.2f%%", impliedYield * 100));
        }
        
        return TradeSignal.neutral(String.format("Net payout yield: %.2f%%", impliedYield * 100));
    }

    @Override
    public String getName() {
        return String.format("Net Payout Yield (min=%.1f%%)", minTotalYield * 100);
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
