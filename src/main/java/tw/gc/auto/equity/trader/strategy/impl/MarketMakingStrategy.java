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
 * MarketMakingStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Ho & Stoll (1981) - 'Optimal Dealer Pricing Under Transactions'
 * 
 * Logic:
 * Provide liquidity, capture spread. Buy on dips, sell on pops.
 */
@Slf4j
public class MarketMakingStrategy implements IStrategy {
    
    private final double spreadCapture;
    private final int maxInventory;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> inventory = new HashMap<>();
    
    public MarketMakingStrategy(double spreadCapture, int maxInventory) {
        this.spreadCapture = spreadCapture;
        this.maxInventory = maxInventory;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) prices.removeFirst();
        
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up market making");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        double prevPrice = priceArray[priceArray.length - 2];
        double priceChange = (currentPrice - prevPrice) / prevPrice;
        
        // Calculate mid-price (fair value)
        double midPrice = 0;
        for (Double p : priceArray) midPrice += p;
        midPrice /= priceArray.length;
        
        int currentInventory = inventory.getOrDefault(symbol, 0);
        int position = portfolio.getPosition(symbol);
        
        // Buy on dips (provide liquidity)
        if (priceChange < -spreadCapture && currentInventory < maxInventory && position <= 0) {
            inventory.put(symbol, currentInventory + 1);
            return TradeSignal.longSignal(0.65,
                String.format("MM buy: price dropped %.2f%%, inv=%d", 
                    priceChange * 100, currentInventory + 1));
        }
        
        // Sell on pops (capture spread)
        if (priceChange > spreadCapture && currentInventory > 0 && position > 0) {
            inventory.put(symbol, currentInventory - 1);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("MM sell: price rose %.2f%%, inv=%d", 
                    priceChange * 100, currentInventory - 1));
        }
        
        // Inventory management: flatten if too much inventory
        if (currentInventory >= maxInventory && currentPrice >= midPrice) {
            inventory.put(symbol, currentInventory - 1);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.60,
                String.format("MM flatten: inv=%d at mid", currentInventory));
        }
        
        return TradeSignal.neutral(String.format("MM: inv=%d, change=%.2f%%", 
            currentInventory, priceChange * 100));
    }

    @Override
    public String getName() {
        return String.format("Market Making (%.2f%%, max=%d)", spreadCapture * 100, maxInventory);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        inventory.clear();
    }
}
