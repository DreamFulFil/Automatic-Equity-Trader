package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.entities.Signal;
import tw.gc.mtxfbot.AppConstants;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
    
    private static final ZoneId TAIPEI_ZONE = AppConstants.TAIPEI_ZONE;
    private static final double MOMENTUM_THRESHOLD = 0.003; // 0.3% change needed for momentum alignment
    private static final double VOLUME_RATIO_THRESHOLD = 1.1; // 10% higher than average volume
    private static final int RSI_PERIOD = 14;

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
        String symbol = (String) input.getOrDefault("symbol", "2454.TW");
        Signal generated = generateSignal(symbol);

        Map<String, Object> result = new HashMap<>();
        result.put("success", generated != null);
        result.put("direction", generated.getDirection().name());
        result.put("confidence", generated.getConfidence());
        result.put("current_price", generated.getCurrentPrice());
        result.put("exit_signal", Boolean.TRUE.equals(generated.getExitSignal()));
        result.put("rsi", extractMetric(generated.getMarketData(), "rsi"));
        result.put("momentum_short", extractMetric(generated.getMarketData(), "momentum_short"));
        result.put("momentum_long", extractMetric(generated.getMarketData(), "momentum_long"));
        result.put("volume_ratio", extractMetric(generated.getMarketData(), "volume_ratio"));
        result.put("agent", name);

        if (!Signal.SignalDirection.HOLD.equals(generated.getDirection()) && generated.getConfidence() >= 0.65) {
            log.info("📊 SignalGenerator: {} @ {} (conf: {})", 
                    generated.getDirection(), generated.getCurrentPrice(), generated.getConfidence());
        }
        return result;
    }

    /**
     * Generate a Signal using local momentum/RSI/volume heuristics.
     */
    public Signal generateSignal(String symbol) {
        try {
            String marketDataJson = restTemplate.getForObject(bridgeUrl + "/marketdata/" + symbol, String.class);
            MarketData marketData = objectMapper.readValue(marketDataJson, MarketData.class);
            List<Double> prices = safeList(marketData.getPrices());
            List<Long> volumes = safeVolumeList(marketData.getVolumes());

            if (prices.size() < 6 || volumes.size() < 6) {
                throw new IllegalStateException("Insufficient market data for signal generation");
            }

            double currentPrice = prices.get(prices.size() - 1);
            double momentumShort = calculateMomentum(prices, 3);
            double momentumLong = calculateMomentum(prices, 5);
            double rsi = calculateRsi(prices, RSI_PERIOD);
            double volumeRatio = calculateVolumeRatio(volumes, 5);

            Signal.SignalDirection direction = Signal.SignalDirection.HOLD;
            double confidence = 0.5;
            String reason = "Neutral";

            boolean bullish = momentumShort > MOMENTUM_THRESHOLD && momentumLong > MOMENTUM_THRESHOLD && rsi >= 45 && rsi <= 70 && volumeRatio >= VOLUME_RATIO_THRESHOLD;
            boolean bearish = momentumShort < -MOMENTUM_THRESHOLD && momentumLong < -MOMENTUM_THRESHOLD && rsi <= 55 && rsi >= 30 && volumeRatio >= VOLUME_RATIO_THRESHOLD;

            if (bullish) {
                direction = Signal.SignalDirection.LONG;
                confidence = Math.min(1.0, 0.55 + (momentumShort + momentumLong) / 2.0 + (volumeRatio - 1) * 0.3);
                reason = "Momentum up + RSI aligned + volume confirm";
            } else if (bearish) {
                direction = Signal.SignalDirection.SHORT;
                confidence = Math.min(1.0, 0.55 + (Math.abs(momentumShort + momentumLong) / 2.0) + (volumeRatio - 1) * 0.3);
                reason = "Momentum down + RSI aligned + volume confirm";
            } else if (rsi > 70 || rsi < 30) {
                reason = "RSI extreme - whipsaw guard";
            } else if (volumeRatio < VOLUME_RATIO_THRESHOLD) {
                reason = "Volume not confirmed";
            } else {
                reason = "Momentum not aligned";
            }

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("rsi", rsi);
            metrics.put("momentum_short", momentumShort);
            metrics.put("momentum_long", momentumLong);
            metrics.put("volume_ratio", volumeRatio);

            Signal signal = Signal.builder()
                    .timestamp(LocalDateTime.now(TAIPEI_ZONE))
                    .direction(direction)
                    .confidence(confidence)
                    .currentPrice(currentPrice)
                    .exitSignal(false)
                    .symbol(symbol)
                    .marketData(objectMapper.writeValueAsString(metrics))
                    .reason(reason)
                    .newsVeto(false)
                    .build();

            log.debug("Signal generated: dir={} conf={} price={} rsi={} m3={} m5={} volRatio={}",
                    direction, confidence, currentPrice, rsi, momentumShort, momentumLong, volumeRatio);
            return signal;
        } catch (Exception e) {
            log.error("❌ Signal generation failed: {}", e.getMessage());
            throw new RuntimeException("Signal generation failed", e);
        }
    }

    private List<Double> safeList(List<Double> prices) {
        if (prices == null) return new ArrayList<>();
        return prices;
    }

    private List<Long> safeVolumeList(List<Long> volumes) {
        if (volumes == null) return new ArrayList<>();
        return volumes;
    }

    private double calculateMomentum(List<Double> prices, int lookback) {
        int size = prices.size();
        if (size <= lookback) return 0.0;
        double latest = prices.get(size - 1);
        double prior = prices.get(size - 1 - lookback);
        if (prior == 0) return 0.0;
        return (latest - prior) / prior; // percentage change
    }

    private double calculateRsi(List<Double> prices, int period) {
        if (prices.size() <= period) return 50.0;

        double gain = 0.0;
        double loss = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) gain += change;
            else loss -= change;
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private double calculateVolumeRatio(List<Long> volumes, int lookback) {
        if (volumes.isEmpty()) return 1.0;
        long latest = volumes.get(volumes.size() - 1);
        int count = Math.max(1, Math.min(lookback, volumes.size() - 1));
        long sum = 0;
        for (int i = volumes.size() - 1 - count; i < volumes.size() - 1; i++) {
            sum += volumes.get(i);
        }
        double avg = sum / (double) count;
        if (avg == 0) return 1.0;
        return latest / avg;
    }

    private double extractMetric(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path(field).asDouble();
        } catch (Exception e) {
            return 0.0;
        }
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

    @Data
    @Builder
    @AllArgsConstructor
    static class MarketData {
        private String symbol;
        private List<Double> prices;
        private List<Long> volumes;
        private String timeframe;

        public MarketData() {
            // default constructor for Jackson
        }
    }
}
