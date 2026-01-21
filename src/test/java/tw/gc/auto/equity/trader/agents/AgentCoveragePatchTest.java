package tw.gc.auto.equity.trader.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.AgentInteractionRepository;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentCoveragePatchTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AgentInteractionRepository interactionRepo;
    @Mock
    private TradeRepository tradeRepository;

    private TutorBotAgent tutorBot;
    private SignalGeneratorAgent signalGenerator;
    private NewsAnalyzerAgent newsAnalyzer;

    @BeforeEach
    void setUp() {
        tutorBot = new TutorBotAgent(restTemplate, new ObjectMapper(), "http://ollama", "llama", interactionRepo, tradeRepository, new PromptFactory());
        signalGenerator = new SignalGeneratorAgent(restTemplate, new ObjectMapper(), "http://localhost:8888");
        newsAnalyzer = spy(new NewsAnalyzerAgent(restTemplate, new ObjectMapper(), "http://localhost:8888"));
    }

    @Test
    void tutorBot_constructor_setsFields() {
        assertNotNull(tutorBot);
    }

    @Test
    void tutorBot_rateLimitDecrements() {
        when(interactionRepo.countInteractions(anyString(), any(), anyString(), any()))
            .thenReturn(0L);
        int remaining = tutorBot.checkAndDecrementRateLimit(123L, "talk");
        assertEquals(9, remaining);
    }

    @Test
    void signalGenerator_execute_populatesResult() throws Exception {
        String json = "{\"symbol\":\"2330.TW\",\"prices\":[100,101,102,103,104,105],\"volumes\":[100,110,120,130,140,200],\"timeframe\":\"1m\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        Map<String, Object> result = signalGenerator.execute(new HashMap<>());
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("direction"));
    }

    @Test
    void newsAnalyzer_execute_defaultFeeds_setsHeadlineCount() throws Exception {
        String rss = "<rss><channel><title>Channel</title><item><title>Headline</title></item></channel></rss>";
        doReturn(rss).when(newsAnalyzer).fetchRss(anyString());
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
            .thenReturn("{\"veto\":false,\"score\":0.5,\"reason\":\"ok\"}");

        Map<String, Object> result = newsAnalyzer.execute(new HashMap<>());
        assertEquals(true, result.get("success"));
        assertEquals(2, result.get("headlines_count"));
    }

    @Test
    void newsAnalyzer_fetchRss_hitsHttpClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rss", exchange -> {
            String body = "ok";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();
        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/rss";
            NewsAnalyzerAgent agent = new NewsAnalyzerAgent(restTemplate, new ObjectMapper(), "http://localhost:8888");
            String result = agent.fetchRss(url);
            assertEquals("ok", result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void newsAnalyzer_retriesAndThrowsAfterFailures() throws Exception {
        doThrow(new RuntimeException("fail")).when(newsAnalyzer).fetchRss(anyString());
        assertThrows(RuntimeException.class, () -> newsAnalyzer.scrapeAndAnalyze(List.of("url1")));
    }

    @Test
    void riskManager_tradeModeDefaultsWhenNull() {
        BotSettingsRepository botSettingsRepository = mock(BotSettingsRepository.class);
        RiskManagerAgent agent = new RiskManagerAgent(tradeRepository, botSettingsRepository);
        Trade trade = Trade.builder().mode(null).build();
        when(tradeRepository.sumPnLSince(any(), any())).thenReturn(0.0);
        Map<String, Object> result = agent.checkTradeRisk(trade);
        assertEquals(true, result.get("allowed"));
        assertEquals("RUNNING", result.get("state"));
    }
}
