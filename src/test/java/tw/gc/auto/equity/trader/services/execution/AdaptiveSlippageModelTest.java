package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for AdaptiveSlippageModel.
 * Phase 4: Realistic Execution Modeling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdaptiveSlippageModel Tests")
class AdaptiveSlippageModelTest {

    @Mock
    private ExecutionAnalyticsService executionAnalyticsService;
    
    private AdaptiveSlippageModel model;
    
    @BeforeEach
    void setUp() {
        when(executionAnalyticsService.getAverageSlippageForSymbol(anyString()))
            .thenReturn(OptionalDouble.empty());
        model = new AdaptiveSlippageModel(executionAnalyticsService);
    }
    
    // ==================== Base Slippage Tests ====================
    
    @Nested
    @DisplayName("Base Slippage Tests")
    class BaseSlippageTests {
        
        @Test
        @DisplayName("should return base slippage rate")
        void shouldReturnBaseSlippageRate() {
            // When
            double baseRate = model.getBaseSlippageRate();
            
            // Then
            assertThat(baseRate).isEqualTo(0.0005); // 5 bps
        }
        
        @Test
        @DisplayName("should return at least base slippage for any trade")
        void shouldReturnAtLeastBaseSlippage() {
            // Given: Optimal conditions (high volume, mid-day)
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(10_000_000) // Very high volume
                .executionTime(LocalTime.of(11, 0)) // Mid-day
                .action("BUY")
                .build();
            
            // When
            double slippage = model.estimateSlippage(context);
            
            // Then
            assertThat(slippage).isGreaterThanOrEqualTo(0.0005);
        }
    }
    
    // ==================== Volume Factor Tests ====================
    
    @Nested
    @DisplayName("Volume Factor Tests")
    class VolumeFactorTests {
        
        @Test
        @DisplayName("should add slippage for low volume stocks")
        void shouldAddSlippageForLowVolumeStocks() {
            // Given: Low volume stock
            AdaptiveSlippageModel.SlippageContext lowVolume = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("ILLIQUID")
                .orderSize(100)
                .recentVolume(50_000) // Low volume
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            AdaptiveSlippageModel.SlippageContext highVolume = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("LIQUID")
                .orderSize(100)
                .recentVolume(5_000_000) // High volume
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            // When
            double lowVolumeSlippage = model.estimateSlippage(lowVolume);
            double highVolumeSlippage = model.estimateSlippage(highVolume);
            
            // Then
            assertThat(lowVolumeSlippage).isGreaterThan(highVolumeSlippage);
        }
        
        @Test
        @DisplayName("should identify illiquid stocks")
        void shouldIdentifyIlliquidStocks() {
            // Given/When/Then
            assertThat(model.isIlliquid("LOW_VOL", 50_000)).isTrue();
            assertThat(model.isIlliquid("HIGH_VOL", 5_000_000)).isFalse();
        }
    }
    
    // ==================== Time Factor Tests ====================
    
    @Nested
    @DisplayName("Time Factor Tests")
    class TimeFactorTests {
        
        @Test
        @DisplayName("should add slippage at market open")
        void shouldAddSlippageAtMarketOpen() {
            // Given
            AdaptiveSlippageModel.SlippageContext openTime = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(9, 15)) // Market open period
                .build();
            
            AdaptiveSlippageModel.SlippageContext midDay = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(11, 0)) // Mid-day
                .build();
            
            // When
            double openSlippage = model.estimateSlippage(openTime);
            double midDaySlippage = model.estimateSlippage(midDay);
            
            // Then
            assertThat(openSlippage).isGreaterThan(midDaySlippage);
        }
        
        @Test
        @DisplayName("should add slippage at market close")
        void shouldAddSlippageAtMarketClose() {
            // Given
            AdaptiveSlippageModel.SlippageContext closeTime = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(13, 15)) // Market close period
                .build();
            
            AdaptiveSlippageModel.SlippageContext midDay = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(11, 0)) // Mid-day
                .build();
            
            // When
            double closeSlippage = model.estimateSlippage(closeTime);
            double midDaySlippage = model.estimateSlippage(midDay);
            
            // Then
            assertThat(closeSlippage).isGreaterThan(midDaySlippage);
        }
        
        @Test
        @DisplayName("should handle null execution time")
        void shouldHandleNullExecutionTime() {
            // Given
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(null)
                .build();
            
            // When/Then
            assertThatCode(() -> model.estimateSlippage(context)).doesNotThrowAnyException();
        }
    }
    
    // ==================== Size Impact Tests ====================
    
    @Nested
    @DisplayName("Order Size Impact Tests")
    class SizeImpactTests {
        
        @Test
        @DisplayName("should add slippage for large orders relative to ADV")
        void shouldAddSlippageForLargeOrders() {
            // Given: Set up ADV
            model.updateADV("2330.TW", 100_000.0);
            
            AdaptiveSlippageModel.SlippageContext smallOrder = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100) // 0.1% of ADV
                .recentVolume(100_000)
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            AdaptiveSlippageModel.SlippageContext largeOrder = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(5_000) // 5% of ADV
                .recentVolume(100_000)
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            // When
            double smallSlippage = model.estimateSlippage(smallOrder);
            double largeSlippage = model.estimateSlippage(largeOrder);
            
            // Then
            assertThat(largeSlippage).isGreaterThan(smallSlippage);
        }
        
        @Test
        @DisplayName("should cache and retrieve ADV")
        void shouldCacheAndRetrieveADV() {
            // Given
            model.updateADV("2330.TW", 500_000.0);
            
            // When
            Optional<Double> adv = model.getADV("2330.TW");
            
            // Then
            assertThat(adv).isPresent();
            assertThat(adv.get()).isEqualTo(500_000.0);
        }
    }
    
    // ==================== Historical Adjustment Tests ====================
    
    @Nested
    @DisplayName("Historical Adjustment Tests")
    class HistoricalAdjustmentTests {
        
        @Test
        @DisplayName("should blend with historical slippage when available")
        void shouldBlendWithHistoricalSlippage() {
            // Given: Mock historical slippage
            when(executionAnalyticsService.getAverageSlippageForSymbol("2330.TW"))
                .thenReturn(OptionalDouble.of(20.0)); // 20 bps historical
            
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            // When
            double slippage = model.estimateSlippage(context);
            
            // Then
            // Should be blended: 70% historical (20 bps) + 30% estimated (~5 bps)
            // Result should be closer to historical
            double slippageBps = slippage * 10000;
            assertThat(slippageBps).isGreaterThan(10.0); // More than base due to historical
        }
    }
    
    // ==================== Slippage Breakdown Tests ====================
    
    @Nested
    @DisplayName("Slippage Breakdown Tests")
    class SlippageBreakdownTests {
        
        @Test
        @DisplayName("should provide detailed breakdown")
        void shouldProvideDetailedBreakdown() {
            // Given
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(100_000) // Low volume
                .executionTime(LocalTime.of(9, 15)) // Market open
                .build();
            
            // When
            AdaptiveSlippageModel.SlippageBreakdown breakdown = model.getSlippageBreakdown(context);
            
            // Then
            assertThat(breakdown.getBaseSlippageBps()).isEqualTo(5.0);
            assertThat(breakdown.getTotalSlippageBps()).isGreaterThan(5.0);
            assertThat(breakdown.getTotalSlippageRate()).isGreaterThan(0.0005);
            assertThat(breakdown.getPrimaryFactor()).isNotNull();
        }
        
        @Test
        @DisplayName("should identify primary slippage factor")
        void shouldIdentifyPrimaryFactor() {
            // Given: Low volume should be primary factor
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("ILLIQUID")
                .orderSize(100)
                .recentVolume(10_000) // Very low volume
                .executionTime(LocalTime.of(11, 0)) // Normal time
                .build();
            
            // When
            AdaptiveSlippageModel.SlippageBreakdown breakdown = model.getSlippageBreakdown(context);
            
            // Then
            assertThat(breakdown.getPrimaryFactor()).isEqualTo("LOW_VOLUME");
        }
        
        @Test
        @DisplayName("should check if slippage is acceptable")
        void shouldCheckIfSlippageIsAcceptable() {
            // Given
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(5_000_000)
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            AdaptiveSlippageModel.SlippageBreakdown breakdown = model.getSlippageBreakdown(context);
            
            // Then
            assertThat(breakdown.isAcceptable(10.0)).isTrue(); // 10 bps threshold
            assertThat(breakdown.isAcceptable(2.0)).isFalse(); // 2 bps threshold (too strict)
        }
    }
    
    // ==================== Transaction Cost Tests ====================
    
    @Nested
    @DisplayName("Transaction Cost Tests")
    class TransactionCostTests {
        
        @Test
        @DisplayName("should calculate total transaction cost for buy")
        void shouldCalculateTotalCostForBuy() {
            // Given
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            // When
            double totalCost = model.estimateTotalTransactionCost(context, 600.0, true);
            
            // Then
            // Slippage (~0.05%) + Fee (0.1425%) = ~0.19%
            assertThat(totalCost).isBetween(0.001, 0.005);
        }
        
        @Test
        @DisplayName("should include tax for sell")
        void shouldIncludeTaxForSell() {
            // Given
            AdaptiveSlippageModel.SlippageContext context = AdaptiveSlippageModel.SlippageContext.builder()
                .symbol("2330.TW")
                .orderSize(100)
                .recentVolume(1_000_000)
                .executionTime(LocalTime.of(11, 0))
                .build();
            
            // When
            double buyCost = model.estimateTotalTransactionCost(context, 600.0, true);
            double sellCost = model.estimateTotalTransactionCost(context, 600.0, false);
            
            // Then: Sell includes 0.3% tax
            assertThat(sellCost).isGreaterThan(buyCost);
            assertThat(sellCost - buyCost).isCloseTo(0.003, within(0.0005)); // ~0.3% tax difference
        }
    }
    
    // ==================== Simplified API Tests ====================
    
    @Nested
    @DisplayName("Simplified API Tests")
    class SimplifiedAPITests {
        
        @Test
        @DisplayName("should estimate slippage with minimal parameters")
        void shouldEstimateWithMinimalParams() {
            // When
            double slippage = model.estimateSlippage("2330.TW", 100, 1_000_000);
            
            // Then
            assertThat(slippage).isGreaterThanOrEqualTo(0.0005);
        }
    }
}
