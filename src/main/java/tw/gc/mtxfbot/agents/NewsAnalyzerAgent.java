package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NewsAnalyzer Agent
 * 
 * Responsibilities:
 * - Scrapes RSS feeds (MoneyDJ, UDN) via Python bridge
 * - Calls Llama 3.1 8B for sentiment analysis
 * - Returns veto decision with score and reason
 * 
 * Best Practices:
 * - Async-ready with retry logic
 * - JSON output schema for consistent parsing
 * - Caches results for 10 minutes (called via TradingEngine)
 */
@Slf4j
public class NewsAnalyzerAgent extends BaseAgent {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String bridgeUrl;
    
    public NewsAnalyzerAgent(RestTemplate restTemplate, ObjectMapper objectMapper, String bridgeUrl) {
        super(
            "NewsAnalyzer",
            "Analyzes news headlines for trading veto decisions using Llama 3.1 8B",
            List.of("news_scrape", "sentiment_analysis", "veto_decision")
        );
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.bridgeUrl = bridgeUrl;
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object feedsObj = input.get("feeds");
        List<String> feeds;
        if (feedsObj instanceof List<?>) {
            feeds = ((List<?>) feedsObj).stream().filter(String.class::isInstance).map(String.class::cast).toList();
        } else {
            feeds = List.of(
                "https://www.moneydj.com/KMDJ/Common/Markets/rss/StockList/2454.TW.xml",
                "https://udn.com/rssfeed/news/2/6638?ch=money"
            );
        }

        VetoResponse veto = scrapeAndAnalyze(feeds);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("veto", veto.isVeto());
        result.put("score", veto.getScore());
        result.put("reason", veto.getReason());
        result.put("headlines_count", veto.getHeadlines() != null ? veto.getHeadlines().size() : 0);
        result.put("agent", name);
        return result;
    }

    /**
     * Scrape RSS feeds, send to Python bridge, and parse veto response.
     */
    public VetoResponse scrapeAndAnalyze(List<String> feedUrls) {
        int attempts = 0;
        long backoffMs = 500;
        Exception last = null;

        while (attempts < 3) {
            attempts++;
            try {
                List<String> headlines = new ArrayList<>();
                for (String url : feedUrls) {
                    String xml = fetchRss(url);
                    headlines.addAll(extractHeadlines(xml));
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("headlines", headlines);

                long start = System.currentTimeMillis();
                String responseJson = restTemplate.postForObject(bridgeUrl + "/signal/news", payload, String.class);
                long elapsed = System.currentTimeMillis() - start;

                if (responseJson == null) {
                    return VetoResponse.builder()
                            .veto(false)
                            .score(0.5)
                            .reason("Bridge returned empty response")
                            .headlines(headlines)
                            .latencyMs(elapsed)
                            .build();
                }

                VetoResponse veto = objectMapper.readValue(responseJson, VetoResponse.class);
                veto.setHeadlines(headlines);
                veto.setLatencyMs(elapsed);

                if (veto.isVeto()) {
                    log.warn("🚨 NewsAnalyzer VETO: {} (score: {})", veto.getReason(), veto.getScore());
                } else {
                    log.info("✅ NewsAnalyzer clear (score: {})", veto.getScore());
                }
                return veto;
            } catch (Exception e) {
                last = e;
                log.warn("⚠️ NewsAnalyzer attempt {}/3 failed: {}", attempts, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs *= 2;
            }
        }
        log.error("❌ News analysis failed after retries: {}", last != null ? last.getMessage() : "unknown error");
        throw new RuntimeException("News analysis failed", last);
    }

    /** Fetch RSS XML content with a short timeout. */
    protected String fetchRss(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Parse RSS XML and extract headlines. */
    protected List<String> extractHeadlines(String xmlContent) throws Exception {
        List<String> headlines = new ArrayList<>();
        var dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setExpandEntityReferences(false);
        var dBuilder = dbFactory.newDocumentBuilder();
        var doc = dBuilder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes()));
        var nodes = doc.getElementsByTagName("title");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (node != null && node.getTextContent() != null && !node.getTextContent().isBlank()) {
                headlines.add(node.getTextContent().trim());
            }
        }
        // Remove the first title if it's the channel title
        if (!headlines.isEmpty()) {
            headlines.remove(0);
        }
        return headlines;
    }
    
    @Override
    protected Map<String, Object> getFallbackResponse() {
        // Safe fallback: no veto (don't block trading due to API issues)
        return Map.of(
            "success", false,
            "veto", false,
            "score", 0.5,
            "reason", "Analysis unavailable - defaulting to no veto",
            "agent", name
        );
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class VetoResponse {
        private boolean veto;
        private double score;
        private String reason;
        private List<String> headlines;
        private long latencyMs;

        public VetoResponse() {
            // default for Jackson
        }
    }
}
