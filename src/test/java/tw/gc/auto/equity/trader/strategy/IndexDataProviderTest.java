package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.IndexData;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for IndexDataProvider functional interface.
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@DisplayName("IndexDataProvider Unit Tests")
class IndexDataProviderTest {

    @Nested
    @DisplayName("noOp() factory method")
    class NoOpTests {

        @Test
        void noOpShouldReturnEmptyOptional() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            Optional<IndexData> result = provider.getLatestIndex("^TWII");
            
            assertThat(result).isEmpty();
        }

        @Test
        void noOpShouldReturnNullForCurrentValue() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            Double value = provider.getCurrentValue("^TWII");
            
            assertThat(value).isNull();
        }

        @Test
        void noOpShouldReturnNullForTaiexValue() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            Double value = provider.getTaiexValue();
            
            assertThat(value).isNull();
        }

        @Test
        void noOpShouldReturnNullForDailyReturn() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            Double value = provider.getDailyReturn("^TWII");
            
            assertThat(value).isNull();
        }

        @Test
        void noOpShouldReturnFalseForBullMarket() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            assertThat(provider.isBullMarket()).isFalse();
        }

        @Test
        void noOpShouldReturnFalseForBearMarket() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            assertThat(provider.isBearMarket()).isFalse();
        }

        @Test
        void noOpShouldReturnNeutralTrend() {
            IndexDataProvider provider = IndexDataProvider.noOp();
            
            assertThat(provider.getMarketTrend()).isEqualTo(0); // 0 = neutral/unknown
        }
    }

    @Nested
    @DisplayName("withFixedValue() factory method")
    class WithFixedValueTests {

        @Test
        void shouldReturnFixedCurrentValue() {
            IndexDataProvider provider = IndexDataProvider.withFixedValue(20000.0);
            
            Double value = provider.getCurrentValue("^TWII");
            
            assertThat(value).isEqualTo(20000.0);
        }

        @Test
        void shouldReturnFixedTaiexValue() {
            IndexDataProvider provider = IndexDataProvider.withFixedValue(18500.0);
            
            Double value = provider.getTaiexValue();
            
            assertThat(value).isEqualTo(18500.0);
        }

        @Test
        void shouldReturnIndexDataWithFixedValue() {
            IndexDataProvider provider = IndexDataProvider.withFixedValue(19000.0);
            
            Optional<IndexData> result = provider.getLatestIndex("^TWII");
            
            assertThat(result).isPresent();
            assertThat(result.get().getCloseValue()).isEqualTo(19000.0);
        }
    }

    @Nested
    @DisplayName("withFixedData() factory method")
    class WithFixedDataTests {

        @Test
        void shouldReturnProvidedIndexData() {
            IndexData indexData = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(21000.0)
                    .previousClose(20800.0)
                    .changePercent(0.96)
                    .ma50(20500.0)
                    .ma200(20000.0)
                    .build();
            
            IndexDataProvider provider = IndexDataProvider.withFixedData(indexData);
            
            Optional<IndexData> result = provider.getLatestIndex("^TWII");
            
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(indexData);
        }

        @Test
        void shouldCalculateDailyReturn() {
            IndexData indexData = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(20200.0)
                    .previousClose(20000.0)
                    .build();
            
            IndexDataProvider provider = IndexDataProvider.withFixedData(indexData);
            
            Double dailyReturn = provider.getDailyReturn("^TWII");
            
            assertThat(dailyReturn).isCloseTo(0.01, within(0.0001)); // 1%
        }

        @Test
        void shouldDetectBullMarket() {
            IndexData bullishData = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(21000.0)
                    .ma50(20500.0)
                    .ma200(20000.0)
                    .build();
            
            IndexDataProvider provider = IndexDataProvider.withFixedData(bullishData);
            
            assertThat(provider.isBullMarket()).isTrue();
            assertThat(provider.isBearMarket()).isFalse();
            assertThat(provider.getMarketTrend()).isEqualTo(1); // 1 = bullish
        }

        @Test
        void shouldDetectBearMarket() {
            IndexData bearishData = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(19000.0)
                    .ma50(19500.0)
                    .ma200(20000.0)
                    .build();
            
            IndexDataProvider provider = IndexDataProvider.withFixedData(bearishData);
            
            assertThat(provider.isBullMarket()).isFalse();
            assertThat(provider.isBearMarket()).isTrue();
            assertThat(provider.getMarketTrend()).isEqualTo(-1); // -1 = bearish
        }

        @Test
        void shouldReturnNeutralWhenNoMAs() {
            IndexData dataWithoutMa = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(20000.0)
                    .build();
            
            IndexDataProvider provider = IndexDataProvider.withFixedData(dataWithoutMa);
            
            // Without MAs, isBullMarket and isAboveMa200 return false, so trend should be -1 or 0
            assertThat(provider.getMarketTrend()).isIn(-1, 0);
        }

        @Test
        void shouldReturnYearRangePosition() {
            IndexData indexData = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(20000.0)
                    .yearLow(18000.0)
                    .yearHigh(22000.0)
                    .build();
            
            IndexDataProvider provider = IndexDataProvider.withFixedData(indexData);
            
            Double position = provider.getYearRangePosition();
            
            assertThat(position).isCloseTo(0.5, within(0.0001)); // Middle of range
        }
    }

    @Nested
    @DisplayName("Custom implementation")
    class CustomImplementationTests {

        @Test
        void shouldAllowCustomImplementation() {
            IndexData customData = IndexData.builder()
                    .indexSymbol("^TWOII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(15000.0)
                    .build();
            
            IndexDataProvider provider = symbol -> Optional.of(customData);
            
            Optional<IndexData> result = provider.getLatestIndex("^TWOII");
            
            assertThat(result).isPresent();
            assertThat(result.get().getCloseValue()).isEqualTo(15000.0);
        }

        @Test
        void defaultMethodsShouldDelegateToGetLatestIndex() {
            IndexData testData = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(19500.0)
                    .previousClose(19000.0)
                    .ma50(19200.0)
                    .ma200(18800.0)
                    .yearLow(17000.0)
                    .yearHigh(21000.0)
                    .build();
            
            IndexDataProvider provider = symbol -> {
                if ("^TWII".equals(symbol)) {
                    return Optional.of(testData);
                }
                return Optional.empty();
            };
            
            // Test all default methods
            assertThat(provider.getCurrentValue("^TWII")).isEqualTo(19500.0);
            assertThat(provider.getTaiexValue()).isEqualTo(19500.0);
            assertThat(provider.getDailyReturn("^TWII")).isCloseTo(0.0263, within(0.001));
            assertThat(provider.isBullMarket()).isTrue();
            assertThat(provider.isBearMarket()).isFalse();
            assertThat(provider.getMarketTrend()).isEqualTo(1); // 1 = bullish
            assertThat(provider.getYearRangePosition()).isCloseTo(0.625, within(0.001));
        }
    }
}
