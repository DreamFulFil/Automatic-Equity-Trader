package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.OllamaProperties;
import tw.gc.auto.equity.trader.entities.LlmInsight;
import tw.gc.auto.equity.trader.entities.LlmInsight.InsightType;
import tw.gc.auto.equity.trader.repositories.LlmInsightRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM Integration Service (Ollama Llama 3.1 8B-Instruct)
 * 
 * Enforces structured JSON output for all LLM interactions.
 * Treats LLM as an Analytical Feature Generator, not a general chatbot.
 * 
 * All LLM outputs are:
 * 1. Schema-validated against predefined JSON structure
 * 2. Directly deserializable into Java objects
 * 3. Persisted in LlmInsight entity for historical analysis
 * 
 * Never allows free-form prose outside schema fields.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmService {
    
    @NonNull
    private final RestTemplate restTemplate;
    @NonNull
    private final ObjectMapper objectMapper;
    @NonNull
    private final OllamaProperties ollamaProperties;
    @NonNull
    private final LlmInsightRepository llmInsightRepository;
    
    /**
     * Execute LLM prompt with enforced JSON schema response
     * 
     * @param prompt The prompt to send to LLM (must include JSON schema requirement)
     * @param insightType Type of insight being requested
     * @param source Source system making the request
     * @param symbol Optional symbol this relates to
     * @param expectedSchema Map describing expected JSON structure
     * @return Structured LLM response as Map
     */
    public Map<String, Object> executeStructuredPrompt(
            String prompt,
            InsightType insightType,
            String source,
            String symbol,
            Map<String, Class<?>> expectedSchema) {
        
        long startTime = System.currentTimeMillis();
        LlmInsight.LlmInsightBuilder insightBuilder = LlmInsight.builder()
            .timestamp(LocalDateTime.now())
            .insightType(insightType)
            .source(source)
            .symbol(symbol)
            .prompt(prompt)
            .modelName(ollamaProperties.getModel());
        
        try {
            // Call Ollama API
            Map<String, Object> request = new HashMap<>();
            request.put("model", ollamaProperties.getModel());
            request.put("prompt", prompt);
            request.put("stream", false);
            
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.3); // Low temperature for structured output
            request.put("options", options);
            
            String response = restTemplate.postForObject(
                ollamaProperties.getUrl() + "/api/generate",
                request,
                String.class
            );
            
            if (response == null) {
                throw new IllegalStateException("LLM returned null response");
            }
            
            // Parse response
            JsonNode responseNode = objectMapper.readTree(response);
            String llmOutput = responseNode.path("response").asText();
            
            // Extract JSON from response (handle markdown code blocks)
            String jsonStr = extractJson(llmOutput);
            
            // Validate schema
            Map<String, Object> parsedResponse = objectMapper.readValue(
                jsonStr, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            validateSchema(parsedResponse, expectedSchema);
            
            // Record successful insight
            long processingTime = System.currentTimeMillis() - startTime;
            LlmInsight insight = insightBuilder
                .responseJson(jsonStr)
                .confidenceScore(extractConfidence(parsedResponse))
                .recommendation(extractRecommendation(parsedResponse))
                .explanation(extractExplanation(parsedResponse))
                .processingTimeMs((int) processingTime)
                .success(true)
                .build();
            
            llmInsightRepository.save(insight);
            log.info("✅ LLM {} completed in {}ms", insightType, processingTime);
            
            return parsedResponse;
            
        } catch (Exception e) {
            // Record failed insight
            long processingTime = System.currentTimeMillis() - startTime;
            LlmInsight insight = insightBuilder
                .processingTimeMs((int) processingTime)
                .success(false)
                .errorMessage(truncate(e.getMessage(), 500))
                .build();
            
            llmInsightRepository.save(insight);
            log.error("❌ LLM {} failed: {}", insightType, e.getMessage());
            
            throw new RuntimeException("LLM execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * News Impact Scoring
     * Convert stored Economic_News entries into structured sentiment/impact scores
     */
    public Map<String, Object> scoreNewsImpact(String headline, String content) {
        String prompt = String.format(
            "Analyze this Taiwan stock market news and provide structured impact scoring.\n\n" +
            "Headline: %s\n" +
            "Content: %s\n\n" +
            "Respond ONLY with valid JSON matching this exact schema:\n" +
            "{\n" +
            "  \"sentiment_score\": <float -1.0 to 1.0, negative=bearish, positive=bullish>,\n" +
            "  \"impact_score\": <float 0.0 to 1.0, 0=no impact, 1=major impact>,\n" +
            "  \"affected_sectors\": [<list of affected sectors>],\n" +
            "  \"affected_symbols\": [<list of Taiwan stock symbols like \"2330\", \"2454\">],\n" +
            "  \"confidence\": <float 0.0 to 1.0>,\n" +
            "  \"explanation\": \"<brief explanation>\"\n" +
            "}",
            headline,
            truncate(content, 1000)
        );
        
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("sentiment_score", Double.class);
        schema.put("impact_score", Double.class);
        schema.put("affected_sectors", Object.class); // List
        schema.put("affected_symbols", Object.class); // List
        schema.put("confidence", Double.class);
        schema.put("explanation", String.class);
        
        return executeStructuredPrompt(
            prompt,
            InsightType.NEWS_IMPACT_SCORING,
            "NewsAnalyzerAgent",
            null,
            schema
        );
    }
    
    /**
     * Veto Rationale Augmentation
     * Generate rich, evidence-based explanations for veto decisions
     */
    public Map<String, Object> generateVetoRationale(
            String vetoType,
            String initialReason,
            Map<String, Object> context) {
        
        String prompt = String.format(
            "Generate detailed veto rationale for trading system.\n\n" +
            "Veto Type: %s\n" +
            "Initial Reason: %s\n" +
            "Context: %s\n\n" +
            "Respond ONLY with valid JSON matching this exact schema:\n" +
            "{\n" +
            "  \"rationale\": \"<detailed explanation>\",\n" +
            "  \"severity\": \"<LOW|MEDIUM|HIGH|CRITICAL>\",\n" +
            "  \"recommended_action\": \"<specific action recommendation>\",\n" +
            "  \"duration_minutes\": <estimated veto duration in minutes>,\n" +
            "  \"confidence\": <float 0.0 to 1.0>,\n" +
            "  \"supporting_evidence\": [<list of evidence points>]\n" +
            "}",
            vetoType,
            initialReason,
            objectToString(context)
        );
        
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("rationale", String.class);
        schema.put("severity", String.class);
        schema.put("recommended_action", String.class);
        schema.put("duration_minutes", Integer.class);
        schema.put("confidence", Double.class);
        schema.put("supporting_evidence", Object.class);
        
        return executeStructuredPrompt(
            prompt,
            InsightType.VETO_RATIONALE,
            "RiskManagementService",
            null,
            schema
        );
    }
    
    /**
     * Statistical Pattern Interpretation
     * Feed complex time-series/statistical results and extract actionable signals
     */
    public Map<String, Object> interpretPattern(
            String patternType,
            Map<String, Object> statistics) {
        
        String prompt = String.format(
            "Interpret this statistical pattern from trading system.\n\n" +
            "Pattern Type: %s\n" +
            "Statistics: %s\n\n" +
            "Respond ONLY with valid JSON matching this exact schema:\n" +
            "{\n" +
            "  \"interpretation\": \"<human-readable summary>\",\n" +
            "  \"signal_strength\": <float 0.0 to 1.0>,\n" +
            "  \"recommended_direction\": \"<LONG|SHORT|NEUTRAL>\",\n" +
            "  \"confidence\": <float 0.0 to 1.0>,\n" +
            "  \"key_observations\": [<list of key points>],\n" +
            "  \"risk_factors\": [<list of risks>]\n" +
            "}",
            patternType,
            objectToString(statistics)
        );
        
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("interpretation", String.class);
        schema.put("signal_strength", Double.class);
        schema.put("recommended_direction", String.class);
        schema.put("confidence", Double.class);
        schema.put("key_observations", Object.class);
        schema.put("risk_factors", Object.class);
        
        return executeStructuredPrompt(
            prompt,
            InsightType.PATTERN_INTERPRETATION,
            "DataAnalysisService",
            null,
            schema
        );
    }
    
    /**
     * Signal Generation Assistance
     * Analyze market data and provide AI-enhanced signal recommendations
     */
    public Map<String, Object> generateSignalAssistance(
            String symbol,
            Map<String, Object> marketData,
            Map<String, Object> strategyContext) {
        
        String prompt = String.format(
            "Analyze market conditions and provide trading signal assistance.\n\n" +
            "Symbol: %s\n" +
            "Market Data: %s\n" +
            "Strategy Context: %s\n\n" +
            "Respond ONLY with valid JSON matching this exact schema:\n" +
            "{\n" +
            "  \"signal_recommendation\": \"<STRONG_BUY|BUY|HOLD|SELL|STRONG_SELL>\",\n" +
            "  \"confidence\": <float 0.0 to 1.0>,\n" +
            "  \"reasoning\": \"<brief explanation>\",\n" +
            "  \"key_factors\": [<list of key factors>],\n" +
            "  \"risk_level\": \"<LOW|MEDIUM|HIGH>\",\n" +
            "  \"suggested_position_size\": <float 0.0 to 1.0>,\n" +
            "  \"time_horizon\": \"<SHORT|MEDIUM|LONG>\"\n" +
            "}",
            symbol,
            objectToString(marketData),
            objectToString(strategyContext)
        );
        
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("signal_recommendation", String.class);
        schema.put("confidence", Double.class);
        schema.put("reasoning", String.class);
        schema.put("key_factors", Object.class);
        schema.put("risk_level", String.class);
        schema.put("suggested_position_size", Double.class);
        schema.put("time_horizon", String.class);
        
        return executeStructuredPrompt(
            prompt,
            InsightType.SIGNAL_GENERATION,
            "SignalGeneratorAgent",
            symbol,
            schema
        );
    }
    
    /**
     * Startup Market Analysis
     * Analyze overall market conditions at system startup
     */
    public Map<String, Object> analyzeMarketAtStartup(
            String market,
            Map<String, Object> recentData) {
        
        String prompt = String.format(
            "Perform comprehensive market analysis at trading system startup.\n\n" +
            "Market: %s\n" +
            "Recent Data: %s\n\n" +
            "Respond ONLY with valid JSON matching this exact schema:\n" +
            "{\n" +
            "  \"market_sentiment\": \"<VERY_BEARISH|BEARISH|NEUTRAL|BULLISH|VERY_BULLISH>\",\n" +
            "  \"confidence\": <float 0.0 to 1.0>,\n" +
            "  \"key_trends\": [<list of important trends>],\n" +
            "  \"risk_factors\": [<list of risk factors>],\n" +
            "  \"opportunities\": [<list of opportunities>],\n" +
            "  \"recommended_strategy\": \"<AGGRESSIVE|MODERATE|CONSERVATIVE|DEFENSIVE>\",\n" +
            "  \"explanation\": \"<comprehensive analysis summary>\"\n" +
            "}",
            market,
            objectToString(recentData)
        );
        
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("market_sentiment", String.class);
        schema.put("confidence", Double.class);
        schema.put("key_trends", Object.class);
        schema.put("risk_factors", Object.class);
        schema.put("opportunities", Object.class);
        schema.put("recommended_strategy", String.class);
        schema.put("explanation", String.class);
        
        return executeStructuredPrompt(
            prompt,
            InsightType.MARKET_ANALYSIS,
            "TradingEngine",
            market,
            schema
        );
    }

    /**
     * Generate a general insight from a prompt.
     */
    public String generateInsight(String prompt) {
        String fullPrompt = prompt + "\n\nRespond ONLY with valid JSON matching this exact schema:\n" +
                "{\n" +
                "  \"insight\": \"<your insight here>\"\n" +
                "}";
        
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("insight", String.class);
        
        Map<String, Object> result = executeStructuredPrompt(
                fullPrompt,
                InsightType.MARKET_ANALYSIS,
                "TradingEngine",
                null,
                schema
        );
        
        return (String) result.get("insight");
    }

    /**
     * Execute trade veto decision using the paranoid risk manager system prompt.
     * Returns APPROVE or VETO with reason.
     * 
     * @param tradeProposal Map containing trade details, system state, market context, news, strategy info
     * @return Map with "veto" (boolean), "reason" (String)
     */
    public Map<String, Object> executeTradeVeto(Map<String, Object> tradeProposal) {
        String systemPrompt = tw.gc.auto.equity.trader.agents.RiskManagerAgent.getRiskManagerSystemPrompt();
        
        String userPrompt = String.format("""
Trade Proposal:
- Symbol: %s
- Direction: %s
- Shares: %s
- Entry Logic: %s
- Strategy: %s

System State:
- Daily P&L: %s TWD
- Weekly P&L: %s TWD
- Current Drawdown: %s%%
- Trades Today: %s
- Win Streak: %s
- Loss Streak: %s

Market Context:
- Volatility: %s
- Time: %s
- Session Phase: %s

News Headlines:
%s

Strategy Context:
- Days Active: %s
- Recent Backtest Stats: %s""",
            tradeProposal.getOrDefault("symbol", "N/A"),
            tradeProposal.getOrDefault("direction", "N/A"),
            tradeProposal.getOrDefault("shares", "N/A"),
            tradeProposal.getOrDefault("entry_logic", "N/A"),
            tradeProposal.getOrDefault("strategy_name", "N/A"),
            tradeProposal.getOrDefault("daily_pnl", "N/A"),
            tradeProposal.getOrDefault("weekly_pnl", "N/A"),
            tradeProposal.getOrDefault("drawdown_percent", "N/A"),
            tradeProposal.getOrDefault("trades_today", "N/A"),
            tradeProposal.getOrDefault("win_streak", "N/A"),
            tradeProposal.getOrDefault("loss_streak", "N/A"),
            tradeProposal.getOrDefault("volatility_level", "N/A"),
            tradeProposal.getOrDefault("time_of_day", "N/A"),
            tradeProposal.getOrDefault("session_phase", "N/A"),
            formatNewsHeadlines(tradeProposal.get("news_headlines")),
            tradeProposal.getOrDefault("strategy_days_active", "N/A"),
            tradeProposal.getOrDefault("recent_backtest_stats", "N/A")
        );

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", ollamaProperties.getModel());
            request.put("system", systemPrompt);
            request.put("prompt", userPrompt);
            request.put("stream", false);
            
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.1);
            request.put("options", options);
            
            String response = restTemplate.postForObject(
                ollamaProperties.getUrl() + "/api/generate",
                request,
                String.class
            );
            
            if (response == null) {
                result.put("veto", true);
                result.put("reason", "LLM returned null response - defaulting to VETO");
                return result;
            }
            
            JsonNode responseNode = objectMapper.readTree(response);
            String llmOutput = responseNode.path("response").asText().trim();
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("✅ Trade veto LLM call completed in {}ms: {}", processingTime, llmOutput);
            
            return parseVetoResponse(llmOutput);
            
        } catch (Exception e) {
            log.error("❌ Trade veto LLM call failed: {}", e.getMessage());
            result.put("veto", true);
            result.put("reason", "Analysis failed: " + e.getMessage());
            return result;
        }
    }
    
    private Map<String, Object> parseVetoResponse(String responseText) {
        Map<String, Object> result = new HashMap<>();
        String text = responseText.trim();
        
        if (text.equals("APPROVE") || text.startsWith("APPROVE")) {
            result.put("veto", false);
            result.put("reason", "APPROVED");
            return result;
        }
        
        if (text.toUpperCase().startsWith("VETO:")) {
            String reason = text.substring(5).trim();
            result.put("veto", true);
            result.put("reason", reason);
            return result;
        }
        
        result.put("veto", true);
        result.put("reason", "unexpected response format - defaulting to VETO");
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private String formatNewsHeadlines(Object headlines) {
        if (headlines == null) {
            return "- No news available";
        }
        if (headlines instanceof java.util.List) {
            java.util.List<String> list = (java.util.List<String>) headlines;
            if (list.isEmpty()) {
                return "- No news available";
            }
            StringBuilder sb = new StringBuilder();
            for (String h : list) {
                sb.append("- ").append(h).append("\n");
            }
            return sb.toString().trim();
        }
        return "- " + headlines.toString();
    }
    
    // Utility methods
    
    private String extractJson(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
    
    private void validateSchema(Map<String, Object> response, Map<String, Class<?>> expectedSchema) {
        for (Map.Entry<String, Class<?>> entry : expectedSchema.entrySet()) {
            if (!response.containsKey(entry.getKey())) {
                throw new IllegalArgumentException(
                    "Missing required field in LLM response: " + entry.getKey());
            }
        }
    }
    
    private Double extractConfidence(Map<String, Object> response) {
        Object conf = response.get("confidence");
        if (conf instanceof Number) {
            return ((Number) conf).doubleValue();
        }
        return null;
    }
    
    private String extractRecommendation(Map<String, Object> response) {
        Object rec = response.get("recommended_action");
        if (rec == null) {
            rec = response.get("recommended_direction");
        }
        return rec != null ? rec.toString() : null;
    }
    
    private String extractExplanation(Map<String, Object> response) {
        Object exp = response.get("explanation");
        if (exp == null) {
            exp = response.get("interpretation");
        }
        if (exp == null) {
            exp = response.get("rationale");
        }
        return exp != null ? exp.toString() : null;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }
    
    private String objectToString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
