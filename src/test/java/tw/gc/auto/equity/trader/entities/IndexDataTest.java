package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for IndexData entity.
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@DisplayName("IndexData Entity Unit Tests")
class IndexDataTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        
        @Test
        void shouldBuildBasicIndexData() {
            IndexData data = IndexData.builder()
                    .indexSymbol("^TWII")
                    .indexName("TAIEX")
                    .tradeDate(LocalDate.of(2026, 1, 26))
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(23000.0)
                    .build();
            
            assertThat(data.getIndexSymbol()).isEqualTo("^TWII");
            assertThat(data.getIndexName()).isEqualTo("TAIEX");
            assertThat(data.getCloseValue()).isEqualTo(23000.0);
        }
        
        @Test
        void shouldBuildCompleteIndexData() {
            IndexData data = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.of(2026, 1, 26))
                    .fetchedAt(OffsetDateTime.now())
                    .openValue(22800.0)
                    .highValue(23100.0)
                    .lowValue(22750.0)
                    .closeValue(23000.0)
                    .previousClose(22900.0)
                    .changePoints(100.0)
                    .changePercent(0.0044)
                    .volume(5000000000L)
                    .yearHigh(24000.0)
                    .yearLow(20000.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            assertThat(data.getOpenValue()).isEqualTo(22800.0);
            assertThat(data.getHighValue()).isEqualTo(23100.0);
            assertThat(data.getLowValue()).isEqualTo(22750.0);
            assertThat(data.getVolume()).isEqualTo(5000000000L);
        }
    }

    @Nested
    @DisplayName("getDailyReturn()")
    class DailyReturnTests {
        
        @Test
        void shouldCalculateDailyReturn() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .previousClose(22900.0)
                    .build();
            
            Double ret = data.getDailyReturn();
            assertThat(ret).isCloseTo(0.00437, within(0.0001));
        }
        
        @Test
        void shouldReturnNullWhenPreviousCloseIsNull() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .build();
            
            assertThat(data.getDailyReturn()).isNull();
        }
        
        @Test
        void shouldReturnNullWhenPreviousCloseIsZero() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .previousClose(0.0)
                    .build();
            
            assertThat(data.getDailyReturn()).isNull();
        }
    }

    @Nested
    @DisplayName("Moving Average Comparisons")
    class MovingAverageTests {
        
        @Test
        void shouldDetectAboveMa50() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .ma50(22500.0)
                    .build();
            
            assertThat(data.isAboveMa50()).isTrue();
        }
        
        @Test
        void shouldDetectBelowMa50() {
            IndexData data = IndexData.builder()
                    .closeValue(22000.0)
                    .ma50(22500.0)
                    .build();
            
            assertThat(data.isAboveMa50()).isFalse();
        }
        
        @Test
        void shouldDetectAboveMa200() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .ma200(21000.0)
                    .build();
            
            assertThat(data.isAboveMa200()).isTrue();
        }
        
        @Test
        void shouldDetectBullMarket() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            assertThat(data.isBullMarket()).isTrue();
        }
        
        @Test
        void shouldNotBeBullMarketWhenBelowMa200() {
            IndexData data = IndexData.builder()
                    .closeValue(20500.0)
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            assertThat(data.isBullMarket()).isFalse();
        }
        
        @Test
        void shouldDetectGoldenCross() {
            IndexData data = IndexData.builder()
                    .ma50(22500.0)
                    .ma200(21000.0)
                    .build();
            
            assertThat(data.hasGoldenCross()).isTrue();
            assertThat(data.hasDeathCross()).isFalse();
        }
        
        @Test
        void shouldDetectDeathCross() {
            IndexData data = IndexData.builder()
                    .ma50(20500.0)
                    .ma200(21000.0)
                    .build();
            
            assertThat(data.hasDeathCross()).isTrue();
            assertThat(data.hasGoldenCross()).isFalse();
        }
    }

    @Nested
    @DisplayName("Year Range Calculations")
    class YearRangeTests {
        
        @Test
        void shouldCalculateDistanceFromYearHigh() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .yearHigh(24000.0)
                    .build();
            
            Double distance = data.getDistanceFromYearHigh();
            assertThat(distance).isCloseTo(-0.0417, within(0.001));
        }
        
        @Test
        void shouldCalculateDistanceFromYearLow() {
            IndexData data = IndexData.builder()
                    .closeValue(23000.0)
                    .yearLow(20000.0)
                    .build();
            
            Double distance = data.getDistanceFromYearLow();
            assertThat(distance).isCloseTo(0.15, within(0.001));
        }
        
        @Test
        void shouldDetectNearYearHigh() {
            IndexData data = IndexData.builder()
                    .closeValue(23800.0)
                    .yearHigh(24000.0)
                    .build();
            
            assertThat(data.isNearYearHigh(0.02)).isTrue();
            assertThat(data.isNearYearHigh(0.005)).isFalse();
        }
        
        @Test
        void shouldDetectNearYearLow() {
            IndexData data = IndexData.builder()
                    .closeValue(20200.0)
                    .yearLow(20000.0)
                    .build();
            
            assertThat(data.isNearYearLow(0.02)).isTrue();
        }
        
        @Test
        void shouldCalculateYearRangePosition() {
            IndexData data = IndexData.builder()
                    .closeValue(22000.0)
                    .yearHigh(24000.0)
                    .yearLow(20000.0)
                    .build();
            
            Double position = data.getYearRangePosition();
            assertThat(position).isEqualTo(0.5); // Midpoint
        }
        
        @Test
        void shouldReturnZeroPointFiveForEqualHighAndLow() {
            IndexData data = IndexData.builder()
                    .closeValue(22000.0)
                    .yearHigh(22000.0)
                    .yearLow(22000.0)
                    .build();
            
            assertThat(data.getYearRangePosition()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Daily Change Detection")
    class DailyChangeTests {
        
        @Test
        void shouldDetectPositiveDay() {
            IndexData data = IndexData.builder()
                    .changePercent(0.01)
                    .build();
            
            assertThat(data.isPositiveDay()).isTrue();
            assertThat(data.isNegativeDay()).isFalse();
        }
        
        @Test
        void shouldDetectNegativeDay() {
            IndexData data = IndexData.builder()
                    .changePercent(-0.01)
                    .build();
            
            assertThat(data.isNegativeDay()).isTrue();
            assertThat(data.isPositiveDay()).isFalse();
        }
        
        @Test
        void shouldDetectSignificantMove() {
            IndexData data = IndexData.builder()
                    .changePercent(0.02)
                    .build();
            
            assertThat(data.hasSignificantMove(0.01)).isTrue();
            assertThat(data.hasSignificantMove(0.03)).isFalse();
        }
        
        @Test
        void shouldDetectSignificantMoveForNegativeChange() {
            IndexData data = IndexData.builder()
                    .changePercent(-0.025)
                    .build();
            
            assertThat(data.hasSignificantMove(0.02)).isTrue();
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {
        
        @Test
        void shouldFormatToString() {
            IndexData data = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.of(2026, 1, 26))
                    .closeValue(23000.0)
                    .changePercent(0.0044)
                    .build();
            
            String str = data.toString();
            assertThat(str).contains("^TWII");
            assertThat(str).contains("2026-01-26");
            assertThat(str).contains("23000");
        }
        
        @Test
        void shouldHandleNullValues() {
            IndexData data = IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(LocalDate.of(2026, 1, 26))
                    .build();
            
            assertThatCode(() -> data.toString()).doesNotThrowAnyException();
        }
    }
}
