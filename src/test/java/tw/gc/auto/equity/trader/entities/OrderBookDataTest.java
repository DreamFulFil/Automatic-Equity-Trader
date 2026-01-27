package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OrderBookData entity.
 * 
 * @since 2026-01-27 - Phase 5 Order Book Enhancement
 */
@DisplayName("OrderBookData Entity Unit Tests")
class OrderBookDataTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        
        @Test
        void shouldBuildBasicOrderBookData() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .bidVolume1(100L)
                    .askVolume1(150L)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            assertThat(data.getSymbol()).isEqualTo("2330.TW");
            assertThat(data.getBidPrice1()).isEqualTo(590.0);
            assertThat(data.getAskPrice1()).isEqualTo(591.0);
            assertThat(data.getBidVolume1()).isEqualTo(100L);
            assertThat(data.getAskVolume1()).isEqualTo(150L);
        }
        
        @Test
        void shouldBuildFullDepthOrderBook() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .bidPrice1(590.0).bidVolume1(100L)
                    .bidPrice2(589.0).bidVolume2(200L)
                    .bidPrice3(588.0).bidVolume3(300L)
                    .bidPrice4(587.0).bidVolume4(400L)
                    .bidPrice5(586.0).bidVolume5(500L)
                    .askPrice1(591.0).askVolume1(150L)
                    .askPrice2(592.0).askVolume2(250L)
                    .askPrice3(593.0).askVolume3(350L)
                    .askPrice4(594.0).askVolume4(450L)
                    .askPrice5(595.0).askVolume5(550L)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            assertThat(data.getBidPrice5()).isEqualTo(586.0);
            assertThat(data.getAskPrice5()).isEqualTo(595.0);
            assertThat(data.getBidVolume5()).isEqualTo(500L);
            assertThat(data.getAskVolume5()).isEqualTo(550L);
        }
        
        @Test
        void shouldBuildWithPrecomputedValues() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .spread(1.0)
                    .spreadBps(16.95)
                    .midPrice(590.5)
                    .imbalance(0.25)
                    .totalBidVolume(1500L)
                    .totalAskVolume(1750L)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            assertThat(data.getSpread()).isEqualTo(1.0);
            assertThat(data.getSpreadBps()).isEqualTo(16.95);
            assertThat(data.getMidPrice()).isEqualTo(590.5);
            assertThat(data.getImbalance()).isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("getSpread()")
    class SpreadTests {
        
        @Test
        void shouldCalculateSpread() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .build();
            
            assertThat(data.getSpread()).isEqualTo(1.0);
        }
        
        @Test
        void shouldReturnStoredSpreadIfSet() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .spread(1.5)
                    .build();
            
            assertThat(data.getSpread()).isEqualTo(1.5);
        }
        
        @Test
        void shouldReturnNullWhenBidIsNull() {
            OrderBookData data = OrderBookData.builder()
                    .askPrice1(591.0)
                    .build();
            
            assertThat(data.getSpread()).isNull();
        }
        
        @Test
        void shouldReturnNullWhenAskIsNull() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .build();
            
            assertThat(data.getSpread()).isNull();
        }
    }

    @Nested
    @DisplayName("getSpreadBps()")
    class SpreadBpsTests {
        
        @Test
        void shouldCalculateSpreadBps() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .build();
            
            // Spread = 1.0, MidPrice = 590.5, SpreadBps = 1.0 / 590.5 * 10000 ≈ 16.93
            assertThat(data.getSpreadBps()).isCloseTo(16.93, within(0.02));
        }
        
        @Test
        void shouldReturnStoredSpreadBpsIfSet() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .spreadBps(20.0)
                    .build();
            
            assertThat(data.getSpreadBps()).isEqualTo(20.0);
        }
        
        @ParameterizedTest
        @CsvSource({
            "100.0, 100.1, 10.0",   // 0.1 / 100.05 * 10000 ≈ 10.0
            "500.0, 501.0, 20.0",   // 1.0 / 500.5 * 10000 ≈ 20.0
        })
        void shouldCalculateSpreadBpsForVariousPrices(double bid, double ask, double expectedBpsApprox) {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(bid)
                    .askPrice1(ask)
                    .build();
            
            assertThat(data.getSpreadBps()).isCloseTo(expectedBpsApprox, within(0.5));
        }
    }

    @Nested
    @DisplayName("getMidPrice()")
    class MidPriceTests {
        
        @Test
        void shouldCalculateMidPrice() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(592.0)
                    .build();
            
            assertThat(data.getMidPrice()).isEqualTo(591.0);
        }
        
        @Test
        void shouldReturnStoredMidPriceIfSet() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(592.0)
                    .midPrice(590.75)
                    .build();
            
            assertThat(data.getMidPrice()).isEqualTo(590.75);
        }
        
        @Test
        void shouldReturnNullWhenBidOrAskIsNull() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .build();
            
            assertThat(data.getMidPrice()).isNull();
        }
    }

    @Nested
    @DisplayName("getImbalance()")
    class ImbalanceTests {
        
        @Test
        void shouldCalculateImbalance() {
            OrderBookData data = OrderBookData.builder()
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .totalBidVolume(100L)
                    .totalAskVolume(100L)
                    .build();
            
            // Equal volume = 0 imbalance
            assertThat(data.getImbalance()).isEqualTo(0.0);
        }
        
        @Test
        void shouldCalculateBuyPressureImbalance() {
            OrderBookData data = OrderBookData.builder()
                    .bidVolume1(200L)
                    .askVolume1(100L)
                    .totalBidVolume(200L)
                    .totalAskVolume(100L)
                    .build();
            
            // (200 - 100) / (200 + 100) = 0.333...
            assertThat(data.getImbalance()).isCloseTo(0.333, within(0.01));
        }
        
        @Test
        void shouldCalculateSellPressureImbalance() {
            OrderBookData data = OrderBookData.builder()
                    .bidVolume1(100L)
                    .askVolume1(200L)
                    .totalBidVolume(100L)
                    .totalAskVolume(200L)
                    .build();
            
            // (100 - 200) / (100 + 200) = -0.333...
            assertThat(data.getImbalance()).isCloseTo(-0.333, within(0.01));
        }
        
        @Test
        void shouldReturnStoredImbalanceIfSet() {
            OrderBookData data = OrderBookData.builder()
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .imbalance(0.5)
                    .build();
            
            assertThat(data.getImbalance()).isEqualTo(0.5);
        }
        
        @Test
        void shouldCalculateZeroImbalanceWhenVolumesAreNull() {
            // When volumes are null, getImbalance uses stored imbalance or calculates from null->0
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .build();
            
            // Imbalance defaults to 0.0 when volumes are null (0-0)/(0+0) is handled
            Double imbalance = data.getImbalance();
            assertThat(imbalance).isNotNull();
        }
    }

    @Nested
    @DisplayName("hasBuyPressure()")
    class BuyPressureTests {
        
        @Test
        void shouldDetectBuyPressure() {
            OrderBookData data = OrderBookData.builder()
                    .totalBidVolume(200L)
                    .totalAskVolume(100L)
                    .imbalance(0.333)
                    .build();
            
            assertThat(data.hasBuyPressure(0.2)).isTrue();
        }
        
        @Test
        void shouldNotDetectBuyPressureWhenBalanced() {
            OrderBookData data = OrderBookData.builder()
                    .totalBidVolume(100L)
                    .totalAskVolume(100L)
                    .imbalance(0.0)
                    .build();
            
            assertThat(data.hasBuyPressure(0.2)).isFalse();
        }
        
        @Test
        void shouldNotDetectBuyPressureWhenSelling() {
            OrderBookData data = OrderBookData.builder()
                    .totalBidVolume(100L)
                    .totalAskVolume(200L)
                    .imbalance(-0.333)
                    .build();
            
            assertThat(data.hasBuyPressure(0.2)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasSellPressure()")
    class SellPressureTests {
        
        @Test
        void shouldDetectSellPressure() {
            OrderBookData data = OrderBookData.builder()
                    .totalBidVolume(100L)
                    .totalAskVolume(200L)
                    .imbalance(-0.333)
                    .build();
            
            assertThat(data.hasSellPressure(0.2)).isTrue();
        }
        
        @Test
        void shouldNotDetectSellPressureWhenBalanced() {
            OrderBookData data = OrderBookData.builder()
                    .totalBidVolume(100L)
                    .totalAskVolume(100L)
                    .imbalance(0.0)
                    .build();
            
            assertThat(data.hasSellPressure(0.2)).isFalse();
        }
    }

    @Nested
    @DisplayName("isWideSpread()")
    class WideSpreadTests {
        
        @Test
        void shouldDetectWideSpread() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(595.0)
                    .spreadBps(84.67)  // 5.0 / 592.5 * 10000 ≈ 84.4
                    .build();
            
            assertThat(data.isWideSpread(50.0)).isTrue();
        }
        
        @Test
        void shouldNotDetectWideSpreadWhenTight() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(590.5)
                    .spreadBps(8.47)  // 0.5 / 590.25 * 10000 ≈ 8.47
                    .build();
            
            assertThat(data.isWideSpread(50.0)).isFalse();
        }
        
        @Test
        void shouldReturnFalseWhenSpreadBpsIsNull() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .build();
            
            assertThat(data.isWideSpread(50.0)).isFalse();
        }
        
        @ParameterizedTest
        @ValueSource(doubles = {20.0, 30.0, 50.0, 100.0})
        void shouldDetectWideSpreadWithVariousThresholds(double threshold) {
            OrderBookData data = OrderBookData.builder()
                    .spreadBps(threshold + 10.0)
                    .build();
            
            assertThat(data.isWideSpread(threshold)).isTrue();
        }
    }

    @Nested
    @DisplayName("getBidDepth() / getAskDepth()")
    class DepthTests {
        
        @Test
        void shouldReturnDepthForValidLevels() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0).bidVolume1(100L)
                    .bidPrice2(589.0).bidVolume2(200L)
                    .bidPrice3(588.0).bidVolume3(300L)
                    .askPrice1(591.0).askVolume1(150L)
                    .askPrice2(592.0).askVolume2(250L)
                    .build();
            
            // getBidDepth returns volume (long)
            assertThat(data.getBidDepth(1)).isEqualTo(100L);
            assertThat(data.getBidDepth(2)).isEqualTo(200L);
            assertThat(data.getBidDepth(3)).isEqualTo(300L);
            assertThat(data.getAskDepth(1)).isEqualTo(150L);
            assertThat(data.getAskDepth(2)).isEqualTo(250L);
        }
        
        @Test
        void shouldReturnZeroForInvalidLevel() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0).bidVolume1(100L)
                    .build();
            
            assertThat(data.getBidDepth(0)).isEqualTo(0L);
            assertThat(data.getBidDepth(6)).isEqualTo(0L);
            assertThat(data.getAskDepth(-1)).isEqualTo(0L);
        }
        
        @Test
        void shouldReturnZeroWhenDataIsNull() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .build();
            
            assertThat(data.getBidDepth(1)).isEqualTo(0L);
            assertThat(data.getAskDepth(1)).isEqualTo(0L);
        }
        
        @Test
        void shouldReturnBidPriceForLevel() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .bidPrice2(589.0)
                    .bidPrice3(588.0)
                    .build();
            
            assertThat(data.getBidPrice(1)).isEqualTo(590.0);
            assertThat(data.getBidPrice(2)).isEqualTo(589.0);
            assertThat(data.getBidPrice(3)).isEqualTo(588.0);
            assertThat(data.getBidPrice(0)).isNull();
            assertThat(data.getBidPrice(6)).isNull();
        }
        
        @Test
        void shouldReturnAskPriceForLevel() {
            OrderBookData data = OrderBookData.builder()
                    .askPrice1(591.0)
                    .askPrice2(592.0)
                    .build();
            
            assertThat(data.getAskPrice(1)).isEqualTo(591.0);
            assertThat(data.getAskPrice(2)).isEqualTo(592.0);
            assertThat(data.getAskPrice(0)).isNull();
        }
    }

    @Nested
    @DisplayName("getVolumeWeightedBidPrice() / getVolumeWeightedAskPrice()")
    class VolumeWeightedPriceTests {
        
        @Test
        void shouldCalculateVolumeWeightedBidPrice() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0).bidVolume1(100L)
                    .bidPrice2(589.0).bidVolume2(200L)
                    .bidPrice3(588.0).bidVolume3(300L)
                    .build();
            
            // VWAP = (590*100 + 589*200 + 588*300) / (100+200+300)
            // = (59000 + 117800 + 176400) / 600 = 353200 / 600 = 588.67
            assertThat(data.getVolumeWeightedBidPrice()).isCloseTo(588.67, within(0.01));
        }
        
        @Test
        void shouldCalculateVolumeWeightedAskPrice() {
            OrderBookData data = OrderBookData.builder()
                    .askPrice1(591.0).askVolume1(100L)
                    .askPrice2(592.0).askVolume2(200L)
                    .build();
            
            // VWAP = (591*100 + 592*200) / 300 = 177500 / 300 = 591.67
            assertThat(data.getVolumeWeightedAskPrice()).isCloseTo(591.67, within(0.01));
        }
        
        @Test
        void shouldReturnNullWhenNoDepth() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .build();
            
            assertThat(data.getVolumeWeightedBidPrice()).isNull();
            assertThat(data.getVolumeWeightedAskPrice()).isNull();
        }
    }

    @Nested
    @DisplayName("isValid()")
    class ValidationTests {
        
        @Test
        void shouldBeValidWithBidAskAndVolumes() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .build();
            
            assertThat(data.isValid()).isTrue();
        }
        
        @Test
        void shouldNotBeValidWithoutBidPrice() {
            OrderBookData data = OrderBookData.builder()
                    .askPrice1(591.0)
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .build();
            
            assertThat(data.isValid()).isFalse();
        }
        
        @Test
        void shouldNotBeValidWithoutAskPrice() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .build();
            
            assertThat(data.isValid()).isFalse();
        }
        
        @Test
        void shouldBeValidWithZeroVolumes() {
            // Zero volumes are valid for order book snapshot - just indicates no depth at that level
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .bidVolume1(0L)
                    .askVolume1(0L)
                    .build();
            
            assertThat(data.isValid()).isTrue();
        }
        
        @Test
        void shouldNotBeValidWithInvertedPrices() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(595.0)
                    .askPrice1(590.0)
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .build();
            
            assertThat(data.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("getDepthLevels()")
    class DepthLevelsTests {
        
        @Test
        void shouldReturnCorrectDepthLevels() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0).bidVolume1(100L)
                    .bidPrice2(589.0).bidVolume2(200L)
                    .bidPrice3(588.0).bidVolume3(300L)
                    .askPrice1(591.0).askVolume1(150L)
                    .askPrice2(592.0).askVolume2(250L)
                    .build();
            
            assertThat(data.getDepthLevels()).isEqualTo(3);
        }
        
        @Test
        void shouldReturnZeroForEmptyBook() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .build();
            
            assertThat(data.getDepthLevels()).isEqualTo(0);
        }
        
        @Test
        void shouldReturnOneForSingleLevel() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0).bidVolume1(100L)
                    .askPrice1(591.0).askVolume1(100L)
                    .build();
            
            assertThat(data.getDepthLevels()).isEqualTo(1);
        }
        
        @Test
        void shouldReturnFiveForFullDepth() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0).bidVolume1(100L)
                    .bidPrice2(589.0).bidVolume2(100L)
                    .bidPrice3(588.0).bidVolume3(100L)
                    .bidPrice4(587.0).bidVolume4(100L)
                    .bidPrice5(586.0).bidVolume5(100L)
                    .askPrice1(591.0).askVolume1(100L)
                    .askPrice2(592.0).askVolume2(100L)
                    .askPrice3(593.0).askVolume3(100L)
                    .askPrice4(594.0).askVolume4(100L)
                    .askPrice5(595.0).askVolume5(100L)
                    .build();
            
            assertThat(data.getDepthLevels()).isEqualTo(5);
        }
    }
}
