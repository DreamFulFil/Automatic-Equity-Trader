package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.*;
import tw.gc.auto.equity.trader.entities.EconomicNews;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for NewsSentimentProvider interface and utility methods.
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@DisplayName("NewsSentimentProvider Unit Tests")
class NewsSentimentProviderTest {

    // ========== noOp Factory Method Tests ==========
    
    @Nested
    @DisplayName("noOp()")
    class NoOpTests {
        
        @Test
        @DisplayName("should return empty list for any symbol")
        void shouldReturnEmptyList() {
            NewsSentimentProvider provider = NewsSentimentProvider.noOp();
            
            List<EconomicNews> result = provider.getRecentNews("2330.TW");
            
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("should work with apply method")
        void shouldWorkWithApplyMethod() {
            NewsSentimentProvider provider = NewsSentimentProvider.noOp();
            
            List<EconomicNews> result = provider.apply("2454.TW");
            
            assertThat(result).isEmpty();
        }
    }

    // ========== calculateAggregateSentiment Tests ==========
    
    @Nested
    @DisplayName("calculateAggregateSentiment()")
    class CalculateAggregateSentimentTests {
        
        @Test
        @DisplayName("should return 0.0 for null list")
        void shouldReturnZeroForNullList() {
            double result = NewsSentimentProvider.calculateAggregateSentiment(null);
            
            assertThat(result).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("should return 0.0 for empty list")
        void shouldReturnZeroForEmptyList() {
            double result = NewsSentimentProvider.calculateAggregateSentiment(List.of());
            
            assertThat(result).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("should calculate simple average of sentiment scores")
        void shouldCalculateSimpleAverage() {
            List<EconomicNews> news = List.of(
                    createNewsWithSentiment(0.8),
                    createNewsWithSentiment(0.6),
                    createNewsWithSentiment(0.4)
            );
            
            double result = NewsSentimentProvider.calculateAggregateSentiment(news);
            
            assertThat(result).isCloseTo(0.6, within(0.001));
        }
        
        @Test
        @DisplayName("should handle mixed positive and negative sentiments")
        void shouldHandleMixedSentiments() {
            List<EconomicNews> news = List.of(
                    createNewsWithSentiment(0.5),
                    createNewsWithSentiment(-0.5)
            );
            
            double result = NewsSentimentProvider.calculateAggregateSentiment(news);
            
            assertThat(result).isCloseTo(0.0, within(0.001));
        }
        
        @Test
        @DisplayName("should skip null sentiment scores")
        void shouldSkipNullSentiments() {
            List<EconomicNews> news = List.of(
                    createNewsWithSentiment(0.8),
                    createNewsWithSentiment(null),
                    createNewsWithSentiment(0.4)
            );
            
            double result = NewsSentimentProvider.calculateAggregateSentiment(news);
            
            // Average of 0.8 and 0.4 = 0.6
            assertThat(result).isCloseTo(0.6, within(0.001));
        }
        
        @Test
        @DisplayName("should return 0.0 if all sentiments are null")
        void shouldReturnZeroIfAllNull() {
            List<EconomicNews> news = List.of(
                    createNewsWithSentiment(null),
                    createNewsWithSentiment(null)
            );
            
            double result = NewsSentimentProvider.calculateAggregateSentiment(news);
            
            assertThat(result).isEqualTo(0.0);
        }
    }

    // ========== calculateWeightedSentiment Tests ==========
    
    @Nested
    @DisplayName("calculateWeightedSentiment()")
    class CalculateWeightedSentimentTests {
        
        @Test
        @DisplayName("should return 0.0 for null list")
        void shouldReturnZeroForNullList() {
            double result = NewsSentimentProvider.calculateWeightedSentiment(null, 240.0);
            
            assertThat(result).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("should return 0.0 for empty list")
        void shouldReturnZeroForEmptyList() {
            double result = NewsSentimentProvider.calculateWeightedSentiment(List.of(), 240.0);
            
            assertThat(result).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("should weight recent news more heavily")
        void shouldWeightRecentNewsMoreHeavily() {
            List<EconomicNews> news = List.of(
                    createNewsWithTime(0.5, OffsetDateTime.now().minusMinutes(30)),  // Recent positive
                    createNewsWithTime(-0.5, OffsetDateTime.now().minusHours(12))    // Old negative
            );
            
            double result = NewsSentimentProvider.calculateWeightedSentiment(news, 240.0);
            
            // Recent positive should dominate
            assertThat(result).isGreaterThan(0.0);
        }
        
        @Test
        @DisplayName("should incorporate confidence scores")
        void shouldIncorporateConfidenceScores() {
            EconomicNews highConfidence = createNewsWithTime(0.5, OffsetDateTime.now().minusHours(1));
            highConfidence.setSentimentConfidence(0.95);
            
            EconomicNews lowConfidence = createNewsWithTime(-0.5, OffsetDateTime.now().minusHours(1));
            lowConfidence.setSentimentConfidence(0.3);
            
            List<EconomicNews> news = List.of(highConfidence, lowConfidence);
            
            double result = NewsSentimentProvider.calculateWeightedSentiment(news, 240.0);
            
            // High confidence positive should dominate
            assertThat(result).isGreaterThan(0.0);
        }
        
        @Test
        @DisplayName("should handle different half-life values")
        void shouldHandleDifferentHalfLifeValues() {
            List<EconomicNews> news = List.of(
                    createNewsWithTime(0.5, OffsetDateTime.now().minusMinutes(60)),
                    createNewsWithTime(-0.5, OffsetDateTime.now().minusMinutes(60))
            );
            
            // With very short half-life (10 min), both are old
            double shortHalfLife = NewsSentimentProvider.calculateWeightedSentiment(news, 10.0);
            
            // Both have same age, so should cancel out
            assertThat(shortHalfLife).isCloseTo(0.0, within(0.01));
        }
        
        @Test
        @DisplayName("should skip null sentiment scores")
        void shouldSkipNullSentiments() {
            List<EconomicNews> news = List.of(
                    createNewsWithTime(0.8, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(null, OffsetDateTime.now().minusMinutes(30))
            );
            
            double result = NewsSentimentProvider.calculateWeightedSentiment(news, 240.0);
            
            assertThat(result).isCloseTo(0.8, within(0.1));
        }
    }

    // ========== hasHighImpactNews Tests ==========
    
    @Nested
    @DisplayName("hasHighImpactNews()")
    class HasHighImpactNewsTests {
        
        @Test
        @DisplayName("should return false for null list")
        void shouldReturnFalseForNullList() {
            boolean result = NewsSentimentProvider.hasHighImpactNews(null);
            
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("should return false for empty list")
        void shouldReturnFalseForEmptyList() {
            boolean result = NewsSentimentProvider.hasHighImpactNews(List.of());
            
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("should return true when HIGH impact exists")
        void shouldReturnTrueWhenHighImpact() {
            EconomicNews highImpact = createNewsWithSentiment(0.5);
            highImpact.setImpactLevel("HIGH");
            
            boolean result = NewsSentimentProvider.hasHighImpactNews(List.of(highImpact));
            
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("should return false when no HIGH impact")
        void shouldReturnFalseWhenNoHighImpact() {
            EconomicNews mediumImpact = createNewsWithSentiment(0.5);
            mediumImpact.setImpactLevel("MEDIUM");
            
            boolean result = NewsSentimentProvider.hasHighImpactNews(List.of(mediumImpact));
            
            assertThat(result).isFalse();
        }
    }

    // ========== hasBreakingNews Tests ==========
    
    @Nested
    @DisplayName("hasBreakingNews()")
    class HasBreakingNewsTests {
        
        @Test
        @DisplayName("should return false for null list")
        void shouldReturnFalseForNullList() {
            boolean result = NewsSentimentProvider.hasBreakingNews(null);
            
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("should return false for empty list")
        void shouldReturnFalseForEmptyList() {
            boolean result = NewsSentimentProvider.hasBreakingNews(List.of());
            
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("should return true when breaking news exists")
        void shouldReturnTrueWhenBreakingExists() {
            EconomicNews breaking = createNewsWithSentiment(0.5);
            breaking.setIsBreaking(true);
            
            boolean result = NewsSentimentProvider.hasBreakingNews(List.of(breaking));
            
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("should return false when no breaking news")
        void shouldReturnFalseWhenNoBreaking() {
            EconomicNews regular = createNewsWithSentiment(0.5);
            regular.setIsBreaking(false);
            
            boolean result = NewsSentimentProvider.hasBreakingNews(List.of(regular));
            
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("should handle null isBreaking field")
        void shouldHandleNullIsBreaking() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .build();
            // isBreaking is null
            
            boolean result = NewsSentimentProvider.hasBreakingNews(List.of(news));
            
            assertThat(result).isFalse();
        }
    }

    // ========== Functional Interface Tests ==========
    
    @Nested
    @DisplayName("Functional Interface")
    class FunctionalInterfaceTests {
        
        @Test
        @DisplayName("should work as lambda")
        void shouldWorkAsLambda() {
            EconomicNews news = createNewsWithSentiment(0.7);
            NewsSentimentProvider provider = symbol -> List.of(news);
            
            List<EconomicNews> result = provider.getRecentNews("TEST");
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSentimentScore()).isEqualTo(0.7);
        }
        
        @Test
        @DisplayName("should work with method reference")
        void shouldWorkWithMethodReference() {
            TestNewsRepository repo = new TestNewsRepository();
            NewsSentimentProvider provider = repo::findBySymbol;
            
            List<EconomicNews> result = provider.getRecentNews("2330.TW");
            
            assertThat(result).hasSize(1);
        }
    }

    // ========== Helper Classes and Methods ==========
    
    private static class TestNewsRepository {
        List<EconomicNews> findBySymbol(String symbol) {
            return List.of(EconomicNews.builder()
                    .primarySymbol(symbol)
                    .headline("Test from repo")
                    .sentimentScore(0.5)
                    .publishedAt(OffsetDateTime.now())
                    .build());
        }
    }
    
    private EconomicNews createNewsWithSentiment(Double sentiment) {
        return EconomicNews.builder()
                .primarySymbol("TEST")
                .headline("Test headline")
                .sentimentScore(sentiment)
                .publishedAt(OffsetDateTime.now().minusHours(1))
                .fetchedAt(OffsetDateTime.now())
                .impactLevel("MEDIUM")
                .isBreaking(false)
                .build();
    }
    
    private EconomicNews createNewsWithTime(Double sentiment, OffsetDateTime publishedAt) {
        return EconomicNews.builder()
                .primarySymbol("TEST")
                .headline("Test headline")
                .sentimentScore(sentiment)
                .publishedAt(publishedAt)
                .fetchedAt(OffsetDateTime.now())
                .impactLevel("MEDIUM")
                .isBreaking(false)
                .build();
    }
}
