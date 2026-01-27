package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.OrderBookData;
import tw.gc.auto.equity.trader.repositories.OrderBookDataRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for OrderBookService.
 * 
 * @since 2026-01-27 - Phase 5 Order Book Enhancement
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderBookService Unit Tests")
class OrderBookServiceTest {

    @Mock
    private OrderBookDataRepository repository;
    
    private OrderBookService orderBookService;
    
    @BeforeEach
    void setUp() {
        // Use constructor for testing - don't hit real bridge
        orderBookService = new OrderBookService(repository, "http://localhost:8888");
    }
    
    // ========== getLatest Tests ==========
    
    @Nested
    @DisplayName("getLatest()")
    class GetLatestTests {
        
        @Test
        @DisplayName("should return cached data if not expired")
        void shouldReturnCachedData() {
            // Given - pre-populate cache via updateOrderBook
            OrderBookData data = createOrderBookData("2330.TW", 590.0, 591.0);
            orderBookService.updateOrderBook("2330.TW", data);
            
            // When
            Optional<OrderBookData> result = orderBookService.getLatest("2330.TW");
            
            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getBidPrice1()).isEqualTo(590.0);
            assertThat(result.get().getAskPrice1()).isEqualTo(591.0);
        }
        
        @Test
        @DisplayName("should fallback to database when cache empty")
        void shouldFallbackToDatabase() {
            // Given
            OrderBookData data = createOrderBookData("2330.TW", 590.0, 591.0);
            given(repository.findFirstBySymbolOrderByTimestampDesc("2330.TW"))
                    .willReturn(Optional.of(data));
            
            // When
            Optional<OrderBookData> result = orderBookService.getLatest("2330.TW");
            
            // Then
            assertThat(result).isPresent();
            then(repository).should().findFirstBySymbolOrderByTimestampDesc("2330.TW");
        }
        
        @Test
        @DisplayName("should return empty when no data available")
        void shouldReturnEmptyWhenNoData() {
            // Given
            given(repository.findFirstBySymbolOrderByTimestampDesc(anyString()))
                    .willReturn(Optional.empty());
            
            // When
            Optional<OrderBookData> result = orderBookService.getLatest("UNKNOWN.TW");
            
            // Then
            assertThat(result).isEmpty();
        }
    }
    
    // ========== getHistory Tests ==========
    
    @Nested
    @DisplayName("getHistory()")
    class GetHistoryTests {
        
        @Test
        @DisplayName("should return historical snapshots")
        void shouldReturnHistoricalSnapshots() {
            // Given
            List<OrderBookData> expected = List.of(
                    createOrderBookData("2330.TW", 590.0, 591.0),
                    createOrderBookData("2330.TW", 589.5, 590.5),
                    createOrderBookData("2330.TW", 589.0, 590.0)
            );
            given(repository.findRecentBySymbol(eq("2330.TW"), any(LocalDateTime.class)))
                    .willReturn(expected);
            
            // When
            List<OrderBookData> result = orderBookService.getHistory("2330.TW", 10);
            
            // Then
            assertThat(result).hasSize(3);
        }
        
        @Test
        @DisplayName("should return empty list when no history")
        void shouldReturnEmptyListWhenNoHistory() {
            // Given
            given(repository.findRecentBySymbol(anyString(), any(LocalDateTime.class)))
                    .willReturn(List.of());
            
            // When
            List<OrderBookData> result = orderBookService.getHistory("NEW.TW", 60);
            
            // Then
            assertThat(result).isEmpty();
        }
    }
    
    // ========== getWithBuyPressure Tests ==========
    
    @Nested
    @DisplayName("getWithBuyPressure()")
    class BuyPressureTests {
        
        @Test
        @DisplayName("should return snapshots with buy pressure")
        void shouldReturnSnapshotsWithBuyPressure() {
            // Given
            OrderBookData buyPressure = createOrderBookDataWithImbalance("2330.TW", 0.4);
            given(repository.findWithBuyPressure(eq("2330.TW"), anyDouble(), any(LocalDateTime.class)))
                    .willReturn(List.of(buyPressure));
            
            // When
            List<OrderBookData> result = orderBookService.getWithBuyPressure("2330.TW", 0.3, 10);
            
            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getImbalance()).isGreaterThan(0);
        }
    }
    
    // ========== getWithSellPressure Tests ==========
    
    @Nested
    @DisplayName("getWithSellPressure()")
    class SellPressureTests {
        
        @Test
        @DisplayName("should return snapshots with sell pressure")
        void shouldReturnSnapshotsWithSellPressure() {
            // Given
            OrderBookData sellPressure = createOrderBookDataWithImbalance("2330.TW", -0.4);
            given(repository.findWithSellPressure(eq("2330.TW"), anyDouble(), any(LocalDateTime.class)))
                    .willReturn(List.of(sellPressure));
            
            // When
            List<OrderBookData> result = orderBookService.getWithSellPressure("2330.TW", 0.3, 10);
            
            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getImbalance()).isLessThan(0);
        }
    }
    
    // ========== getAverageSpread Tests ==========
    
    @Nested
    @DisplayName("getAverageSpread()")
    class AverageSpreadTests {
        
        @Test
        @DisplayName("should calculate average spread")
        void shouldCalculateAverageSpread() {
            // Given
            given(repository.calculateAverageSpread(eq("2330.TW"), any(LocalDateTime.class)))
                    .willReturn(15.5);
            
            // When
            Double result = orderBookService.getAverageSpread("2330.TW", 30);
            
            // Then
            assertThat(result).isEqualTo(15.5);
        }
        
        @Test
        @DisplayName("should return null when no data")
        void shouldReturnNullWhenNoData() {
            // Given
            given(repository.calculateAverageSpread(anyString(), any(LocalDateTime.class)))
                    .willReturn(null);
            
            // When
            Double result = orderBookService.getAverageSpread("NEW.TW", 30);
            
            // Then
            assertThat(result).isNull();
        }
    }
    
    // ========== getAverageImbalance Tests ==========
    
    @Nested
    @DisplayName("getAverageImbalance()")
    class AverageImbalanceTests {
        
        @Test
        @DisplayName("should calculate average imbalance")
        void shouldCalculateAverageImbalance() {
            // Given
            given(repository.calculateAverageImbalance(eq("2330.TW"), any(LocalDateTime.class)))
                    .willReturn(0.12);
            
            // When
            Double result = orderBookService.getAverageImbalance("2330.TW", 30);
            
            // Then
            assertThat(result).isEqualTo(0.12);
        }
    }
    
    // ========== hasData Tests ==========
    
    @Nested
    @DisplayName("hasData()")
    class HasDataTests {
        
        @Test
        @DisplayName("should return true when data in cache")
        void shouldReturnTrueWhenInCache() {
            // Given - pre-populate cache
            OrderBookData data = createOrderBookData("2330.TW", 590.0, 591.0);
            orderBookService.updateOrderBook("2330.TW", data);
            
            // When
            boolean result = orderBookService.hasData("2330.TW");
            
            // Then
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("should return true when data in database")
        void shouldReturnTrueWhenInDatabase() {
            // Given
            given(repository.existsBySymbol("2454.TW")).willReturn(true);
            
            // When
            boolean result = orderBookService.hasData("2454.TW");
            
            // Then
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("should return false when no data anywhere")
        void shouldReturnFalseWhenNoData() {
            // Given
            given(repository.existsBySymbol(anyString())).willReturn(false);
            
            // When
            boolean result = orderBookService.hasData("UNKNOWN.TW");
            
            // Then
            assertThat(result).isFalse();
        }
    }
    
    // ========== updateOrderBook Tests ==========
    
    @Nested
    @DisplayName("updateOrderBook()")
    class UpdateOrderBookTests {
        
        @Test
        @DisplayName("should update cache with new data")
        void shouldUpdateCache() {
            // Given
            OrderBookData data = createOrderBookData("2330.TW", 595.0, 596.0);
            
            // When
            orderBookService.updateOrderBook("2330.TW", data);
            
            // Then
            Optional<OrderBookData> result = orderBookService.getLatest("2330.TW");
            assertThat(result).isPresent();
            assertThat(result.get().getBidPrice1()).isEqualTo(595.0);
        }
        
        @Test
        @DisplayName("should set timestamp on data")
        void shouldSetTimestamp() {
            // Given
            OrderBookData data = createOrderBookData("2330.TW", 595.0, 596.0);
            
            // When
            orderBookService.updateOrderBook("2330.TW", data);
            
            // Then
            Optional<OrderBookData> result = orderBookService.getLatest("2330.TW");
            assertThat(result).isPresent();
            assertThat(result.get().getTimestamp()).isNotNull();
        }
    }
    
    // ========== clearCache Tests ==========
    
    @Nested
    @DisplayName("clearCache()")
    class ClearCacheTests {
        
        @Test
        @DisplayName("should clear cached data for specific symbol")
        void shouldClearCachedDataForSymbol() {
            // Given - populate cache
            orderBookService.updateOrderBook("2330.TW", createOrderBookData("2330.TW", 590.0, 591.0));
            orderBookService.updateOrderBook("2454.TW", createOrderBookData("2454.TW", 1200.0, 1201.0));
            
            // When
            orderBookService.clearCache("2330.TW");
            
            // Then - 2330.TW cache should be empty, 2454.TW still in cache
            given(repository.findFirstBySymbolOrderByTimestampDesc("2330.TW"))
                    .willReturn(Optional.empty());
            assertThat(orderBookService.getLatest("2330.TW")).isEmpty();
            // 2454.TW should still be in cache
            assertThat(orderBookService.getLatest("2454.TW")).isPresent();
        }
        
        @Test
        @DisplayName("should handle clearing non-existent symbol")
        void shouldHandleClearingNonExistentSymbol() {
            // When/Then - should not throw
            orderBookService.clearCache("UNKNOWN.TW");
        }
    }
    
    // ========== Helper Methods ==========
    
    private OrderBookData createOrderBookData(String symbol, double bid, double ask) {
        OrderBookData data = OrderBookData.builder()
                .symbol(symbol)
                .bidPrice1(bid)
                .askPrice1(ask)
                .bidVolume1(100L)
                .askVolume1(100L)
                .spread(ask - bid)
                .spreadBps((ask - bid) / ((bid + ask) / 2) * 10000)
                .midPrice((bid + ask) / 2)
                .imbalance(0.0)
                .totalBidVolume(100L)
                .totalAskVolume(100L)
                .timestamp(LocalDateTime.now())
                .build();
        return data;
    }
    
    private OrderBookData createOrderBookDataWithImbalance(String symbol, double imbalance) {
        long bidVol = imbalance > 0 ? 200L : 100L;
        long askVol = imbalance < 0 ? 200L : 100L;
        return OrderBookData.builder()
                .symbol(symbol)
                .bidPrice1(590.0)
                .askPrice1(591.0)
                .bidVolume1(bidVol)
                .askVolume1(askVol)
                .imbalance(imbalance)
                .totalBidVolume(bidVol)
                .totalAskVolume(askVol)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
