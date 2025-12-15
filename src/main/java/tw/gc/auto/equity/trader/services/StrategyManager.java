package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;
import tw.gc.auto.equity.trader.strategy.impl.*;
import tw.gc.auto.equity.trader.strategy.impl.*;
import tw.gc.auto.equity.trader.services.ContractScalingService;
import tw.gc.auto.equity.trader.services.StockSettingsService;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyManager {

    private final ContractScalingService contractScalingService;
    private final StockSettingsService stockSettingsService;
    private final TradingStateService tradingStateService;
    private final OrderExecutionService orderExecutionService;
    private final PositionManager positionManager;
    private final DataLoggingService dataLoggingService;
    private final ShadowModeStockService shadowModeStockService;
    private final ActiveStockService activeStockService;

    private final List<IStrategy> activeStrategies = new ArrayList<>();
    private final Map<String, Portfolio> strategyPortfolios = new ConcurrentHashMap<>();
    
    // Shadow mode: Map<Symbol, Map<StrategyName, Portfolio>>
    private final Map<String, Map<String, Portfolio>> shadowStockPortfolios = new ConcurrentHashMap<>();

    public void reinitializeStrategies(tw.gc.auto.equity.trader.strategy.StrategyFactory factory) {
        activeStrategies.clear();
        strategyPortfolios.clear();
        shadowStockPortfolios.clear();
        
        activeStrategies.addAll(factory.createStrategies());
        
        // Initialize portfolios for each strategy
        String tradingMode = tradingStateService.getTradingMode();
        for (IStrategy strategy : activeStrategies) {
            Map<String, Integer> pos = new HashMap<>();
            pos.put(getActiveSymbol(), 0);
            
            Portfolio p = Portfolio.builder()
                .equity(80000.0) // 80k per strategy as requested
                .availableMargin(80000.0)
                .positions(pos)
                .tradingMode(tradingMode)
                .tradingQuantity(getTradingQuantity())
                .build();
            
            strategyPortfolios.put(strategy.getName(), p);
            log.info("✅ Initialized Strategy: {}", strategy.getName());
        }
        
        // Initialize shadow mode stocks
        initializeShadowModeStocks();
        
        log.info("🔥 Total Active Strategies: {}", activeStrategies.size());
        
        // Active strategy is now managed by database (ActiveStrategyService)
        log.info("📊 Active Strategy: {}", tradingStateService.getActiveStrategyName());
    }
    
    private void initializeShadowModeStocks() {
        List<ShadowModeStock> shadowStocks = shadowModeStockService.getEnabledStocks();
        
        for (ShadowModeStock shadowStock : shadowStocks) {
            String symbol = shadowStock.getSymbol();
            String strategyName = shadowStock.getStrategyName();
            
            // Find the strategy
            IStrategy matchingStrategy = activeStrategies.stream()
                .filter(s -> s.getName().equals(strategyName))
                .findFirst()
                .orElse(null);
            
            if (matchingStrategy != null) {
                Map<String, Integer> pos = new HashMap<>();
                pos.put(symbol, 0);
                
                Portfolio p = Portfolio.builder()
                    .equity(80000.0) // 80k per strategy
                    .availableMargin(80000.0)
                    .positions(pos)
                    .tradingMode("shadow")
                    .tradingQuantity(1)
                    .build();
                
                shadowStockPortfolios.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                    .put(strategyName, p);
                
                log.info("👻 Initialized Shadow Stock: {} with Strategy: {}", symbol, strategyName);
            }
        }
        
        log.info("🌙 Total Shadow Mode Stocks: {}", shadowStockPortfolios.size());
    }

    // Removed @PostConstruct as initialization is now driven by TradingModeService
    public void initializeStrategies() {
        // Legacy method kept for compatibility if needed, but logic moved to reinitializeStrategies
    }

    public List<IStrategy> getActiveStrategies() {
        return activeStrategies;
    }
    
    /**
     * Execute shadow trades for configured stocks
     */
    public void executeShadowStockStrategies(String symbol, MarketData marketData, double currentPrice) {
        Map<String, Portfolio> strategyPortfoliosForStock = shadowStockPortfolios.get(symbol);
        
        if (strategyPortfoliosForStock == null || strategyPortfoliosForStock.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, Portfolio> entry : strategyPortfoliosForStock.entrySet()) {
            String strategyName = entry.getKey();
            Portfolio portfolio = entry.getValue();
            
            // Find the strategy
            IStrategy strategy = activeStrategies.stream()
                .filter(s -> s.getName().equals(strategyName))
                .findFirst()
                .orElse(null);
            
            if (strategy != null) {
                try {
                    TradeSignal signal = strategy.execute(portfolio, marketData);
                    
                    if (signal.getDirection() != TradeSignal.SignalDirection.NEUTRAL) {
                        log.info("🌙 Shadow Stock [{}] Strategy [{}] Signal: {} ({})", 
                            symbol, strategyName, signal.getDirection(), signal.getReason());
                        
                        executeShadowTradeForStock(symbol, strategyName, portfolio, signal, currentPrice);
                    }
                } catch (Exception e) {
                    log.error("Error running shadow strategy {} for {}", strategyName, symbol, e);
                }
            }
        }
    }

    public void executeStrategies(MarketData marketData, double currentPrice) {
        for (IStrategy strategy : activeStrategies) {
            try {
                Portfolio p = strategyPortfolios.get(strategy.getName());
                TradeSignal signal = strategy.execute(p, marketData);
                
                if (signal.getDirection() != TradeSignal.SignalDirection.NEUTRAL) {
                    log.info("💡 Strategy [{}] Signal: {} ({})", strategy.getName(), signal.getDirection(), signal.getReason());
                    
                    // Execute Shadow Trade (Virtual)
                    executeShadowTrade(strategy.getName(), p, signal, currentPrice);
                    
                    // Execute REAL Trade if this is the selected active strategy
                    if (strategy.getName().equalsIgnoreCase(tradingStateService.getActiveStrategyName())) {
                        executeRealStrategyTrade(signal, currentPrice);
                    }
                }
            } catch (Exception e) {
                log.error("Error running strategy {}", strategy.getName(), e);
            }
        }
    }

    private void executeRealStrategyTrade(TradeSignal signal, double price) {
        String symbol = getActiveSymbol();
        int currentPos = positionManager.getPosition(symbol);
        String tradingMode = tradingStateService.getTradingMode();
        boolean emergencyShutdown = tradingStateService.isEmergencyShutdown();
        String strategyName = tradingStateService.getActiveStrategyName();
        
        // LONG Signal
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG && currentPos <= 0) {
            // Close Short if any
            if (currentPos < 0) {
                orderExecutionService.flattenPosition("Strategy Reversal (Short -> Long)", symbol, tradingMode, emergencyShutdown);
            }
            
            // Open Long
            if (positionManager.getPosition(symbol) == 0) {
                int qty = getTradingQuantity();
                qty = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", qty, price, symbol, tradingMode);
                if (qty > 0) {
                    orderExecutionService.executeOrderWithRetry("BUY", qty, price, symbol, false, emergencyShutdown, strategyName);
                }
            }
        } 
        // SHORT Signal
        else if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && currentPos >= 0) {
            // Close Long if any
            if (currentPos > 0) {
                orderExecutionService.flattenPosition("Strategy Reversal (Long -> Short)", symbol, tradingMode, emergencyShutdown);
            }
            
            // Open Short (only if futures mode or margin allowed)
            if ("futures".equals(tradingMode) && positionManager.getPosition(symbol) == 0) {
                int qty = getTradingQuantity();
                qty = orderExecutionService.checkBalanceAndAdjustQuantity("SELL", qty, price, symbol, tradingMode);
                if (qty > 0) {
                    orderExecutionService.executeOrderWithRetry("SELL", qty, price, symbol, false, emergencyShutdown, strategyName);
                }
            }
        }
        // EXIT Signal (Explicit Close)
        else if (signal.isExitSignal() && currentPos != 0) {
             orderExecutionService.flattenPosition("Strategy Exit Signal: " + signal.getReason(), symbol, tradingMode, emergencyShutdown);
        }
    }

    private void executeShadowTrade(String strategyName, Portfolio p, TradeSignal signal, double price) {
        String symbol = getActiveSymbol();
        int currentPos = p.getPosition(symbol);
        
        // LONG Signal
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG && currentPos <= 0) {
            // Close Short if any
            if (currentPos < 0) {
                double pnl = (p.getEntryPrice(symbol) - price) * Math.abs(currentPos);
                p.setEquity(p.getEquity() + pnl);
                p.setPosition(symbol, 0);
                logShadowTrade(strategyName, "BUY_TO_COVER", Math.abs(currentPos), price, pnl, signal.getReason());
            }
            
            // Open Long
            int qty = 1; // Fixed 1 unit for shadow tracking
            p.setPosition(symbol, qty);
            p.setEntryPrice(symbol, price);
            logShadowTrade(strategyName, "BUY", qty, price, null, signal.getReason());
            
        } 
        // SHORT Signal
        else if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && currentPos >= 0) {
            // Close Long if any
            if (currentPos > 0) {
                double pnl = (price - p.getEntryPrice(symbol)) * currentPos;
                p.setEquity(p.getEquity() + pnl);
                p.setPosition(symbol, 0);
                logShadowTrade(strategyName, "SELL", currentPos, price, pnl, signal.getReason());
            }
            
            // Open Short
            int qty = 1;
            p.setPosition(symbol, -qty);
            p.setEntryPrice(symbol, price);
            logShadowTrade(strategyName, "SELL_SHORT", qty, price, null, signal.getReason());
        }
    }

    private void executeShadowTradeForStock(String symbol, String strategyName, Portfolio p, TradeSignal signal, double price) {
        int currentPos = p.getPosition(symbol);
        
        // LONG Signal
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG && currentPos <= 0) {
            // Close Short if any
            if (currentPos < 0) {
                double pnl = (p.getEntryPrice(symbol) - price) * Math.abs(currentPos);
                p.setEquity(p.getEquity() + pnl);
                p.setPosition(symbol, 0);
                logShadowTradeForStock(symbol, strategyName, "BUY_TO_COVER", Math.abs(currentPos), price, pnl, signal.getReason());
            }
            
            // Open Long
            int qty = 1; // Fixed 1 unit for shadow tracking
            p.setPosition(symbol, qty);
            p.setEntryPrice(symbol, price);
            logShadowTradeForStock(symbol, strategyName, "BUY", qty, price, null, signal.getReason());
            
        } 
        // SHORT Signal
        else if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && currentPos >= 0) {
            // Close Long if any
            if (currentPos > 0) {
                double pnl = (price - p.getEntryPrice(symbol)) * currentPos;
                p.setEquity(p.getEquity() + pnl);
                p.setPosition(symbol, 0);
                logShadowTradeForStock(symbol, strategyName, "SELL", currentPos, price, pnl, signal.getReason());
            }
            
            // Open Short
            int qty = 1;
            p.setPosition(symbol, -qty);
            p.setEntryPrice(symbol, price);
            logShadowTradeForStock(symbol, strategyName, "SELL_SHORT", qty, price, null, signal.getReason());
        }
    }
    
    private void logShadowTradeForStock(String symbol, String strategyName, String action, int qty, double price, Double pnl, String reason) {
        Trade trade = Trade.builder()
            .timestamp(LocalDateTime.now(ZoneId.of("Asia/Taipei")))
            .action(action.contains("BUY") ? Trade.TradeAction.BUY : Trade.TradeAction.SELL)
            .quantity(qty)
            .entryPrice(price)
            .symbol(symbol)
            .strategyName(strategyName)
            .reason(reason)
            .mode(Trade.TradingMode.SIMULATION)
            .status(Trade.TradeStatus.CLOSED)
            .realizedPnL(pnl)
            .build();
            
        dataLoggingService.logTrade(trade);
        log.info("🌙 Shadow Stock Trade [{}][{}]: {} {} @ {} (PnL: {})", symbol, strategyName, action, qty, price, pnl);
    }
    
    private void logShadowTrade(String strategyName, String action, int qty, double price, Double pnl, String reason) {
        Trade trade = Trade.builder()
            .timestamp(LocalDateTime.now(ZoneId.of("Asia/Taipei")))
            .action(action.contains("BUY") ? Trade.TradeAction.BUY : Trade.TradeAction.SELL)
            .quantity(qty)
            .entryPrice(price)
            .symbol(getActiveSymbol())
            .strategyName(strategyName) // Track which strategy did this
            .reason(reason)
            .mode(Trade.TradingMode.SIMULATION) // Always SIMULATION for shadow trades
            .status(Trade.TradeStatus.CLOSED)
            .realizedPnL(pnl)
            .build();
            
        dataLoggingService.logTrade(trade);
        log.info("👻 Shadow Trade [{}]: {} {} @ {} (PnL: {})", strategyName, action, qty, price, pnl);
    }

    private String getActiveSymbol() {
        return activeStockService.getActiveSymbol(tradingStateService.getTradingMode());
    }

    private int getTradingQuantity() {
        if ("stock".equals(tradingStateService.getTradingMode())) {
            return stockSettingsService.getBaseStockQuantity(contractScalingService.getLastEquity());
        } else {
            return contractScalingService.getMaxContracts();
        }
    }
}
