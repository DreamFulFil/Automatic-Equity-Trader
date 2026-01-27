package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.EconomicNews;
import tw.gc.auto.equity.trader.entities.LlmInsight.InsightType;
import tw.gc.auto.equity.trader.repositories.EconomicNewsRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for fetching and managing economic news with sentiment analysis.
 * 
 * <h3>Data Source:</h3>
 * Fetches news from Python bridge which retrieves news from:
 * <ul>
 *   <li>Yahoo Finance news feed</li>
 *   <li>Google News RSS</li>
 *   <li>CNYES (Chinese financial news)</li>
 * </ul>
 * 
 * <h3>Sentiment Analysis:</h3>
 * Uses LLM (Ollama) via LlmService to score news sentiment:
 * <ul>
 *   <li>-1.0 to +1.0 sentiment score</li>
 *   <li>0.0 to 1.0 confidence score</li>
 *   <li>Category classification</li>
 *   <li>Impact level assessment</li>
 * </ul>
 * 
 * <h3>Refresh Schedule:</h3>
 * <ul>
 *   <li>Every 30 minutes during trading hours</li>
 *   <li>On-demand via manual refresh</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NewsService {

    private static final String PYTHON_BRIDGE_URL = "http://localhost:8888";
    private static final Duration FRESH_NEWS_THRESHOLD = Duration.ofMinutes(30);
    private static final Duration NEWS_LOOKBACK = Duration.ofHours(24);
    private static final int MAX_RETRIES = 3;
    private static final Duration[] BACKOFFS = {
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8)
    };

    /**
     * Default tickers to track for news.
     */
    private static final List<String> DEFAULT_TICKERS = List.of(
            "2330.TW",  // TSMC
            "2454.TW",  // MediaTek
            "2317.TW",  // Hon Hai (Foxconn)
            "2303.TW",  // UMC
            "2412.TW",  // Chunghwa Telecom
            "2882.TW",  // Cathay Financial
            "2881.TW",  // Fubon Financial
            "1301.TW",  // Formosa Plastics
            "2002.TW",  // China Steel
            "2308.TW"   // Delta Electronics
    );

    private final EconomicNewsRepository newsRepository;
    private final LlmService llmService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // ========== Public API ==========

    /**
     * Get recent news for a symbol.
     * 
     * @param symbol Stock ticker symbol
     * @return List of recent news articles
     */
    @Transactional(readOnly = true)
    public List<EconomicNews> getRecentNews(String symbol) {
        OffsetDateTime since = OffsetDateTime.now().minus(NEWS_LOOKBACK);
        return newsRepository.findRecentBySymbol(symbol, since);
    }

    /**
     * Get recent news for a symbol within a custom time window.
     * 
     * @param symbol Stock ticker symbol
     * @param lookbackHours Hours to look back
     * @return List of recent news articles
     */
    @Transactional(readOnly = true)
    public List<EconomicNews> getRecentNews(String symbol, int lookbackHours) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(lookbackHours);
        return newsRepository.findRecentBySymbol(symbol, since);
    }

    /**
     * Calculate aggregate sentiment for a symbol.
     * 
     * @param symbol Stock ticker symbol
     * @return Aggregate sentiment (-1.0 to +1.0), or null if no data
     */
    @Transactional(readOnly = true)
    public Double getAggregateSentiment(String symbol) {
        OffsetDateTime since = OffsetDateTime.now().minus(NEWS_LOOKBACK);
        return newsRepository.calculateAverageSentiment(symbol, since);
    }

    /**
     * Calculate weighted sentiment with exponential decay.
     * More recent news has higher weight.
     * 
     * @param symbol Stock ticker symbol
     * @return Weighted sentiment (-1.0 to +1.0), or null if no data
     */
    @Transactional(readOnly = true)
    public Double getWeightedSentiment(String symbol) {
        OffsetDateTime since = OffsetDateTime.now().minus(NEWS_LOOKBACK);
        List<EconomicNews> news = newsRepository.findForMomentumCalculation(symbol, since);
        
        if (news.isEmpty()) {
            return null;
        }

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        OffsetDateTime now = OffsetDateTime.now();
        
        // Half-life of 4 hours - news older than 4 hours has half the weight
        double halfLifeMinutes = 240.0;
        
        for (EconomicNews article : news) {
            double ageMinutes = Duration.between(article.getPublishedAt(), now).toMinutes();
            double weight = Math.exp(-0.693 * ageMinutes / halfLifeMinutes); // ln(2) ≈ 0.693
            
            // Also weight by confidence if available
            if (article.getSentimentConfidence() != null) {
                weight *= article.getSentimentConfidence();
            }
            
            if (article.getSentimentScore() != null) {
                weightedSum += article.getSentimentScore() * weight;
                totalWeight += weight;
            }
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : null;
    }

    /**
     * Get sentiment momentum (change in sentiment over time).
     * 
     * @param symbol Stock ticker symbol
     * @return Sentiment momentum, positive = improving, negative = worsening
     */
    @Transactional(readOnly = true)
    public Double getSentimentMomentum(String symbol) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime recentStart = now.minusHours(4);
        OffsetDateTime olderStart = now.minusHours(24);
        
        Double recentSentiment = newsRepository.calculateAverageSentiment(symbol, recentStart);
        Double olderSentiment = newsRepository.calculateAverageSentiment(symbol, olderStart);
        
        if (recentSentiment == null || olderSentiment == null) {
            return null;
        }
        
        return recentSentiment - olderSentiment;
    }

    /**
     * Count news articles for a symbol.
     * 
     * @param symbol Stock ticker symbol
     * @return News count in the last 24 hours
     */
    @Transactional(readOnly = true)
    public long getNewsCount(String symbol) {
        OffsetDateTime since = OffsetDateTime.now().minus(NEWS_LOOKBACK);
        return newsRepository.countNewsBySymbol(symbol, since);
    }

    /**
     * Get high-impact news articles.
     * 
     * @param threshold Minimum absolute sentiment score (0.0 to 1.0)
     * @return List of high-impact news
     */
    @Transactional(readOnly = true)
    public List<EconomicNews> getHighImpactNews(double threshold) {
        OffsetDateTime since = OffsetDateTime.now().minus(NEWS_LOOKBACK);
        return newsRepository.findHighImpactNews(threshold, since);
    }

    /**
     * Check if there's breaking news for a symbol.
     * 
     * @param symbol Stock ticker symbol
     * @return true if breaking news exists in the last 2 hours
     */
    @Transactional(readOnly = true)
    public boolean hasBreakingNews(String symbol) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(2);
        List<EconomicNews> recent = newsRepository.findRecentBySymbol(symbol, since);
        return recent.stream().anyMatch(EconomicNews::getIsBreaking);
    }

    /**
     * Fetch fresh news for a symbol from Python bridge.
     * Stores to database and returns the new articles.
     * 
     * @param symbol Stock ticker symbol
     * @return List of newly fetched news articles
     */
    @Transactional
    public List<EconomicNews> fetchNewsForSymbol(String symbol) {
        List<EconomicNews> newArticles = new ArrayList<>();
        
        try {
            String url = PYTHON_BRIDGE_URL + "/api/news/" + symbol;
            String response = callWithRetry(url);
            
            if (response == null) {
                log.warn("Failed to fetch news for {} after {} retries", symbol, MAX_RETRIES);
                return newArticles;
            }
            
            JsonNode root = objectMapper.readTree(response);
            
            if (root.has("error")) {
                log.warn("Error fetching news for {}: {}", symbol, root.get("error").asText());
                return newArticles;
            }
            
            JsonNode articles = root.get("articles");
            if (articles == null || !articles.isArray()) {
                return newArticles;
            }
            
            for (JsonNode article : articles) {
                try {
                    EconomicNews news = parseAndScoreArticle(article, symbol);
                    if (news != null && !isDuplicate(news)) {
                        newsRepository.save(news);
                        newArticles.add(news);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse/save article: {}", e.getMessage());
                }
            }
            
            log.info("📰 Fetched {} new articles for {}", newArticles.size(), symbol);
            
        } catch (Exception e) {
            log.error("Error fetching news for {}: {}", symbol, e.getMessage());
        }
        
        return newArticles;
    }

    /**
     * Process unprocessed news articles with LLM sentiment scoring.
     * 
     * @return Number of articles processed
     */
    @Transactional
    public int processUnprocessedNews() {
        List<EconomicNews> unprocessed = newsRepository.findByIsProcessedFalseOrderByPublishedAtAsc();
        int processed = 0;
        
        for (EconomicNews article : unprocessed) {
            try {
                scoreArticleSentiment(article);
                article.setIsProcessed(true);
                newsRepository.save(article);
                processed++;
            } catch (Exception e) {
                log.warn("Failed to process article {}: {}", article.getId(), e.getMessage());
            }
        }
        
        log.info("📊 Processed {} unprocessed articles", processed);
        return processed;
    }

    // ========== Scheduled Tasks ==========

    /**
     * Refresh news for all default tickers every 30 minutes during trading hours.
     * Runs at 0 and 30 minutes past each hour.
     */
    @Scheduled(cron = "0 0,30 9-14 * * MON-FRI", zone = "Asia/Taipei")
    @Transactional
    public void scheduledNewsRefresh() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("⏳ News refresh already in progress, skipping");
            return;
        }

        try {
            log.info("📰 Starting scheduled news refresh for {} tickers", DEFAULT_TICKERS.size());
            int totalNew = 0;
            
            for (String ticker : DEFAULT_TICKERS) {
                List<EconomicNews> news = fetchNewsForSymbol(ticker);
                totalNew += news.size();
                
                // Rate limiting
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Process any unprocessed articles
            int processedCount = processUnprocessedNews();
            
            log.info("✅ News refresh completed: {} new articles, {} processed", totalNew, processedCount);
            
            // Notify if high-impact news found
            notifyHighImpactNews();
            
        } finally {
            refreshInProgress.set(false);
        }
    }

    /**
     * Manual trigger for news refresh.
     * 
     * @return Number of new articles fetched
     */
    @Transactional
    public int manualRefresh() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("⏳ News refresh already in progress");
            return 0;
        }

        try {
            int totalNew = 0;
            for (String ticker : DEFAULT_TICKERS) {
                List<EconomicNews> news = fetchNewsForSymbol(ticker);
                totalNew += news.size();
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            processUnprocessedNews();
            return totalNew;
            
        } finally {
            refreshInProgress.set(false);
        }
    }

    /**
     * Manual refresh for a single symbol.
     * 
     * @param symbol Stock ticker symbol
     * @return Number of new articles fetched
     */
    @Transactional
    public int manualRefresh(String symbol) {
        List<EconomicNews> news = fetchNewsForSymbol(symbol);
        processUnprocessedNews();
        return news.size();
    }

    // ========== Internal Methods ==========

    /**
     * Parse a news article from JSON and score sentiment using LLM.
     */
    private EconomicNews parseAndScoreArticle(JsonNode article, String symbol) {
        String headline = article.path("headline").asText(null);
        if (headline == null || headline.isBlank()) {
            return null;
        }
        
        String summary = article.path("summary").asText(null);
        String url = article.path("url").asText(null);
        String source = article.path("source").asText("Unknown");
        String author = article.path("author").asText(null);
        
        // Parse published time
        OffsetDateTime publishedAt = OffsetDateTime.now();
        if (article.has("published_at")) {
            try {
                publishedAt = OffsetDateTime.parse(article.get("published_at").asText());
            } catch (Exception e) {
                log.debug("Could not parse published_at, using current time");
            }
        }
        
        // Build article entity
        EconomicNews news = EconomicNews.builder()
                .headline(headline)
                .summary(summary)
                .url(url)
                .source(source)
                .author(author)
                .publishedAt(publishedAt)
                .fetchedAt(OffsetDateTime.now())
                .primarySymbol(symbol)
                .isProcessed(false)
                .isBreaking(false)
                .isEarningsRelated(isEarningsRelated(headline, summary))
                .build();
        
        // Try to score sentiment immediately
        try {
            scoreArticleSentiment(news);
            news.setIsProcessed(true);
        } catch (Exception e) {
            log.debug("Will score sentiment later for: {}", headline);
        }
        
        return news;
    }

    /**
     * Score article sentiment using LLM.
     */
    private void scoreArticleSentiment(EconomicNews article) {
        String content = article.getSummary() != null ? article.getSummary() : "";
        
        Map<String, Object> result = llmService.scoreNewsImpact(
                article.getHeadline(),
                content
        );
        
        // Extract sentiment score
        if (result.containsKey("sentiment_score")) {
            Object score = result.get("sentiment_score");
            if (score instanceof Number) {
                article.setSentimentScore(((Number) score).doubleValue());
            }
        }
        
        // Extract confidence
        if (result.containsKey("confidence")) {
            Object confidence = result.get("confidence");
            if (confidence instanceof Number) {
                article.setSentimentConfidence(((Number) confidence).doubleValue());
            }
        }
        
        // Extract impact score to determine impact level
        if (result.containsKey("impact_score")) {
            Object impact = result.get("impact_score");
            if (impact instanceof Number) {
                double impactScore = ((Number) impact).doubleValue();
                if (impactScore >= 0.8) {
                    article.setImpactLevel("HIGH");
                    article.setIsBreaking(true);
                } else if (impactScore >= 0.5) {
                    article.setImpactLevel("MEDIUM");
                } else {
                    article.setImpactLevel("LOW");
                }
            }
        }
        
        // Extract affected symbols to related_symbols
        if (result.containsKey("affected_symbols")) {
            Object symbols = result.get("affected_symbols");
            if (symbols instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> symbolList = (List<String>) symbols;
                article.setRelatedSymbols(String.join(",", symbolList));
            }
        }
        
        // Set sentiment method
        article.setSentimentMethod("LLM");
    }

    /**
     * Check if article is likely earnings-related based on keywords.
     */
    private boolean isEarningsRelated(String headline, String summary) {
        String combined = (headline + " " + (summary != null ? summary : "")).toLowerCase();
        return combined.contains("earnings") ||
               combined.contains("profit") ||
               combined.contains("revenue") ||
               combined.contains("quarterly") ||
               combined.contains("財報") ||
               combined.contains("營收") ||
               combined.contains("獲利") ||
               combined.contains("業績");
    }

    /**
     * Check if an article is a duplicate.
     */
    private boolean isDuplicate(EconomicNews news) {
        if (news.getUrl() != null && newsRepository.existsByUrl(news.getUrl())) {
            return true;
        }
        return newsRepository.existsByHeadlineAndPublishedAt(
                news.getHeadline(),
                news.getPublishedAt()
        );
    }

    /**
     * Notify via Telegram if high-impact news is found.
     */
    private void notifyHighImpactNews() {
        List<EconomicNews> highImpact = getHighImpactNews(0.7);
        
        for (EconomicNews news : highImpact) {
            if (news.getIsBreaking() && news.isRecent(60)) { // Only if breaking and <1hr old
                String message = String.format(
                        "🚨 *High-Impact News*\n\n" +
                        "*%s*\n\n" +
                        "📊 Sentiment: %.2f\n" +
                        "🎯 Impact: %s\n" +
                        "🏷️ Symbol: %s",
                        news.getHeadline(),
                        news.getSentimentScore(),
                        news.getImpactLevel(),
                        news.getPrimarySymbol()
                );
                telegramService.sendMessage(message);
            }
        }
    }

    /**
     * Call Python bridge with retry logic.
     */
    private String callWithRetry(String url) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return restTemplate.getForObject(url, String.class);
            } catch (RestClientException e) {
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(BACKOFFS[attempt].toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
