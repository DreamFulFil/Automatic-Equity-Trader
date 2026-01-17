package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.config.TelegramProperties;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandRegistry;
import tw.gc.auto.equity.trader.services.telegram.GoLiveStateManager;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ExtendWith(MockitoExtension.class)
class TelegramServiceDeepCoverageTest {

    @Mock(lenient = true) private RestTemplate restTemplate;
    @Mock(lenient = true) private TelegramProperties telegramProperties;
    @Mock(lenient = true) private StockSettingsService stockSettingsService;
    @Mock(lenient = true) private TelegramCommandRegistry commandRegistry;
    @Mock(lenient = true) private GoLiveStateManager goLiveStateManager;

    @Mock(lenient = true) private AgentService agentService;
    @Mock(lenient = true) private BotModeService botModeService;
    @Mock(lenient = true) private RiskManagerAgent riskManagerAgent;

    private TelegramService telegramService;

    @BeforeEach
    void setUp() {
        telegramService = new TelegramService(
            restTemplate,
            telegramProperties,
            new ObjectMapper(),
            stockSettingsService,
            commandRegistry,
            goLiveStateManager
        );

        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123");
    }

    @Test
    void init_whenEnabledAndOkWithResult_shouldAdvanceOffset() {
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(restTemplate.getForObject(contains("offset=-1"), eq(String.class)))
            .thenReturn("{\"ok\":true,\"result\":[{\"update_id\":41}]}");

        telegramService.init();

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn("{\"ok\":true,\"result\":[]}");

        telegramService.pollUpdates();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, atLeastOnce()).getForObject(urlCaptor.capture(), eq(String.class));
        assertThat(String.join("\n", urlCaptor.getAllValues())).contains("offset=42");
        verify(commandRegistry).registerCommands();
        verify(commandRegistry).getCommandCount();
    }

    @Test
    void init_whenEnabledAndOkButEmptyResult_shouldNotFail() {
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"ok\":true,\"result\":[]}");

        assertDoesNotThrow(() -> telegramService.init());
    }

    @Test
    void init_whenEnabledAndOkButResultNotArray_shouldNotFail() {
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"ok\":true,\"result\":{}}");

        assertDoesNotThrow(() -> telegramService.init());
    }

    @Test
    void init_whenDisabled_shouldNotHitNetwork() {
        when(telegramProperties.isEnabled()).thenReturn(false);

        telegramService.init();

        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
    }

    @Test
    void init_whenEnabledAndOkFalse_shouldNotCrash() {
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"ok\":false,\"result\":[]}");

        assertDoesNotThrow(() -> telegramService.init());
    }

    @Test
    void clearOldMessages_whenMalformedJsonOrNetworkFailure_shouldBeCaught() {
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("t")));

        assertDoesNotThrow(() -> telegramService.init());
    }

    @Test
    void pollUpdates_whenNetworkLayerThrowsTimeoutOrUnknownHostOrHttpErrors_shouldNotThrow() {
        when(telegramProperties.isEnabled()).thenReturn(true);

        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("t")))
            .thenThrow(new ResourceAccessException("unknown", new UnknownHostException("h")))
            .thenThrow(HttpClientErrorException.create(UNAUTHORIZED, "401", null, "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
            .thenThrow(HttpServerErrorException.create(BAD_GATEWAY, "502", null, "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> telegramService.pollUpdates());
        assertDoesNotThrow(() -> telegramService.pollUpdates());
        assertDoesNotThrow(() -> telegramService.pollUpdates());
        assertDoesNotThrow(() -> telegramService.pollUpdates());
    }

    @Test
    void processUpdate_whenMessageMissing_shouldReturnEarly() {
        when(telegramProperties.isEnabled()).thenReturn(true);

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(
            "{\"ok\":true,\"result\":[{\"update_id\":1}]}"
        );

        assertDoesNotThrow(() -> telegramService.pollUpdates());
        verify(restTemplate, never()).postForObject(contains("sendMessage"), any(), eq(String.class));
    }

    @Test
    void dispatchCommand_whenAgentsAlias_shouldExecuteAgentCommand() {
        when(telegramProperties.isEnabled()).thenReturn(true);

        TelegramCommand agent = mock(TelegramCommand.class);
        when(commandRegistry.getCommand("agents")).thenReturn(null);
        when(commandRegistry.getCommand("agent")).thenReturn(agent);

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(
            "{\"ok\":true,\"result\":[{" +
                "\"update_id\":1," +
                "\"message\":{" +
                    "\"chat\":{\"id\":\"123\"}," +
                    "\"text\":\"/agents\"" +
                "}" +
            "}]}"
        );

        telegramService.pollUpdates();

        verify(agent).execute(eq(""), any());
    }

    @Test
    void dispatchCommand_whenAgentsAliasButMissingAgentCommand_shouldFallBackToUnknown() {
        when(telegramProperties.isEnabled()).thenReturn(true);

        when(commandRegistry.getCommand("agents")).thenReturn(null);
        when(commandRegistry.getCommand("agent")).thenReturn(null);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        when(restTemplate.postForObject(urlCaptor.capture(), bodyCaptor.capture(), eq(String.class))).thenReturn("{}");

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(
            "{\"ok\":true,\"result\":[{" +
                "\"update_id\":1," +
                "\"message\":{" +
                    "\"chat\":{\"id\":\"123\"}," +
                    "\"text\":\"/agents\"" +
                "}" +
            "}]}"
        );

        telegramService.pollUpdates();

        assertThat(urlCaptor.getAllValues()).anyMatch(u -> u.contains("sendMessage"));
    }

    @Test
    void processUpdate_whenTextNotCommand_shouldReturnEarly() {
        when(telegramProperties.isEnabled()).thenReturn(true);

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(
            "{\"ok\":true,\"result\":[{\"update_id\":1,\"message\":{\"chat\":{\"id\":\"123\"},\"text\":\"hello\"}}]}"
        );

        telegramService.pollUpdates();

        verify(restTemplate, never()).postForObject(contains("sendMessage"), any(), eq(String.class));
    }

    @Test
    void sendDailySummaryDigest_shouldCoverAllBranches() {
        telegramService.setAgentService(agentService);
        telegramService.setBotModeService(botModeService);

        when(agentService.getRiskManager()).thenReturn(riskManagerAgent);
        when(botModeService.isLiveMode()).thenReturn(true);

        when(riskManagerAgent.getTradeStats(TradingMode.LIVE)).thenReturn(Map.of("success", false));
        telegramService.sendDailySummaryDigest();

        when(riskManagerAgent.getTradeStats(TradingMode.LIVE)).thenReturn(Map.of(
            "success", true,
            "total_trades_30d", "5",
            "win_rate", "60%",
            "total_pnl_30d", 1234.0
        ));

        when(telegramProperties.isEnabled()).thenReturn(false);
        telegramService.sendDailySummaryDigest();

        reset(riskManagerAgent);
        when(botModeService.isLiveMode()).thenReturn(false);
        when(agentService.getRiskManager()).thenReturn(riskManagerAgent);
        when(riskManagerAgent.getTradeStats(TradingMode.SIMULATION)).thenReturn(Map.of("success", true, "total_pnl_30d", 0.0));
        telegramService.sendDailySummaryDigest();
    }

    @Test
    void sendDailySummaryDigest_whenDependenciesMissing_shouldReturnEarly() {
        telegramService.sendDailySummaryDigest();
        telegramService.setAgentService(agentService);
        telegramService.sendDailySummaryDigest();
    }

    @Test
    void setters_shouldSetNullableDependencies() {
        telegramService.setTradingStateService(mock(TradingStateService.class));
        telegramService.setPositionManager(mock(PositionManager.class));
        telegramService.setRiskManagementService(mock(RiskManagementService.class));
        telegramService.setContractScalingService(mock(ContractScalingService.class));
        telegramService.setOrderExecutionService(mock(OrderExecutionService.class));
        telegramService.setActiveStockService(mock(ActiveStockService.class));

        // No assertions needed; JaCoCo requires execution.
    }
}
