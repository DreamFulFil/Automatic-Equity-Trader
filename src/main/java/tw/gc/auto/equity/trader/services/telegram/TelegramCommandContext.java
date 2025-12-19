package tw.gc.auto.equity.trader.services.telegram;

import lombok.Builder;
import lombok.Getter;
import tw.gc.auto.equity.trader.services.*;
import java.util.function.Consumer;

/**
 * Context object for command execution.
 * 
 * <p><b>Why Context Object:</b> Bundles all dependencies needed by commands into a single
 * immutable object. This pattern prevents parameter proliferation and makes it easy to
 * add new dependencies without changing all command signatures.</p>
 * 
 * <p><b>Design Decision:</b> Uses Lombok @Builder for immutability and clean construction.
 * All fields are nullable to support gradual dependency injection as services are initialized.</p>
 */
@Getter
@Builder
public class TelegramCommandContext {
    
    // Core services
    private final TelegramService telegramService;
    private final TradingStateService tradingStateService;
    private final PositionManager positionManager;
    private final RiskManagementService riskManagementService;
    private final ContractScalingService contractScalingService;
    private final StockSettingsService stockSettingsService;
    private final OrderExecutionService orderExecutionService;
    private final ActiveStockService activeStockService;
    
    // Agent and mode services
    private final AgentService agentService;
    private final BotModeService botModeService;
    
    // Go-live state management
    private final GoLiveStateManager goLiveStateManager;
    
    // Command handlers for legacy integration
    private final Consumer<Void> statusHandler;
    private final Consumer<Void> pauseHandler;
    private final Consumer<Void> resumeHandler;
    private final Consumer<Void> closeHandler;
    private final Consumer<Void> shutdownHandler;
}
