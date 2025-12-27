package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SignalGenerator Agent
 * 
 * Responsibilities:
 * - Generates momentum signals via Python bridge
 * - Anti-whipsaw logic with consecutive signal confirmation
 * - Confidence scoring (0-1) for entry decisions
 * - Logs signals to DB for backtesting
 * 
 * Best Practices:
 * - Uses deque-based price/volume history in Python
 * - Requires 3 consecutive aligned signals before entry
 * - Includes RSI-like overbought/oversold filter
 */
@Slf4j
public class SignalGeneratorAgent extends BaseAgent {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String bridgeUrl;
    
    public SignalGeneratorAgent(RestTemplate restTemplate, ObjectMapper objectMapper, String bridgeUrl) {
        super(
            "SignalGenerator",
            "Generates momentum trading signals with anti-whipsaw protection",
            List.of("momentum_signal", "confidence_score", "exit_signal")
        );
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.bridgeUrl = bridgeUrl;
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String signalJson = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
            JsonNode signal = objectMapper.readTree(signalJson);
            
            double currentPrice = signal.path("current_price").asDouble(0.0);
            String direction = signal.path("direction").asText("NEUTRAL");
            double confidence = signal.path("confidence").asDouble(0.0);
            boolean exitSignal = signal.path("exit_signal").asBoolean(false);
            
            // Additional metrics
            double momentum3min = signal.path("momentum_3min").asDouble(0.0);
            double momentum5min = signal.path("momentum_5min").asDouble(0.0);
            double volumeRatio = signal.path("volume_ratio").asDouble(1.0);
            double rsi = signal.path("rsi").asDouble(50.0);
            int consecutiveSignals = signal.path("consecutive_signals").asInt(0);
            boolean inCooldown = signal.path("in_cooldown").asBoolean(false);
            
            result.put("success", true);
            result.put("current_price", currentPrice);
            result.put("direction", direction);
            result.put("confidence", confidence);
            result.put("exit_signal", exitSignal);
            result.put("momentum_3min", momentum3min);
            result.put("momentum_5min", momentum5min);
            result.put("volume_ratio", volumeRatio);
            result.put("rsi", rsi);
            result.put("consecutive_signals", consecutiveSignals);
            result.put("in_cooldown", inCooldown);
            result.put("agent", name);
            
            if (!"NEUTRAL".equals(direction) && confidence >= 0.65) {
                log.info("📊 SignalGenerator: {} @ {} (conf: {}, consec: {})", 
                        direction, currentPrice, confidence, consecutiveSignals);
            }
            
        } catch (Exception e) {
            log.error("❌ SignalGenerator failed: {}", e.getMessage());
            throw new RuntimeException("Signal generation failed", e);
        }
        
        return result;
    }
    
    @Override
    protected Map<String, Object> getFallbackResponse() {
        return Map.of(
            "success", false,
            "direction", "NEUTRAL",
            "confidence", 0.0,
            "exit_signal", false,
            "reason", "Signal service unavailable",
            "agent", name
        );
    }
}
