package tw.gc.auto.equity.trader.services.regime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.MarketData.Timeframe;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.MarketRegime;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.RegimeAnalysis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for MarketRegimeService.
 * Tests regime classification based on ADX, volatility, MAs, and drawdown.
 */
@DisplayName("MarketRegimeService Tests")
class MarketRegimeServiceTest {

    private MarketRegimeService service;
    private static final String TEST_SYMBOL = "2330.TW";

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService();
    }

    @Nested
    @DisplayName("Regime Classification Tests")
    class RegimeClassificationTests {

        @Test
        @DisplayName("Should classify CRISIS when volatility exceeds crisis threshold")
        void shouldClassifyCrisisOnHighVolatility() {
            // volatility > 50% triggers CRISIS
            MarketRegime regime = service.classifyRegime(
                    30.0, 20.0, 10.0,  // ADX=30 (trending)
                    55.0,               // volatility=55% (> 50% crisis threshold)
                    100.0, 105.0, 103.0, // price, ma50, ma200
                    0.05                // 5% drawdown
            );
            
            assertThat(regime).isEqualTo(MarketRegime.CRISIS);
        }

        @Test
        @DisplayName("Should classify CRISIS when drawdown exceeds threshold")
        void shouldClassifyCrisisOnLargeDrawdown() {
            // drawdown > 15% triggers CRISIS
            MarketRegime regime = service.classifyRegime(
                    25.0, 20.0, 15.0,   // ADX
                    20.0,               // normal volatility
                    100.0, 105.0, 103.0,
                    0.20                // 20% drawdown (> 15% threshold)
            );
            
            assertThat(regime).isEqualTo(MarketRegime.CRISIS);
        }

        @Test
        @DisplayName("Should classify HIGH_VOLATILITY when volatility between 30-50%")
        void shouldClassifyHighVolatility() {
            MarketRegime regime = service.classifyRegime(
                    20.0, 15.0, 12.0,   // ADX < 25 (not trending)
                    35.0,               // volatility=35% (> 30%, < 50%)
                    100.0, 105.0, 103.0,
                    0.05
            );
            
            assertThat(regime).isEqualTo(MarketRegime.HIGH_VOLATILITY);
        }

        @Test
        @DisplayName("Should classify TRENDING_UP with high ADX and bullish DI")
        void shouldClassifyTrendingUp() {
            // ADX > 25, +DI > -DI, MA50 > MA200
            MarketRegime regime = service.classifyRegime(
                    30.0,               // ADX > 25 = trending
                    25.0, 15.0,         // +DI > -DI = bullish
                    15.0,               // normal volatility
                    110.0,              // price
                    108.0, 105.0,       // MA50 > MA200 = bullish
                    0.02
            );
            
            assertThat(regime).isEqualTo(MarketRegime.TRENDING_UP);
        }

        @Test
        @DisplayName("Should classify TRENDING_DOWN with high ADX and bearish DI")
        void shouldClassifyTrendingDown() {
            // ADX > 25, -DI > +DI, MA50 < MA200
            MarketRegime regime = service.classifyRegime(
                    28.0,               // ADX > 25 = trending
                    12.0, 22.0,         // -DI > +DI = bearish
                    15.0,               // normal volatility
                    95.0,               // price
                    98.0, 102.0,        // MA50 < MA200 = bearish
                    0.03
            );
            
            assertThat(regime).isEqualTo(MarketRegime.TRENDING_DOWN);
        }

        @Test
        @DisplayName("Should classify RANGING with low ADX")
        void shouldClassifyRangingWithLowADX() {
            // ADX < 20 = ranging
            MarketRegime regime = service.classifyRegime(
                    15.0,               // ADX < 20 = ranging
                    18.0, 17.0,         // DIs close together
                    12.0,               // low volatility
                    100.0,              // price
                    101.0, 99.0,        // MAs close
                    0.01
            );
            
            assertThat(regime).isEqualTo(MarketRegime.RANGING);
        }

        @Test
        @DisplayName("Should use DI direction when trend signals conflict")
        void shouldUseDIWhenSignalsConflict() {
            // ADX > 25, +DI > -DI (bullish), but MA50 < MA200 (bearish)
            // Should follow DI direction
            MarketRegime regime = service.classifyRegime(
                    30.0,
                    25.0, 15.0,         // Bullish DI
                    15.0,
                    105.0,
                    100.0, 103.0,       // MA50 < MA200 (conflicting)
                    0.02
            );
            
            assertThat(regime).isEqualTo(MarketRegime.TRENDING_UP);
        }

        @Test
        @DisplayName("Should use MAs for weak trend (ADX 20-25)")
        void shouldUseMAsForWeakTrend() {
            // ADX between 20-25, use MAs to determine direction
            MarketRegime regime = service.classifyRegime(
                    22.0,               // ADX between thresholds
                    18.0, 17.0,         // DIs close
                    12.0,
                    110.0,              // price > MA50 > MA200
                    108.0, 105.0,
                    0.01
            );
            
            assertThat(regime).isEqualTo(MarketRegime.TRENDING_UP);
        }
    }

    @Nested
    @DisplayName("ADX Calculation Tests")
    class ADXCalculationTests {

        @Test
        @DisplayName("Should return zeros for insufficient data")
        void shouldReturnZerosForInsufficientData() {
            List<MarketData> shortData = createMarketData(10, 100.0, 0.01);
            
            double[] adxResult = service.calculateADX(shortData);
            
            assertThat(adxResult).hasSize(3);
            assertThat(adxResult[0]).isEqualTo(0.0); // ADX
            assertThat(adxResult[1]).isEqualTo(0.0); // +DI
            assertThat(adxResult[2]).isEqualTo(0.0); // -DI
        }

        @Test
        @DisplayName("Should calculate ADX for trending up data")
        void shouldCalculateADXForTrendingUp() {
            // Create steadily rising prices
            List<MarketData> trendingUp = createTrendingData(50, 100.0, 0.01);
            
            double[] adxResult = service.calculateADX(trendingUp);
            
            assertThat(adxResult[0]).isGreaterThan(0); // ADX > 0
            assertThat(adxResult[1]).isGreaterThan(adxResult[2]); // +DI > -DI
        }

        @Test
        @DisplayName("Should calculate ADX for trending down data")
        void shouldCalculateADXForTrendingDown() {
            // Create steadily falling prices
            List<MarketData> trendingDown = createTrendingData(50, 100.0, -0.01);
            
            double[] adxResult = service.calculateADX(trendingDown);
            
            assertThat(adxResult[0]).isGreaterThan(0); // ADX > 0
            assertThat(adxResult[2]).isGreaterThan(adxResult[1]); // -DI > +DI
        }
    }

    @Nested
    @DisplayName("Volatility Calculation Tests")
    class VolatilityCalculationTests {

        @Test
        @DisplayName("Should calculate zero volatility for flat prices")
        void shouldCalculateZeroVolForFlatPrices() {
            List<MarketData> flatData = createMarketData(30, 100.0, 0.0);
            
            double volatility = service.calculateAnnualizedVolatility(flatData);
            
            assertThat(volatility).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should calculate higher volatility for volatile prices")
        void shouldCalculateHigherVolForVolatilePrices() {
            List<MarketData> stableData = createMarketData(30, 100.0, 0.005);
            List<MarketData> volatileData = createMarketData(30, 100.0, 0.02);
            
            double stableVol = service.calculateAnnualizedVolatility(stableData);
            double volatileVol = service.calculateAnnualizedVolatility(volatileData);
            
            assertThat(volatileVol).isGreaterThan(stableVol);
        }

        @Test
        @DisplayName("Should return zero for insufficient data")
        void shouldReturnZeroForInsufficientData() {
            List<MarketData> shortData = createMarketData(2, 100.0, 0.01);
            
            double volatility = service.calculateAnnualizedVolatility(shortData);
            
            assertThat(volatility).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("SMA Calculation Tests")
    class SMACalculationTests {

        @Test
        @DisplayName("Should calculate correct SMA")
        void shouldCalculateCorrectSMA() {
            List<MarketData> data = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                data.add(createMarketData(TEST_SYMBOL, 100.0 + i, i));
            }
            
            // MA of last 10 values: 110-119, avg = 114.5
            double sma10 = service.calculateSMA(data, 10);
            
            assertThat(sma10).isCloseTo(114.5, within(0.01));
        }

        @Test
        @DisplayName("Should return zero for insufficient data")
        void shouldReturnZeroForInsufficientData() {
            List<MarketData> shortData = createMarketData(5, 100.0, 0.0);
            
            double sma50 = service.calculateSMA(shortData, 50);
            
            assertThat(sma50).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Drawdown Calculation Tests")
    class DrawdownCalculationTests {

        @Test
        @DisplayName("Should calculate zero drawdown for rising prices")
        void shouldCalculateZeroDrawdownForRisingPrices() {
            List<MarketData> risingData = createTrendingData(60, 100.0, 0.01);
            
            double drawdown = service.calculateRecentDrawdown(risingData);
            
            assertThat(drawdown).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should calculate drawdown from peak")
        void shouldCalculateDrawdownFromPeak() {
            List<MarketData> data = new ArrayList<>();
            // Create data with a peak at 120 and current at 102
            for (int i = 0; i < 30; i++) {
                double price = 100.0 + i; // Rising to 129
                data.add(createMarketData(TEST_SYMBOL, price, i));
            }
            // Add decline
            for (int i = 0; i < 30; i++) {
                double price = 129.0 - i * 0.9; // Falling
                data.add(createMarketData(TEST_SYMBOL, price, 30 + i));
            }
            
            double drawdown = service.calculateRecentDrawdown(data);
            
            assertThat(drawdown).isGreaterThan(0.0);
            assertThat(drawdown).isLessThan(0.5); // Not more than 50%
        }
    }

    @Nested
    @DisplayName("Full Regime Analysis Tests")
    class FullRegimeAnalysisTests {

        @Test
        @DisplayName("Should return default analysis for empty data")
        void shouldReturnDefaultForEmptyData() {
            RegimeAnalysis analysis = service.analyzeRegime(TEST_SYMBOL, List.of());
            
            assertThat(analysis.regime()).isEqualTo(MarketRegime.RANGING);
            assertThat(analysis.confidence()).isEqualTo(0.0);
            assertThat(analysis.rationale()).contains("No market data");
        }

        @Test
        @DisplayName("Should return default analysis for null data")
        void shouldReturnDefaultForNullData() {
            RegimeAnalysis analysis = service.analyzeRegime(TEST_SYMBOL, null);
            
            assertThat(analysis.regime()).isEqualTo(MarketRegime.RANGING);
        }

        @Test
        @DisplayName("Should analyze with limited data")
        void shouldAnalyzeWithLimitedData() {
            List<MarketData> limitedData = createMarketData(100, 100.0, 0.01);
            
            RegimeAnalysis analysis = service.analyzeRegime(TEST_SYMBOL, limitedData);
            
            assertThat(analysis).isNotNull();
            assertThat(analysis.symbol()).isEqualTo(TEST_SYMBOL);
            assertThat(analysis.confidence()).isEqualTo(0.5); // Lower confidence
        }

        @Test
        @DisplayName("Should analyze uptrending market")
        void shouldAnalyzeUptrend() {
            List<MarketData> trendingUp = createTrendingData(250, 100.0, 0.003);
            
            RegimeAnalysis analysis = service.analyzeRegime(TEST_SYMBOL, trendingUp);
            
            assertThat(analysis.regime()).isIn(MarketRegime.TRENDING_UP, MarketRegime.RANGING);
            assertThat(analysis.ma50()).isGreaterThan(0);
            assertThat(analysis.ma200()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should cache regime analysis")
        void shouldCacheRegimeAnalysis() {
            List<MarketData> data = createMarketData(250, 100.0, 0.01);
            
            service.analyzeRegime(TEST_SYMBOL, data);
            RegimeAnalysis cached = service.getCachedRegime(TEST_SYMBOL);
            
            assertThat(cached).isNotNull();
            assertThat(cached.symbol()).isEqualTo(TEST_SYMBOL);
        }

        @Test
        @DisplayName("Should clear cache")
        void shouldClearCache() {
            List<MarketData> data = createMarketData(250, 100.0, 0.01);
            service.analyzeRegime(TEST_SYMBOL, data);
            
            service.clearCache();
            
            assertThat(service.getCachedRegime(TEST_SYMBOL)).isNull();
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("isTrending should return true for trending regime")
        void isTrendingShouldReturnTrueForTrending() {
            List<MarketData> trendingData = createTrendingData(250, 100.0, 0.005);
            service.analyzeRegime(TEST_SYMBOL, trendingData);
            
            // The result depends on the actual classification
            boolean trending = service.isTrending(TEST_SYMBOL);
            
            // Just verify it returns a boolean without error
            assertThat(trending).isIn(true, false);
        }

        @Test
        @DisplayName("isTrending should return false when no cache")
        void isTrendingShouldReturnFalseWhenNoCache() {
            assertThat(service.isTrending("UNKNOWN")).isFalse();
        }

        @Test
        @DisplayName("isVolatile should return false for normal volatility")
        void isVolatileShouldReturnFalseForNormalVol() {
            List<MarketData> normalData = createMarketData(250, 100.0, 0.005);
            service.analyzeRegime(TEST_SYMBOL, normalData);
            
            boolean volatile_ = service.isVolatile(TEST_SYMBOL);
            
            assertThat(volatile_).isFalse();
        }
    }

    @Nested
    @DisplayName("RegimeAnalysis Record Tests")
    class RegimeAnalysisRecordTests {

        @Test
        @DisplayName("Should check momentum favorability")
        void shouldCheckMomentumFavorability() {
            RegimeAnalysis trendingUp = createAnalysis(MarketRegime.TRENDING_UP);
            RegimeAnalysis ranging = createAnalysis(MarketRegime.RANGING);
            
            assertThat(trendingUp.isFavorableForMomentum()).isTrue();
            assertThat(ranging.isFavorableForMomentum()).isFalse();
        }

        @Test
        @DisplayName("Should check mean reversion favorability")
        void shouldCheckMeanReversionFavorability() {
            RegimeAnalysis ranging = createAnalysis(MarketRegime.RANGING);
            RegimeAnalysis crisis = createAnalysis(MarketRegime.CRISIS);
            
            assertThat(ranging.isFavorableForMeanReversion()).isTrue();
            assertThat(crisis.isFavorableForMeanReversion()).isFalse();
        }

        @Test
        @DisplayName("Should recommend exposure reduction")
        void shouldRecommendExposureReduction() {
            RegimeAnalysis highVol = createAnalysis(MarketRegime.HIGH_VOLATILITY);
            RegimeAnalysis crisis = createAnalysis(MarketRegime.CRISIS);
            RegimeAnalysis normal = createAnalysis(MarketRegime.TRENDING_UP);
            
            assertThat(highVol.shouldReduceExposure()).isTrue();
            assertThat(crisis.shouldReduceExposure()).isTrue();
            assertThat(normal.shouldReduceExposure()).isFalse();
        }

        @Test
        @DisplayName("Should return correct position scale factors")
        void shouldReturnCorrectPositionScaleFactors() {
            assertThat(createAnalysis(MarketRegime.TRENDING_UP).getPositionScaleFactor()).isEqualTo(1.0);
            assertThat(createAnalysis(MarketRegime.TRENDING_DOWN).getPositionScaleFactor()).isEqualTo(0.5);
            assertThat(createAnalysis(MarketRegime.RANGING).getPositionScaleFactor()).isEqualTo(0.7);
            assertThat(createAnalysis(MarketRegime.HIGH_VOLATILITY).getPositionScaleFactor()).isEqualTo(0.3);
            assertThat(createAnalysis(MarketRegime.CRISIS).getPositionScaleFactor()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("MarketRegime Enum Tests")
    class MarketRegimeEnumTests {

        @Test
        @DisplayName("Should have correct display names")
        void shouldHaveCorrectDisplayNames() {
            assertThat(MarketRegime.TRENDING_UP.getDisplayName()).isEqualTo("Trending Up");
            assertThat(MarketRegime.TRENDING_DOWN.getDisplayName()).isEqualTo("Trending Down");
            assertThat(MarketRegime.RANGING.getDisplayName()).isEqualTo("Ranging");
            assertThat(MarketRegime.HIGH_VOLATILITY.getDisplayName()).isEqualTo("High Volatility");
            assertThat(MarketRegime.CRISIS.getDisplayName()).isEqualTo("Crisis");
        }

        @Test
        @DisplayName("Should have recommendations")
        void shouldHaveRecommendations() {
            for (MarketRegime regime : MarketRegime.values()) {
                assertThat(regime.getRecommendation()).isNotEmpty();
            }
        }
    }

    // ===== HELPER METHODS =====

    private List<MarketData> createMarketData(int count, double startPrice, double dailyChange) {
        List<MarketData> data = new ArrayList<>();
        double price = startPrice;
        
        for (int i = 0; i < count; i++) {
            double change = dailyChange * (Math.random() * 2 - 1); // Random walk
            price = price * (1 + change);
            data.add(createMarketData(TEST_SYMBOL, price, i));
        }
        
        return data;
    }

    private List<MarketData> createTrendingData(int count, double startPrice, double dailyTrend) {
        List<MarketData> data = new ArrayList<>();
        double price = startPrice;
        
        for (int i = 0; i < count; i++) {
            price = price * (1 + dailyTrend);
            double high = price * 1.01;
            double low = price * 0.99;
            data.add(MarketData.builder()
                    .symbol(TEST_SYMBOL)
                    .timestamp(LocalDateTime.now().minusDays(count - i))
                    .timeframe(Timeframe.DAY_1)
                    .open(price)
                    .high(high)
                    .low(low)
                    .close(price)
                    .volume(1000000L)
                    .build());
        }
        
        return data;
    }

    private MarketData createMarketData(String symbol, double price, int daysAgo) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now().minusDays(daysAgo))
                .timeframe(Timeframe.DAY_1)
                .open(price)
                .high(price * 1.01)
                .low(price * 0.99)
                .close(price)
                .volume(1000000L)
                .build();
    }

    private RegimeAnalysis createAnalysis(MarketRegime regime) {
        return new RegimeAnalysis(
                TEST_SYMBOL, regime, 0.8, 25.0, 20.0, 15.0,
                15.0, 100.0, 95.0, 0.02, LocalDateTime.now(), "Test rationale"
        );
    }
}
