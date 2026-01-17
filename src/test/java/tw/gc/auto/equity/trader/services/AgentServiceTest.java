package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.agents.BaseAgent;
import tw.gc.auto.equity.trader.agents.NewsAnalyzerAgent;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.agents.SignalGeneratorAgent;
import tw.gc.auto.equity.trader.agents.TutorBotAgent;
import tw.gc.auto.equity.trader.config.OllamaProperties;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.Agent.AgentStatus;
import tw.gc.auto.equity.trader.repositories.AgentInteractionRepository;
import tw.gc.auto.equity.trader.repositories.AgentRepository;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AgentInteractionRepository interactionRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TradingProperties tradingProperties;

    @Mock
    private OllamaProperties ollamaProperties;

    @Mock
    private TelegramService telegramService;

    @Mock
    private BotModeService botModeService;

    @Mock
    private BotSettingsRepository botSettingsRepository;

    private AgentService service;

    @BeforeEach
    void setUp() {
        TradingProperties.Bridge bridge = mock(TradingProperties.Bridge.class);
        lenient().when(bridge.getUrl()).thenReturn("http://localhost:5000");
        lenient().when(tradingProperties.getBridge()).thenReturn(bridge);
        lenient().when(ollamaProperties.getUrl()).thenReturn("http://localhost:11434");
        lenient().when(ollamaProperties.getModel()).thenReturn("llama3.1:8b");

        service = new AgentService(
                agentRepository,
                tradeRepository,
                interactionRepository,
                restTemplate,
                objectMapper,
                tradingProperties,
                ollamaProperties,
                telegramService,
                botModeService,
                botSettingsRepository
        );
    }

    @Test
    void initialize_shouldRegisterAllAgents() {
        doNothing().when(telegramService).setAgentService(any());
        doNothing().when(telegramService).setBotModeService(any());
        when(agentRepository.existsByName(anyString())).thenReturn(false);

        service.initialize();

        verify(agentRepository, atLeast(4)).save(any());
        verify(telegramService).setAgentService(service);
        verify(telegramService).setBotModeService(botModeService);
    }

    @Test
    void registerAgent_shouldAddAgentToRegistry() {
        BaseAgent mockAgent = mock(BaseAgent.class);
        when(mockAgent.getName()).thenReturn("TestAgent");
        when(mockAgent.getDescription()).thenReturn("Test Description");

        service.registerAgent(mockAgent);

        assertThat(service.getAgent("TestAgent")).isEqualTo(mockAgent);
    }

    @Test
    void getAgent_withExistingName_shouldReturnAgent() {
        BaseAgent mockAgent = mock(BaseAgent.class);
        when(mockAgent.getName()).thenReturn("TestAgent");
        when(mockAgent.getDescription()).thenReturn("Test");

        service.registerAgent(mockAgent);

        BaseAgent retrieved = service.getAgent("TestAgent");

        assertThat(retrieved).isEqualTo(mockAgent);
    }

    @Test
    void getAgent_withNonExistingName_shouldReturnNull() {
        BaseAgent retrieved = service.getAgent("NonExistent");

        assertThat(retrieved).isNull();
    }

    @Test
    void getAllAgents_shouldReturnAllRegisteredAgents() {
        BaseAgent agent1 = mock(BaseAgent.class);
        when(agent1.getName()).thenReturn("Agent1");
        when(agent1.getDescription()).thenReturn("First");
        
        BaseAgent agent2 = mock(BaseAgent.class);
        when(agent2.getName()).thenReturn("Agent2");
        when(agent2.getDescription()).thenReturn("Second");

        service.registerAgent(agent1);
        service.registerAgent(agent2);

        List<BaseAgent> allAgents = service.getAllAgents();

        assertThat(allAgents).hasSize(2);
        assertThat(allAgents).containsExactlyInAnyOrder(agent1, agent2);
    }

    @Test
    void getAgentListMessage_shouldFormatAgentsForTelegram() {
        BaseAgent mockAgent = mock(BaseAgent.class);
        when(mockAgent.getName()).thenReturn("TestBot");
        when(mockAgent.getDescription()).thenReturn("Test agent");
        when(mockAgent.getStatus()).thenReturn(AgentStatus.ACTIVE);
        when(mockAgent.getCapabilities()).thenReturn(List.of("test", "analyze"));

        service.registerAgent(mockAgent);

        String message = service.getAgentListMessage();

        assertThat(message).contains("AVAILABLE AGENTS");
        assertThat(message).contains("TestBot");
        assertThat(message).contains("Test agent");
        assertThat(message).contains("test, analyze");
        assertThat(message).contains("/talk");
        assertThat(message).contains("/insight");
    }

    @Test
    void getAgentListMessage_withActiveAgent_shouldShowGreenIcon() {
        BaseAgent activeAgent = mock(BaseAgent.class);
        when(activeAgent.getName()).thenReturn("Active");
        when(activeAgent.getDescription()).thenReturn("Active");
        when(activeAgent.getStatus()).thenReturn(AgentStatus.ACTIVE);
        when(activeAgent.getCapabilities()).thenReturn(List.of());

        service.registerAgent(activeAgent);

        String message = service.getAgentListMessage();

        assertThat(message).contains("🟢");
    }

    @Test
    void getAgentListMessage_withErrorAgent_shouldShowRedIcon() {
        BaseAgent errorAgent = mock(BaseAgent.class);
        when(errorAgent.getName()).thenReturn("Error");
        when(errorAgent.getDescription()).thenReturn("Error");
        when(errorAgent.getStatus()).thenReturn(AgentStatus.ERROR);
        when(errorAgent.getCapabilities()).thenReturn(List.of());

        service.registerAgent(errorAgent);

        String message = service.getAgentListMessage();

        assertThat(message).contains("🔴");
    }

    @Test
    void getAgentListMessage_withInactiveAgent_shouldShowYellowIcon() {
        BaseAgent inactiveAgent = mock(BaseAgent.class);
        when(inactiveAgent.getName()).thenReturn("Inactive");
        when(inactiveAgent.getDescription()).thenReturn("Inactive");
        when(inactiveAgent.getStatus()).thenReturn(AgentStatus.INACTIVE);
        when(inactiveAgent.getCapabilities()).thenReturn(List.of());

        service.registerAgent(inactiveAgent);

        String message = service.getAgentListMessage();

        assertThat(message).contains("🟡");
    }

    @Test
    void executeAgent_withValidAgent_shouldExecuteAndReturnResult() {
        BaseAgent mockAgent = mock(BaseAgent.class);
        when(mockAgent.getName()).thenReturn("TestAgent");
        when(mockAgent.getDescription()).thenReturn("Test");
        
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("success", true);
        expectedResult.put("data", "result");
        when(mockAgent.safeExecute(any())).thenReturn(expectedResult);

        service.registerAgent(mockAgent);

        Map<String, Object> input = new HashMap<>();
        input.put("query", "test");
        
        Map<String, Object> result = service.executeAgent("TestAgent", input);

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("data", "result");
        verify(mockAgent).safeExecute(input);
    }

    @Test
    void executeAgent_withNonExistingAgent_shouldReturnError() {
        Map<String, Object> input = new HashMap<>();
        
        Map<String, Object> result = service.executeAgent("NonExistent", input);

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("Agent not found");
    }

    @Test
    void getNewsAnalyzer_shouldReturnNewsAnalyzerAgent() {
        doNothing().when(telegramService).setAgentService(any());
        doNothing().when(telegramService).setBotModeService(any());
        when(agentRepository.existsByName(anyString())).thenReturn(false);

        service.initialize();

        NewsAnalyzerAgent newsAnalyzer = service.getNewsAnalyzer();

        assertThat(newsAnalyzer).isNotNull();
        assertThat(newsAnalyzer.getName()).isEqualTo("NewsAnalyzer");
    }

    @Test
    void getTutorBot_shouldReturnTutorBotAgent() {
        doNothing().when(telegramService).setAgentService(any());
        doNothing().when(telegramService).setBotModeService(any());
        when(agentRepository.existsByName(anyString())).thenReturn(false);

        service.initialize();

        TutorBotAgent tutorBot = service.getTutorBot();

        assertThat(tutorBot).isNotNull();
        assertThat(tutorBot.getName()).isEqualTo("TutorBot");
    }

    @Test
    void getSignalGenerator_shouldReturnSignalGeneratorAgent() {
        doNothing().when(telegramService).setAgentService(any());
        doNothing().when(telegramService).setBotModeService(any());
        when(agentRepository.existsByName(anyString())).thenReturn(false);

        service.initialize();

        SignalGeneratorAgent signalGenerator = service.getSignalGenerator();

        assertThat(signalGenerator).isNotNull();
        assertThat(signalGenerator.getName()).isEqualTo("SignalGenerator");
    }

    @Test
    void getRiskManager_shouldReturnRiskManagerAgent() {
        doNothing().when(telegramService).setAgentService(any());
        doNothing().when(telegramService).setBotModeService(any());
        when(agentRepository.existsByName(anyString())).thenReturn(false);

        service.initialize();

        RiskManagerAgent riskManager = service.getRiskManager();

        assertThat(riskManager).isNotNull();
        assertThat(riskManager.getName()).isEqualTo("RiskManager");
    }

    @Test
    void initialize_withExistingAgentsInDatabase_shouldNotSaveAgain() {
        doNothing().when(telegramService).setAgentService(any());
        doNothing().when(telegramService).setBotModeService(any());
        when(agentRepository.existsByName(anyString())).thenReturn(true);

        service.initialize();

        verify(agentRepository, never()).save(any());
    }
}
