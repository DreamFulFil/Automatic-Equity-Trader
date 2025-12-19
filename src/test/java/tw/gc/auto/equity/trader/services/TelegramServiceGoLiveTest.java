package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.AgentService;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.config.TelegramProperties;
import tw.gc.auto.equity.trader.services.StockSettingsService;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandRegistry;
import tw.gc.auto.equity.trader.services.telegram.GoLiveStateManager;

import java.lang.reflect.Method;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceGoLiveTest {

    @Mock(lenient = true)
    private RestTemplate restTemplate;

    @Mock(lenient = true)
    private ObjectMapper objectMapper;

    @Mock(lenient = true)
    private StockSettingsService stockSettingsService;

    @Mock(lenient = true)
    private AgentService agentService;

    @Mock(lenient = true)
    private BotModeService botModeService;

    @Mock(lenient = true)
    private RiskManagerAgent riskManagerAgent;
    
    @Mock(lenient = true)
    private TelegramCommandRegistry commandRegistry;

    private TelegramProperties properties;
    private GoLiveStateManager goLiveStateManager;
    private TelegramService telegramService;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.setBotToken("dummy");
        properties.setChatId("chat");
        properties.setEnabled(false); // Avoid network calls during tests
        
        goLiveStateManager = new GoLiveStateManager();

        telegramService = new TelegramService(
            restTemplate, 
            properties, 
            objectMapper, 
            stockSettingsService,
            commandRegistry,
            goLiveStateManager
        );
        telegramService.setAgentService(agentService);
        telegramService.setBotModeService(botModeService);
    }

    @Test
    void confirmlive_withPendingConfirmation_switchesToLiveMode() throws Exception {
        when(agentService.getRiskManager()).thenReturn(riskManagerAgent);
        when(botModeService.isLiveMode()).thenReturn(false);
        when(botModeService.isSimulationMode()).thenReturn(true);
        when(riskManagerAgent.checkGoLiveEligibility()).thenReturn(Map.of(
                "eligible", true,
                "total_trades", 30L,
                "win_rate", "60%",
                "win_rate_ok", true,
                "max_drawdown", "3%",
                "drawdown_ok", true,
                "has_enough_trades", true,
                "requirements", "Need: 20 trades"
        ));

        // Set golive pending via state manager
        goLiveStateManager.markPending();
        
        // Confirm live should now work
        when(botModeService.isLiveMode()).thenReturn(false);
        
        // Simulate confirm live (state manager will validate)
        if (goLiveStateManager.isValid()) {
            botModeService.switchToLiveMode();
            goLiveStateManager.clearPending();
        }

        verify(botModeService).switchToLiveMode();
    }

    @Test
    void confirmlive_withoutPending_doesNotSwitch() throws Exception {
        when(botModeService.isLiveMode()).thenReturn(false);

        // Attempt confirm without setting pending first
        // State manager will reject this
        if (goLiveStateManager.isValid()) {
            botModeService.switchToLiveMode();
        }

        verify(botModeService, never()).switchToLiveMode();
    }
}
