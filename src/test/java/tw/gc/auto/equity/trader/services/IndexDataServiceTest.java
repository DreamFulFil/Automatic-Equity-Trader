package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.IndexData;
import tw.gc.auto.equity.trader.repositories.IndexDataRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IndexDataService.
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndexDataService Unit Tests")
class IndexDataServiceTest {

    @Mock
    private IndexDataRepository indexDataRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TelegramService telegramService;

    private ObjectMapper objectMapper;
    private IndexDataService indexDataService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        indexDataService = new IndexDataService(
                indexDataRepository,
                restTemplate,
                objectMapper,
                telegramService
        );
    }

    @Nested
    @DisplayName("getLatest()")
    class GetLatestTests {

        @Test
        void shouldReturnLatestIndexData() {
            IndexData expected = IndexData.builder()
                    .indexSymbol("^TWII")
                    .closeValue(23000.0)
                    .tradeDate(LocalDate.now())
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(expected));
            
            Optional<IndexData> result = indexDataService.getLatest("^TWII");
            
            assertThat(result).isPresent();
            assertThat(result.get().getCloseValue()).isEqualTo(23000.0);
        }

        @Test
        void shouldReturnEmptyWhenNoData() {
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.empty());
            
            Optional<IndexData> result = indexDataService.getLatest("^TWII");
            
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLatestTaiex()")
    class GetLatestTaiexTests {

        @Test
        void shouldReturnLatestTaiexData() {
            IndexData expected = IndexData.builder()
                    .indexSymbol("^TWII")
                    .closeValue(23500.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(expected));
            
            Optional<IndexData> result = indexDataService.getLatestTaiex();
            
            assertThat(result).isPresent();
            assertThat(result.get().getIndexSymbol()).isEqualTo("^TWII");
        }
    }

    @Nested
    @DisplayName("getCurrentValue()")
    class GetCurrentValueTests {

        @Test
        void shouldReturnCurrentIndexValue() {
            IndexData data = IndexData.builder()
                    .indexSymbol("^TWII")
                    .closeValue(23000.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            Double value = indexDataService.getCurrentValue("^TWII");
            
            assertThat(value).isEqualTo(23000.0);
        }

        @Test
        void shouldReturnNullWhenNoData() {
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.empty());
            
            Double value = indexDataService.getCurrentValue("^TWII");
            
            assertThat(value).isNull();
        }
    }

    @Nested
    @DisplayName("getCurrentTaiexValue()")
    class GetCurrentTaiexValueTests {

        @Test
        void shouldReturnCurrentTaiexValue() {
            IndexData data = IndexData.builder()
                    .closeValue(24000.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            Double value = indexDataService.getCurrentTaiexValue();
            
            assertThat(value).isEqualTo(24000.0);
        }
    }

    @Nested
    @DisplayName("getDailyReturn()")
    class GetDailyReturnTests {

        @Test
        void shouldReturnDailyReturn() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .previousClose(22900.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            Double ret = indexDataService.getDailyReturn("^TWII");
            
            assertThat(ret).isCloseTo(0.00437, within(0.0001));
        }
    }

    @Nested
    @DisplayName("getAverageReturn()")
    class GetAverageReturnTests {

        @Test
        void shouldCalculateAverageReturn() {
            when(indexDataRepository.calculateAverageReturn(eq("^TWII"), any(), any()))
                    .thenReturn(0.001);
            
            Double avgReturn = indexDataService.getAverageReturn("^TWII", 60);
            
            assertThat(avgReturn).isEqualTo(0.001);
        }
    }

    @Nested
    @DisplayName("getVolatility()")
    class GetVolatilityTests {

        @Test
        void shouldCalculateAnnualizedVolatility() {
            // Daily std dev of 0.01 (1%)
            when(indexDataRepository.calculateReturnStdDev(eq("^TWII"), any(), any()))
                    .thenReturn(0.01);
            
            Double volatility = indexDataService.getVolatility("^TWII", 20);
            
            // Annualized: 0.01 * sqrt(252) ≈ 0.159
            assertThat(volatility).isCloseTo(0.159, within(0.01));
        }

        @Test
        void shouldReturnNullWhenNoStdDev() {
            when(indexDataRepository.calculateReturnStdDev(eq("^TWII"), any(), any()))
                    .thenReturn(null);
            
            Double volatility = indexDataService.getVolatility("^TWII", 20);
            
            assertThat(volatility).isNull();
        }
    }

    @Nested
    @DisplayName("isBullMarket()")
    class IsBullMarketTests {

        @Test
        void shouldReturnTrueWhenBullMarket() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            assertThat(indexDataService.isBullMarket()).isTrue();
        }

        @Test
        void shouldReturnFalseWhenBearMarket() {
            IndexData data = IndexData.builder()
                    .closeValue(20000.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            assertThat(indexDataService.isBullMarket()).isFalse();
        }

        @Test
        void shouldReturnFalseWhenNoData() {
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.empty());
            
            assertThat(indexDataService.isBullMarket()).isFalse();
        }
    }

    @Nested
    @DisplayName("getMarketTrend()")
    class GetMarketTrendTests {

        @Test
        void shouldReturnBullishTrend() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            assertThat(indexDataService.getMarketTrend()).isEqualTo(1);
        }

        @Test
        void shouldReturnBearishTrend() {
            IndexData data = IndexData.builder()
                    .closeValue(20000.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.of(data));
            
            assertThat(indexDataService.getMarketTrend()).isEqualTo(-1);
        }

        @Test
        void shouldReturnNeutralWhenNoData() {
            when(indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc("^TWII"))
                    .thenReturn(Optional.empty());
            
            assertThat(indexDataService.getMarketTrend()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getHistorical()")
    class GetHistoricalTests {

        @Test
        void shouldReturnHistoricalData() {
            List<IndexData> expected = List.of(
                    IndexData.builder().closeValue(22000.0).tradeDate(LocalDate.now().minusDays(2)).build(),
                    IndexData.builder().closeValue(22500.0).tradeDate(LocalDate.now().minusDays(1)).build(),
                    IndexData.builder().closeValue(23000.0).tradeDate(LocalDate.now()).build()
            );
            
            when(indexDataRepository.findRecentBySymbol("^TWII", 60))
                    .thenReturn(expected);
            
            List<IndexData> result = indexDataService.getHistorical("^TWII", 60);
            
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("fetchAndSaveForSymbol()")
    class FetchAndSaveTests {

        @Test
        void shouldFetchAndSaveIndexData() throws Exception {
            String jsonResponse = """
                {
                    "status": "success",
                    "symbol": "^TWII",
                    "name": "TAIEX",
                    "trade_date": "2026-01-26",
                    "open": 22800.0,
                    "high": 23100.0,
                    "low": 22750.0,
                    "close": 23000.0,
                    "previous_close": 22900.0,
                    "volume": 5000000000
                }
                """;
            
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);
            when(indexDataRepository.existsByIndexSymbolAndTradeDate(anyString(), any()))
                    .thenReturn(false);
            when(indexDataRepository.save(any(IndexData.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            
            Optional<IndexData> result = indexDataService.fetchAndSaveForSymbol("^TWII");
            
            assertThat(result).isPresent();
            assertThat(result.get().getCloseValue()).isEqualTo(23000.0);
            verify(indexDataRepository).save(any(IndexData.class));
        }

        @Test
        void shouldHandleApiError() {
            String jsonResponse = """
                {
                    "error": "Symbol not found"
                }
                """;
            
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);
            
            Optional<IndexData> result = indexDataService.fetchAndSaveForSymbol("INVALID");
            
            assertThat(result).isEmpty();
        }

        @Test
        void shouldHandleEmptyResponse() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn("");
            
            Optional<IndexData> result = indexDataService.fetchAndSaveForSymbol("^TWII");
            
            assertThat(result).isEmpty();
        }

        @Test
        void shouldRetryOnRestClientException() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenThrow(new RestClientException("Connection failed"))
                    .thenThrow(new RestClientException("Connection failed"))
                    .thenThrow(new RestClientException("Connection failed"));
            
            Optional<IndexData> result = indexDataService.fetchAndSaveForSymbol("^TWII");
            
            assertThat(result).isEmpty();
            verify(restTemplate, times(3)).getForObject(anyString(), eq(String.class));
        }

        @Test
        void shouldUpdateExistingRecord() throws Exception {
            String jsonResponse = """
                {
                    "symbol": "^TWII",
                    "trade_date": "2026-01-26",
                    "close": 23000.0
                }
                """;
            
            IndexData existing = IndexData.builder()
                    .id(1L)
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.of(2026, 1, 26))
                    .closeValue(22900.0)
                    .fetchedAt(OffsetDateTime.now().minusHours(1))
                    .build();
            
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);
            when(indexDataRepository.existsByIndexSymbolAndTradeDate("^TWII", LocalDate.of(2026, 1, 26)))
                    .thenReturn(true);
            when(indexDataRepository.findByIndexSymbolAndTradeDate("^TWII", LocalDate.of(2026, 1, 26)))
                    .thenReturn(Optional.of(existing));
            when(indexDataRepository.save(any(IndexData.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            
            Optional<IndexData> result = indexDataService.fetchAndSaveForSymbol("^TWII");
            
            assertThat(result).isPresent();
            assertThat(result.get().getCloseValue()).isEqualTo(23000.0);
        }
    }

    @Nested
    @DisplayName("manualRefresh()")
    class ManualRefreshTests {

        @Test
        void shouldRefreshAllIndices() {
            String jsonResponse = """
                {
                    "symbol": "^TWII",
                    "close": 23000.0,
                    "trade_date": "2026-01-26"
                }
                """;
            
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);
            when(indexDataRepository.existsByIndexSymbolAndTradeDate(anyString(), any()))
                    .thenReturn(false);
            when(indexDataRepository.save(any(IndexData.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            
            IndexDataService.RefreshResult result = indexDataService.manualRefresh();
            
            assertThat(result.success()).isGreaterThan(0);
        }
    }
}
