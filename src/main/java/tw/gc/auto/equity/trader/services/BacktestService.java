package tw.gc.auto.equity.trader.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

/**
 * Backtest Service
 * Runs strategies against historical data and tracks performance.
 */
@Service
@Slf4j
public class BacktestService {

    public Map<String, BacktestResult> runBacktest(List<IStrategy> strategies, List<MarketData> history, double initialCapital) {
        log.info("🚀 Starting Backtest with {} strategies on {} data points...", strategies.size(), history.size());
        
        Map<String, BacktestResult> results = new HashMap<>();
        
        // Initialize portfolios
        Map<String, Portfolio> portfolios = new HashMap<>();
        for (IStrategy strategy : strategies) {
            strategy.reset(); // Reset state
            
            Map<String, Integer> pos = new HashMap<>();
            // Assuming single symbol backtest for now
            String symbol = history.isEmpty() ? "UNKNOWN" : history.get(0).getSymbol();
            pos.put(symbol, 0);
            
            Portfolio p = Portfolio.builder()
                .equity(initialCapital)
                .availableMargin(initialCapital)
                .positions(pos)
                .tradingMode("backtest")
                .tradingQuantity(1) // Default quantity
                .build();
            
            portfolios.put(strategy.getName(), p);
            results.put(strategy.getName(), new BacktestResult(strategy.getName()));
        }
        
        // Run Simulation Loop
        for (MarketData data : history) {
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                BacktestResult result = results.get(strategy.getName());
                
                try {
                    TradeSignal signal = strategy.execute(p, data);
                    processSignal(strategy, p, data, signal, result);
                } catch (Exception e) {
                    log.error("Error in backtest for strategy {}", strategy.getName(), e);
                }
            }
        }
        
        // Close open positions at end
        if (!history.isEmpty()) {
            MarketData lastData = history.get(history.size() - 1);
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                BacktestResult result = results.get(strategy.getName());
                closeAllPositions(p, lastData, result);
                result.setFinalEquity(p.getEquity());
            }
        }
        
        return results;
    }
    
    private void processSignal(IStrategy strategy, Portfolio p, MarketData data, TradeSignal signal, BacktestResult result) {
        String symbol = data.getSymbol();
        int currentPos = p.getPosition(symbol);
        double price = data.getClose();
        
        // Simple execution logic (Market Orders)
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG && currentPos <= 0) {
            // Close Short if any
            if (currentPos < 0) {
                double pnl = (p.getEntryPrice(symbol) - price) * Math.abs(currentPos);
                p.setEquity(p.getEquity() + pnl);
                p.setAvailableMargin(p.getAvailableMargin() + (price * Math.abs(currentPos)) + pnl);
                p.setPosition(symbol, 0);
                result.addTrade(pnl);
            }
            
            // Open Long
            int qty = calculateQuantity(p, price);
            if (qty > 0) {
                p.setAvailableMargin(p.getAvailableMargin() - (price * qty));
                p.setPosition(symbol, qty);
                p.setEntryPrice(symbol, price);
            }
            
        } else if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && currentPos >= 0) {
            // Close Long if any
            if (currentPos > 0) {
                double pnl = (price - p.getEntryPrice(symbol)) * currentPos;
                p.setEquity(p.getEquity() + pnl);
                p.setAvailableMargin(p.getAvailableMargin() + (price * currentPos) + pnl);
                p.setPosition(symbol, 0);
                result.addTrade(pnl);
            }
            
            // Open Short (assuming margin allowed)
            int qty = calculateQuantity(p, price);
            if (qty > 0) {
                p.setAvailableMargin(p.getAvailableMargin() - (price * qty));
                p.setPosition(symbol, -qty);
                p.setEntryPrice(symbol, price);
            }
        }
    }
    
    private void closeAllPositions(Portfolio p, MarketData data, BacktestResult result) {
        String symbol = data.getSymbol();
        int currentPos = p.getPosition(symbol);
        double price = data.getClose();
        
        if (currentPos != 0) {
            double pnl;
            if (currentPos > 0) {
                pnl = (price - p.getEntryPrice(symbol)) * currentPos;
            } else {
                pnl = (p.getEntryPrice(symbol) - price) * Math.abs(currentPos);
            }
            p.setEquity(p.getEquity() + pnl);
            p.setPosition(symbol, 0);
            result.addTrade(pnl);
        }
    }
    
    private int calculateQuantity(Portfolio p, double price) {
        // Simple sizing: use 95% of available margin
        double budget = p.getAvailableMargin() * 0.95;
        return (int) (budget / price);
    }
    
    @lombok.Data
    public static class BacktestResult {
        private final String strategyName;
        private double finalEquity;
        private int totalTrades;
        private int winningTrades;
        private double totalPnL;
        
        public void addTrade(double pnl) {
            totalTrades++;
            totalPnL += pnl;
            if (pnl > 0) winningTrades++;
        }
        
        public double getWinRate() {
            return totalTrades == 0 ? 0 : (double) winningTrades / totalTrades * 100;
        }
    }
}
