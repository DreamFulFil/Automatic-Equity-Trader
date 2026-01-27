package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FundamentalDataTest {

    @Test
    void getEarningsYield_calculatesCorrectly() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .peRatio(20.0)
                .build();

        // When
        Double earningsYield = data.getEarningsYield();

        // Then
        assertThat(earningsYield).isEqualTo(0.05); // 1/20 = 0.05 = 5%
    }

    @Test
    void getEarningsYield_returnsNullWhenPeIsZero() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .peRatio(0.0)
                .build();

        // When
        Double earningsYield = data.getEarningsYield();

        // Then
        assertThat(earningsYield).isNull();
    }

    @Test
    void getEarningsYield_returnsNullWhenPeIsNull() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .peRatio(null)
                .build();

        // When
        Double earningsYield = data.getEarningsYield();

        // Then
        assertThat(earningsYield).isNull();
    }

    @Test
    void getBookToMarket_calculatesCorrectly() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .pbRatio(4.0)
                .build();

        // When
        Double bookToMarket = data.getBookToMarket();

        // Then
        assertThat(bookToMarket).isEqualTo(0.25); // 1/4 = 0.25
    }

    @Test
    void getBookToMarket_returnsNullWhenPbIsZero() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .pbRatio(0.0)
                .build();

        // When
        Double bookToMarket = data.getBookToMarket();

        // Then
        assertThat(bookToMarket).isNull();
    }

    @Test
    void getAltmanZScoreProxy_calculatesWithAvailableData() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .currentRatio(2.5)
                .roe(0.25)
                .operatingMargin(0.40)
                .revenue(500000000000.0)
                .totalAssets(1000000000000.0)
                .marketCap(15000000000000.0)
                .totalDebt(100000000000.0)
                .build();

        // When
        Double zScore = data.getAltmanZScoreProxy();

        // Then
        assertThat(zScore).isNotNull();
        assertThat(zScore).isGreaterThan(0); // Healthy company should have positive Z-score
    }

    @Test
    void getAltmanZScoreProxy_returnsNullWhenNoAssets() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .totalAssets(null)
                .build();

        // When
        Double zScore = data.getAltmanZScoreProxy();

        // Then
        assertThat(zScore).isNull();
    }

    @Test
    void getAltmanZScoreProxy_returnsNullWhenAssetsAreZero() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .totalAssets(0.0)
                .build();

        // When
        Double zScore = data.getAltmanZScoreProxy();

        // Then
        assertThat(zScore).isNull();
    }

    @Test
    void equals_returnsTrueForSameId() {
        // Given
        FundamentalData data1 = FundamentalData.builder()
                .id(1L)
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .build();

        FundamentalData data2 = FundamentalData.builder()
                .id(1L)
                .symbol("DIFFERENT")
                .reportDate(LocalDate.now().minusDays(1))
                .build();

        // When/Then
        assertThat(data1).isEqualTo(data2);
    }

    @Test
    void equals_returnsTrueForSameSymbolAndDate() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 26);
        FundamentalData data1 = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(date)
                .peRatio(15.0)
                .build();

        FundamentalData data2 = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(date)
                .peRatio(20.0) // Different P/E but same symbol+date
                .build();

        // When/Then
        assertThat(data1).isEqualTo(data2);
    }

    @Test
    void equals_returnsFalseForDifferentSymbol() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 26);
        FundamentalData data1 = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(date)
                .build();

        FundamentalData data2 = FundamentalData.builder()
                .symbol("2454.TW")
                .reportDate(date)
                .build();

        // When/Then
        assertThat(data1).isNotEqualTo(data2);
    }

    @Test
    void hashCode_consistentForSameData() {
        // Given
        FundamentalData data1 = FundamentalData.builder()
                .id(1L)
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .build();

        FundamentalData data2 = FundamentalData.builder()
                .id(1L)
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .build();

        // When/Then
        assertThat(data1.hashCode()).isEqualTo(data2.hashCode());
    }

    @Test
    void builder_setsDefaultValues() {
        // Given/When
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .build();

        // Then
        assertThat(data.getDataSource()).isEqualTo("yfinance");
        assertThat(data.getCurrency()).isEqualTo("TWD");
    }

    @Test
    void prePersist_setsFetchedAtWhenNull() {
        // Given
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .fetchedAt(null)
                .build();

        // When
        data.onCreate();

        // Then
        assertThat(data.getFetchedAt()).isNotNull();
        assertThat(data.getFetchedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    void prePersist_doesNotOverwriteExistingFetchedAt() {
        // Given
        OffsetDateTime existingTime = OffsetDateTime.now().minusHours(1);
        FundamentalData data = FundamentalData.builder()
                .symbol("2330.TW")
                .reportDate(LocalDate.now())
                .fetchedAt(existingTime)
                .build();

        // When
        data.onCreate();

        // Then
        assertThat(data.getFetchedAt()).isEqualTo(existingTime);
    }

    @Test
    void allFieldsCanBeSet() {
        // Given/When
        FundamentalData data = FundamentalData.builder()
                .id(1L)
                .symbol("2330.TW")
                .name("Taiwan Semiconductor")
                .reportDate(LocalDate.now())
                .fetchedAt(OffsetDateTime.now())
                // Valuation
                .eps(5.5)
                .peRatio(15.5)
                .forwardPe(14.0)
                .pbRatio(4.2)
                .psRatio(8.5)
                .bookValue(35.0)
                .marketCap(15000000000000.0)
                .enterpriseValue(14500000000000.0)
                .evToEbitda(12.5)
                // Profitability
                .roe(0.28)
                .roa(0.15)
                .roic(0.22)
                .grossMargin(0.52)
                .operatingMargin(0.42)
                .netMargin(0.38)
                // Financial Health
                .debtToEquity(0.25)
                .currentRatio(2.5)
                .quickRatio(2.2)
                .totalDebt(100000000000.0)
                .totalCash(200000000000.0)
                // Dividends
                .dividendYield(0.025)
                .payoutRatio(0.35)
                .dividendRate(3.0)
                .dividendYears(15)
                // Cash Flow
                .operatingCashFlow(800000000000.0)
                .freeCashFlow(600000000000.0)
                .fcfPerShare(23.0)
                // Growth
                .revenue(2000000000000.0)
                .revenueGrowth(0.15)
                .earningsGrowth(0.20)
                .totalAssets(3000000000000.0)
                .assetGrowth(0.10)
                // Quality
                .accrualsRatio(0.02)
                .sharesOutstanding(26000000000L)
                .netStockIssuance(-50000000.0)
                // Analyst
                .analystCount(35)
                .targetPrice(700.0)
                .recommendationMean(1.8)
                // Beta & Range
                .beta(1.1)
                .fiftyTwoWeekHigh(720.0)
                .fiftyTwoWeekLow(450.0)
                // Metadata
                .dataSource("yfinance")
                .currency("TWD")
                .build();

        // Then - verify all fields are set
        assertThat(data.getId()).isEqualTo(1L);
        assertThat(data.getSymbol()).isEqualTo("2330.TW");
        assertThat(data.getName()).isEqualTo("Taiwan Semiconductor");
        assertThat(data.getEps()).isEqualTo(5.5);
        assertThat(data.getPeRatio()).isEqualTo(15.5);
        assertThat(data.getRoe()).isEqualTo(0.28);
        assertThat(data.getDividendYield()).isEqualTo(0.025);
        assertThat(data.getRevenueGrowth()).isEqualTo(0.15);
        assertThat(data.getAccrualsRatio()).isEqualTo(0.02);
        assertThat(data.getBeta()).isEqualTo(1.1);
    }
}
