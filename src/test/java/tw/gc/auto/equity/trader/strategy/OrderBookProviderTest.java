package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.OrderBookData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OrderBookProvider functional interface and factory methods.
 * 
 * @since 2026-01-27 - Phase 5 Order Book Enhancement
 */
@DisplayName("OrderBookProvider Unit Tests")
class OrderBookProviderTest {

    @Nested
    @DisplayName("noOp()")
    class NoOpTests {
        
        @Test
        void shouldReturnEmptyOptional() {
            OrderBookProvider provider = OrderBookProvider.noOp();
            
            Optional<OrderBookData> result = provider.getOrderBook("2330.TW");
            
            assertThat(result).isEmpty();
        }
        
        @Test
        void shouldReturnEmptyForAnySymbol() {
            OrderBookProvider provider = OrderBookProvider.noOp();
            
            assertThat(provider.getOrderBook("2330.TW")).isEmpty();
            assertThat(provider.getOrderBook("2454.TW")).isEmpty();
            assertThat(provider.getOrderBook("UNKNOWN")).isEmpty();
        }
        
        @Test
        void shouldReturnNullForConvenienceMethods() {
            OrderBookProvider provider = OrderBookProvider.noOp();
            
            assertThat(provider.getBestBid("2330.TW")).isNull();
            assertThat(provider.getBestAsk("2330.TW")).isNull();
            assertThat(provider.getMidPrice("2330.TW")).isNull();
            assertThat(provider.getSpread("2330.TW")).isNull();
            assertThat(provider.getSpreadBps("2330.TW")).isNull();
            assertThat(provider.getImbalance("2330.TW")).isNull();
        }
        
        @Test
        void shouldReturnFalseForBooleanMethods() {
            OrderBookProvider provider = OrderBookProvider.noOp();
            
            assertThat(provider.hasBuyPressure("2330.TW", 0.2)).isFalse();
            assertThat(provider.hasSellPressure("2330.TW", 0.2)).isFalse();
            assertThat(provider.isWideSpread("2330.TW", 30.0)).isFalse();
            assertThat(provider.hasValidData("2330.TW")).isFalse();
        }
    }

    @Nested
    @DisplayName("withFixedData()")
    class FixedDataTests {
        
        @Test
        void shouldReturnFixedOrderBookData() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 150L);
            
            Optional<OrderBookData> result = provider.getOrderBook("2330.TW");
            
            assertThat(result).isPresent();
            assertThat(result.get().getBidPrice1()).isEqualTo(590.0);
            assertThat(result.get().getAskPrice1()).isEqualTo(591.0);
            assertThat(result.get().getBidVolume1()).isEqualTo(100L);
            assertThat(result.get().getAskVolume1()).isEqualTo(150L);
        }
        
        @Test
        void shouldCalculateDerivedValues() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 100L);
            
            Optional<OrderBookData> result = provider.getOrderBook("TEST.TW");
            
            assertThat(result).isPresent();
            assertThat(result.get().getSpread()).isEqualTo(1.0);
            assertThat(result.get().getMidPrice()).isEqualTo(590.5);
            assertThat(result.get().getSpreadBps()).isCloseTo(16.95, within(0.1));
            assertThat(result.get().getImbalance()).isEqualTo(0.0);  // Equal volumes
        }
        
        @Test
        void shouldCalculateBuyPressure() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 200L, 100L);
            
            assertThat(provider.hasBuyPressure("2330.TW", 0.2)).isTrue();
            assertThat(provider.hasSellPressure("2330.TW", 0.2)).isFalse();
        }
        
        @Test
        void shouldCalculateSellPressure() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 200L);
            
            assertThat(provider.hasBuyPressure("2330.TW", 0.2)).isFalse();
            assertThat(provider.hasSellPressure("2330.TW", 0.2)).isTrue();
        }
        
        @Test
        void shouldHaveValidData() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 100L);
            
            assertThat(provider.hasValidData("2330.TW")).isTrue();
        }
    }

    @Nested
    @DisplayName("withFixedSpread()")
    class FixedSpreadTests {
        
        @Test
        void shouldReturnOrderBookWithFixedSpread() {
            OrderBookProvider provider = OrderBookProvider.withFixedSpread(590.5, 20.0);
            
            Optional<OrderBookData> result = provider.getOrderBook("2330.TW");
            
            assertThat(result).isPresent();
            assertThat(result.get().getMidPrice()).isEqualTo(590.5);
            assertThat(result.get().getSpreadBps()).isCloseTo(20.0, within(0.1));
        }
        
        @Test
        void shouldCalculateBidAskFromSpread() {
            OrderBookProvider provider = OrderBookProvider.withFixedSpread(600.0, 20.0);
            
            Optional<OrderBookData> result = provider.getOrderBook("2330.TW");
            
            assertThat(result).isPresent();
            // Spread in price = 600.0 * 20.0 / 10000 = 1.2
            // Bid = 600.0 - 0.6 = 599.4, Ask = 600.0 + 0.6 = 600.6
            assertThat(result.get().getBidPrice1()).isCloseTo(599.4, within(0.01));
            assertThat(result.get().getAskPrice1()).isCloseTo(600.6, within(0.01));
        }
        
        @Test
        void shouldHaveNeutralImbalance() {
            OrderBookProvider provider = OrderBookProvider.withFixedSpread(590.5, 20.0);
            
            assertThat(provider.getImbalance("2330.TW")).isCloseTo(0.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("withFixedImbalance()")
    class FixedImbalanceTests {
        
        @Test
        void shouldReturnOrderBookWithFixedImbalance() {
            OrderBookProvider provider = OrderBookProvider.withFixedImbalance(590.5, 0.5);
            
            Optional<OrderBookData> result = provider.getOrderBook("2330.TW");
            
            assertThat(result).isPresent();
            assertThat(result.get().getImbalance()).isEqualTo(0.5);
        }
        
        @Test
        void shouldDetectBuyPressureFromPositiveImbalance() {
            OrderBookProvider provider = OrderBookProvider.withFixedImbalance(590.5, 0.5);
            
            assertThat(provider.hasBuyPressure("2330.TW", 0.2)).isTrue();
            assertThat(provider.hasSellPressure("2330.TW", 0.2)).isFalse();
        }
        
        @Test
        void shouldDetectSellPressureFromNegativeImbalance() {
            OrderBookProvider provider = OrderBookProvider.withFixedImbalance(590.5, -0.5);
            
            assertThat(provider.hasBuyPressure("2330.TW", 0.2)).isFalse();
            assertThat(provider.hasSellPressure("2330.TW", 0.2)).isTrue();
        }
        
        @Test
        void shouldHaveNeutralPressureAtZero() {
            OrderBookProvider provider = OrderBookProvider.withFixedImbalance(590.5, 0.0);
            
            assertThat(provider.hasBuyPressure("2330.TW", 0.2)).isFalse();
            assertThat(provider.hasSellPressure("2330.TW", 0.2)).isFalse();
        }
    }

    @Nested
    @DisplayName("withFullData()")
    class FullDataTests {
        
        @Test
        void shouldReturnProvidedOrderBookData() {
            OrderBookData data = OrderBookData.builder()
                    .symbol("2330.TW")
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .bidVolume1(100L)
                    .askVolume1(150L)
                    .spread(1.0)
                    .spreadBps(16.95)
                    .midPrice(590.5)
                    .imbalance(-0.2)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            OrderBookProvider provider = OrderBookProvider.withFullData(data);
            
            Optional<OrderBookData> result = provider.getOrderBook("ANY.TW");
            
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(data);
        }
        
        @Test
        void shouldReturnAllFieldsFromProvidedData() {
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .bidVolume1(100L)
                    .askVolume1(150L)
                    .spread(1.0)
                    .spreadBps(16.95)
                    .midPrice(590.5)
                    .imbalance(-0.2)
                    .totalBidVolume(1000L)
                    .totalAskVolume(1500L)
                    .build();
            
            OrderBookProvider provider = OrderBookProvider.withFullData(data);
            
            assertThat(provider.getBestBid("2330.TW")).isEqualTo(590.0);
            assertThat(provider.getBestAsk("2330.TW")).isEqualTo(591.0);
            assertThat(provider.getSpread("2330.TW")).isEqualTo(1.0);
            assertThat(provider.getSpreadBps("2330.TW")).isEqualTo(16.95);
            assertThat(provider.getMidPrice("2330.TW")).isEqualTo(590.5);
            assertThat(provider.getImbalance("2330.TW")).isEqualTo(-0.2);
            assertThat(provider.getTotalBidVolume("2330.TW")).isEqualTo(1000L);
            assertThat(provider.getTotalAskVolume("2330.TW")).isEqualTo(1500L);
        }
    }

    @Nested
    @DisplayName("fromFunction()")
    class FromFunctionTests {
        
        @Test
        void shouldCreateProviderFromFunction() {
            OrderBookData tsmc = OrderBookData.builder()
                    .symbol("2330.TW")
                    .bidPrice1(590.0)
                    .askPrice1(591.0)
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .build();
            
            OrderBookData mediatek = OrderBookData.builder()
                    .symbol("2454.TW")
                    .bidPrice1(1200.0)
                    .askPrice1(1201.0)
                    .bidVolume1(50L)
                    .askVolume1(50L)
                    .build();
            
            OrderBookProvider provider = OrderBookProvider.fromFunction(symbol -> {
                if ("2330.TW".equals(symbol)) return Optional.of(tsmc);
                if ("2454.TW".equals(symbol)) return Optional.of(mediatek);
                return Optional.empty();
            });
            
            assertThat(provider.getOrderBook("2330.TW")).contains(tsmc);
            assertThat(provider.getOrderBook("2454.TW")).contains(mediatek);
            assertThat(provider.getOrderBook("UNKNOWN")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Convenience Methods")
    class ConvenienceMethodsTests {
        
        private final OrderBookProvider provider = OrderBookProvider.withFixedData(
                590.0, 591.0, 200L, 100L);
        
        @Test
        void shouldReturnBestBid() {
            assertThat(provider.getBestBid("2330.TW")).isEqualTo(590.0);
        }
        
        @Test
        void shouldReturnBestAsk() {
            assertThat(provider.getBestAsk("2330.TW")).isEqualTo(591.0);
        }
        
        @Test
        void shouldReturnMidPrice() {
            assertThat(provider.getMidPrice("2330.TW")).isEqualTo(590.5);
        }
        
        @Test
        void shouldReturnSpread() {
            assertThat(provider.getSpread("2330.TW")).isEqualTo(1.0);
        }
        
        @Test
        void shouldReturnSpreadBps() {
            assertThat(provider.getSpreadBps("2330.TW")).isCloseTo(16.95, within(0.1));
        }
        
        @Test
        void shouldReturnTotalBidVolume() {
            assertThat(provider.getTotalBidVolume("2330.TW")).isEqualTo(200L);
        }
        
        @Test
        void shouldReturnTotalAskVolume() {
            assertThat(provider.getTotalAskVolume("2330.TW")).isEqualTo(100L);
        }
        
        @Test
        void shouldReturnBidDepthAsVolume() {
            // getBidDepth returns long volume
            assertThat(provider.getBidDepth("2330.TW", 1)).isEqualTo(200L);
            assertThat(provider.getBidDepth("2330.TW", 2)).isEqualTo(0L); // No level 2 data
        }
        
        @Test
        void shouldReturnAskDepthAsVolume() {
            // getAskDepth returns long volume
            assertThat(provider.getAskDepth("2330.TW", 1)).isEqualTo(100L);
            assertThat(provider.getAskDepth("2330.TW", 2)).isEqualTo(0L); // No level 2 data
        }
    }

    @Nested
    @DisplayName("History and Average Methods")
    class HistoryMethodsTests {
        
        @Test
        void shouldReturnEmptyHistoryByDefault() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 100L);
            
            List<OrderBookData> history = provider.getHistory("2330.TW", 10);
            
            assertThat(history).isEmpty();
        }
        
        @Test
        void shouldReturnCurrentValueForAveragesByDefault() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 100L);
            
            // Default implementation returns current values (not historical average)
            assertThat(provider.getAverageSpreadBps("2330.TW", 10)).isCloseTo(16.93, within(0.1));
            assertThat(provider.getAverageImbalance("2330.TW", 10)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("isWideSpread()")
    class WideSpreadTests {
        
        @Test
        void shouldDetectWideSpread() {
            // SpreadBps = 50 (wide)
            OrderBookProvider provider = OrderBookProvider.withFixedSpread(600.0, 50.0);
            
            assertThat(provider.isWideSpread("2330.TW", 30.0)).isTrue();
        }
        
        @Test
        void shouldNotDetectWideSpreadWhenTight() {
            // SpreadBps = 10 (tight)
            OrderBookProvider provider = OrderBookProvider.withFixedSpread(600.0, 10.0);
            
            assertThat(provider.isWideSpread("2330.TW", 30.0)).isFalse();
        }
        
        @Test
        void shouldNotDetectWideSpreadWhenNoData() {
            OrderBookProvider provider = OrderBookProvider.noOp();
            
            assertThat(provider.isWideSpread("2330.TW", 30.0)).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {
        
        @Test
        void shouldHandleNullSymbol() {
            OrderBookProvider provider = OrderBookProvider.noOp();
            
            assertThat(provider.getOrderBook(null)).isEmpty();
            assertThat(provider.getBestBid(null)).isNull();
            assertThat(provider.hasValidData(null)).isFalse();
        }
        
        @Test
        void shouldHandleEmptySymbol() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 100L, 100L);
            
            // Fixed data returns same data for any symbol
            assertThat(provider.getOrderBook("")).isPresent();
        }
        
        @Test
        void shouldHandleZeroVolumes() {
            OrderBookProvider provider = OrderBookProvider.withFixedData(590.0, 591.0, 0L, 0L);
            
            Optional<OrderBookData> result = provider.getOrderBook("2330.TW");
            
            assertThat(result).isPresent();
            // Zero volumes are valid for order book snapshot
            assertThat(result.get().isValid()).isTrue();
        }
        
        @Test
        void shouldHandleInvertedPrices() {
            // This shouldn't happen in real markets but test robustness
            OrderBookData data = OrderBookData.builder()
                    .bidPrice1(591.0)  // Inverted prices
                    .askPrice1(590.0)
                    .bidVolume1(100L)
                    .askVolume1(100L)
                    .build();
            
            OrderBookProvider provider = OrderBookProvider.withFullData(data);
            
            assertThat(provider.hasValidData("2330.TW")).isFalse();
        }
    }
}
