package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EconomicNews entity.
 * Tests entity fields and utility methods.
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@DisplayName("EconomicNews Entity Unit Tests")
class EconomicNewsTest {

    // ========== Builder Tests ==========
    
    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        
        @Test
        @DisplayName("should create entity with all required fields")
        void shouldCreateWithRequiredFields() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test headline")
                    .publishedAt(OffsetDateTime.now())
                    .build();
            
            assertThat(news.getHeadline()).isEqualTo("Test headline");
            assertThat(news.getPublishedAt()).isNotNull();
        }
        
        @Test
        @DisplayName("should apply default values")
        void shouldApplyDefaultValues() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .build();
            
            // Check builder defaults
            assertThat(news.getSentimentMethod()).isEqualTo("ollama");
            assertThat(news.getImpactLevel()).isEqualTo("medium");
            assertThat(news.getIsEarningsRelated()).isFalse();
            assertThat(news.getIsBreaking()).isFalse();
            assertThat(news.getIsProcessed()).isFalse();
        }
        
        @Test
        @DisplayName("should create entity with full metadata")
        void shouldCreateWithFullMetadata() {
            OffsetDateTime now = OffsetDateTime.now();
            
            EconomicNews news = EconomicNews.builder()
                    .headline("TSMC Q4 Earnings Beat")
                    .summary("Taiwan Semiconductor reports record revenue")
                    .url("https://example.com/article")
                    .source("Yahoo Finance")
                    .author("John Doe")
                    .publishedAt(now.minusHours(1))
                    .fetchedAt(now)
                    .primarySymbol("2330.TW")
                    .relatedSymbols("2454.TW,2317.TW")
                    .sentimentScore(0.75)
                    .sentimentConfidence(0.92)
                    .sentimentMethod("LLM")
                    .sentimentLabel("POSITIVE")
                    .category("earnings")
                    .impactLevel("HIGH")
                    .isEarningsRelated(true)
                    .isBreaking(true)
                    .isProcessed(true)
                    .build();
            
            assertThat(news.getHeadline()).isEqualTo("TSMC Q4 Earnings Beat");
            assertThat(news.getSummary()).isEqualTo("Taiwan Semiconductor reports record revenue");
            assertThat(news.getUrl()).isEqualTo("https://example.com/article");
            assertThat(news.getSource()).isEqualTo("Yahoo Finance");
            assertThat(news.getAuthor()).isEqualTo("John Doe");
            assertThat(news.getPrimarySymbol()).isEqualTo("2330.TW");
            assertThat(news.getRelatedSymbols()).isEqualTo("2454.TW,2317.TW");
            assertThat(news.getSentimentScore()).isEqualTo(0.75);
            assertThat(news.getSentimentConfidence()).isEqualTo(0.92);
            assertThat(news.getSentimentMethod()).isEqualTo("LLM");
            assertThat(news.getSentimentLabel()).isEqualTo("POSITIVE");
            assertThat(news.getCategory()).isEqualTo("earnings");
            assertThat(news.getImpactLevel()).isEqualTo("HIGH");
            assertThat(news.getIsEarningsRelated()).isTrue();
            assertThat(news.getIsBreaking()).isTrue();
            assertThat(news.getIsProcessed()).isTrue();
        }
    }

    // ========== getRelatedSymbolsList Tests ==========
    
    @Nested
    @DisplayName("getRelatedSymbolsList()")
    class GetRelatedSymbolsListTests {
        
        @Test
        @DisplayName("should return empty list when relatedSymbols is null")
        void shouldReturnEmptyListWhenNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .relatedSymbols(null)
                    .build();
            
            List<String> result = news.getRelatedSymbolsList();
            
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("should return empty list when relatedSymbols is blank")
        void shouldReturnEmptyListWhenBlank() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .relatedSymbols("   ")
                    .build();
            
            List<String> result = news.getRelatedSymbolsList();
            
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("should split comma-separated symbols")
        void shouldSplitCommaSeperatedSymbols() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .relatedSymbols("2330.TW,2454.TW,2317.TW")
                    .build();
            
            List<String> result = news.getRelatedSymbolsList();
            
            assertThat(result).containsExactly("2330.TW", "2454.TW", "2317.TW");
        }
        
        @Test
        @DisplayName("should handle single symbol")
        void shouldHandleSingleSymbol() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .relatedSymbols("2330.TW")
                    .build();
            
            List<String> result = news.getRelatedSymbolsList();
            
            assertThat(result).containsExactly("2330.TW");
        }
    }

    // ========== setRelatedSymbolsList Tests ==========
    
    @Nested
    @DisplayName("setRelatedSymbolsList()")
    class SetRelatedSymbolsListTests {
        
        @Test
        @DisplayName("should set null when list is null")
        void shouldSetNullWhenListIsNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .build();
            
            news.setRelatedSymbolsList(null);
            
            assertThat(news.getRelatedSymbols()).isNull();
        }
        
        @Test
        @DisplayName("should set null when list is empty")
        void shouldSetNullWhenListIsEmpty() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .build();
            
            news.setRelatedSymbolsList(List.of());
            
            assertThat(news.getRelatedSymbols()).isNull();
        }
        
        @Test
        @DisplayName("should join list with commas")
        void shouldJoinListWithCommas() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .build();
            
            news.setRelatedSymbolsList(List.of("2330.TW", "2454.TW", "2317.TW"));
            
            assertThat(news.getRelatedSymbols()).isEqualTo("2330.TW,2454.TW,2317.TW");
        }
    }

    // ========== isBullish Tests ==========
    
    @Nested
    @DisplayName("isBullish()")
    class IsBullishTests {
        
        @Test
        @DisplayName("should return true when sentiment is above threshold")
        void shouldReturnTrueWhenAboveThreshold() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(0.5)
                    .build();
            
            assertThat(news.isBullish(0.3)).isTrue();
        }
        
        @Test
        @DisplayName("should return false when sentiment is below threshold")
        void shouldReturnFalseWhenBelowThreshold() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(0.2)
                    .build();
            
            assertThat(news.isBullish(0.3)).isFalse();
        }
        
        @Test
        @DisplayName("should return false when sentiment is null")
        void shouldReturnFalseWhenSentimentNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(null)
                    .build();
            
            assertThat(news.isBullish(0.3)).isFalse();
        }
        
        @Test
        @DisplayName("should return true when sentiment equals threshold")
        void shouldReturnTrueWhenEqualsThreshold() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(0.3)
                    .build();
            
            assertThat(news.isBullish(0.3)).isTrue();
        }
    }

    // ========== isBearish Tests ==========
    
    @Nested
    @DisplayName("isBearish()")
    class IsBearishTests {
        
        @Test
        @DisplayName("should return true when sentiment is below negative threshold")
        void shouldReturnTrueWhenBelowNegativeThreshold() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(-0.5)
                    .build();
            
            assertThat(news.isBearish(0.3)).isTrue();
        }
        
        @Test
        @DisplayName("should return false when sentiment is above negative threshold")
        void shouldReturnFalseWhenAboveNegativeThreshold() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(-0.2)
                    .build();
            
            assertThat(news.isBearish(0.3)).isFalse();
        }
        
        @Test
        @DisplayName("should return false when sentiment is null")
        void shouldReturnFalseWhenSentimentNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(null)
                    .build();
            
            assertThat(news.isBearish(0.3)).isFalse();
        }
    }

    // ========== isConfident Tests ==========
    
    @Nested
    @DisplayName("isConfident()")
    class IsConfidentTests {
        
        @Test
        @DisplayName("should return true when confidence is above minimum")
        void shouldReturnTrueWhenAboveMinimum() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentConfidence(0.9)
                    .build();
            
            assertThat(news.isConfident(0.8)).isTrue();
        }
        
        @Test
        @DisplayName("should return false when confidence is below minimum")
        void shouldReturnFalseWhenBelowMinimum() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentConfidence(0.5)
                    .build();
            
            assertThat(news.isConfident(0.8)).isFalse();
        }
        
        @Test
        @DisplayName("should return false when confidence is null")
        void shouldReturnFalseWhenConfidenceNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentConfidence(null)
                    .build();
            
            assertThat(news.isConfident(0.8)).isFalse();
        }
    }

    // ========== getAgeMinutes Tests ==========
    
    @Nested
    @DisplayName("getAgeMinutes()")
    class GetAgeMinutesTests {
        
        @Test
        @DisplayName("should return correct age in minutes")
        void shouldReturnCorrectAgeInMinutes() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now().minusMinutes(30))
                    .build();
            
            long age = news.getAgeMinutes();
            
            assertThat(age).isBetween(29L, 31L);
        }
        
        @Test
        @DisplayName("should return MAX_VALUE when publishedAt is null")
        void shouldReturnMaxValueWhenPublishedAtNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(null)
                    .build();
            
            long age = news.getAgeMinutes();
            
            assertThat(age).isEqualTo(Long.MAX_VALUE);
        }
    }

    // ========== isRecent Tests ==========
    
    @Nested
    @DisplayName("isRecent()")
    class IsRecentTests {
        
        @Test
        @DisplayName("should return true when within time window")
        void shouldReturnTrueWhenRecent() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now().minusMinutes(30))
                    .build();
            
            assertThat(news.isRecent(60)).isTrue();
        }
        
        @Test
        @DisplayName("should return false when outside time window")
        void shouldReturnFalseWhenNotRecent() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(OffsetDateTime.now().minusMinutes(90))
                    .build();
            
            assertThat(news.isRecent(60)).isFalse();
        }
        
        @Test
        @DisplayName("should return false when publishedAt is null")
        void shouldReturnFalseWhenPublishedAtNull() {
            EconomicNews news = EconomicNews.builder()
                    .headline("Test")
                    .publishedAt(null)
                    .build();
            
            assertThat(news.isRecent(60)).isFalse();
        }
    }

    // ========== Sentiment Range Tests ==========
    
    @Nested
    @DisplayName("Sentiment Score Ranges")
    class SentimentRangeTests {
        
        @Test
        @DisplayName("should accept sentiment score in valid range -1.0 to 1.0")
        void shouldAcceptValidSentimentRange() {
            EconomicNews negative = EconomicNews.builder()
                    .headline("Negative")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(-1.0)
                    .build();
            
            EconomicNews neutral = EconomicNews.builder()
                    .headline("Neutral")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(0.0)
                    .build();
            
            EconomicNews positive = EconomicNews.builder()
                    .headline("Positive")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentScore(1.0)
                    .build();
            
            assertThat(negative.getSentimentScore()).isEqualTo(-1.0);
            assertThat(neutral.getSentimentScore()).isEqualTo(0.0);
            assertThat(positive.getSentimentScore()).isEqualTo(1.0);
        }
        
        @Test
        @DisplayName("should accept confidence score in valid range 0.0 to 1.0")
        void shouldAcceptValidConfidenceRange() {
            EconomicNews lowConf = EconomicNews.builder()
                    .headline("Low")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentConfidence(0.0)
                    .build();
            
            EconomicNews highConf = EconomicNews.builder()
                    .headline("High")
                    .publishedAt(OffsetDateTime.now())
                    .sentimentConfidence(1.0)
                    .build();
            
            assertThat(lowConf.getSentimentConfidence()).isEqualTo(0.0);
            assertThat(highConf.getSentimentConfidence()).isEqualTo(1.0);
        }
    }
}
