package tw.gc.auto.equity.trader.bulk;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PgBulkInsert mapping classes.
 * Tests mapping instantiation and configuration.
 */
class BulkInsertMappingTest {

    @Test
    void barBulkInsertMapping_shouldInstantiateCorrectly() {
        BarBulkInsertMapping mapping = new BarBulkInsertMapping();
        assertThat(mapping).isNotNull();
    }

    @Test
    void marketDataBulkInsertMapping_shouldInstantiateCorrectly() {
        MarketDataBulkInsertMapping mapping = new MarketDataBulkInsertMapping();
        assertThat(mapping).isNotNull();
    }

    @Test
    void barEntity_shouldHaveRequiredFieldsForBulkInsert() {
        Bar bar = Bar.builder()
            .timestamp(LocalDateTime.now())
            .symbol("2330.TW")
            .market("TSE")
            .timeframe("1day")
            .open(580.0)
            .high(590.0)
            .low(575.0)
            .close(585.0)
            .volume(10000000L)
            .isComplete(true)
            .build();

        assertThat(bar.getTimestamp()).isNotNull();
        assertThat(bar.getSymbol()).isEqualTo("2330.TW");
        assertThat(bar.getMarket()).isEqualTo("TSE");
        assertThat(bar.getTimeframe()).isEqualTo("1day");
        assertThat(bar.getOpen()).isEqualTo(580.0);
        assertThat(bar.getHigh()).isEqualTo(590.0);
        assertThat(bar.getLow()).isEqualTo(575.0);
        assertThat(bar.getClose()).isEqualTo(585.0);
        assertThat(bar.getVolume()).isEqualTo(10000000L);
        assertThat(bar.isComplete()).isTrue();
    }

    @Test
    void marketDataEntity_shouldHaveRequiredFieldsForBulkInsert() {
        MarketData marketData = MarketData.builder()
            .timestamp(LocalDateTime.now())
            .symbol("2454.TW")
            .timeframe(MarketData.Timeframe.DAY_1)
            .open(900.0)
            .high(920.0)
            .low(895.0)
            .close(915.0)
            .volume(5000000L)
            .assetType(MarketData.AssetType.STOCK)
            .build();

        assertThat(marketData.getTimestamp()).isNotNull();
        assertThat(marketData.getSymbol()).isEqualTo("2454.TW");
        assertThat(marketData.getTimeframe()).isEqualTo(MarketData.Timeframe.DAY_1);
        assertThat(marketData.getOpen()).isEqualTo(900.0);
        assertThat(marketData.getHigh()).isEqualTo(920.0);
        assertThat(marketData.getLow()).isEqualTo(895.0);
        assertThat(marketData.getClose()).isEqualTo(915.0);
        assertThat(marketData.getVolume()).isEqualTo(5000000L);
        assertThat(marketData.getAssetType()).isEqualTo(MarketData.AssetType.STOCK);
    }

    @Test
    void marketDataEntity_shouldDefaultAssetTypeToStock() {
        MarketData marketData = MarketData.builder()
            .timestamp(LocalDateTime.now())
            .symbol("2330.TW")
            .timeframe(MarketData.Timeframe.DAY_1)
            .open(580.0)
            .high(590.0)
            .low(575.0)
            .close(585.0)
            .volume(10000000L)
            .build();

        assertThat(marketData.getAssetType()).isEqualTo(MarketData.AssetType.STOCK);
    }
}
