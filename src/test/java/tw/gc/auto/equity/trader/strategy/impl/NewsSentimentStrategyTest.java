package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.*;
import tw.gc.auto.equity.trader.entities.EconomicNews;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.*;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for NewsSentimentStrategy.
 * Tests sentiment-based trading signal generation.
 * 
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@DisplayName("NewsSentimentStrategy Unit Tests")
class NewsSentimentStrategyTest {

    private Portfolio portfolio;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        marketData = MarketData.builder()
                .symbol("2330.TW")
                .close(500.0)
                .high(510.0)
                .low(495.0)
                .volume(10000L)
                .build();
    }

    // ========== Constructor Tests ==========
    
    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        
        @Test
        @DisplayName("should create with default parameters")
        void shouldCreateWithDefaults() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy();
            
            assertThat(strategy.getSentimentThreshold()).isEqualTo(0.3);
            assertThat(strategy.getMinNewsCount()).isEqualTo(2);
            assertThat(strategy.getHalfLifeMinutes()).isEqualTo(240.0);
        }
        
        @Test
        @DisplayName("should create with custom threshold and provider")
        void shouldCreateWithCustomThresholdAndProvider() {
            NewsSentimentProvider provider = symbol -> List.of();
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.5, provider);
            
            assertThat(strategy.getSentimentThreshold()).isEqualTo(0.5);
        }
        
        @Test
        @DisplayName("should create with full customization")
        void shouldCreateWithFullCustomization() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(
                    0.4, 3, 120.0, NewsSentimentProvider.noOp());
            
            assertThat(strategy.getSentimentThreshold()).isEqualTo(0.4);
            assertThat(strategy.getMinNewsCount()).isEqualTo(3);
            assertThat(strategy.getHalfLifeMinutes()).isEqualTo(120.0);
        }
    }

    // ========== Execute Tests - Null/Missing Data ==========
    
    @Nested
    @DisplayName("execute() - Null/Missing Data")
    class ExecuteNullDataTests {
        
        @Test
        @DisplayName("should return neutral when market data is null")
        void shouldReturnNeutralWhenMarketDataNull() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy();
            
            TradeSignal signal = strategy.execute(portfolio, null);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
            assertThat(signal.getReason()).isEqualTo("No market data");
        }
        
        @Test
        @DisplayName("should return neutral when no news available")
        void shouldReturnNeutralWhenNoNews() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(
                    0.3, NewsSentimentProvider.noOp());
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
            assertThat(signal.getReason()).contains("No news data");
        }
        
        @Test
        @DisplayName("should return neutral when insufficient news count")
        void shouldReturnNeutralWhenInsufficientNews() {
            // Only 1 article, but minimum is 2
            NewsSentimentProvider provider = symbol -> List.of(
                    createNews(0.8)
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
            assertThat(signal.getReason()).contains("Insufficient news");
        }
    }

    // ========== Execute Tests - Bullish Signals ==========
    
    @Nested
    @DisplayName("execute() - Bullish Signals")
    class ExecuteBullishTests {
        
        @Test
        @DisplayName("should return LONG when sentiment is strongly positive")
        void shouldReturnLongWhenBullish() {
            NewsSentimentProvider provider = symbol -> List.of(
                    createNewsWithTime(0.7, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(0.8, OffsetDateTime.now().minusHours(1))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.LONG);
            assertThat(signal.getReason()).contains("Sentiment=");
        }
        
        @Test
        @DisplayName("should lower threshold for high-impact news")
        void shouldLowerThresholdForHighImpactNews() {
            // Sentiment 0.25 is below normal threshold 0.3, but high-impact lowers it to 0.21
            EconomicNews highImpact = createNewsWithTime(0.25, OffsetDateTime.now().minusMinutes(30));
            highImpact.setImpactLevel("HIGH");
            
            NewsSentimentProvider provider = symbol -> List.of(
                    highImpact,
                    createNewsWithTime(0.25, OffsetDateTime.now().minusHours(1))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.LONG);
            assertThat(signal.getReason()).contains("HIGH_IMPACT");
        }
        
        @Test
        @DisplayName("should mark breaking news in reason")
        void shouldMarkBreakingNewsInReason() {
            EconomicNews breaking = createNewsWithTime(0.8, OffsetDateTime.now().minusMinutes(10));
            breaking.setIsBreaking(true);
            
            NewsSentimentProvider provider = symbol -> List.of(
                    breaking,
                    createNewsWithTime(0.6, OffsetDateTime.now().minusHours(1))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getReason()).contains("BREAKING");
        }
    }

    // ========== Execute Tests - Bearish Signals ==========
    
    @Nested
    @DisplayName("execute() - Bearish Signals")
    class ExecuteBearishTests {
        
        @Test
        @DisplayName("should return SHORT when sentiment is strongly negative")
        void shouldReturnShortWhenBearish() {
            NewsSentimentProvider provider = symbol -> List.of(
                    createNewsWithTime(-0.7, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(-0.8, OffsetDateTime.now().minusHours(1))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.SHORT);
            assertThat(signal.getReason()).contains("Sentiment=");
        }
        
        @Test
        @DisplayName("should use absolute value for confidence")
        void shouldUseAbsoluteValueForConfidence() {
            NewsSentimentProvider provider = symbol -> List.of(
                    createNewsWithTime(-0.6, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(-0.5, OffsetDateTime.now().minusHours(1))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            // Confidence should be positive (absolute value of sentiment)
            assertThat(signal.getConfidence()).isPositive();
        }
    }

    // ========== Execute Tests - Neutral Signals ==========
    
    @Nested
    @DisplayName("execute() - Neutral Signals")
    class ExecuteNeutralTests {
        
        @Test
        @DisplayName("should return NEUTRAL when sentiment is below threshold")
        void shouldReturnNeutralWhenSentimentBelowThreshold() {
            NewsSentimentProvider provider = symbol -> List.of(
                    createNewsWithTime(0.1, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(0.15, OffsetDateTime.now().minusHours(1))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
            assertThat(signal.getReason()).contains("Neutral sentiment");
        }
        
        @Test
        @DisplayName("should return NEUTRAL when mixed sentiment cancels out")
        void shouldReturnNeutralWhenMixedSentiment() {
            NewsSentimentProvider provider = symbol -> List.of(
                    createNewsWithTime(0.5, OffsetDateTime.now().minusMinutes(30)),
                    createNewsWithTime(-0.5, OffsetDateTime.now().minusMinutes(30))
            );
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, provider);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        }
    }

    // ========== Metadata Tests ==========
    
    @Nested
    @DisplayName("Metadata")
    class MetadataTests {
        
        @Test
        @DisplayName("should return correct name")
        void shouldReturnCorrectName() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy();
            assertThat(strategy.getName()).isEqualTo("News / Sentiment-Based Trading");
        }
        
        @Test
        @DisplayName("should return SHORT_TERM type")
        void shouldReturnShortTermType() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy();
            assertThat(strategy.getType()).isEqualTo(StrategyType.SHORT_TERM);
        }
    }

    // ========== Backward Compatibility Tests ==========
    
    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTests {
        
        @Test
        @DisplayName("should work with default constructor (no provider)")
        void shouldWorkWithDefaultConstructor() {
            // Default constructor uses noOp provider - should not throw
            NewsSentimentStrategy strategy = new NewsSentimentStrategy();
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        }
        
        @Test
        @DisplayName("should handle null provider gracefully")
        void shouldHandleNullProviderGracefully() {
            NewsSentimentStrategy strategy = new NewsSentimentStrategy(0.3, 2, 240.0, null);
            
            TradeSignal signal = strategy.execute(portfolio, marketData);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        }
    }

    // ========== Helper Methods ==========
    
    private EconomicNews createNews(Double sentiment) {
        return createNewsWithTime(sentiment, OffsetDateTime.now().minusHours(1));
    }
    
    private EconomicNews createNewsWithTime(Double sentiment, OffsetDateTime publishedAt) {
        return EconomicNews.builder()
                .primarySymbol("2330.TW")
                .headline("Test headline")
                .sentimentScore(sentiment)
                .sentimentConfidence(0.9)
                .publishedAt(publishedAt)
                .fetchedAt(OffsetDateTime.now())
                .impactLevel("MEDIUM")
                .isProcessed(true)
                .isBreaking(false)
                .isEarningsRelated(false)
                .build();
    }
}
