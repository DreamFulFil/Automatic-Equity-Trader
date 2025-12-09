package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.entities.AgentInteraction;
import tw.gc.mtxfbot.entities.AgentInteraction.InteractionType;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.entities.Trade.TradeStatus;
import tw.gc.mtxfbot.entities.Trade.TradingMode;
import tw.gc.mtxfbot.repositories.AgentInteractionRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

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
}
