package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.EconomicNews;

import java.util.List;
import java.util.function.Function;

/**
 * Functional interface for providing news sentiment data to strategies.
 * 
 * <p>This interface allows strategies to access news sentiment data
 * (sentiment scores, news counts, impact levels) without direct dependency
 * on the NewsService.
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // In BacktestService (provider implementation)
 * NewsSentimentProvider provider = symbol -> newsService.getRecentNews(symbol);
 * 
 * // In strategy constructor
 * NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.5, provider);
 * </pre>
 * 
 * <h3>Fallback Behavior:</h3>
 * Strategies should return neutral signals when no news data is available
 * (returns empty list).
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 * @see EconomicNews
 */
@FunctionalInterface
public interface NewsSentimentProvider extends Function<String, List<EconomicNews>> {
    
    /**
     * Get recent news articles for a symbol.
     * 
     * @param symbol Stock ticker symbol (e.g., "2330.TW")
     * @return List of recent news articles, empty list if none available
     */
    List<EconomicNews> getRecentNews(String symbol);
    
    /**
     * Default implementation delegates to getRecentNews.
     */
    @Override
    default List<EconomicNews> apply(String symbol) {
        return getRecentNews(symbol);
    }
    
    /**
     * Factory method for a no-op provider that returns empty list.
     * Useful for testing and backward compatibility.
     * 
     * @return NewsSentimentProvider that always returns empty list
     */
    static NewsSentimentProvider noOp() {
        return symbol -> List.of();
    }
    
    /**
     * Calculate aggregate sentiment score from a list of news articles.
     * 
     * @param news List of news articles
     * @return Aggregate sentiment score (-1.0 to +1.0), or 0.0 if empty
     */
    static double calculateAggregateSentiment(List<EconomicNews> news) {
        if (news == null || news.isEmpty()) {
            return 0.0;
        }
        
        double sum = 0.0;
        int count = 0;
        
        for (EconomicNews article : news) {
            if (article.getSentimentScore() != null) {
                sum += article.getSentimentScore();
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0.0;
    }
    
    /**
     * Calculate weighted sentiment with exponential decay.
     * More recent news has higher weight.
     * 
     * @param news List of news articles (should be sorted by publishedAt DESC)
     * @param halfLifeMinutes Half-life in minutes for weight decay
     * @return Weighted sentiment score (-1.0 to +1.0), or 0.0 if empty
     */
    static double calculateWeightedSentiment(List<EconomicNews> news, double halfLifeMinutes) {
        if (news == null || news.isEmpty()) {
            return 0.0;
        }
        
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        
        for (EconomicNews article : news) {
            if (article.getSentimentScore() == null) {
                continue;
            }
            
            double ageMinutes = java.time.Duration.between(article.getPublishedAt(), now).toMinutes();
            double weight = Math.exp(-0.693 * ageMinutes / halfLifeMinutes);
            
            if (article.getSentimentConfidence() != null) {
                weight *= article.getSentimentConfidence();
            }
            
            weightedSum += article.getSentimentScore() * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
    
    /**
     * Check if any news articles have high impact.
     * 
     * @param news List of news articles
     * @return true if any article has HIGH impact level
     */
    static boolean hasHighImpactNews(List<EconomicNews> news) {
        if (news == null || news.isEmpty()) {
            return false;
        }
        return news.stream()
                .anyMatch(n -> "HIGH".equals(n.getImpactLevel()));
    }
    
    /**
     * Check if there's breaking news.
     * 
     * @param news List of news articles
     * @return true if any article is marked as breaking
     */
    static boolean hasBreakingNews(List<EconomicNews> news) {
        if (news == null || news.isEmpty()) {
            return false;
        }
        return news.stream()
                .anyMatch(n -> Boolean.TRUE.equals(n.getIsBreaking()));
    }
}
