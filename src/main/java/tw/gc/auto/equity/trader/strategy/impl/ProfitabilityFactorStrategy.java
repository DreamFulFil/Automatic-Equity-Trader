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
 * ProfitabilityFactorStrategy
 * Type: Quality Factor
 * 
 * Academic Foundation:
 * - Novy-Marx (2013) - 'The Other Side of Value'
 * 
 * Logic:
 * High gross profitability stocks outperform. Use price stability as proxy.
 */
@Slf4j
public class ProfitabilityFactorStrategy implements IStrategy {
    
    private final double minGrossProfitability;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public ProfitabilityFactorStrategy(double minGrossProfitability) {
        this.minGrossProfitability = minGrossProfitability;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 120) prices.removeFirst();
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up profitability factor");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate profitability proxy metrics
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        // Positive returns (profitability proxy)
        int positiveReturns = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) positiveReturns++;
        }
        double winRate = (double) positiveReturns / (priceArray.length - 1);
        
        // Price stability (quality proxy)
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double volatility = Math.sqrt(variance / priceArray.length) / avgPrice;
        double stability = 1 / (1 + volatility * 5);
        
        // Gross profitability proxy
        double profitabilityProxy = winRate * stability;
        
        int position = portfolio.getPosition(symbol);
        
        // High profitability
        if (profitabilityProxy > minGrossProfitability && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("High profitability: proxy=%.2f (winRate=%.1f%%, stability=%.2f)", 
                    profitabilityProxy, winRate * 100, stability));
        }
        
        // Exit if profitability deteriorates
        if (position > 0 && profitabilityProxy < minGrossProfitability / 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Profitability decline: %.2f", profitabilityProxy));
        }
        
        return TradeSignal.neutral(String.format("Profitability: %.2f", profitabilityProxy));
    }

    @Override
    public String getName() {
        return String.format("Profitability Factor (min=%.0f%%)", minGrossProfitability * 100);
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
