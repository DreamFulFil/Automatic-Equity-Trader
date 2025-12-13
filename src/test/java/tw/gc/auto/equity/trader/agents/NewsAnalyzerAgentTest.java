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

        when(restTemplate.postForObject(anyString(), any(), Mockito.eq(String.class)))
                .thenReturn("{\"veto\":true,\"score\":0.82,\"reason\":\"Negative headlines\"}");

        VetoResponse response = agent.scrapeAndAnalyze(List.of("url1"));

        assertTrue(response.isVeto());
        assertEquals(0.82, response.getScore(), 0.0001);
        assertEquals("Negative headlines", response.getReason());
        assertEquals(2, response.getHeadlines().size());
    }
}
