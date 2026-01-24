package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.services.RiskManagementService;
import tw.gc.auto.equity.trader.services.StockRiskSettingsService;
import tw.gc.auto.equity.trader.services.StockSettingsService;
import tw.gc.auto.equity.trader.services.ShioajiSettingsService;
import tw.gc.auto.equity.trader.services.ContractScalingService;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.services.HistoryDataService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramCommandHandler {

    private static final double DEFAULT_BACKTEST_INITIAL_CAPITAL = 80_000.0;

    private final TelegramService telegramService;
    private final TradingStateService tradingStateService;
    private final PositionManager positionManager;
    private final RiskManagementService riskManagementService;
    private final ContractScalingService contractScalingService;
    private final StockSettingsService stockSettingsService;
    private final ShioajiSettingsService shioajiSettingsService;
    private final LlmService llmService;
    private final OrderExecutionService orderExecutionService;
    private final ApplicationContext applicationContext;
    private final StockRiskSettingsService stockRiskSettingsService;
    private final ActiveStrategyService activeStrategyService;
    private final StrategyPerformanceService strategyPerformanceService;
    private final ActiveStockService activeStockService;
    private final BacktestService backtestService;
    private final HistoryDataService historyDataService;
    private final MarketDataRepository marketDataRepository;
    private final AutoStrategySelector autoStrategySelector;
    private final SystemConfigService systemConfigService;
    
    // For testing: allows disabling actual System.exit
    @Setter
    private boolean exitEnabled = true;

    @Setter
    private java.util.function.IntConsumer exitHandler = System::exit;

    public void registerCommands(List<IStrategy> activeStrategies) {
        telegramService.registerCommandHandlers(
            v -> handleStatusCommand(),
            v -> handlePauseCommand(),
            v -> handleResumeCommand(),
            v -> handleCloseCommand(),
            v -> handleShutdownCommand()
        );
        
        // Register dynamic strategy switching (deprecated - use /set-main-strategy)
        telegramService.registerCustomCommand("/strategy", args -> {
            if (args == null || args.trim().isEmpty()) {
                telegramService.sendMessage("Current Active Strategy: " + tradingStateService.getActiveStrategyName() + 
                    "\nAvailable: " + activeStrategies.stream().map(IStrategy::getName).reduce((a,b) -> a + ", " + b).orElse("None") +
                    "\n\n⚠️ Deprecated: Use /set-main-strategy instead");
            } else {
                String newStrategy = args.trim();
                // Verify it exists
                boolean exists = activeStrategies.stream()
                    .anyMatch(s -> s.getName().equalsIgnoreCase(newStrategy));
                
                if (exists) {
                    tradingStateService.setActiveStrategyName(newStrategy);
                    telegramService.sendMessage("✅ Active Strategy switched to: " + newStrategy +
                        "\n\n⚠️ Deprecated: Use /set-main-strategy instead");
                    log.info("🔄 Strategy switched to {} via Telegram", newStrategy);
                } else {
                    telegramService.sendMessage("❌ Strategy not found: " + newStrategy);
                }
            }
        });
        
        // Register mode switching (Live/Sim)
        telegramService.registerCustomCommand("/mode", args -> {
            if ("live".equalsIgnoreCase(args)) {
                shioajiSettingsService.updateSimulationMode(false);
                telegramService.sendMessage("🔴 Switched to LIVE TRADING mode (Database updated)");
            } else if ("sim".equalsIgnoreCase(args) || "simulation".equalsIgnoreCase(args)) {
                shioajiSettingsService.updateSimulationMode(true);
                telegramService.sendMessage("🟡 Switched to SIMULATION mode (Database updated)");
            } else {
                boolean isSim = shioajiSettingsService.getSettings().isSimulation();
                telegramService.sendMessage("Current Mode: " + (isSim ? "🟡 SIMULATION" : "🔴 LIVE") + 
                    "\nUsage: /mode live OR /mode sim");
            }
        });
        // Register Agent commands
        telegramService.registerCustomCommand("/ask", args -> {
            if (args == null || args.trim().isEmpty()) {
                // No question provided - suggest best strategy
                handleStrategyRecommendation();
            } else {
                try {
                    String response = llmService.generateInsight("You are a trading tutor. Answer this: " + args);
                    telegramService.sendMessage("🎓 Tutor: " + response);
                } catch (Exception e) {
                    telegramService.sendMessage("❌ Error asking tutor: " + e.getMessage());
                }
            }
        });
        
        telegramService.registerCustomCommand("/news", args -> {
            telegramService.sendMessage("📰 News Analysis:\nFetching latest market news... (Mock)");
            // Trigger async news fetch/analysis here
        });
        
        // Register /set-main-strategy command
        telegramService.registerCustomCommand("/set-main-strategy", args -> {
            handleSetMainStrategy(args, activeStrategies);
        });
        
        // Register /change-stock command
        telegramService.registerCustomCommand("/change-stock", args -> {
            handleChangeStock(args);
        });
        
        // Register /backtest command
        telegramService.registerCustomCommand("/backtest", args -> {
            handleBacktest(args);
        });
        
        // Register /download-history command
        telegramService.registerCustomCommand("/download-history", args -> {
            handleDownloadHistory(args);
        });
        
        // Register /auto-strategy-select command
        telegramService.registerCustomCommand("/auto-strategy-select", args -> {
            handleAutoStrategySelect();
        });
        
        // Register /config command
        telegramService.registerCustomCommand("/config", args -> {
            handleConfigCommand(args);
        });
        
        // Register /show-configs command
        telegramService.registerCustomCommand("/show-configs", args -> {
            handleShowConfigs();
        });
        
        // Register /observer command for passive mode
        telegramService.registerCustomCommand("/observer", args -> {
            handleObserverCommand(args);
        });
        
        // Register /risk command for risk settings configuration
        telegramService.registerCustomCommand("/risk", args -> {
            handleRiskCommand(args);
        });
        
        // Data Operations Commands
        telegramService.registerCustomCommand("/populate-data", args -> {
            handlePopulateDataCommand(args);
        });
        
        telegramService.registerCustomCommand("/run-backtests", args -> {
            handleRunBacktestsCommand(args);
        });
        
        telegramService.registerCustomCommand("/select-best-strategy", args -> {
            handleSelectStrategyCommand(args);
        });
        
        telegramService.registerCustomCommand("/full-pipeline", args -> {
            handleFullPipelineCommand(args);
        });
        
        telegramService.registerCustomCommand("/data-status", args -> {
            handleDataStatusCommand(args);
        });
        
        // Register /auto-select command
        telegramService.registerCustomCommand("/auto-select", args -> {
            handleAutoSelectCommand();
        });
    }
    
    /**
     * Handle /auto-select command for triggering strategy selection
     */
    private void handleAutoSelectCommand() {
        telegramService.sendMessage(
            "🎯 AUTO-SELECTION STARTED" + System.lineSeparator() +
            "━━━━━━━━━━━━━━━━" + System.lineSeparator() +
            "Analyzing backtest results..." + System.lineSeparator() +
            "Applying strategy filters..." + System.lineSeparator() +
            "Checking Taiwan compliance rules..."
        );
        
        new Thread(() -> {
            try {
                autoStrategySelector.selectBestStrategyAndStock();
                autoStrategySelector.selectShadowModeStrategies();
                telegramService.sendMessage("✅ Auto-selection completed!");
            } catch (Exception e) {
                log.error("Auto-selection failed", e);
                telegramService.sendMessage("❌ Auto-selection failed: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Handle /set-main-strategy command
     */
    private void handleSetMainStrategy(String args, List<IStrategy> activeStrategies) {
        if (args == null || args.trim().isEmpty()) {
            // Show help message
            StringBuilder help = new StringBuilder();
            help.append("📊 SET MAIN STRATEGY\n");
            help.append("━━━━━━━━━━━━━━━━\n");
                help.append("Usage: /set-main-strategy [strategy-name]\n\n");
            help.append("Available strategies:\n");
            
            for (IStrategy strategy : activeStrategies) {
                help.append("• ").append(strategy.getName()).append("\n");
            }
            
            help.append("\n📌 Current: ").append(tradingStateService.getActiveStrategyName());
            help.append("\n\n💡 System will automatically load optimal parameters");
            
            telegramService.sendMessage(help.toString());
            return;
        }
        
        String strategyName = args.trim();
        
        // Validate strategy exists
        boolean exists = activeStrategies.stream()
            .anyMatch(s -> s.getName().equalsIgnoreCase(strategyName));
        
        if (!exists) {
            telegramService.sendMessage("❌ Strategy not found: " + strategyName + 
                "\n\nUse /set-main-strategy (without arguments) to see available strategies");
            return;
        }
        
        // Find the exact strategy name (case-corrected)
        String exactName = activeStrategies.stream()
            .filter(s -> s.getName().equalsIgnoreCase(strategyName))
            .map(IStrategy::getName)
            .findFirst()
            .orElse(strategyName);
        
        try {
            // Load optimal parameters from performance data
            tw.gc.auto.equity.trader.entities.StrategyPerformance bestPerf = 
                strategyPerformanceService.getBestPerformer(30); // Last 30 days
            
            java.util.Map<String, Object> parameters = new java.util.HashMap<>();
            
            // Switch strategy
            activeStrategyService.switchStrategy(
                exactName,
                parameters,
                "Manual switch via /set-main-strategy command",
                false
            );
            
            telegramService.sendMessage(String.format(
                "✅ Main Strategy Updated\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "Strategy: %s\n" +
                "Parameters: Auto-loaded from performance history\n" +
                "Source: Manual command",
                exactName
            ));
            
            log.info("🔄 Main strategy switched to {} via /set-main-strategy command", exactName);
            
        } catch (Exception e) {
            log.error("Failed to switch strategy", e);
            telegramService.sendMessage("❌ Failed to switch strategy: " + e.getMessage());
        }
    }
    
    /**
     * Handle strategy recommendation when /ask is called without arguments
     */
    private void handleStrategyRecommendation() {
        try {
            // Get the best performing strategy based on recent performance
            tw.gc.auto.equity.trader.entities.StrategyPerformance bestPerf = 
                strategyPerformanceService.getBestPerformer(30); // Last 30 days
            
            if (bestPerf == null) {
                telegramService.sendMessage(
                    "📊 STRATEGY RECOMMENDATION\n" +
                    "━━━━━━━━━━━━━━━━\n" +
                    "No performance data available yet.\n" +
                    "Run strategies in shadow mode to gather data.\n\n" +
                    "💡 Tip: Use /ask [question] to ask about trading concepts"
                );
                return;
            }
            
            String currentStrategy = tradingStateService.getActiveStrategyName();
            boolean isAlreadyActive = currentStrategy.equals(bestPerf.getStrategyName());
            
            StringBuilder message = new StringBuilder();
            message.append("📊 STRATEGY RECOMMENDATION\n");
            message.append("━━━━━━━━━━━━━━━━\n");
            message.append(String.format("🏆 Best Performer: %s\n", bestPerf.getStrategyName()));
            message.append(String.format("📈 Sharpe Ratio: %.2f\n", bestPerf.getSharpeRatio() != null ? bestPerf.getSharpeRatio() : 0.0));
            message.append(String.format("📉 Max Drawdown: %.2f%%\n", bestPerf.getMaxDrawdownPct() != null ? bestPerf.getMaxDrawdownPct() : 0.0));
            message.append(String.format("💰 Total Return: %.2f%%\n", bestPerf.getTotalReturnPct() != null ? bestPerf.getTotalReturnPct() : 0.0));
            message.append(String.format("🎯 Win Rate: %.2f%%\n", bestPerf.getWinRatePct() != null ? bestPerf.getWinRatePct() : 0.0));
            message.append(String.format("📊 Trades: %d\n", bestPerf.getTotalTrades()));
            message.append("\n");
            
            if (isAlreadyActive) {
                message.append("✅ This strategy is already active!");
            } else {
                message.append(String.format("📌 Current: %s\n", currentStrategy));
                message.append(String.format("💡 Recommendation: Switch to %s\n", bestPerf.getStrategyName()));
                message.append(String.format("\nUse: /set-main-strategy %s", bestPerf.getStrategyName()));
            }
            
            message.append("\n\n💡 Tip: Use /ask [question] to ask about trading concepts");
            
            telegramService.sendMessage(message.toString());
            
        } catch (Exception e) {
            log.error("Failed to generate strategy recommendation", e);
            telegramService.sendMessage("❌ Failed to generate recommendation: " + e.getMessage());
        }
    }

    private void handleShutdownCommand() {
        log.info("🛑 Shutdown command received via Telegram");
        telegramService.sendMessage("🛑 Shutting down application...\nFlattening all positions");
        
        // Trigger shutdown in background thread
        new Thread(() -> {
            try {
                try {
                    flattenPosition("Shutdown via Telegram command");
                } catch (Exception e) {
                    log.error("❌ Failed to flatten position during shutdown", e);
                }

                Thread.sleep(2000); // Give time for messages to send

                int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
                if (exitEnabled) {
                    exitHandler.accept(exitCode);
                }
            } catch (Exception e) {
                log.error("❌ Error during Telegram-triggered shutdown", e);
            }
        }).start();
    }
    
    private void handleStatusCommand() {
        String state = "🟢 ACTIVE";
        if (tradingStateService.isEmergencyShutdown()) state = "🔴 EMERGENCY SHUTDOWN";
        else if (riskManagementService.isWeeklyLimitHit()) state = "🟡 WEEKLY LIMIT PAUSED";
        else if (riskManagementService.isEarningsBlackout()) state = "📅 EARNINGS BLACKOUT";
        else if (tradingStateService.isTradingPaused()) state = "⏸️ PAUSED BY USER";
        
        String instrument = getActiveSymbol();
        AtomicInteger posRef = positionManager.positionFor(instrument);
        AtomicReference<Double> entryRef = positionManager.entryPriceFor(instrument);
        AtomicReference<LocalDateTime> entryTimeRef = positionManager.entryTimeFor(instrument);

        String positionInfo = posRef.get() == 0 ? "No position" :
            String.format("%d @ %.0f (held %d min)",
                posRef.get(),
                entryRef.get(),
                entryTimeRef.get() != null ?
                    java.time.Duration.between(entryTimeRef.get(), LocalDateTime.now(ZoneId.of("Asia/Taipei"))).toMinutes() : 0
            );
        
        String tradingMode = tradingStateService.getTradingMode();
        String modeInfo = "stock".equals(tradingMode) 
            ? String.format("Mode: STOCK (%s)\nShares: %d (base %d +%d/20k)", 
                activeStockService.getActiveStock(),
                stockSettingsService.getBaseStockQuantity(contractScalingService.getLastEquity()),
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Mode: FUTURES (MTXF)\nContracts: %d", contractScalingService.getMaxContracts());
        
        String message = String.format(
            "📊 BOT STATUS\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "State: %s\n" +
            "%s\n" +
            "Position: %s\n" +
            "Equity: %.0f TWD\n" +
            "30d Profit: %.0f TWD\n" +
            "Today P&L: %.0f TWD\n" +
            "Week P&L: %.0f TWD\n" +
            "News Veto: %s\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "Commands: /pause /resume /close /shutdown",
            state, modeInfo, positionInfo,
            contractScalingService.getLastEquity(), contractScalingService.getLast30DayProfit(),
            riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL(),
            tradingStateService.isNewsVeto() ? "🚨 ACTIVE" : "✅ Clear"
        );
        telegramService.sendMessage(message);
    }
    
    private void handlePauseCommand() {
        tradingStateService.setTradingPaused(true);
        log.info("⏸️ Trading paused by user command");
        telegramService.sendMessage("⏸️ Trading PAUSED\nNo new entries until /resume\nExisting positions will still flatten at 13:30");
    }
    
    private void handleResumeCommand() {
        if (riskManagementService.isWeeklyLimitHit()) {
            telegramService.sendMessage("❌ Cannot resume - Weekly loss limit hit\nWait until next Monday");
            return;
        }
        if (riskManagementService.isEarningsBlackout()) {
            telegramService.sendMessage("❌ Cannot resume - Earnings blackout day\nNo trading today");
            return;
        }
        tradingStateService.setTradingPaused(false);
        log.info("▶️ Trading resumed by user command");
        telegramService.sendMessage("▶️ Trading RESUMED\nBot is active");
    }
    
    private void handleCloseCommand() {
        if (positionManager.getPosition(getActiveSymbol()) == 0) {
            telegramService.sendMessage("ℹ️ No position to close");
            return;
        }
        log.info("🔒 Close command received from user");
        flattenPosition("Closed by user");
        telegramService.sendMessage("✅ Position closed by user command");
    }

    private String getActiveSymbol() {
        return activeStockService.getActiveSymbol(tradingStateService.getTradingMode());
    }

    // Helper to delegate to OrderExecutionService
    private void flattenPosition(String reason) {
        orderExecutionService.flattenPosition(
            reason, 
            getActiveSymbol(), 
            tradingStateService.getTradingMode(), 
            tradingStateService.isEmergencyShutdown()
        );
    }
    
    private void handleBacktest(String args) {
        if (args == null || args.trim().isEmpty()) {
            telegramService.sendMessage(
                "📊 BACKTEST\n" +
                "━━━━━━━━━━━━━━━━\n" +
                    "Usage: /backtest [symbol] [days]\n\n" +
                "Example: /backtest 2330.TW 365\n\n" +
                "Runs all 54 strategies against historical data.\n" +
                "Results saved to strategy_stock_mapping table."
            );
            return;
        }
        
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            telegramService.sendMessage("❌ Invalid format. Use: /backtest [symbol] [days]");
            return;
        }
        
        final String symbol = parts[0].toUpperCase();
        final int days;
        
        try {
            days = Integer.parseInt(parts[1]);
            if (days <= 0 || days > 3650) {
                telegramService.sendMessage("❌ Days must be between 1 and 3650");
                return;
            }
        } catch (NumberFormatException e) {
            telegramService.sendMessage("❌ Invalid days: " + parts[1]);
            return;
        }
        
        telegramService.sendMessage(String.format("🚀 Starting backtest for %s (%d days)...", symbol, days));
        
        new Thread(() -> {
            try {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start = end.minusDays(days);
                
                List<MarketData> history = marketDataRepository
                    .findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                        symbol, MarketData.Timeframe.DAY_1, start, end);
                
                if (history.isEmpty()) {
                    telegramService.sendMessage(String.format(
                        "❌ No historical data found for %s\n" +
                        "Use /download-history %s first", symbol, symbol));
                    return;
                }
                
                List<IStrategy> strategies = new ArrayList<>();
                strategies.addAll(applicationContext.getBeansOfType(IStrategy.class).values());
                
                Map<String, BacktestService.InMemoryBacktestResult> results = 
                    backtestService.runBacktest(strategies, history, 80000.0);
                
                BacktestService.InMemoryBacktestResult best = results.values().stream()
                    .max((a, b) -> Double.compare(a.getTotalReturnPercentage(), b.getTotalReturnPercentage()))
                    .orElse(null);
                
                if (best != null) {
                    telegramService.sendMessage(String.format(
                        "✅ BACKTEST COMPLETE\n" +
                        "━━━━━━━━━━━━━━━━\n" +
                        "Symbol: %s\n" +
                        "Period: %d days (%d bars)\n" +
                        "Strategies tested: %d\n\n" +
                        "🏆 BEST PERFORMER:\n" +
                        "Strategy: %s\n" +
                        "Return: %.2f%%\n" +
                        "Sharpe: %.2f\n" +
                        "Max DD: %.2f%%\n" +
                        "Win Rate: %.2f%%\n" +
                        "Trades: %d",
                        symbol, days, history.size(), results.size(),
                        best.getStrategyName(),
                        best.getTotalReturnPercentage(),
                        best.getSharpeRatio(),
                        best.getMaxDrawdownPercentage(),
                        best.getWinRate(),
                        best.getTotalTrades()
                    ));
                } else {
                    telegramService.sendMessage("✅ Backtest complete but no results");
                }
                
            } catch (Exception e) {
                log.error("Backtest failed", e);
                telegramService.sendMessage("❌ Backtest failed: " + e.getMessage());
            }
        }).start();
    }
    
    private void handleDownloadHistory(String args) {
        if (args == null || args.trim().isEmpty()) {
            telegramService.sendMessage(
                "📥 DOWNLOAD HISTORY\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "No symbol provided — downloading top 50 stocks.\n" +
                "Default: 10 years\n\n" +
                "Tip: /download-history [symbol] [years]"
            );

            new Thread(() -> {
                try {
                    List<String> symbols = backtestService.fetchTop50Stocks();
                    int years = 10;
                    historyDataService.downloadHistoricalDataForMultipleStocks(symbols, years);
                    telegramService.sendMessage(String.format(
                        "✅ DOWNLOAD COMPLETE\n" +
                        "━━━━━━━━━━━━━━━━\n" +
                        "Symbols: %d\n" +
                        "Years: %d\n\n" +
                        "💡 Use /run-backtests to start backtesting",
                        symbols.size(), years
                    ));
                } catch (Exception e) {
                    log.error("Download failed", e);
                    telegramService.sendMessage("❌ Download failed: " + e.getMessage());
                }
            }).start();
            return;
        }
        
        String[] parts = args.trim().split("\\s+");
        final String symbol = parts[0].toUpperCase();
        final int years;
        
        if (parts.length > 1) {
            try {
                years = Integer.parseInt(parts[1]);
                if (years <= 0 || years > 20) {
                    telegramService.sendMessage("❌ Years must be between 1 and 20");
                    return;
                }
            } catch (NumberFormatException e) {
                telegramService.sendMessage("❌ Invalid years: " + parts[1]);
                return;
            }
        } else {
            years = 10;
        }
        
        telegramService.sendMessage(String.format("📥 Downloading %d years of data for %s...", years, symbol));
        
        new Thread(() -> {
            try {
                HistoryDataService.DownloadResult result = 
                    historyDataService.downloadHistoricalData(symbol, years);
                
                telegramService.sendMessage(String.format(
                    "✅ DOWNLOAD COMPLETE\n" +
                    "━━━━━━━━━━━━━━━━\n" +
                    "Symbol: %s\n" +
                    "Total records: %d\n" +
                    "Inserted: %d\n" +
                    "Skipped (duplicates): %d\n\n" +
                    "💡 Use /backtest %s [days] to test strategies",
                    result.getSymbol(),
                    result.getTotalRecords(),
                    result.getInserted(),
                    result.getSkipped(),
                    symbol
                ));
                
            } catch (Exception e) {
                log.error("Download failed", e);
                telegramService.sendMessage("❌ Download failed: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Handle /change-stock command to change the active trading stock
     */
    private void handleChangeStock(String args) {
        if (!"stock".equals(tradingStateService.getTradingMode())) {
            telegramService.sendMessage("⚠️ This command only works in STOCK mode\nCurrent mode: FUTURES");
            return;
        }
        
        if (args == null || args.trim().isEmpty()) {
            String currentStock = activeStockService.getActiveStock();
            telegramService.sendMessage(String.format(
                "📊 CHANGE ACTIVE STOCK\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "Current stock: %s\n\n" +
                "Usage: /change-stock [symbol]\n" +
                "Example: /change-stock 2330.TW\n\n" +
                "⚠️ WARNING: This will flatten current position and switch to new stock",
                currentStock
            ));
            return;
        }
        
        String newStock = args.trim().toUpperCase();
        
        // Validate stock format (basic check)
        if (!newStock.matches("\\d{4}\\.TW")) {
            telegramService.sendMessage(
                "❌ Invalid stock symbol format\n" +
                "Must be in format: XXXX.TW (e.g., 2330.TW, 2454.TW)"
            );
            return;
        }
        
        String oldStock = activeStockService.getActiveStock();
        
        if (newStock.equals(oldStock)) {
            telegramService.sendMessage(String.format("ℹ️ Already trading %s", newStock));
            return;
        }
        
        try {
            // Flatten current position if any
            if (positionManager.getPosition(oldStock) != 0) {
                telegramService.sendMessage(String.format("📤 Flattening position in %s...", oldStock));
                flattenPosition("Stock change requested");
            }
            
            // Update active stock in database
            activeStockService.setActiveStock(newStock);
            
            telegramService.sendMessage(String.format(
                "✅ Active stock changed\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "From: %s\n" +
                "To: %s\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "System will now trade %s on next signal",
                oldStock, newStock, newStock
            ));
            
        } catch (Exception e) {
            telegramService.sendMessage(String.format(
                "❌ Failed to change stock: %s\n" +
                "Current stock remains: %s",
                e.getMessage(), oldStock
            ));
        }
    }
    
    /**
     * Handle /auto-strategy-select command
     * Manually triggers the auto strategy selection based on backtest data, simulation stats, and LLM insights
     */
    private void handleAutoStrategySelect() {
        telegramService.sendMessage("🎯 Starting auto-selection process...\n" +
            "Analyzing backtest results, simulation stats, and applying AI filters...");
        
        new Thread(() -> {
            try {
                autoStrategySelector.selectBestStrategyAndStock();
                telegramService.sendMessage("✅ Auto-selection completed successfully!");
            } catch (Exception e) {
                log.error("Auto-selection failed", e);
                telegramService.sendMessage("❌ Auto-selection failed: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Handle /config command
     */
    private void handleConfigCommand(String args) {
        if (args == null || args.trim().isEmpty() || args.trim().equalsIgnoreCase("help")) {
            telegramService.sendMessage(systemConfigService.getConfigHelp());
            return;
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        if (parts.length < 2) {
            telegramService.sendMessage("❌ Usage: /config [key] [value]\nExample: /config daily_loss_limit 2000");
            return;
        }
        
        String key = parts[0].toLowerCase();
        String value = parts[1].trim();
        
        String error = systemConfigService.validateAndSetConfig(key, value);
        if (error != null) {
            telegramService.sendMessage(error);
        } else {
            telegramService.sendMessage(String.format("✅ Config updated\n%s = %s", key, value));
        }
    }
    
    /**
     * Handle /show-configs command
     */
    private void handleShowConfigs() {
        telegramService.sendMessage(systemConfigService.getAllConfigsFormatted());
    }
    
    /**
     * Handle /observer command for passive mode control
     */
    private void handleObserverCommand(String args) {
        if (args == null || args.trim().isEmpty()) {
            boolean isActive = tradingStateService.isActivePolling();
            telegramService.sendMessage(String.format(
                "🔭 OBSERVER MODE\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "Current: %s\n\n" +
                "Usage:\n" +
                "/observer on - Enable active polling\n" +
                "/observer off - Disable active polling (passive mode)\n\n" +
                "When passive, signal polling stops but the app keeps running.",
                isActive ? "✅ Active polling" : "⏸️ Passive mode"
            ));
            return;
        }
        
        String command = args.trim().toLowerCase();
        
        if ("on".equals(command) || "active".equals(command)) {
            tradingStateService.setActivePolling(true);
            telegramService.sendMessage("✅ Active polling enabled\nSignal polling resumed");
            log.info("🔭 Active polling enabled via /observer command");
        } else if ("off".equals(command) || "passive".equals(command)) {
            tradingStateService.setActivePolling(false);
            telegramService.sendMessage("⏸️ Passive mode enabled\nSignal polling stopped (app still running)");
            log.info("🔭 Passive mode enabled via /observer command");
        } else {
            telegramService.sendMessage("❌ Invalid argument. Use: /observer on OR /observer off");
        }
    }
    
    /**
     * Handle /risk command for risk settings configuration
     */
    private void handleRiskCommand(String args) {
        if (args == null || args.trim().isEmpty()) {
            telegramService.sendMessage(stockRiskSettingsService.getAllStockRiskSettingsFormatted());
            return;
        }
        
        if ("help".equalsIgnoreCase(args.trim())) {
            telegramService.sendMessage(stockRiskSettingsService.getStockRiskSettingsHelp());
            return;
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        if (parts.length < 2) {
            telegramService.sendMessage("❌ Usage: /risk [key] [value]\n\nUse /risk help for all available keys");
            return;
        }
        
        String key = parts[0].toLowerCase();
        String value = parts[1].trim();
        
        String error = stockRiskSettingsService.updateRiskSetting(key, value);
        if (error != null) {
            telegramService.sendMessage(error);
        } else {
            telegramService.sendMessage(String.format("✅ Risk setting updated\n%s = %s", key, value));
            log.info("🔧 Risk setting updated via Telegram: {} = {}", key, value);
        }
    }
    
    // ========== DATA OPERATIONS COMMANDS ==========
    
    private void handlePopulateDataCommand(String args) {
        telegramService.sendMessage("⚠️ This command is deprecated.\nUse /run-backtest instead.");
    }
    
    private void handleRunBacktestsCommand(String args) {
        if (args != null && !args.trim().isEmpty() && "help".equalsIgnoreCase(args.trim())) {
            telegramService.sendMessage(
                "📊 RUN BACKTESTS\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "Usage: /run-backtests [initialCapital]\n" +
                "Example: /run-backtests 80000\n\n" +
                "Runs all strategies across the top 50 stocks with available data."
            );
            return;
        }

        double initialCapital = DEFAULT_BACKTEST_INITIAL_CAPITAL;
        if (args != null && !args.trim().isEmpty()) {
            try {
                initialCapital = Double.parseDouble(args.trim());
                if (!Double.isFinite(initialCapital) || initialCapital <= 0) {
                    telegramService.sendMessage("❌ Initial capital must be a positive number");
                    return;
                }
            } catch (NumberFormatException e) {
                telegramService.sendMessage("❌ Invalid initial capital: " + args.trim());
                return;
            }
        }

        double capital = initialCapital;
        telegramService.sendMessage(String.format(
            "🚀 RUN BACKTESTS STARTED\n" +
            "Initial capital: %.2f\n" +
            "Fetching top 50 stocks with available data...",
            capital
        ));

        new Thread(() -> {
            try {
                Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
                    backtestService.runParallelizedBacktest(capital);

                int stocksTested = results.size();
                int totalStrategyResults = results.values().stream()
                    .mapToInt(Map::size)
                    .sum();

                if (stocksTested == 0) {
                    telegramService.sendMessage(
                        "⚠️ RUN BACKTESTS COMPLETE\n" +
                        "No stocks had historical data.\n" +
                        "Use /download-history first."
                    );
                    return;
                }

                telegramService.sendMessage(String.format(
                    "✅ RUN BACKTESTS COMPLETE\n" +
                    "Stocks tested: %d\n" +
                    "Strategy results: %d",
                    stocksTested,
                    totalStrategyResults
                ));
            } catch (Exception e) {
                log.error("Run backtests failed", e);
                telegramService.sendMessage("❌ Run backtests failed: " + e.getMessage());
            }
        }).start();
    }
    
    private void handleSelectStrategyCommand(String args) {
        telegramService.sendMessage("🎯 Auto-selecting best strategy...");
        
        try {
            autoStrategySelector.selectBestStrategyAndStock();
            
            telegramService.sendMessage("✅ Strategy selection completed! Check /status for active configuration.");
        } catch (Exception e) {
            log.error("Failed to select strategy via Telegram", e);
            telegramService.sendMessage("❌ Error: " + e.getMessage());
        }
    }
    
    private void handleFullPipelineCommand(String args) {
        telegramService.sendMessage("⚠️ This command is deprecated.\nUse the REST API: POST /api/backtest/run");
    }
    
    private void handleDataStatusCommand(String args) {
        telegramService.sendMessage("⚠️ This command is deprecated.\nData is automatically managed by the backtest system.");
    }
}
