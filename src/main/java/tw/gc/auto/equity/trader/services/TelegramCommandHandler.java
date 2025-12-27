package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.TelegramService;
import tw.gc.auto.equity.trader.RiskManagementService;
import tw.gc.auto.equity.trader.RiskSettingsService;
import tw.gc.auto.equity.trader.StockSettingsService;
import tw.gc.auto.equity.trader.ShioajiSettingsService;
import tw.gc.auto.equity.trader.ContractScalingService;
import tw.gc.auto.equity.trader.strategy.IStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramCommandHandler {

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
    private final RiskSettingsService riskSettingsService; // Added for flattenPosition call if needed

    public void registerCommands(List<IStrategy> activeStrategies) {
        telegramService.registerCommandHandlers(
            v -> handleStatusCommand(),
            v -> handlePauseCommand(),
            v -> handleResumeCommand(),
            v -> handleCloseCommand(),
            v -> handleShutdownCommand()
        );
        
        // Register dynamic strategy switching
        telegramService.registerCustomCommand("/strategy", args -> {
            if (args == null || args.trim().isEmpty()) {
                telegramService.sendMessage("Current Active Strategy: " + tradingStateService.getActiveStrategyName() + 
                    "\nAvailable: " + activeStrategies.stream().map(IStrategy::getName).reduce((a,b) -> a + ", " + b).orElse("None"));
            } else {
                String newStrategy = args.trim();
                // Verify it exists (fuzzy match)
                boolean exists = activeStrategies.stream()
                    .anyMatch(s -> s.getName().equalsIgnoreCase(newStrategy));
                
                if (exists || "LegacyBridge".equalsIgnoreCase(newStrategy)) {
                    tradingStateService.setActiveStrategyName(newStrategy);
                    telegramService.sendMessage("✅ Active Strategy switched to: " + newStrategy);
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
                telegramService.sendMessage("Usage: /ask <question>\nAsk the Tutor Bot about trading concepts.");
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
    }

    private void handleShutdownCommand() {
        log.info("🛑 Shutdown command received via Telegram");
        telegramService.sendMessage("🛑 Shutting down application...\nFlattening all positions");
        
        // Trigger shutdown in background thread
        new Thread(() -> {
            try {
                flattenPosition("Shutdown via Telegram command");
                Thread.sleep(2000); // Give time for messages to send
                
                int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
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
                    java.time.Duration.between(entryTimeRef.get(), LocalDateTime.now(tw.gc.auto.equity.trader.AppConstants.TAIPEI_ZONE)).toMinutes() : 0
            );
        
        String tradingMode = tradingStateService.getTradingMode();
        String modeInfo = "stock".equals(tradingMode) 
            ? String.format("Mode: STOCK (2454.TW)\nShares: %d (base %d +%d/20k)", 
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
        telegramService.sendMessage("⏸️ Trading PAUSED\nNo new entries until /resume\nExisting positions will still flatten at 13:00");
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
        return "stock".equals(tradingStateService.getTradingMode()) ? "2454.TW" : "AUTO_EQUITY_TRADER";
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
}
