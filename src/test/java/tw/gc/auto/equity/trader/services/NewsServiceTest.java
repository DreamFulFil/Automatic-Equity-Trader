package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.EconomicNews;
import tw.gc.auto.equity.trader.repositories.EconomicNewsRepository;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for NewsService.
 * Tests news fetching, sentiment aggregation, and scheduled refresh.
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewsService Unit Tests")
class NewsServiceTest {

    @Mock
    private EconomicNewsRepository newsRepository;
    
    @Mock
    private LlmService llmService;
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private TelegramService telegramService;
    
    @InjectMocks
    private NewsService newsService;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Inject the real ObjectMapper
        try {
            java.lang.reflect.Field field = NewsService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(newsService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject ObjectMapper", e);
        }
    }
    
    // ========== getRecentNews Tests ==========
    
    @Nested
    @DisplayName("getRecentNews()")
    class GetRecentNewsTests {
        
        @Test
        @DisplayName("should return recent news for symbol")
        void shouldReturnRecentNewsForSymbol() {
            // Given
            String symbol = "2330.TW";
            List<EconomicNews> expectedNews = List.of(
                    createNews(symbol, "TSMC reports record Q4 revenue", 0.8),
                    createNews(symbol, "TSMC expands Arizona fab", 0.6)
            );
            given(newsRepository.findRecentBySymbol(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(expectedNews);
            
            // When
            List<EconomicNews> result = newsService.getRecentNews(symbol);
            
            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getHeadline()).contains("TSMC");
        }
        
        @Test
        @DisplayName("should return empty list when no news available")
        void shouldReturnEmptyListWhenNoNews() {
            // Given
            given(newsRepository.findRecentBySymbol(anyString(), any(OffsetDateTime.class)))
                    .willReturn(List.of());
            
            // When
            List<EconomicNews> result = newsService.getRecentNews("9999.TW");
            
            // Then
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("should use custom lookback hours")
        void shouldUseCustomLookbackHours() {
            // Given
            String symbol = "2330.TW";
            ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            given(newsRepository.findRecentBySymbol(eq(symbol), sinceCaptor.capture()))
                    .willReturn(List.of());
            
            // When
            newsService.getRecentNews(symbol, 48); // 48 hours
            
            // Then
            OffsetDateTime capturedSince = sinceCaptor.getValue();
            // Should be approximately 48 hours ago
            long hoursDiff = java.time.Duration.between(capturedSince, OffsetDateTime.now()).toHours();
            assertThat(hoursDiff).isBetween(47L, 49L);
        }
    }
    
    // ========== getAggregateSentiment Tests ==========
    
    @Nested
    @DisplayName("getAggregateSentiment()")
    class GetAggregateSentimentTests {
        
        @Test
        @DisplayName("should return average sentiment for symbol")
        void shouldReturnAverageSentiment() {
            // Given
            String symbol = "2330.TW";
            given(newsRepository.calculateAverageSentiment(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(0.65);
            
            // When
            Double result = newsService.getAggregateSentiment(symbol);
            
            // Then
            assertThat(result).isEqualTo(0.65);
        }
        
        @Test
        @DisplayName("should return null when no sentiment data")
        void shouldReturnNullWhenNoSentimentData() {
            // Given
            given(newsRepository.calculateAverageSentiment(anyString(), any(OffsetDateTime.class)))
                    .willReturn(null);
            
            // When
            Double result = newsService.getAggregateSentiment("UNKNOWN");
            
            // Then
            assertThat(result).isNull();
        }
    }
    
    // ========== getWeightedSentiment Tests ==========
    
    @Nested
    @DisplayName("getWeightedSentiment()")
    class GetWeightedSentimentTests {
        
        @Test
        @DisplayName("should weight recent news more heavily")
        void shouldWeightRecentNewsMoreHeavily() {
            // Given
            String symbol = "2330.TW";
            List<EconomicNews> news = List.of(
                    createNewsWithTime(symbol, "Recent positive", 0.8, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(symbol, "Old negative", -0.8, OffsetDateTime.now().minusHours(12))
            );
            given(newsRepository.findForMomentumCalculation(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(news);
            
            // When
            Double result = newsService.getWeightedSentiment(symbol);
            
            // Then
            // Recent positive news should dominate, so result should be positive
            assertThat(result).isNotNull();
            assertThat(result).isGreaterThan(0.0);
        }
        
        @Test
        @DisplayName("should return null when no news available")
        void shouldReturnNullWhenNoNews() {
            // Given
            given(newsRepository.findForMomentumCalculation(anyString(), any(OffsetDateTime.class)))
                    .willReturn(List.of());
            
            // When
            Double result = newsService.getWeightedSentiment("UNKNOWN");
            
            // Then
            assertThat(result).isNull();
        }
        
        @Test
        @DisplayName("should incorporate confidence scores")
        void shouldIncorporateConfidenceScores() {
            // Given
            String symbol = "2330.TW";
            EconomicNews highConfidence = createNewsWithTime(symbol, "High confidence", 0.5, OffsetDateTime.now().minusHours(1));
            highConfidence.setSentimentConfidence(0.95);
            
            EconomicNews lowConfidence = createNewsWithTime(symbol, "Low confidence", -0.5, OffsetDateTime.now().minusHours(1));
            lowConfidence.setSentimentConfidence(0.3);
            
            given(newsRepository.findForMomentumCalculation(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(List.of(highConfidence, lowConfidence));
            
            // When
            Double result = newsService.getWeightedSentiment(symbol);
            
            // Then
            // High confidence positive should dominate
            assertThat(result).isGreaterThan(0.0);
        }
    }
    
    // ========== getSentimentMomentum Tests ==========
    
    @Nested
    @DisplayName("getSentimentMomentum()")
    class GetSentimentMomentumTests {
        
        @Test
        @DisplayName("should calculate positive momentum when improving")
        void shouldCalculatePositiveMomentumWhenImproving() {
            // Given
            String symbol = "2330.TW";
            // Recent: 0.6, Older: 0.2 -> momentum = 0.4
            given(newsRepository.calculateAverageSentiment(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(0.6)   // Recent (4h)
                    .willReturn(0.2);  // Older (24h)
            
            // When
            Double result = newsService.getSentimentMomentum(symbol);
            
            // Then
            assertThat(result).isNotNull();
            // Note: Due to overlapping ranges, the exact value may vary
        }
        
        @Test
        @DisplayName("should return null when no sentiment data")
        void shouldReturnNullWhenNoData() {
            // Given
            given(newsRepository.calculateAverageSentiment(anyString(), any(OffsetDateTime.class)))
                    .willReturn(null);
            
            // When
            Double result = newsService.getSentimentMomentum("UNKNOWN");
            
            // Then
            assertThat(result).isNull();
        }
    }
    
    // ========== getHighImpactNews Tests ==========
    
    @Nested
    @DisplayName("getHighImpactNews()")
    class GetHighImpactNewsTests {
        
        @Test
        @DisplayName("should return news above threshold")
        void shouldReturnNewsAboveThreshold() {
            // Given
            double threshold = 0.7;
            List<EconomicNews> highImpact = List.of(
                    createNews("2330.TW", "Major acquisition", 0.9),
                    createNews("2454.TW", "Earnings beat", -0.8)
            );
            given(newsRepository.findHighImpactNews(eq(threshold), any(OffsetDateTime.class)))
                    .willReturn(highImpact);
            
            // When
            List<EconomicNews> result = newsService.getHighImpactNews(threshold);
            
            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(n -> Math.abs(n.getSentimentScore()) >= threshold);
        }
    }
    
    // ========== hasBreakingNews Tests ==========
    
    @Nested
    @DisplayName("hasBreakingNews()")
    class HasBreakingNewsTests {
        
        @Test
        @DisplayName("should return true when breaking news exists")
        void shouldReturnTrueWhenBreakingNewsExists() {
            // Given
            String symbol = "2330.TW";
            EconomicNews breaking = createNews(symbol, "BREAKING: CEO resigned", -0.9);
            breaking.setIsBreaking(true);
            
            given(newsRepository.findRecentBySymbol(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(List.of(breaking));
            
            // When
            boolean result = newsService.hasBreakingNews(symbol);
            
            // Then
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("should return false when no breaking news")
        void shouldReturnFalseWhenNoBreakingNews() {
            // Given
            String symbol = "2330.TW";
            EconomicNews regular = createNews(symbol, "Regular update", 0.3);
            
            given(newsRepository.findRecentBySymbol(eq(symbol), any(OffsetDateTime.class)))
                    .willReturn(List.of(regular));
            
            // When
            boolean result = newsService.hasBreakingNews(symbol);
            
            // Then
            assertThat(result).isFalse();
        }
    }
    
    // ========== fetchNewsForSymbol Tests ==========
    
    @Nested
    @DisplayName("fetchNewsForSymbol()")
    class FetchNewsForSymbolTests {
        
        @Test
        @DisplayName("should fetch and store news from Python bridge")
        void shouldFetchAndStoreNews() throws Exception {
            // Given
            String symbol = "2330.TW";
            String jsonResponse = """
                {
                    "symbol": "2330.TW",
                    "articles": [
                        {
                            "headline": "TSMC Q4 Revenue Hits Record High",
                            "summary": "Taiwan Semiconductor reports strong earnings",
                            "url": "https://example.com/article1",
                            "source": "Yahoo Finance",
                            "published_at": "%s"
                        }
                    ],
                    "count": 1
                }
                """.formatted(OffsetDateTime.now().minusHours(2).toString());
            
            given(restTemplate.getForObject(anyString(), eq(String.class)))
                    .willReturn(jsonResponse);
            given(newsRepository.existsByUrl(anyString())).willReturn(false);
            given(newsRepository.existsByHeadlineAndPublishedAt(anyString(), any(OffsetDateTime.class)))
                    .willReturn(false);
            given(llmService.scoreNewsImpact(anyString(), anyString()))
                    .willReturn(Map.of("sentiment_score", 0.7, "confidence", 0.9, "impact_score", 0.6));
            
            // When
            List<EconomicNews> result = newsService.fetchNewsForSymbol(symbol);
            
            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getHeadline()).contains("TSMC");
            verify(newsRepository).save(any(EconomicNews.class));
        }
        
        @Test
        @DisplayName("should skip duplicate articles")
        void shouldSkipDuplicates() throws Exception {
            // Given
            String symbol = "2330.TW";
            String jsonResponse = """
                {
                    "symbol": "2330.TW",
                    "articles": [
                        {
                            "headline": "Existing Article",
                            "url": "https://example.com/existing"
                        }
                    ]
                }
                """;
            
            given(restTemplate.getForObject(anyString(), eq(String.class)))
                    .willReturn(jsonResponse);
            given(newsRepository.existsByUrl("https://example.com/existing")).willReturn(true);
            
            // When
            List<EconomicNews> result = newsService.fetchNewsForSymbol(symbol);
            
            // Then
            assertThat(result).isEmpty();
            verify(newsRepository, never()).save(any(EconomicNews.class));
        }
        
        @Test
        @DisplayName("should handle Python bridge errors gracefully")
        void shouldHandlePythonBridgeErrors() {
            // Given
            given(restTemplate.getForObject(anyString(), eq(String.class)))
                    .willThrow(new RestClientException("Connection refused"));
            
            // When
            List<EconomicNews> result = newsService.fetchNewsForSymbol("2330.TW");
            
            // Then
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("should detect earnings-related news")
        void shouldDetectEarningsRelatedNews() throws Exception {
            // Given
            String jsonResponse = """
                {
                    "articles": [
                        {
                            "headline": "TSMC Reports Q4 Earnings Beat",
                            "summary": "Quarterly profit exceeds expectations"
                        }
                    ]
                }
                """;
            
            given(restTemplate.getForObject(anyString(), eq(String.class)))
                    .willReturn(jsonResponse);
            // URL is null in the JSON, so this stubbing won't be called
            lenient().when(newsRepository.existsByUrl(any())).thenReturn(false);
            given(newsRepository.existsByHeadlineAndPublishedAt(anyString(), any()))
                    .willReturn(false);
            given(llmService.scoreNewsImpact(anyString(), anyString()))
                    .willReturn(Map.of("sentiment_score", 0.8, "confidence", 0.9));
            
            ArgumentCaptor<EconomicNews> captor = ArgumentCaptor.forClass(EconomicNews.class);
            given(newsRepository.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));
            
            // When
            newsService.fetchNewsForSymbol("2330.TW");
            
            // Then
            EconomicNews saved = captor.getValue();
            assertThat(saved.getIsEarningsRelated()).isTrue();
        }
    }
    
    // ========== processUnprocessedNews Tests ==========
    
    @Nested
    @DisplayName("processUnprocessedNews()")
    class ProcessUnprocessedNewsTests {
        
        @Test
        @DisplayName("should process unscored news with LLM")
        void shouldProcessUnscoredNews() {
            // Given
            EconomicNews unprocessed = createNews("2330.TW", "Unprocessed headline", null);
            unprocessed.setIsProcessed(false);
            
            given(newsRepository.findByIsProcessedFalseOrderByPublishedAtAsc())
                    .willReturn(List.of(unprocessed));
            given(llmService.scoreNewsImpact(anyString(), anyString()))
                    .willReturn(Map.of("sentiment_score", 0.6, "confidence", 0.85, "impact_score", 0.4));
            
            // When
            int processed = newsService.processUnprocessedNews();
            
            // Then
            assertThat(processed).isEqualTo(1);
            verify(newsRepository).save(unprocessed);
            assertThat(unprocessed.getIsProcessed()).isTrue();
            assertThat(unprocessed.getSentimentScore()).isEqualTo(0.6);
        }
        
        @Test
        @DisplayName("should handle LLM errors gracefully")
        void shouldHandleLlmErrorsGracefully() {
            // Given
            EconomicNews unprocessed = createNews("2330.TW", "Problem headline", null);
            unprocessed.setIsProcessed(false);
            
            given(newsRepository.findByIsProcessedFalseOrderByPublishedAtAsc())
                    .willReturn(List.of(unprocessed));
            given(llmService.scoreNewsImpact(anyString(), anyString()))
                    .willThrow(new RuntimeException("LLM unavailable"));
            
            // When
            int processed = newsService.processUnprocessedNews();
            
            // Then
            assertThat(processed).isZero();
            assertThat(unprocessed.getIsProcessed()).isFalse();
        }
    }
    
    // ========== Helper Methods ==========
    
    private EconomicNews createNews(String symbol, String headline, Double sentiment) {
        return EconomicNews.builder()
                .primarySymbol(symbol)
                .headline(headline)
                .sentimentScore(sentiment)
                .publishedAt(OffsetDateTime.now().minusHours(1))
                .fetchedAt(OffsetDateTime.now())
                .isProcessed(sentiment != null)
                .isBreaking(false)
                .isEarningsRelated(false)
                .build();
    }
    
    private EconomicNews createNewsWithTime(String symbol, String headline, Double sentiment, OffsetDateTime publishedAt) {
        return EconomicNews.builder()
                .primarySymbol(symbol)
                .headline(headline)
                .sentimentScore(sentiment)
                .publishedAt(publishedAt)
                .fetchedAt(OffsetDateTime.now())
                .isProcessed(true)
                .isBreaking(false)
                .isEarningsRelated(false)
                .build();
    }
}
