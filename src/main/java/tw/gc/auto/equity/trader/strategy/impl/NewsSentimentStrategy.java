package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.EconomicNews;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.*;

import java.util.List;

/**
 * News Sentiment Strategy - Trade based on news sentiment analysis.
 * 
 * <h3>Strategy Logic:</h3>
 * <ul>
 *   <li>Aggregate sentiment from recent news articles</li>
 *   <li>Generate BUY signal when sentiment is strongly bullish</li>
 *   <li>Generate SELL signal when sentiment is strongly bearish</li>
 *   <li>Apply confidence weighting to sentiment scores</li>
 * </ul>
 * 
 * <h3>Parameters:</h3>
 * <ul>
 *   <li>sentimentThreshold: Minimum absolute sentiment for signal (default 0.3)</li>
 *   <li>minNewsCount: Minimum news articles required (default 2)</li>
 *   <li>halfLifeMinutes: Weight decay half-life (default 240 = 4 hours)</li>
 * </ul>
 * 
 * <h3>Data Source:</h3>
 * Uses NewsService via NewsSentimentProvider to fetch news with LLM-scored sentiment.
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan (real sentiment integration)
 */
@Slf4j
public class NewsSentimentStrategy implements IStrategy {
    
    private static final double DEFAULT_SENTIMENT_THRESHOLD = 0.3;
    private static final int DEFAULT_MIN_NEWS_COUNT = 2;
    private static final double DEFAULT_HALF_LIFE_MINUTES = 240.0; // 4 hours
    
    private final double sentimentThreshold;
    private final int minNewsCount;
    private final double halfLifeMinutes;
    private final NewsSentimentProvider newsProvider;
    
    /**
     * Create strategy with default parameters and no-op provider (backward compatible).
     */
    public NewsSentimentStrategy() {
        this(DEFAULT_SENTIMENT_THRESHOLD, DEFAULT_MIN_NEWS_COUNT, DEFAULT_HALF_LIFE_MINUTES, NewsSentimentProvider.noOp());
        log.info("[News Sentiment] Strategy initialized with default parameters (no data provider)");
    }
    
    /**
     * Create strategy with custom threshold and news provider.
     * 
     * @param sentimentThreshold Minimum absolute sentiment for signal generation
     * @param newsProvider Provider for news sentiment data
     */
    public NewsSentimentStrategy(double sentimentThreshold, NewsSentimentProvider newsProvider) {
        this(sentimentThreshold, DEFAULT_MIN_NEWS_COUNT, DEFAULT_HALF_LIFE_MINUTES, newsProvider);
    }
    
    /**
     * Create strategy with full customization.
     * 
     * @param sentimentThreshold Minimum absolute sentiment for signal generation
     * @param minNewsCount Minimum news articles required for signal
     * @param halfLifeMinutes Weight decay half-life in minutes
     * @param newsProvider Provider for news sentiment data
     */
    public NewsSentimentStrategy(double sentimentThreshold, int minNewsCount, 
                                  double halfLifeMinutes, NewsSentimentProvider newsProvider) {
        this.sentimentThreshold = sentimentThreshold;
        this.minNewsCount = minNewsCount;
        this.halfLifeMinutes = halfLifeMinutes;
        this.newsProvider = newsProvider != null ? newsProvider : NewsSentimentProvider.noOp();
        log.info("[News Sentiment] Strategy initialized: threshold={}, minNews={}, halfLife={}min", 
                sentimentThreshold, minNewsCount, halfLifeMinutes);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null) {
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        log.trace("[News Sentiment] Analyzing sentiment for {}", symbol);
        
        // Get recent news for the symbol
        List<EconomicNews> news = newsProvider.getRecentNews(symbol);
        
        if (news.isEmpty()) {
            log.debug("[News Sentiment] No news available for {}", symbol);
            return TradeSignal.neutral("No news data available");
        }
        
        // Count articles with sentiment scores
        long scoredCount = news.stream()
                .filter(n -> n.getSentimentScore() != null)
                .count();
        
        if (scoredCount < minNewsCount) {
            log.debug("[News Sentiment] Insufficient news for {}: {} < {}", symbol, scoredCount, minNewsCount);
            return TradeSignal.neutral(String.format("Insufficient news (%d < %d)", scoredCount, minNewsCount));
        }
        
        // Calculate weighted sentiment
        double sentiment = NewsSentimentProvider.calculateWeightedSentiment(news, halfLifeMinutes);
        
        // Check for high-impact news
        boolean hasHighImpact = NewsSentimentProvider.hasHighImpactNews(news);
        boolean hasBreaking = NewsSentimentProvider.hasBreakingNews(news);
        
        // Adjust threshold for high-impact news (lower threshold = easier to trigger)
        double effectiveThreshold = hasHighImpact ? sentimentThreshold * 0.7 : sentimentThreshold;
        
        // Generate signal based on sentiment
        String reason = String.format("Sentiment=%.2f (threshold=%.2f, news=%d)", 
                sentiment, effectiveThreshold, scoredCount);
        
        if (hasBreaking) {
            reason += " [BREAKING]";
        }
        if (hasHighImpact) {
            reason += " [HIGH_IMPACT]";
        }
        
        if (sentiment >= effectiveThreshold) {
            log.info("[News Sentiment] Bullish signal for {}: {}", symbol, reason);
            return TradeSignal.longSignal(Math.abs(sentiment), reason);
        } else if (sentiment <= -effectiveThreshold) {
            log.info("[News Sentiment] Bearish signal for {}: {}", symbol, reason);
            return TradeSignal.shortSignal(Math.abs(sentiment), reason);
        }
        
        log.debug("[News Sentiment] Neutral signal for {}: {}", symbol, reason);
        return TradeSignal.neutral("Neutral sentiment: " + reason);
    }
    
    @Override
    public String getName() { 
        return "News / Sentiment-Based Trading"; 
    }
    
    @Override
    public StrategyType getType() { 
        return StrategyType.SHORT_TERM; 
    }
    
    /**
     * Get the configured sentiment threshold.
     */
    public double getSentimentThreshold() {
        return sentimentThreshold;
    }
    
    /**
     * Get the minimum news count.
     */
    public int getMinNewsCount() {
        return minNewsCount;
    }
    
    /**
     * Get the half-life for weight decay.
     */
    public double getHalfLifeMinutes() {
        return halfLifeMinutes;
    }
}
