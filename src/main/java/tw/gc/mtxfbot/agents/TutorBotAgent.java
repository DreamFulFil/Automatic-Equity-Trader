package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.entities.AgentInteraction;
import tw.gc.mtxfbot.entities.AgentInteraction.InteractionType;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.entities.Trade.TradeStatus;
import tw.gc.mtxfbot.entities.Trade.TradingMode;
import tw.gc.mtxfbot.repositories.AgentInteractionRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TutorBot Agent
 * 
 * Responsibilities:
 * - Handles free talk mode with trading Q&A
 * - Rate limited: 10 questions/day, 3 insights/tutorials per day
 * - Context-aware: tracks user progress in DB
 * - Provides trading lessons and insights
 * 
 * Best Practices:
 * - Rate limiting with DB tracking
 * - Context awareness via interaction history
 * - Fallback to safe responses on API failure
 */
@Slf4j
public class TutorBotAgent extends BaseAgent {
    
    @NonNull
    private final RestTemplate restTemplate;
    @NonNull
    private final ObjectMapper objectMapper;
    @NonNull
    private final String ollamaUrl;
    @NonNull
    private final String ollamaModel;
    @NonNull
    private final AgentInteractionRepository interactionRepo;
    @NonNull
    private final TradeRepository tradeRepository;
    @NonNull
    private final PromptFactory promptFactory;
    
    private static final int MAX_QUESTIONS_PER_DAY = 10;
    private static final int MAX_INSIGHTS_PER_DAY = 3;
    
    public TutorBotAgent(@NonNull RestTemplate restTemplate, @NonNull ObjectMapper objectMapper, 
                         @NonNull String ollamaUrl, @NonNull String ollamaModel,
                         @NonNull AgentInteractionRepository interactionRepo,
                         @NonNull TradeRepository tradeRepository,
                         @NonNull PromptFactory promptFactory) {
        super(
            "TutorBot",
            "Provides trading lessons, Q&A, and insights (10 questions/day, 3 insights/day)",
            List.of("trading_qa", "lesson_generation", "insight_generation")
        );
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.interactionRepo = interactionRepo;
        this.tradeRepository = tradeRepository;
        this.promptFactory = promptFactory;
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String question = (String) input.getOrDefault("question", "");
        String userId = (String) input.getOrDefault("userId", "default");
        if (input.containsKey("whatif")) {
            return handleWhatIfCommand(Long.parseLong(userId), (String) input.get("whatif"));
        }
        return handleTalkCommand(Long.parseLong(userId), question);
    }
    
    private String callOllama(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", ollamaModel);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("options", Map.of("temperature", 0.7));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(ollamaUrl + "/api/generate", request, String.class);

            if (response != null) {
                var jsonNode = objectMapper.readTree(response);
                return jsonNode.path("response").asText("I couldn't generate a response.");
            }
        } catch (Exception e) {
            log.error("❌ Ollama call failed: {}", e.getMessage());
        }

        return "I'm having trouble connecting to the AI service. Please try again later.";
    }
    
    /**
     * Get daily insight without user question
     */
    public Map<String, Object> generateDailyInsight(String userId) {
        int remaining = checkAndDecrementRateLimit(Long.parseLong(userId), "insight");
        String prompt = promptFactory.buildInsightPrompt();
        String response = callOllama(prompt);
        logInteraction(userId, prompt, response, InteractionType.INSIGHT, 0L);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("response", response);
        result.put("remaining", remaining);
        result.put("agent", name);
        return result;
    }

    /** Main talk entry point for Telegram. */
    public Map<String, Object> handleTalkCommand(long chatId, String message) {
        int remaining = checkAndDecrementRateLimit(chatId, "talk");
        String prompt = promptFactory.buildQuestionPrompt(message);
        long start = System.currentTimeMillis();
        String response = callOllama(prompt);
        long elapsed = System.currentTimeMillis() - start;

        logInteraction(String.valueOf(chatId), message, response, InteractionType.QUESTION, elapsed);

        return Map.of(
                "success", true,
                "response", response,
                "remaining", remaining,
                "agent", name,
                "responseTimeMs", elapsed
        );
    }

    /** Rate limit helper: throws if exceeded and returns remaining count. */
    public int checkAndDecrementRateLimit(long chatId, String commandType) {
        InteractionType type = "insight".equalsIgnoreCase(commandType) ? InteractionType.INSIGHT : InteractionType.QUESTION;
        LocalDateTime startOfDay = LocalDate.now().atTime(LocalTime.MIN);
        int limit = type == InteractionType.QUESTION ? MAX_QUESTIONS_PER_DAY : MAX_INSIGHTS_PER_DAY;
        long used = interactionRepo.countInteractions(name, type, String.valueOf(chatId), startOfDay);
        if (used >= limit) {
            throw new IllegalStateException(String.format("Daily limit reached (%d/%d %s)", used, limit, commandType));
        }
        return (int) (limit - used - 1);
    }

    /**
     * /whatif temporary simulation using recent closed trades in SIMULATION mode.
     */
    public Map<String, Object> handleWhatIfCommand(long chatId, String hypothesis) {
        int remaining = checkAndDecrementRateLimit(chatId, "insight");
        var trades = tradeRepository.findByModeAndStatusOrderByTimestampDesc(TradingMode.SIMULATION, TradeStatus.CLOSED);
        int limit = Math.min(20, trades.size());
        double baseline = 0.0;
        double simulated = 0.0;
        for (int i = 0; i < limit; i++) {
            Trade trade = trades.get(i);
            double pnl = trade.getRealizedPnL() != null ? trade.getRealizedPnL() : 0.0;
            baseline += pnl;
            simulated += applyHypothesisAdjustment(pnl, hypothesis);
        }
        double delta = simulated - baseline;

        Map<String, Object> simResult = new HashMap<>();
        simResult.put("baselinePnl", baseline);
        simResult.put("simulatedPnl", simulated);
        simResult.put("delta", delta);
        simResult.put("tradesAnalyzed", limit);
        simResult.put("hypothesis", hypothesis);

        String prompt = promptFactory.buildWhatIfPrompt(hypothesis, simResult);
        String response = callOllama(prompt);
        logInteraction(String.valueOf(chatId), hypothesis, response, InteractionType.INSIGHT, 0L);

        simResult.put("analysis", response);
        simResult.put("remaining", remaining);
        simResult.put("success", true);
        simResult.put("agent", name);
        return simResult;
    }

    private double applyHypothesisAdjustment(double pnl, String hypothesis) {
        String lower = Objects.toString(hypothesis, "").toLowerCase();
        if (lower.contains("stop")) {
            return pnl < 0 ? pnl * 0.85 : pnl; // tighter stop reduces losses
        }
        if (lower.contains("size") || lower.contains("position")) {
            return pnl * 1.1; // larger size effect
        }
        if (lower.contains("take profit") || lower.contains("tp")) {
            return pnl > 0 ? pnl * 0.95 : pnl; // earlier take-profit trims wins
        }
        return pnl * 1.05; // default modest improvement
    }

    private void logInteraction(String userId, String input, String output, InteractionType type, long responseTimeMs) {
        if (interactionRepo == null) return;
        AgentInteraction interaction = AgentInteraction.builder()
                .agentName(name)
                .timestamp(LocalDateTime.now())
                .type(type)
                .input(input)
                .output(output.length() > 1000 ? output.substring(0, 1000) : output)
                .userId(userId)
                .responseTimeMs(responseTimeMs)
                .build();
        interactionRepo.save(interaction);
    }
    
    @Override
    protected Map<String, Object> getFallbackResponse() {
        return Map.of(
            "success", false,
            "response", "I'm temporarily unavailable. Try again in a few minutes.",
            "agent", name
        );
    }
}
