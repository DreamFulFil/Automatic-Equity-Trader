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

import java.lang.reflect.Method;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceGoLiveTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StockSettingsService stockSettingsService;

    @Mock
    private AgentService agentService;

    @Mock
    private BotModeService botModeService;

    @Mock
    private RiskManagerAgent riskManagerAgent;

    private TelegramProperties properties;
    private TelegramService telegramService;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.setBotToken("dummy");
        properties.setChatId("chat");
        properties.setEnabled(false); // Avoid network calls during tests

        telegramService = new TelegramService(restTemplate, properties, objectMapper, stockSettingsService);
        telegramService.setAgentService(agentService);
        telegramService.setBotModeService(botModeService);
    }

    @Test
    void confirmlive_withPendingConfirmation_switchesToLiveMode() throws Exception {
        when(agentService.getRiskManager()).thenReturn(riskManagerAgent);
        when(botModeService.isLiveMode()).thenReturn(false);
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

        invokeHandleGoLive("chat");
        invokeHandleConfirmLive("chat");

        verify(botModeService).switchToLiveMode();
    }

    @Test
    void confirmlive_withoutPending_doesNotSwitch() throws Exception {
        when(botModeService.isLiveMode()).thenReturn(false);

        invokeHandleConfirmLive("chat");

        verify(botModeService, never()).switchToLiveMode();
    }

    private void invokeHandleGoLive(String chatId) throws Exception {
        Method method = TelegramService.class.getDeclaredMethod("handleGoLiveCommand", String.class);
        method.setAccessible(true);
        method.invoke(telegramService, chatId);
    }

    private void invokeHandleConfirmLive(String chatId) throws Exception {
        Method method = TelegramService.class.getDeclaredMethod("handleConfirmLiveCommand", String.class);
        method.setAccessible(true);
        method.invoke(telegramService, chatId);
    }
}
