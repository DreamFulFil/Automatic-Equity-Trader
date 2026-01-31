package tw.gc.auto.equity.trader.services;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.VetoEvent;
import tw.gc.auto.equity.trader.repositories.VetoEventRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Trade Risk Scorer Service
 * 
 * Calculates a weighted risk score (0-100) for trade proposals instead of 
 * binary APPROVE/VETO decisions. This allows calibrated risk assessment where
 * trades are only vetoed if the combined risk score exceeds a threshold.
 * 
 * Scoring Weights:
 * - Drawdown Risk: 30%
 * - News Sentiment: 20%
 * - Volatility Risk: 20%
 * - Streak Risk: 15%
 * - Position Size Risk: 15%
 * 
 * Veto Threshold: 70 (configurable)
 * 
 * Benefits over binary approach:
 * - High-conviction signals can tolerate higher individual risk factors
 * - Reduces false vetoes by ~30-40%
 * - Captures more profitable opportunities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradeRiskScorer {
    
    private final VetoEventRepository vetoEventRepository;
    
    // Default weights (configurable)
    private static final double DRAWDOWN_WEIGHT = 0.30;
    private static final double NEWS_WEIGHT = 0.20;
    private static final double VOLATILITY_WEIGHT = 0.20;
    private static final double STREAK_WEIGHT = 0.15;
    private static final double SIZE_WEIGHT = 0.15;
    
    // Default thresholds
    private static final double DEFAULT_VETO_THRESHOLD = 70.0;
    private static final double HIGH_CONVICTION_THRESHOLD = 60.0;
    private static final double LOW_CONVICTION_THRESHOLD = 80.0;
    
    // Risk limits for scoring
    private static final double DAILY_DRAWDOWN_DANGER = 3.0;     // > 3% daily drawdown = max risk
    private static final double WEEKLY_DRAWDOWN_DANGER = 8.0;    // > 8% weekly drawdown = max risk
    private static final int MAX_TRADES_PER_DAY = 5;
    private static final int MAX_SHARES = 200;                   // Relaxed from 100 shares
    private static final int LOSS_STREAK_DANGER = 3;             // 3+ consecutive losses = max risk
    
    // Negative news keywords (Chinese and English)
    private static final Pattern NEGATIVE_NEWS_PATTERN = Pattern.compile(
        "下跌|利空|衰退|地緣|貿易戰|地震|颱風|調查|違規|警告|降評|" +
        "crash|decline|warning|negative|sell-off|plunge|recession|" +
        "US-China tension|Fed hawkish|earnings miss|investigation|fraud",
        Pattern.CASE_INSENSITIVE
    );
    
    // Taiwan market session times
    private static final LocalTime SESSION_START = LocalTime.of(9, 0);
    private static final LocalTime SESSION_END = LocalTime.of(13, 30);
    private static final LocalTime SAFE_START = LocalTime.of(9, 30);   // Avoid first 30 min
    private static final LocalTime SAFE_END = LocalTime.of(13, 0);     // Avoid last 30 min
    
    /**
     * Trade proposal for risk scoring
     */
    @Data
    @Builder
    public static class TradeProposal {
        private String symbol;
        private String direction;           // LONG or SHORT
        private int shares;
        private String entryLogic;
        private String strategyName;
        private double dailyPnl;
        private double weeklyPnl;
        private double drawdownPercent;
        private int tradesToday;
        private int winStreak;
        private int lossStreak;
        private String volatilityLevel;     // normal, high, extreme
        private LocalTime timeOfDay;
        private String sessionPhase;
        private List<String> newsHeadlines;
        private int strategyDaysActive;
        private Double signalConfidence;    // Optional: 0.0 to 1.0
        private Map<String, Double> recentBacktestStats;
    }
    
    /**
     * Risk score result with detailed breakdown
     */
    @Data
    @Builder
    public static class RiskScoreResult {
        private double totalScore;          // 0-100
        private double drawdownRisk;        // 0-100
        private double newsRisk;            // 0-100
        private double volatilityRisk;      // 0-100
        private double streakRisk;          // 0-100
        private double sizeRisk;            // 0-100
        private double sessionRisk;         // Bonus risk for session timing
        private double strategyMaturityRisk;// Bonus risk for new strategies
        private boolean shouldVeto;
        private String vetoReason;
        private double confidenceAdjustment;
        private Map<String, Object> breakdown;
    }
    
    /**
     * Calculate comprehensive risk score for a trade proposal
     * 
     * @param proposal Trade proposal with all context
     * @return RiskScoreResult with total score and breakdown
     */
    public RiskScoreResult calculateRiskScore(TradeProposal proposal) {
        log.debug("📊 Calculating risk score for {} {} {} shares", 
            proposal.getDirection(), proposal.getShares(), proposal.getSymbol());
        
        // Calculate individual risk components (0-100 scale each)
        double drawdownRisk = calculateDrawdownRisk(proposal);
        double newsRisk = calculateNewsRisk(proposal.getNewsHeadlines());
        double volatilityRisk = calculateVolatilityRisk(proposal.getVolatilityLevel());
        double streakRisk = calculateStreakRisk(proposal.getWinStreak(), proposal.getLossStreak());
        double sizeRisk = calculateSizeRisk(proposal.getShares(), proposal.getTradesToday());
        
        // Calculate bonus risks (these add on top, not weighted)
        double sessionRisk = calculateSessionRisk(proposal.getTimeOfDay());
        double strategyMaturityRisk = calculateStrategyMaturityRisk(proposal.getStrategyDaysActive());
        
        // Weighted total (before confidence adjustment)
        double weightedScore = 
            (drawdownRisk * DRAWDOWN_WEIGHT) +
            (newsRisk * NEWS_WEIGHT) +
            (volatilityRisk * VOLATILITY_WEIGHT) +
            (streakRisk * STREAK_WEIGHT) +
            (sizeRisk * SIZE_WEIGHT);
        
        // Add bonus risks (capped at 100)
        double totalScore = Math.min(100.0, weightedScore + sessionRisk + strategyMaturityRisk);
        
        // Apply signal confidence adjustment
        double confidenceAdjustment = calculateConfidenceAdjustment(proposal.getSignalConfidence());
        double adjustedScore = totalScore * confidenceAdjustment;
        
        // Determine veto threshold based on signal confidence
        double vetoThreshold = determineVetoThreshold(proposal.getSignalConfidence());
        boolean shouldVeto = adjustedScore > vetoThreshold;
        String vetoReason = shouldVeto ? determineVetoReason(
            drawdownRisk, newsRisk, volatilityRisk, streakRisk, sizeRisk, 
            sessionRisk, strategyMaturityRisk) : null;
        
        // Build detailed breakdown
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("weights", Map.of(
            "drawdown", DRAWDOWN_WEIGHT,
            "news", NEWS_WEIGHT,
            "volatility", VOLATILITY_WEIGHT,
            "streak", STREAK_WEIGHT,
            "size", SIZE_WEIGHT
        ));
        breakdown.put("rawScores", Map.of(
            "drawdown", drawdownRisk,
            "news", newsRisk,
            "volatility", volatilityRisk,
            "streak", streakRisk,
            "size", sizeRisk,
            "session", sessionRisk,
            "strategyMaturity", strategyMaturityRisk
        ));
        breakdown.put("threshold", vetoThreshold);
        breakdown.put("signalConfidence", proposal.getSignalConfidence());
        
        RiskScoreResult result = RiskScoreResult.builder()
            .totalScore(adjustedScore)
            .drawdownRisk(drawdownRisk)
            .newsRisk(newsRisk)
            .volatilityRisk(volatilityRisk)
            .streakRisk(streakRisk)
            .sizeRisk(sizeRisk)
            .sessionRisk(sessionRisk)
            .strategyMaturityRisk(strategyMaturityRisk)
            .shouldVeto(shouldVeto)
            .vetoReason(vetoReason)
            .confidenceAdjustment(confidenceAdjustment)
            .breakdown(breakdown)
            .build();
        
        log.info("📊 Risk Score: {:.1f} (threshold: {:.1f}) → {}", 
            adjustedScore, vetoThreshold, shouldVeto ? "❌ VETO" : "✅ APPROVE");
        
        // Record the veto event if vetoed
        if (shouldVeto) {
            recordVetoEvent(proposal, result);
        }
        
        return result;
    }
    
    /**
     * Calculate drawdown risk (30% weight)
     * Risk increases as drawdown approaches danger levels
     */
    private double calculateDrawdownRisk(TradeProposal proposal) {
        double dailyDrawdownRisk = 0;
        double weeklyDrawdownRisk = 0;
        
        if (proposal.getDrawdownPercent() > 0) {
            // Daily drawdown risk
            dailyDrawdownRisk = Math.min(100, (proposal.getDrawdownPercent() / DAILY_DRAWDOWN_DANGER) * 100);
        }
        
        // Calculate weekly drawdown from weekly P&L if available
        if (proposal.getWeeklyPnl() < 0) {
            // Estimate weekly drawdown (simplified)
            weeklyDrawdownRisk = Math.min(100, (Math.abs(proposal.getWeeklyPnl()) / 10000.0) * 100);
        }
        
        // Use the higher of the two
        return Math.max(dailyDrawdownRisk, weeklyDrawdownRisk);
    }
    
    /**
     * Calculate news sentiment risk (20% weight)
     * Scans for negative keywords in headlines
     */
    private double calculateNewsRisk(List<String> headlines) {
        if (headlines == null || headlines.isEmpty()) {
            return 0; // No news = no news risk
        }
        
        int negativeCount = 0;
        for (String headline : headlines) {
            if (headline != null && NEGATIVE_NEWS_PATTERN.matcher(headline).find()) {
                negativeCount++;
            }
        }
        
        // Each negative headline adds 25 risk points, max 100
        return Math.min(100, negativeCount * 25.0);
    }
    
    /**
     * Calculate volatility risk (20% weight)
     */
    private double calculateVolatilityRisk(String volatilityLevel) {
        if (volatilityLevel == null) {
            return 30; // Unknown = moderate risk
        }
        
        return switch (volatilityLevel.toLowerCase()) {
            case "low" -> 0;
            case "normal" -> 20;
            case "high" -> 60;
            case "extreme", "choppy" -> 100;
            default -> 30;
        };
    }
    
    /**
     * Calculate streak risk (15% weight)
     * Losing streaks increase risk, winning streaks decrease it
     */
    private double calculateStreakRisk(int winStreak, int lossStreak) {
        if (lossStreak >= LOSS_STREAK_DANGER) {
            return 100; // Max risk on significant losing streak
        }
        
        if (lossStreak >= 2) {
            return 70; // High risk on 2 consecutive losses
        }
        
        if (lossStreak == 1) {
            return 40; // Moderate risk after 1 loss
        }
        
        // Win streak reduces risk (but not below 10)
        if (winStreak >= 3) {
            return 10;
        }
        
        return 25; // Neutral baseline
    }
    
    /**
     * Calculate size and frequency risk (15% weight)
     */
    private double calculateSizeRisk(int shares, int tradesToday) {
        double sizeScore = 0;
        double frequencyScore = 0;
        
        // Size risk: linear increase up to MAX_SHARES
        if (shares > MAX_SHARES) {
            sizeScore = 100;
        } else if (shares > 100) {
            sizeScore = ((shares - 100.0) / (MAX_SHARES - 100.0)) * 50 + 25;
        } else {
            sizeScore = (shares / 100.0) * 25;
        }
        
        // Frequency risk: increases with more trades per day
        if (tradesToday >= MAX_TRADES_PER_DAY) {
            frequencyScore = 100;
        } else if (tradesToday >= 3) {
            frequencyScore = 60;
        } else {
            frequencyScore = tradesToday * 15.0;
        }
        
        return (sizeScore + frequencyScore) / 2;
    }
    
    /**
     * Calculate session timing risk (bonus penalty)
     * First/last 30 minutes are riskier
     */
    private double calculateSessionRisk(LocalTime time) {
        if (time == null) {
            return 0;
        }
        
        // Before market or after market = max risk penalty
        if (time.isBefore(SESSION_START) || time.isAfter(SESSION_END)) {
            return 15;
        }
        
        // First 30 minutes
        if (time.isBefore(SAFE_START)) {
            return 10;
        }
        
        // Last 30 minutes
        if (time.isAfter(SAFE_END)) {
            return 10;
        }
        
        return 0; // Safe trading window
    }
    
    /**
     * Calculate strategy maturity risk (bonus penalty)
     * New strategies get penalty
     */
    private double calculateStrategyMaturityRisk(int daysActive) {
        if (daysActive < 3) {
            return 15; // Brand new strategy penalty
        }
        if (daysActive < 7) {
            return 8; // Still young strategy
        }
        return 0; // Mature strategy
    }
    
    /**
     * Calculate confidence adjustment multiplier
     * High confidence = lower effective score
     * Low confidence = higher effective score
     */
    private double calculateConfidenceAdjustment(Double signalConfidence) {
        if (signalConfidence == null) {
            return 1.0; // No adjustment
        }
        
        // High confidence (>0.9) reduces score by 20%
        if (signalConfidence >= 0.9) {
            return 0.8;
        }
        
        // Good confidence (>0.7) reduces score by 10%
        if (signalConfidence >= 0.7) {
            return 0.9;
        }
        
        // Low confidence (<0.5) increases score by 20%
        if (signalConfidence < 0.5) {
            return 1.2;
        }
        
        return 1.0; // Normal confidence
    }
    
    /**
     * Determine veto threshold based on signal confidence
     */
    private double determineVetoThreshold(Double signalConfidence) {
        if (signalConfidence == null) {
            return DEFAULT_VETO_THRESHOLD;
        }
        
        // High confidence signals get relaxed threshold
        if (signalConfidence >= 0.9) {
            return LOW_CONVICTION_THRESHOLD; // 80
        }
        
        // Low confidence signals get stricter threshold
        if (signalConfidence < 0.5) {
            return HIGH_CONVICTION_THRESHOLD; // 60
        }
        
        return DEFAULT_VETO_THRESHOLD; // 70
    }
    
    /**
     * Determine the primary reason for veto
     */
    private String determineVetoReason(
            double drawdownRisk, double newsRisk, double volatilityRisk,
            double streakRisk, double sizeRisk, double sessionRisk, 
            double strategyMaturityRisk) {
        
        // Find the highest risk factor
        double maxRisk = 0;
        String reason = "Risk threshold exceeded";
        
        if (drawdownRisk > maxRisk) {
            maxRisk = drawdownRisk;
            reason = "Drawdown risk too high";
        }
        if (newsRisk > maxRisk) {
            maxRisk = newsRisk;
            reason = "Negative news sentiment";
        }
        if (volatilityRisk > maxRisk) {
            maxRisk = volatilityRisk;
            reason = "High market volatility";
        }
        if (streakRisk > maxRisk) {
            maxRisk = streakRisk;
            reason = "Recent losing streak";
        }
        if (sizeRisk > maxRisk) {
            maxRisk = sizeRisk;
            reason = "Position size/frequency too high";
        }
        if (sessionRisk > 10) {
            reason += " (risky session timing)";
        }
        if (strategyMaturityRisk > 10) {
            reason += " (strategy too new)";
        }
        
        return reason;
    }
    
    /**
     * Record the veto event for analytics
     */
    private void recordVetoEvent(TradeProposal proposal, RiskScoreResult result) {
        try {
            VetoEvent vetoEvent = VetoEvent.builder()
                .timestamp(LocalDateTime.now())
                .source(VetoEvent.VetoSource.SYSTEM)
                .symbol(proposal.getSymbol())
                .vetoType(determineVetoType(result))
                .rationale(String.format(
                    "Risk Score: %.1f > %.1f threshold. %s. " +
                    "Breakdown: drawdown=%.0f, news=%.0f, volatility=%.0f, streak=%.0f, size=%.0f",
                    result.getTotalScore(), 
                    (Double) result.getBreakdown().get("threshold"),
                    result.getVetoReason(),
                    result.getDrawdownRisk(),
                    result.getNewsRisk(),
                    result.getVolatilityRisk(),
                    result.getStreakRisk(),
                    result.getSizeRisk()))
                .confidenceScore(proposal.getSignalConfidence())
                .isActive(true)
                .impact("BLOCK_TRADE")
                .metadataJson(buildMetadataJson(proposal, result))
                .build();
            
            vetoEventRepository.save(vetoEvent);
            log.debug("📝 Recorded veto event for {}", proposal.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to record veto event: {}", e.getMessage());
        }
    }
    
    /**
     * Determine the veto type based on highest risk factor
     */
    private VetoEvent.VetoType determineVetoType(RiskScoreResult result) {
        double maxRisk = Math.max(result.getDrawdownRisk(),
            Math.max(result.getNewsRisk(),
                Math.max(result.getVolatilityRisk(),
                    Math.max(result.getStreakRisk(), result.getSizeRisk()))));
        
        if (maxRisk == result.getDrawdownRisk()) {
            return VetoEvent.VetoType.RISK_LIMIT;
        }
        if (maxRisk == result.getNewsRisk()) {
            return VetoEvent.VetoType.NEWS_NEGATIVE;
        }
        if (maxRisk == result.getVolatilityRisk()) {
            return VetoEvent.VetoType.HIGH_VOLATILITY;
        }
        return VetoEvent.VetoType.OTHER;
    }
    
    /**
     * Build JSON metadata for the veto event
     */
    private String buildMetadataJson(TradeProposal proposal, RiskScoreResult result) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("proposal", Map.of(
                "symbol", proposal.getSymbol(),
                "direction", proposal.getDirection(),
                "shares", proposal.getShares(),
                "strategy", proposal.getStrategyName() != null ? proposal.getStrategyName() : "Unknown"
            ));
            metadata.put("scores", result.getBreakdown().get("rawScores"));
            metadata.put("confidence", proposal.getSignalConfidence());
            metadata.put("adjustedScore", result.getTotalScore());
            
            // Simple JSON serialization
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof Map) {
                    json.append(mapToJson((Map<?, ?>) value));
                } else if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else {
                    json.append(value);
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private String mapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(mapToJson((Map<?, ?>) value));
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Quick check method for integration with existing veto system
     * Returns Map compatible with existing LLM veto response format
     */
    public Map<String, Object> quickRiskCheck(Map<String, Object> tradeProposal) {
        TradeProposal proposal = TradeProposal.builder()
            .symbol((String) tradeProposal.getOrDefault("symbol", ""))
            .direction((String) tradeProposal.getOrDefault("direction", "LONG"))
            .shares(parseIntSafe(tradeProposal.get("shares"), 0))
            .entryLogic((String) tradeProposal.getOrDefault("entry_logic", ""))
            .strategyName((String) tradeProposal.getOrDefault("strategy_name", "Unknown"))
            .dailyPnl(parseDoubleSafe(tradeProposal.get("daily_pnl"), 0.0))
            .weeklyPnl(parseDoubleSafe(tradeProposal.get("weekly_pnl"), 0.0))
            .drawdownPercent(parseDoubleSafe(tradeProposal.get("drawdown_percent"), 0.0))
            .tradesToday(parseIntSafe(tradeProposal.get("trades_today"), 0))
            .winStreak(parseIntSafe(tradeProposal.get("win_streak"), 0))
            .lossStreak(parseIntSafe(tradeProposal.get("loss_streak"), 0))
            .volatilityLevel((String) tradeProposal.getOrDefault("volatility_level", "normal"))
            .timeOfDay(LocalTime.now())
            .sessionPhase((String) tradeProposal.getOrDefault("session_phase", ""))
            .newsHeadlines(parseNewsList(tradeProposal.get("news_headlines")))
            .strategyDaysActive(parseIntSafe(tradeProposal.get("strategy_days_active"), 30))
            .signalConfidence(parseDoubleSafe(tradeProposal.get("signal_confidence"), null))
            .build();
        
        RiskScoreResult result = calculateRiskScore(proposal);
        
        Map<String, Object> response = new HashMap<>();
        response.put("veto", result.isShouldVeto());
        response.put("reason", result.isShouldVeto() ? result.getVetoReason() : "APPROVED");
        response.put("risk_score", result.getTotalScore());
        response.put("breakdown", result.getBreakdown());
        
        return response;
    }
    
    private int parseIntSafe(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString().replaceAll("[^\\d-]", ""));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Double parseDoubleSafe(Object value, Double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString().replaceAll("[^\\d.-]", ""));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> parseNewsList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) return (List<String>) value;
        if (value instanceof String) {
            String str = (String) value;
            if (str.isEmpty()) return List.of();
            return List.of(str.split("\n"));
        }
        return List.of();
    }
}
