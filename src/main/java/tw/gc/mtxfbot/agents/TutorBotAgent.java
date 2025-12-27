package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.entities.AgentInteraction;
import tw.gc.mtxfbot.entities.AgentInteraction.InteractionType;
import tw.gc.mtxfbot.repositories.AgentInteractionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String ollamaUrl;
    private final String ollamaModel;
    private final AgentInteractionRepository interactionRepo;
    
    private static final int MAX_QUESTIONS_PER_DAY = 10;
    private static final int MAX_INSIGHTS_PER_DAY = 3;
    
    public TutorBotAgent(RestTemplate restTemplate, ObjectMapper objectMapper, 
                         String ollamaUrl, String ollamaModel,
                         AgentInteractionRepository interactionRepo) {
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
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String question = (String) input.getOrDefault("question", "");
        String userId = (String) input.getOrDefault("userId", "default");
        InteractionType type = (InteractionType) input.getOrDefault("type", InteractionType.QUESTION);
        
        Map<String, Object> result = new HashMap<>();
        
        // Check rate limits
        LocalDateTime startOfDay = LocalDate.now().atTime(LocalTime.MIN);
        int limit = type == InteractionType.QUESTION ? MAX_QUESTIONS_PER_DAY : MAX_INSIGHTS_PER_DAY;
        
        if (interactionRepo != null) {
            long usedToday = interactionRepo.countInteractions(name, type, userId, startOfDay);
            if (usedToday >= limit) {
                result.put("success", false);
                result.put("error", String.format("Daily limit reached (%d/%d %s)", 
                        usedToday, limit, type.name().toLowerCase()));
                result.put("remaining", 0);
                return result;
            }
            result.put("remaining", limit - usedToday - 1);
        }
        
        try {
            long startTime = System.currentTimeMillis();
            String response = callOllama(question, type);
            long responseTime = System.currentTimeMillis() - startTime;
            
            result.put("success", true);
            result.put("response", response);
            result.put("agent", name);
            result.put("responseTimeMs", responseTime);
            
            // Log interaction
            if (interactionRepo != null) {
                AgentInteraction interaction = AgentInteraction.builder()
                        .agentName(name)
                        .timestamp(LocalDateTime.now())
                        .type(type)
                        .input(question)
                        .output(response.length() > 1000 ? response.substring(0, 1000) : response)
                        .userId(userId)
                        .responseTimeMs(responseTime)
                        .build();
                interactionRepo.save(interaction);
            }
            
            log.info("📚 TutorBot responded in {}ms", responseTime);
            
        } catch (Exception e) {
            log.error("❌ TutorBot failed: {}", e.getMessage());
            throw new RuntimeException("Tutor response failed", e);
        }
        
        return result;
    }
    
    private String callOllama(String question, InteractionType type) {
        String systemPrompt;
        if (type == InteractionType.INSIGHT) {
            systemPrompt = """
                You are a Taiwan stock market trading tutor. Generate a concise trading insight or lesson.
                Focus on: momentum trading, risk management, or market psychology.
                Keep response under 200 words. Use practical examples when possible.
                """;
        } else {
            systemPrompt = """
                You are a Taiwan stock market trading tutor helping a student learn day trading.
                Answer concisely and practically. Focus on:
                - MTXF futures and MediaTek (2454.TW)
                - Momentum trading strategies
                - Risk management
                - Market psychology
                Keep answers under 150 words unless more detail is requested.
                """;
        }
        
        String prompt = String.format("%s\n\nUser: %s", systemPrompt, question);
        
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
        Map<String, Object> input = new HashMap<>();
        input.put("question", "Generate today's trading insight for Taiwan markets");
        input.put("userId", userId);
        input.put("type", InteractionType.INSIGHT);
        return safeExecute(input);
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
