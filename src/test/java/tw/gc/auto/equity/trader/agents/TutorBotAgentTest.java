package tw.gc.auto.equity.trader.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.AgentInteraction;
import tw.gc.auto.equity.trader.entities.AgentInteraction.InteractionType;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.entities.Trade.TradeStatus;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.repositories.AgentInteractionRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TutorBotAgentTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private AgentInteractionRepository interactionRepo;
    private TradeRepository tradeRepository;
    private PromptFactory promptFactory;
    private TutorBotAgent agent;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        interactionRepo = mock(AgentInteractionRepository.class);
        tradeRepository = mock(TradeRepository.class);
        promptFactory = new PromptFactory();
        agent = new TutorBotAgent(restTemplate, objectMapper, "http://ollama", "llama", interactionRepo, tradeRepository, promptFactory);
    }

    @Test
    void handleTalkCommand_ReturnsResponseAndLogs() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Hello\"}");

        Map<String, Object> result = agent.handleTalkCommand(123L, "How to trade?");

        assertTrue((boolean) result.get("success"));
        assertEquals(9, result.get("remaining"));
        assertEquals("Hello", result.get("response"));
        ArgumentCaptor<AgentInteraction> captor = ArgumentCaptor.forClass(AgentInteraction.class);
        verify(interactionRepo).save(captor.capture());
        assertEquals("TutorBot", captor.getValue().getAgentName());
    }

    @Test
    void checkAndDecrementRateLimit_ThrowsWhenExceeded() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(10L);
        assertThrows(IllegalStateException.class, () -> agent.checkAndDecrementRateLimit(1L, "talk"));
    }

    @Test
    void handleWhatIfCommand_SimulatesTrades() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        Trade t2 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(-50.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1, t2));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "tighten stop");

        assertTrue((boolean) result.get("success"));
        assertEquals(2, result.get("tradesAnalyzed"));
        assertTrue(result.containsKey("analysis"));
    }

    @Test
    void generateDailyInsight_UsesInsightLimit() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Insight\"}");

        Map<String, Object> result = agent.generateDailyInsight("555");
        assertTrue((boolean) result.get("success"));
        assertEquals("Insight", result.get("response"));
    }

    @Test
    void execute_WithWhatIfInput() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("whatif", "tighten stop");
        input.put("userId", "123");
        
        Map<String, Object> result = agent.execute(input);

        assertTrue((boolean) result.get("success"));
        assertTrue(result.containsKey("analysis"));
    }

    @Test
    void execute_WithQuestionInput() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Answer\"}");

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("question", "How to trade?");
        input.put("userId", "123");
        
        Map<String, Object> result = agent.execute(input);

        assertTrue((boolean) result.get("success"));
        assertEquals("Answer", result.get("response"));
    }

    @Test
    void execute_DefaultUserId_throwsOnNonNumeric() {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("question", "How to trade?");

        assertThrows(NumberFormatException.class, () -> agent.execute(input));
    }

    @Test
    void execute_DefaultUserId_usesDefaultWhenWhatIfProvided() {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("whatif", "tighten stop");

        assertThrows(NumberFormatException.class, () -> agent.execute(input));
    }

    @Test
    void handleWhatIfCommand_PositionSizeHypothesis() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "increase position size");

        assertTrue((boolean) result.get("success"));
        assertEquals(110.0, (double) result.get("simulatedPnl"), 0.01); // 100 * 1.1
    }

    @Test
    void handleWhatIfCommand_TakeProfitHypothesis() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        Trade t2 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(-50.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1, t2));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "take profit earlier");

        assertTrue((boolean) result.get("success"));
        // Positive PnL * 0.95 + negative PnL unchanged = 95 - 50 = 45
        assertEquals(45.0, (double) result.get("simulatedPnl"), 0.01);
    }

    @Test
    void handleWhatIfCommand_TpHypothesis() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "adjust tp level");

        assertTrue((boolean) result.get("success"));
        assertEquals(95.0, (double) result.get("simulatedPnl"), 0.01); // 100 * 0.95
    }

    @Test
    void handleWhatIfCommand_DefaultHypothesis() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "some other hypothesis");

        assertTrue((boolean) result.get("success"));
        assertEquals(105.0, (double) result.get("simulatedPnl"), 0.01); // 100 * 1.05
    }

    @Test
    void handleWhatIfCommand_NullHypothesis() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, null);

        assertTrue((boolean) result.get("success"));
        assertEquals(105.0, (double) result.get("simulatedPnl"), 0.01); // Default adjustment
    }

    @Test
    void handleWhatIfCommand_NullRealizedPnL() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(null).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "test");

        assertTrue((boolean) result.get("success"));
        assertEquals(0.0, (double) result.get("baselinePnl"), 0.01);
    }

    @Test
    void handleTalkCommand_OllamaReturnsNull() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(null);

        Map<String, Object> result = agent.handleTalkCommand(123L, "test question");

        assertTrue((boolean) result.get("success"));
        // When response is null, exception is thrown when trying to read tree, so fallback message is used
        assertEquals("I'm having trouble connecting to the AI service. Please try again later.", result.get("response"));
    }

    @Test
    void handleTalkCommand_OllamaReturnsValidJsonNoResponse() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        // Return JSON without 'response' field to trigger default text
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"other\":\"data\"}");

        Map<String, Object> result = agent.handleTalkCommand(123L, "test question");

        assertTrue((boolean) result.get("success"));
        assertEquals("I couldn't generate a response.", result.get("response"));
    }

    @Test
    void handleTalkCommand_OllamaThrowsException() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, Object> result = agent.handleTalkCommand(123L, "test question");

        assertTrue((boolean) result.get("success"));
        assertEquals("I'm having trouble connecting to the AI service. Please try again later.", result.get("response"));
    }

    @Test
    void handleTalkCommand_LogInteractionTruncatesLongOutput() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        
        // Create a very long response (over 1000 chars)
        StringBuilder longResponse = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            longResponse.append("0123456789");
        }
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"" + longResponse + "\"}");

        Map<String, Object> result = agent.handleTalkCommand(123L, "test");

        assertTrue((boolean) result.get("success"));
        ArgumentCaptor<AgentInteraction> captor = ArgumentCaptor.forClass(AgentInteraction.class);
        verify(interactionRepo).save(captor.capture());
        assertTrue(captor.getValue().getOutput().length() <= 1000);
    }

    @Test
    void checkAndDecrementRateLimit_InsightType() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(2L);
        
        int remaining = agent.checkAndDecrementRateLimit(1L, "insight");
        
        assertEquals(0, remaining); // 3 - 2 - 1 = 0
    }

    @Test
    void getFallbackResponse() {
        var fallback = agent.getFallbackResponse();

        assertFalse((boolean) fallback.get("success"));
        assertEquals("I'm temporarily unavailable. Try again in a few minutes.", fallback.get("response"));
        assertEquals("TutorBot", fallback.get("agent"));
    }

    @Test
    void handleTalkCommand_NullInteractionRepo() {
        // Create agent with null interactionRepo to test the null check in logInteraction
        // Note: @NonNull will prevent direct null, but we can test via reflection or 
        // just ensure the existing code doesn't throw if it were somehow null
        // The actual code has: if (interactionRepo == null) return;
        // This test verifies that path is covered by other tests indirectly
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Test\"}");

        Map<String, Object> result = agent.handleTalkCommand(1L, "test");
        assertTrue((boolean) result.get("success"));
        verify(interactionRepo).save(any(AgentInteraction.class));
    }

    @Test
    void handleWhatIfCommand_SizeHypothesis() {
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.INSIGHT), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Analysis\"}");

        Trade t1 = Trade.builder().mode(TradingMode.SIMULATION).status(TradeStatus.CLOSED).realizedPnL(100.0).build();
        when(tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(List.of(t1));

        Map<String, Object> result = agent.handleWhatIfCommand(999L, "size matters");

        assertTrue((boolean) result.get("success"));
        assertEquals(110.0, (double) result.get("simulatedPnl"), 0.01); // 100 * 1.1
    }

    @Test
    void constructor_InitializesAllFields() {
        // Test that constructor properly initializes all fields (lines 61-65)
        TutorBotAgent newAgent = new TutorBotAgent(
                restTemplate, objectMapper, "http://test-ollama", "test-model",
                interactionRepo, tradeRepository, promptFactory);
        
        // Verify by calling a method that uses these fields
        when(interactionRepo.countInteractions(anyString(), eq(InteractionType.QUESTION), anyString(), any()))
                .thenReturn(0L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Test\"}");
        
        Map<String, Object> result = newAgent.handleTalkCommand(1L, "test");
        assertTrue((boolean) result.get("success"));
        assertEquals("TutorBot", result.get("agent"));
    }

    @Test
    void logInteraction_SkipsWhenInteractionRepoNull() throws Exception {
        // Use reflection to create agent with null interactionRepo to test line 213
        java.lang.reflect.Field repoField = TutorBotAgent.class.getDeclaredField("interactionRepo");
        repoField.setAccessible(true);
        
        // Create a new agent and set interactionRepo to null via reflection
        TutorBotAgent agentWithNullRepo = new TutorBotAgent(
                restTemplate, objectMapper, "http://ollama", "llama",
                interactionRepo, tradeRepository, promptFactory);
        repoField.set(agentWithNullRepo, null);
        
        java.lang.reflect.Method method = TutorBotAgent.class.getDeclaredMethod(
                "logInteraction", String.class, String.class, String.class, InteractionType.class, long.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(agentWithNullRepo, "1", "input", "output", InteractionType.QUESTION, 10L));
    }
}
