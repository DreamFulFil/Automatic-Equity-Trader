package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.OllamaProperties;
import tw.gc.auto.equity.trader.entities.LlmInsight;
import tw.gc.auto.equity.trader.repositories.LlmInsightRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LlmInsightRepository llmInsightRepository;

    @Mock
    private OllamaProperties ollamaProperties;

    private ObjectMapper objectMapper;
    private LlmService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(ollamaProperties.getUrl()).thenReturn("http://localhost:11434");
        when(ollamaProperties.getModel()).thenReturn("llama3.1:8b-instruct");
        service = new LlmService(restTemplate, objectMapper, ollamaProperties, llmInsightRepository);
    }

    @Test
    void executeStructuredPrompt_withValidResponse_shouldReturnParsedJson() throws Exception {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("confidence", Double.class);
        schema.put("explanation", String.class);

        String llmResponseJson = "{\"confidence\": 0.85, \"explanation\": \"Market shows strong momentum\"}";
        String escapedJson = "{\\\"confidence\\\": 0.85, \\\"explanation\\\": \\\"Market shows strong momentum\\\"}";
        String apiResponse = "{\"response\": \"" + escapedJson + "\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(apiResponse);

        Map<String, Object> result = service.executeStructuredPrompt(
                "Test prompt",
                LlmInsight.InsightType.MARKET_ANALYSIS,
                "TestSource",
                "2330",
                schema
        );

        assertThat(result).containsEntry("confidence", 0.85);
        assertThat(result).containsEntry("explanation", "Market shows strong momentum");
        verify(llmInsightRepository).save(any(LlmInsight.class));
    }

    @Test
    void executeStructuredPrompt_withMarkdownCodeBlocks_shouldExtractJson() throws Exception {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("value", String.class);

        String llmResponse = "```json\\n{\\\"value\\\": \\\"test\\\"}\\n```";
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"" + llmResponse + "\"}");

        Map<String, Object> result = service.executeStructuredPrompt(
                "Test",
                LlmInsight.InsightType.SIGNAL_GENERATION,
                "Test",
                null,
                schema
        );

        assertThat(result).containsEntry("value", "test");
    }

    @Test
    void executeStructuredPrompt_withMissingSchemaField_shouldThrowException() {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("required_field", String.class);

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"{\\\"other_field\\\": \\\"value\\\"}\"}");

        assertThatThrownBy(() -> service.executeStructuredPrompt(
                "Test",
                LlmInsight.InsightType.PATTERN_INTERPRETATION,
                "Test",
                null,
                schema
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("LLM execution failed");

        verify(llmInsightRepository).save(argThat(insight -> !insight.isSuccess()));
    }

    @Test
    void executeStructuredPrompt_withNullResponse_shouldThrowException() {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("field", String.class);

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.executeStructuredPrompt(
                "Test",
                LlmInsight.InsightType.NEWS_IMPACT_SCORING,
                "Test",
                null,
                schema
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("LLM returned null response");
    }

    @Test
    void scoreNewsImpact_shouldCallWithCorrectSchema() throws Exception {
        String mockResponse = "{\"response\": \"{\\\"sentiment_score\\\": 0.5, \\\"impact_score\\\": 0.7, " +
                "\\\"affected_sectors\\\": [], \\\"affected_symbols\\\": [], \\\"confidence\\\": 0.8, \\\"explanation\\\": \\\"test\\\"}\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.scoreNewsImpact("Test headline", "Test content");

        assertThat(result).containsKeys("sentiment_score", "impact_score", "confidence", "explanation");
        verify(llmInsightRepository).save(any(LlmInsight.class));
    }

    @Test
    void generateVetoRationale_shouldIncludeContextInPrompt() throws Exception {
        Map<String, Object> context = new HashMap<>();
        context.put("volatility", "high");
        context.put("pnl", -5000.0);

        String mockResponse = "{\"response\": \"{\\\"rationale\\\": \\\"High risk\\\", \\\"severity\\\": \\\"HIGH\\\", " +
                "\\\"recommended_action\\\": \\\"Wait\\\", \\\"duration_minutes\\\": 30, \\\"confidence\\\": 0.9, " +
                "\\\"supporting_evidence\\\": []}\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.generateVetoRationale("VOLATILITY", "Too volatile", context);

        assertThat(result).containsEntry("severity", "HIGH");
        verify(llmInsightRepository).save(any(LlmInsight.class));
    }

    @Test
    void interpretPattern_shouldReturnPatternAnalysis() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("mean", 100.0);
        stats.put("stddev", 5.0);

        String mockResponse = "{\"response\": \"{\\\"interpretation\\\": \\\"Strong trend\\\", " +
                "\\\"signal_strength\\\": 0.8, \\\"recommended_direction\\\": \\\"LONG\\\", \\\"confidence\\\": 0.75, " +
                "\\\"key_observations\\\": [], \\\"risk_factors\\\": []}\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.interpretPattern("TREND", stats);

        assertThat(result).containsEntry("recommended_direction", "LONG");
    }

    @Test
    void generateSignalAssistance_shouldIncludeMarketData() throws Exception {
        Map<String, Object> marketData = new HashMap<>();
        marketData.put("price", 500.0);
        marketData.put("volume", 10000);

        Map<String, Object> strategyContext = new HashMap<>();
        strategyContext.put("strategy", "MomentumTrading");

        String mockResponse = "{\"response\": \"{\\\"signal_recommendation\\\": \\\"BUY\\\", \\\"confidence\\\": 0.7, " +
                "\\\"reasoning\\\": \\\"Good entry\\\", \\\"key_factors\\\": [], \\\"risk_level\\\": \\\"MEDIUM\\\", " +
                "\\\"suggested_position_size\\\": 0.5, \\\"time_horizon\\\": \\\"SHORT\\\"}\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.generateSignalAssistance("2330", marketData, strategyContext);

        assertThat(result).containsEntry("signal_recommendation", "BUY");
        assertThat(result).containsEntry("risk_level", "MEDIUM");
    }

    @Test
    void analyzeMarketAtStartup_shouldReturnComprehensiveAnalysis() throws Exception {
        Map<String, Object> recentData = new HashMap<>();
        recentData.put("index", "TAIEX");
        recentData.put("change", 50.0);

        String mockResponse = "{\"response\": \"{\\\"market_sentiment\\\": \\\"BULLISH\\\", \\\"confidence\\\": 0.8, " +
                "\\\"key_trends\\\": [], \\\"risk_factors\\\": [], \\\"opportunities\\\": [], " +
                "\\\"recommended_strategy\\\": \\\"MODERATE\\\", \\\"explanation\\\": \\\"Market stable\\\"}\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.analyzeMarketAtStartup("TWSE", recentData);

        assertThat(result).containsEntry("market_sentiment", "BULLISH");
        assertThat(result).containsEntry("recommended_strategy", "MODERATE");
    }

    @Test
    void generateInsight_shouldReturnInsightString() throws Exception {
        String mockResponse = "{\"response\": \"{\\\"insight\\\": \\\"The market looks favorable\\\"}\"}";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(mockResponse);

        String result = service.generateInsight("What is the market outlook?");

        assertThat(result).isEqualTo("The market looks favorable");
    }

    @Test
    void executeTradeVeto_withApprove_shouldReturnNoVeto() throws Exception {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2330");
        proposal.put("direction", "LONG");
        proposal.put("shares", 100);

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"APPROVE\"}");

        Map<String, Object> result = service.executeTradeVeto(proposal);

        assertThat(result).containsEntry("veto", false);
        assertThat(result).containsEntry("reason", "APPROVED");
    }

    @Test
    void executeTradeVeto_withVeto_shouldReturnVetoWithReason() throws Exception {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2454");
        proposal.put("direction", "SHORT");

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"VETO: High volatility detected\"}");

        Map<String, Object> result = service.executeTradeVeto(proposal);

        assertThat(result).containsEntry("veto", true);
        assertThat(result).containsEntry("reason", "High volatility detected");
    }

    @Test
    void executeTradeVeto_withNullResponse_shouldDefaultToVeto() {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2330");

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(null);

        Map<String, Object> result = service.executeTradeVeto(proposal);

        assertThat(result).containsEntry("veto", true);
        assertThat(result.get("reason")).asString().contains("null response");
    }

    @Test
    void executeTradeVeto_withException_shouldDefaultToVeto() {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2330");

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, Object> result = service.executeTradeVeto(proposal);

        assertThat(result).containsEntry("veto", true);
        assertThat(result.get("reason")).asString().contains("failed");
    }

    @Test
    void executeTradeVeto_withUnexpectedFormat_shouldDefaultToVeto() throws Exception {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2330");

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"Some unexpected response\"}");

        Map<String, Object> result = service.executeTradeVeto(proposal);

        assertThat(result).containsEntry("veto", true);
        assertThat(result.get("reason")).asString().contains("unexpected");
    }

    @Test
    void executeTradeVeto_withNewsHeadlinesList_shouldFormatCorrectly() throws Exception {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2330");
        List<String> headlines = new ArrayList<>();
        headlines.add("TSMC reports Q4 earnings");
        headlines.add("Chip demand rising");
        proposal.put("news_headlines", headlines);

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"APPROVE\"}");

        service.executeTradeVeto(proposal);

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        
        Map<String, Object> capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.get("prompt")).asString().contains("TSMC reports Q4 earnings");
    }

    @Test
    void executeTradeVeto_withNoNewsHeadlines_shouldHandleGracefully() throws Exception {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("symbol", "2330");
        proposal.put("news_headlines", null);

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\": \"APPROVE\"}");

        service.executeTradeVeto(proposal);

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        
        Map<String, Object> capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.get("prompt")).asString().contains("No news available");
    }
}
