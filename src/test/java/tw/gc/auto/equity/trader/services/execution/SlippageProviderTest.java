package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.*;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SlippageProvider functional interface.
 * Phase 4: Realistic Execution Modeling
 */
@DisplayName("SlippageProvider Tests")
class SlippageProviderTest {

    // ==================== Fixed Provider Tests ====================
    
    @Nested
    @DisplayName("Fixed Slippage Provider Tests")
    class FixedProviderTests {
        
        @Test
        @DisplayName("should always return fixed rate")
        void shouldAlwaysReturnFixedRate() {
            // Given
            SlippageProvider provider = SlippageProvider.fixed(0.001); // 10 bps
            
            // When
            double slippage = provider.calculateSlippage(createContext("2330.TW", 100, 1_000_000));
            
            // Then
            assertThat(slippage).isEqualTo(0.001);
        }
        
        @Test
        @DisplayName("should ignore context for fixed rate")
        void shouldIgnoreContextForFixedRate() {
            // Given
            SlippageProvider provider = SlippageProvider.fixed(0.0005);
            
            // When
            double lowVolume = provider.calculateSlippage(createContext("A", 100, 1000));
            double highVolume = provider.calculateSlippage(createContext("B", 100, 10_000_000));
            
            // Then
            assertThat(lowVolume).isEqualTo(highVolume);
        }
    }
    
    // ==================== Volume-Aware Provider Tests ====================
    
    @Nested
    @DisplayName("Volume-Aware Provider Tests")
    class VolumeAwareProviderTests {
        
        @Test
        @DisplayName("should return base rate for high volume")
        void shouldReturnBaseRateForHighVolume() {
            // Given
            SlippageProvider provider = SlippageProvider.volumeAware(0.0005, 100_000, 0.002);
            
            // When
            double slippage = provider.calculateSlippage(createContext("HIGH_VOL", 100, 500_000));
            
            // Then
            assertThat(slippage).isEqualTo(0.0005);
        }
        
        @Test
        @DisplayName("should interpolate for low volume")
        void shouldInterpolateForLowVolume() {
            // Given
            double baseRate = 0.0005;
            double lowVolumeRate = 0.002;
            long threshold = 100_000;
            SlippageProvider provider = SlippageProvider.volumeAware(baseRate, threshold, lowVolumeRate);
            
            // When: Volume is 50% of threshold
            double slippage = provider.calculateSlippage(createContext("MID_VOL", 100, 50_000));
            
            // Then: Should be halfway between base and low volume rate
            double expected = baseRate + (lowVolumeRate - baseRate) * 0.5;
            assertThat(slippage).isCloseTo(expected, within(0.0001));
        }
        
        @Test
        @DisplayName("should return max rate for very low volume")
        void shouldReturnMaxRateForVeryLowVolume() {
            // Given
            SlippageProvider provider = SlippageProvider.volumeAware(0.0005, 100_000, 0.002);
            
            // When: Volume is 0
            double slippage = provider.calculateSlippage(createContext("ILLIQUID", 100, 0));
            
            // Then
            assertThat(slippage).isEqualTo(0.002);
        }
    }
    
    // ==================== Capped Provider Tests ====================
    
    @Nested
    @DisplayName("Capped Provider Tests")
    class CappedProviderTests {
        
        @Test
        @DisplayName("should cap slippage at maximum")
        void shouldCapSlippageAtMaximum() {
            // Given: Provider that returns high slippage
            SlippageProvider base = context -> 0.01; // 1%
            SlippageProvider capped = base.capped(0.005); // Cap at 0.5%
            
            // When
            double slippage = capped.calculateSlippage(createContext("X", 100, 1000));
            
            // Then
            assertThat(slippage).isEqualTo(0.005);
        }
        
        @Test
        @DisplayName("should not modify slippage below cap")
        void shouldNotModifySlippageBelowCap() {
            // Given
            SlippageProvider base = context -> 0.001;
            SlippageProvider capped = base.capped(0.005);
            
            // When
            double slippage = capped.calculateSlippage(createContext("X", 100, 1000));
            
            // Then
            assertThat(slippage).isEqualTo(0.001);
        }
    }
    
    // ==================== Floored Provider Tests ====================
    
    @Nested
    @DisplayName("Floored Provider Tests")
    class FlooredProviderTests {
        
        @Test
        @DisplayName("should floor slippage at minimum")
        void shouldFloorSlippageAtMinimum() {
            // Given: Provider that returns low slippage
            SlippageProvider base = context -> 0.0001;
            SlippageProvider floored = base.floored(0.0005);
            
            // When
            double slippage = floored.calculateSlippage(createContext("X", 100, 1000));
            
            // Then
            assertThat(slippage).isEqualTo(0.0005);
        }
        
        @Test
        @DisplayName("should not modify slippage above floor")
        void shouldNotModifySlippageAboveFloor() {
            // Given
            SlippageProvider base = context -> 0.001;
            SlippageProvider floored = base.floored(0.0005);
            
            // When
            double slippage = floored.calculateSlippage(createContext("X", 100, 1000));
            
            // Then
            assertThat(slippage).isEqualTo(0.001);
        }
    }
    
    // ==================== Context Factory Tests ====================
    
    @Nested
    @DisplayName("SlippageContext Factory Tests")
    class ContextFactoryTests {
        
        @Test
        @DisplayName("should create context from market data")
        void shouldCreateContextFromMarketData() {
            // Given
            MarketData data = MarketData.builder()
                .symbol("2330.TW")
                .timestamp(LocalDateTime.of(2026, 1, 15, 10, 30))
                .close(600.0)
                .volume(1_000_000)
                .build();
            
            // When
            SlippageProvider.SlippageContext context = 
                SlippageProvider.SlippageContext.fromMarketData(data, 100, "BUY");
            
            // Then
            assertThat(context.symbol()).isEqualTo("2330.TW");
            assertThat(context.orderSize()).isEqualTo(100);
            assertThat(context.recentVolume()).isEqualTo(1_000_000);
            assertThat(context.executionTime()).isEqualTo(LocalTime.of(10, 30));
            assertThat(context.action()).isEqualTo("BUY");
            assertThat(context.price()).isEqualTo(600.0);
        }
        
        @Test
        @DisplayName("should create minimal context")
        void shouldCreateMinimalContext() {
            // When
            SlippageProvider.SlippageContext context = 
                SlippageProvider.SlippageContext.minimal("2330.TW", 600.0);
            
            // Then
            assertThat(context.symbol()).isEqualTo("2330.TW");
            assertThat(context.price()).isEqualTo(600.0);
            assertThat(context.orderSize()).isEqualTo(0);
            assertThat(context.recentVolume()).isEqualTo(0);
            assertThat(context.executionTime()).isNull();
            assertThat(context.action()).isNull();
        }
    }
    
    // ==================== Composite Provider Tests ====================
    
    @Nested
    @DisplayName("Composite Provider Tests")
    class CompositeProviderTests {
        
        @Test
        @DisplayName("should chain capped and floored")
        void shouldChainCappedAndFloored() {
            // Given: Custom provider chained with cap and floor
            SlippageProvider base = context -> 
                context.recentVolume() < 10000 ? 0.01 : 0.0001;
            
            SlippageProvider bounded = base.capped(0.005).floored(0.0003);
            
            // When: Low volume (would be 1%, capped to 0.5%)
            double lowVolSlippage = bounded.calculateSlippage(createContext("LOW", 100, 1000));
            
            // When: High volume (would be 0.01%, floored to 0.03%)
            double highVolSlippage = bounded.calculateSlippage(createContext("HIGH", 100, 1_000_000));
            
            // Then
            assertThat(lowVolSlippage).isEqualTo(0.005);
            assertThat(highVolSlippage).isEqualTo(0.0003);
        }
    }
    
    // ==================== Lambda Provider Tests ====================
    
    @Nested
    @DisplayName("Lambda Provider Tests")
    class LambdaProviderTests {
        
        @Test
        @DisplayName("should work as functional interface")
        void shouldWorkAsFunctionalInterface() {
            // Given: Lambda implementation
            SlippageProvider provider = context -> 
                context.orderSize() > 500 ? 0.002 : 0.001;
            
            // When
            double smallOrder = provider.calculateSlippage(createContext("X", 100, 1000));
            double largeOrder = provider.calculateSlippage(createContext("X", 1000, 1000));
            
            // Then
            assertThat(smallOrder).isEqualTo(0.001);
            assertThat(largeOrder).isEqualTo(0.002);
        }
    }
    
    // ==================== Helper Methods ====================
    
    private SlippageProvider.SlippageContext createContext(String symbol, int orderSize, long volume) {
        return new SlippageProvider.SlippageContext(
            symbol, orderSize, volume, LocalTime.of(10, 30), "BUY", 100.0
        );
    }
}
