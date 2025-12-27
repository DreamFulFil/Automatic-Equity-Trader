package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

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
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Call Python bridge for news analysis
            String newsJson = restTemplate.getForObject(bridgeUrl + "/signal/news", String.class);
            JsonNode newsData = objectMapper.readTree(newsJson);
            
            boolean veto = newsData.path("news_veto").asBoolean(false);
            double score = newsData.path("news_score").asDouble(0.5);
            String reason = newsData.path("news_reason").asText("");
            int headlinesCount = newsData.path("headlines_count").asInt(0);
            
            result.put("success", true);
            result.put("veto", veto);
            result.put("score", score);
            result.put("reason", reason);
            result.put("headlines_count", headlinesCount);
            result.put("agent", name);
            
            if (veto) {
                log.warn("🚨 NewsAnalyzer VETO: {} (score: {})", reason, score);
            } else {
                log.info("✅ NewsAnalyzer: Clear (score: {})", score);
            }
            
        } catch (Exception e) {
            log.error("❌ NewsAnalyzer failed: {}", e.getMessage());
            throw new RuntimeException("News analysis failed", e);
        }
        
        return result;
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
}
