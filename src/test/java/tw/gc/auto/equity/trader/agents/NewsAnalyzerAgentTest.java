package tw.gc.auto.equity.trader.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.agents.NewsAnalyzerAgent.VetoResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class NewsAnalyzerAgentTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private NewsAnalyzerAgent agent;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        agent = spy(new NewsAnalyzerAgent(restTemplate, objectMapper, "http://localhost:8888"));
    }

    @Test
    void extractHeadlines_ParsesRssTitles() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>Headline A</title></item>
                <item><title>Headline B</title></item>
                </channel></rss>
                """;
        var headlines = agent.extractHeadlines(rss);

        assertEquals(2, headlines.size());
        assertTrue(headlines.contains("Headline A"));
        assertTrue(headlines.contains("Headline B"));
    }

    @Test
    void scrapeAndAnalyze_ParsesVetoResponse() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>Bad news</title></item>
                <item><title>More bad news</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());

        Mockito.doReturn("{\"veto\":true,\"score\":0.82,\"reason\":\"Negative headlines\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        VetoResponse response = agent.scrapeAndAnalyze(List.of("url1"));

        assertTrue(response.isVeto());
        assertEquals(0.82, response.getScore(), 0.0001);
        assertEquals("Negative headlines", response.getReason());
        assertEquals(2, response.getHeadlines().size());
    }

    @Test
    void scrapeAndAnalyze_NoVeto() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>Neutral news</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());

        Mockito.doReturn("{\"veto\":false,\"score\":0.45,\"reason\":\"Clear\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        VetoResponse response = agent.scrapeAndAnalyze(List.of("url1"));

        assertFalse(response.isVeto());
        assertEquals(0.45, response.getScore(), 0.0001);
        assertEquals("Clear", response.getReason());
    }

    @Test
    void scrapeAndAnalyze_NullResponseFromBridge() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>Test</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());
        Mockito.doReturn(null).when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        VetoResponse response = agent.scrapeAndAnalyze(List.of("url1"));

        assertFalse(response.isVeto());
        assertEquals(0.5, response.getScore(), 0.0001);
        assertEquals("Bridge returned empty response", response.getReason());
        assertEquals(1, response.getHeadlines().size());
    }

    @Test
    void scrapeAndAnalyze_RetriesOnFailure() throws Exception {
        Mockito.doThrow(new RuntimeException("Network error")).when(agent).fetchRss(anyString());

        assertThrows(RuntimeException.class, () -> agent.scrapeAndAnalyze(List.of("url1")));
    }

    @Test
    void scrapeAndAnalyze_SucceedsAfterRetry() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>News</title></item>
                </channel></rss>
                """;
        Mockito.doThrow(new RuntimeException("Temporary failure"))
                .doReturn(rss)
                .when(agent).fetchRss(anyString());

        Mockito.doReturn("{\"veto\":false,\"score\":0.5,\"reason\":\"OK\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        VetoResponse response = agent.scrapeAndAnalyze(List.of("url1"));

        assertFalse(response.isVeto());
        assertEquals(1, response.getHeadlines().size());
    }

    @Test
    void scrapeAndAnalyze_InterruptedDuringRetry() throws Exception {
        Mockito.doThrow(new RuntimeException("Network error")).when(agent).fetchRss(anyString());

        Thread.currentThread().interrupt();
        assertThrows(RuntimeException.class, () -> agent.scrapeAndAnalyze(List.of("url1")));
        assertTrue(Thread.interrupted()); // Clear interrupt flag
    }

    @Test
    void scrapeAndAnalyze_MultipleFeeds() throws Exception {
        String rss1 = """
                <rss><channel><title>Feed1</title>
                <item><title>News A</title></item>
                </channel></rss>
                """;
        String rss2 = """
                <rss><channel><title>Feed2</title>
                <item><title>News B</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss1, rss2).when(agent).fetchRss(anyString());

        Mockito.doReturn("{\"veto\":false,\"score\":0.5,\"reason\":\"OK\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        VetoResponse response = agent.scrapeAndAnalyze(List.of("url1", "url2"));

        assertEquals(2, response.getHeadlines().size());
    }

    @Test
    void extractHeadlines_HandlesBlankTitles() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>   </title></item>
                <item><title>Real News</title></item>
                </channel></rss>
                """;
        var headlines = agent.extractHeadlines(rss);

        assertEquals(1, headlines.size());
        assertEquals("Real News", headlines.get(0));
    }

    @Test
    void extractHeadlines_HandlesNullTitles() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item></item>
                <item><title>Valid</title></item>
                </channel></rss>
                """;
        var headlines = agent.extractHeadlines(rss);

        assertEquals(1, headlines.size());
        assertEquals("Valid", headlines.get(0));
    }

    @Test
    void extractHeadlines_RemovesChannelTitle() throws Exception {
        String rss = """
                <rss><channel><title>Channel Name</title>
                <item><title>Article 1</title></item>
                </channel></rss>
                """;
        var headlines = agent.extractHeadlines(rss);

        assertEquals(1, headlines.size());
        assertFalse(headlines.contains("Channel Name"));
        assertTrue(headlines.contains("Article 1"));
    }

    @Test
    void extractHeadlines_EmptyRss() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                </channel></rss>
                """;
        var headlines = agent.extractHeadlines(rss);

        assertEquals(0, headlines.size());
    }

    @Test
    void execute_WithCustomFeeds() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>Test</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());
        Mockito.doReturn("{\"veto\":false,\"score\":0.5,\"reason\":\"OK\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        var input = new java.util.HashMap<String, Object>();
        input.put("feeds", List.of("custom-url"));
        var result = agent.execute(input);

        assertTrue((boolean) result.get("success"));
        assertFalse((boolean) result.get("veto"));
        assertEquals("NewsAnalyzer", result.get("agent"));
    }

    @Test
    void execute_WithDefaultFeeds() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>Default feed news</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());
        Mockito.doReturn("{\"veto\":true,\"score\":0.7,\"reason\":\"Negative\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        var input = new java.util.HashMap<String, Object>();
        var result = agent.execute(input);

        assertTrue((boolean) result.get("success"));
        assertTrue((boolean) result.get("veto"));
        assertEquals(0.7, (double) result.get("score"), 0.001);
    }

    @Test
    void execute_WithNonListFeeds() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>News</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());
        Mockito.doReturn("{\"veto\":false,\"score\":0.5,\"reason\":\"OK\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        var input = new java.util.HashMap<String, Object>();
        input.put("feeds", "not-a-list");
        var result = agent.execute(input);

        assertTrue((boolean) result.get("success"));
        // Should use default feeds
    }

    @Test
    void execute_WithMixedTypeList() throws Exception {
        String rss = """
                <rss><channel><title>Channel</title>
                <item><title>News</title></item>
                </channel></rss>
                """;
        Mockito.doReturn(rss).when(agent).fetchRss(anyString());
        Mockito.doReturn("{\"veto\":false,\"score\":0.5,\"reason\":\"OK\"}")
                .when(restTemplate).postForObject(anyString(), any(), Mockito.eq(String.class));

        var input = new java.util.HashMap<String, Object>();
        input.put("feeds", List.of("url1", 123, "url2"));
        var result = agent.execute(input);

        assertTrue((boolean) result.get("success"));
        // Should filter non-String items
    }

    @Test
    void getFallbackResponse() {
        var fallback = agent.getFallbackResponse();

        assertFalse((boolean) fallback.get("success"));
        assertFalse((boolean) fallback.get("veto")); // Fail-safe: don't block trading
        assertEquals(0.5, fallback.get("score"));
        assertEquals("NewsAnalyzer", fallback.get("agent"));
    }

    @Test
    void vetoResponse_DefaultConstructor() {
        VetoResponse response = new VetoResponse();
        response.setVeto(true);
        response.setScore(0.8);
        response.setReason("Test");
        response.setHeadlines(List.of("H1", "H2"));
        response.setLatencyMs(100L);

        assertTrue(response.isVeto());
        assertEquals(0.8, response.getScore(), 0.0001);
        assertEquals("Test", response.getReason());
        assertEquals(2, response.getHeadlines().size());
        assertEquals(100L, response.getLatencyMs());
    }
}
