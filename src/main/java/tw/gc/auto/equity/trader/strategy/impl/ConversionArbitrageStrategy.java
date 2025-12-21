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
 * ConversionArbitrageStrategy
 * Type: Convertible Arbitrage
 * 
 * Academic Foundation:
 * - Mitchell, Pulvino & Stafford (2002) - 'Limited Arbitrage in Equity Markets'
 * 
 * Logic:
 * Trade price discrepancies using relative valuation proxy.
 * When stock underperforms implied value, go long expecting convergence.
 */
@Slf4j
public class ConversionArbitrageStrategy implements IStrategy {
    
    private final String bondSymbol;
    private final String stockSymbol;
    private final double minSpread;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public ConversionArbitrageStrategy(String bondSymbol, String stockSymbol, double minSpread) {
        this.bondSymbol = bondSymbol;
        this.stockSymbol = stockSymbol;
        this.minSpread = minSpread;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) {
            prices.removeFirst();
        }
        
        if (prices.size() < 30) {
            return TradeSignal.neutral("Warming up conversion arb");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate implied conversion value (simplified: moving average)
        double impliedValue = 0;
        for (Double p : priceArray) impliedValue += p;
        impliedValue /= priceArray.length;
        
        // Spread between current price and implied value
        double spread = (impliedValue - currentPrice) / impliedValue;
        
        // Volatility for confidence adjustment
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - impliedValue, 2);
        double stdDev = Math.sqrt(variance / priceArray.length);
        double relativeVol = stdDev / impliedValue;
        
        int position = portfolio.getPosition(symbol);
        
        // Long when stock is undervalued vs implied conversion
        if (spread > minSpread && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Conversion arb long: spread=%.2f%% (implied=%.2f)", 
                    spread * 100, impliedValue));
        }
        
        // Exit when spread narrows
        if (position > 0 && spread < minSpread / 3) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Spread converged: %.2f%%", spread * 100));
        }
        
        // Short when overvalued vs implied
        if (spread < -minSpread && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Conversion arb short: spread=%.2f%%", spread * 100));
        }
        
        return TradeSignal.neutral(String.format("Spread: %.2f%%", spread * 100));
    }

    @Override
    public String getName() {
        return String.format("Conversion Arbitrage (%.1f%%)", minSpread * 100);
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
