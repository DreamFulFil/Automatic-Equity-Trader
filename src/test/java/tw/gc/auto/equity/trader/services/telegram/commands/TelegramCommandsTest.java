package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.agents.TutorBotAgent;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.services.*;
import tw.gc.auto.equity.trader.services.telegram.GoLiveStateManager;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramCommandsTest {

    @Mock
    private TelegramService telegramService;
    
    @Mock
    private BotModeService botModeService;
    
    @Mock
    private AgentService agentService;
    
    @Mock
    private OrderExecutionService orderExecutionService;
    
    @Mock
    private StockSettingsService stockSettingsService;
    
    @Mock
    private TutorBotAgent tutorBotAgent;
    
    @Mock
    private RiskManagerAgent riskManagerAgent;
    
    @Mock
    private GoLiveStateManager goLiveStateManager;
    
    @Mock
    private Consumer<Void> shutdownHandler;
    
    private TelegramCommandContext context;

    @BeforeEach
    void setUp() {
        context = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .botModeService(botModeService)
                .agentService(agentService)
                .orderExecutionService(orderExecutionService)
                .stockSettingsService(stockSettingsService)
                .goLiveStateManager(goLiveStateManager)
                .shutdownHandler(shutdownHandler)
                .build();
        
        lenient().when(agentService.getTutorBot()).thenReturn(tutorBotAgent);
        lenient().when(agentService.getRiskManager()).thenReturn(riskManagerAgent);
    }

    @Test
    void testAgentCommandWithService() {
        AgentCommand command = new AgentCommand();
        String mockAgentList = "Available Agents:\n1. TutorBot\n2. RiskManager";
        when(agentService.getAgentListMessage()).thenReturn(mockAgentList);
        
        command.execute("", context);
        
        verify(agentService).getAgentListMessage();
        verify(telegramService).sendMessage(mockAgentList);
        assertEquals("agent", command.getCommandName());
        assertEquals("/agent - List agents", command.getHelpText());
    }

    @Test
    void testAgentCommandWithoutService() {
        AgentCommand command = new AgentCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .build();
        
        command.execute("", emptyContext);
        
        verify(telegramService).sendMessage("⚠️ Agent service not initialized");
        verifyNoInteractions(agentService);
    }

    @Test
    void testBackToSimCommandAlreadyInSimMode() {
        BackToSimCommand command = new BackToSimCommand();
        when(botModeService.isSimulationMode()).thenReturn(true);
        
        command.execute("", context);
        
        verify(botModeService).isSimulationMode();
        verify(botModeService, never()).switchToSimulationMode();
        verify(telegramService).sendMessage("ℹ️ Already in SIMULATION mode!");
        assertEquals("backtosim", command.getCommandName());
        assertEquals("/backtosim - Switch to simulation", command.getHelpText());
    }

    @Test
    void testBackToSimCommandSwitchFromLive() {
        BackToSimCommand command = new BackToSimCommand();
        when(botModeService.isSimulationMode()).thenReturn(false);
        
        command.execute("", context);
        
        verify(botModeService).isSimulationMode();
        verify(botModeService).switchToSimulationMode();
        verify(telegramService).sendMessage("✅ Switched back to SIMULATION mode\nNo real money at risk.");
    }

    @Test
    void testBackToSimCommandWithoutBotModeService() {
        BackToSimCommand command = new BackToSimCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .build();
        
        command.execute("", emptyContext);
        
        verify(telegramService).sendMessage("⚠️ Bot mode service not initialized");
        verifyNoInteractions(botModeService);
    }

    @Test
    void testHelpCommand() {
        HelpCommand command = new HelpCommand();
        when(telegramService.getRegistryHelpLines()).thenReturn(java.util.List.of(
            "/status - Bot status & position",
            "/agent - List agents",
            "/golive - Check live eligibility"
        ));
        when(telegramService.getCustomCommandNames()).thenReturn(java.util.List.of());
        
        command.execute("", context);
        
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("AVAILABLE COMMANDS") &&
            message.contains("/status") &&
            message.contains("/agent") &&
            message.contains("/golive")
        ));
        assertEquals("help", command.getCommandName());
        assertEquals("/help - Show all commands", command.getHelpText());
    }

    @Test
    void testHelpCommandWithoutTelegramService() {
        HelpCommand command = new HelpCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder().build();
        
        command.execute("", emptyContext);
        
        verifyNoInteractions(telegramService);
    }

    @Test
    void testShutdownCommand() {
        ShutdownCommand command = new ShutdownCommand();
        
        command.execute("", context);
        
        verify(shutdownHandler).accept(null);
        assertEquals("shutdown", command.getCommandName());
        assertEquals("/shutdown - Stop application", command.getHelpText());
    }

    @Test
    void testShutdownCommandWithoutHandler() {
        ShutdownCommand command = new ShutdownCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder().build();
        
        command.execute("", emptyContext);
        
        verifyNoInteractions(shutdownHandler);
    }

    @Test
    void testInsightCommand() {
        InsightCommand command = new InsightCommand();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("response", "Market looking bullish");
        result.put("remaining", 5);
        when(tutorBotAgent.generateDailyInsight("telegram")).thenReturn(result);
        
        command.execute("", context);
        
        verify(agentService).getTutorBot();
        verify(tutorBotAgent).generateDailyInsight("telegram");
        verify(telegramService).sendMessage("💡 Generating insight...");
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("Daily Insight") && 
            msg.contains("Market looking bullish") &&
            msg.contains("5 insights remaining today")
        ));
        assertEquals("insight", command.getCommandName());
        assertEquals("/insight - Daily insight", command.getHelpText());
    }
    
    @Test
    void testInsightCommandFailure() {
        InsightCommand command = new InsightCommand();
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", "Rate limit exceeded");
        when(tutorBotAgent.generateDailyInsight("telegram")).thenReturn(result);
        
        command.execute("", context);
        
        verify(telegramService).sendMessage(argThat(msg -> msg.contains("Rate limit exceeded")));
    }

    @Test
    void testInsightCommandWithoutAgentService() {
        InsightCommand command = new InsightCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .build();
        
        command.execute("", emptyContext);
        
        verify(telegramService).sendMessage("⚠️ Agent service not initialized");
        verifyNoInteractions(agentService);
    }

    @Test
    void testTalkCommand() {
        TalkCommand command = new TalkCommand();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("response", "Momentum trading involves...");
        result.put("remaining", 8);
        when(tutorBotAgent.safeExecute(any())).thenReturn(result);
        
        command.execute("test question", context);
        
        verify(agentService).getTutorBot();
        verify(tutorBotAgent).safeExecute(any());
        verify(telegramService).sendMessage("🤔 Thinking...");
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("TutorBot") && 
            msg.contains("Momentum trading involves") &&
            msg.contains("8 questions remaining today")
        ));
        assertEquals("talk", command.getCommandName());
        assertEquals("/talk [question] - Ask TutorBot", command.getHelpText());
    }

    @Test
    void testTalkCommandWithEmptyQuestion() {
        TalkCommand command = new TalkCommand();
        
        command.execute(null, context);
        
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("Usage: /talk") && msg.contains("Example")
        ));
        verifyNoInteractions(tutorBotAgent);
    }

    @Test
    void testTalkCommand_whenTelegramServiceNull_shouldNotSendMessage() {
        TalkCommand command = new TalkCommand();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("response", "OK");
        result.put("remaining", 1);
        when(tutorBotAgent.safeExecute(any())).thenReturn(result);

        TelegramCommandContext noTelegramServiceContext = TelegramCommandContext.builder()
                .agentService(agentService)
                .build();

        command.execute("test", noTelegramServiceContext);

        verify(tutorBotAgent).safeExecute(any());
        verifyNoInteractions(telegramService);
    }
    
    @Test
    void testTalkCommandFailure() {
        TalkCommand command = new TalkCommand();
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", "TutorBot is offline");
        when(tutorBotAgent.safeExecute(any())).thenReturn(result);
        
        command.execute("test", context);
        
        verify(telegramService).sendMessage(argThat(msg -> msg.contains("TutorBot is offline")));
    }

    @Test
    void testTalkCommandWithoutAgentService() {
        TalkCommand command = new TalkCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .build();
        
        command.execute("test", emptyContext);
        
        verify(telegramService).sendMessage("⚠️ Agent service not initialized");
        verifyNoInteractions(agentService);
    }

    @Test
    void testGoLiveCommand() {
        GoLiveCommand command = new GoLiveCommand();
        when(botModeService.isLiveMode()).thenReturn(false);
        
        Map<String, Object> eligibilityResult = new HashMap<>();
        eligibilityResult.put("eligible", true);
        eligibilityResult.put("total_trades", 150);
        eligibilityResult.put("win_rate", "60%");
        eligibilityResult.put("win_rate_ok", true);
        eligibilityResult.put("max_drawdown", "8%");
        eligibilityResult.put("drawdown_ok", true);
        eligibilityResult.put("has_enough_trades", true);
        eligibilityResult.put("requirements", "All requirements met");
        when(riskManagerAgent.checkGoLiveEligibility()).thenReturn(eligibilityResult);
        
        command.execute("", context);
        
        verify(botModeService).isLiveMode();
        verify(riskManagerAgent).checkGoLiveEligibility();
        verify(goLiveStateManager).markPending();
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("GO-LIVE ELIGIBILITY") &&
            message.contains("ELIGIBLE FOR LIVE TRADING") &&
            message.contains("/confirmlive")
        ));
        assertEquals("golive", command.getCommandName());
        assertEquals("/golive - Check live eligibility", command.getHelpText());
    }

    @Test
    void testGoLiveCommandAlreadyLive() {
        GoLiveCommand command = new GoLiveCommand();
        when(botModeService.isLiveMode()).thenReturn(true);
        
        command.execute("", context);
        
        verify(botModeService).isLiveMode();
        verifyNoInteractions(riskManagerAgent);
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("Already in LIVE mode")
        ));
    }

    @Test
    void testGoLiveCommandNotEligible() {
        GoLiveCommand command = new GoLiveCommand();
        when(botModeService.isLiveMode()).thenReturn(false);
        
        Map<String, Object> eligibilityResult = new HashMap<>();
        eligibilityResult.put("eligible", false);
        eligibilityResult.put("total_trades", 50);
        eligibilityResult.put("win_rate", "45%");
        eligibilityResult.put("win_rate_ok", false);
        eligibilityResult.put("max_drawdown", "15%");
        eligibilityResult.put("drawdown_ok", false);
        eligibilityResult.put("has_enough_trades", false);
        eligibilityResult.put("requirements", "Need more trades");
        when(riskManagerAgent.checkGoLiveEligibility()).thenReturn(eligibilityResult);
        
        command.execute("", context);
        
        verify(goLiveStateManager).clearPending();
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("NOT YET ELIGIBLE") &&
            message.contains("simulation")
        ));
    }

    @Test
    void testConfirmLiveCommand() {
        ConfirmLiveCommand command = new ConfirmLiveCommand();
        when(botModeService.isLiveMode()).thenReturn(false);
        when(goLiveStateManager.isValid()).thenReturn(true);
        
        command.execute("", context);
        
        verify(botModeService).switchToLiveMode();
        verify(goLiveStateManager).clearPending();
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("LIVE MODE ENABLED") &&
            message.contains("Real orders")
        ));
        assertEquals("confirmlive", command.getCommandName());
        assertEquals("/confirmlive - Confirm live switch", command.getHelpText());
    }

    @Test
    void testConfirmLiveCommandAlreadyLive() {
        ConfirmLiveCommand command = new ConfirmLiveCommand();
        when(botModeService.isLiveMode()).thenReturn(true);
        
        command.execute("", context);
        
        verify(botModeService, never()).switchToLiveMode();
        verify(telegramService).sendMessage("ℹ️ Already in LIVE mode");
    }

    @Test
    void testConfirmLiveCommandExpired() {
        ConfirmLiveCommand command = new ConfirmLiveCommand();
        when(botModeService.isLiveMode()).thenReturn(false);
        when(goLiveStateManager.isValid()).thenReturn(false);
        
        command.execute("", context);
        
        verify(botModeService, never()).switchToLiveMode();
        verify(goLiveStateManager).clearPending();
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("expired") &&
            message.contains("/golive")
        ));
    }

    @Test
    void testChangeIncrementCommand() {
        ChangeIncrementCommand command = new ChangeIncrementCommand();
        StockSettings settings = new StockSettings();
        settings.setShares(100);
        when(stockSettingsService.getSettings()).thenReturn(settings);
        
        command.execute("10", context);
        
        verify(stockSettingsService).getSettings();
        verify(stockSettingsService).updateSettings(100, 10);
        verify(telegramService).sendMessage("✅ Share increment updated to: 10");
        assertEquals("change-increment", command.getCommandName());
        assertEquals("/change-increment [number] - Change share increment", command.getHelpText());
    }

    @Test
    void testChangeIncrementCommandInvalidNumber() {
        ChangeIncrementCommand command = new ChangeIncrementCommand();
        
        command.execute("abc", context);
        
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("Invalid number")
        ));
        verifyNoInteractions(stockSettingsService);
    }

    @Test
    void testChangeIncrementCommandEmptyArgs() {
        ChangeIncrementCommand command = new ChangeIncrementCommand();
        
        command.execute("", context);
        
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("Usage: /change-increment")
        ));
        verifyNoInteractions(stockSettingsService);
    }
    
    @Test
    void testChangeIncrementCommandNegativeNumber() {
        ChangeIncrementCommand command = new ChangeIncrementCommand();
        
        command.execute("-5", context);
        
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("must be positive")
        ));
        verifyNoInteractions(stockSettingsService);
    }
    
    @Test
    void testGoLiveCommandWithNullServices() {
        GoLiveCommand command = new GoLiveCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .build();
        
        command.execute("", emptyContext);
        
        verify(telegramService).sendMessage("⚠️ Services not initialized");
        verifyNoInteractions(agentService);
        verifyNoInteractions(botModeService);
    }
    
    @Test
    void testConfirmLiveCommandWithNullService() {
        ConfirmLiveCommand command = new ConfirmLiveCommand();
        TelegramCommandContext emptyContext = TelegramCommandContext.builder()
                .telegramService(telegramService)
                .build();
        
        command.execute("", emptyContext);
        
        verify(telegramService).sendMessage("⚠️ Bot mode service not initialized");
        verifyNoInteractions(botModeService);
    }
    
    @Test
    void testChangeIncrementCommandWithException() {
        ChangeIncrementCommand command = new ChangeIncrementCommand();
        StockSettings settings = new StockSettings();
        settings.setShares(100);
        when(stockSettingsService.getSettings()).thenReturn(settings);
        doThrow(new RuntimeException("DB error")).when(stockSettingsService).updateSettings(anyInt(), anyInt());
        
        command.execute("10", context);
        
        verify(stockSettingsService).getSettings();
        verify(stockSettingsService).updateSettings(100, 10);
        verify(telegramService).sendMessage("❌ Failed to update increment");
    }
    
    @Test
    void testStatusCommand() {
        StatusCommand command = new StatusCommand();
        Consumer<Void> statusHandler = mock(Consumer.class);
        TelegramCommandContext contextWithHandler = TelegramCommandContext.builder()
                .statusHandler(statusHandler)
                .build();
        
        command.execute("", contextWithHandler);
        
        verify(statusHandler).accept(null);
        assertEquals("status", command.getCommandName());
        assertEquals("/status - Bot status & position", command.getHelpText());
    }
    
    @Test
    void testResumeCommand() {
        ResumeCommand command = new ResumeCommand();
        Consumer<Void> resumeHandler = mock(Consumer.class);
        TelegramCommandContext contextWithHandler = TelegramCommandContext.builder()
                .resumeHandler(resumeHandler)
                .build();
        
        command.execute("", contextWithHandler);
        
        verify(resumeHandler).accept(null);
        assertEquals("resume", command.getCommandName());
        assertEquals("/resume - Resume trading", command.getHelpText());
    }
    
    @Test
    void testPauseCommand() {
        PauseCommand command = new PauseCommand();
        Consumer<Void> pauseHandler = mock(Consumer.class);
        TelegramCommandContext contextWithHandler = TelegramCommandContext.builder()
                .pauseHandler(pauseHandler)
                .build();
        
        command.execute("", contextWithHandler);
        
        verify(pauseHandler).accept(null);
        assertEquals("pause", command.getCommandName());
        assertEquals("/pause - Pause trading", command.getHelpText());
    }
    
    @Test
    void testCloseCommand() {
        CloseCommand command = new CloseCommand();
        Consumer<Void> closeHandler = mock(Consumer.class);
        TelegramCommandContext contextWithHandler = TelegramCommandContext.builder()
                .closeHandler(closeHandler)
                .build();
        
        command.execute("", contextWithHandler);
        
        verify(closeHandler).accept(null);
        assertEquals("close", command.getCommandName());
        assertEquals("/close - Close position", command.getHelpText());
    }
}
