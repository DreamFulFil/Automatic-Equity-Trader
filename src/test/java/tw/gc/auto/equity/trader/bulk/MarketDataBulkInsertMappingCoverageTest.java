package tw.gc.auto.equity.trader.bulk;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataBulkInsertMappingCoverageTest {

    @Test
    void mapping_shouldExecuteTimeframeAndAssetTypeExtractors() {
        // Constructor execution covers the mapping lambdas registration lines.
        new MarketDataBulkInsertMapping();

        MarketData stock = MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2330.TW")
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(1.0)
                .high(1.0)
                .low(1.0)
                .close(1.0)
                .volume(1L)
                .assetType(MarketData.AssetType.STOCK)
                .build();

        MarketData nullAssetType = MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2330.TW")
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(1.0)
                .high(1.0)
                .low(1.0)
                .close(1.0)
                .volume(1L)
                .assetType(null)
                .build();

        assertEquals(MarketData.Timeframe.DAY_1.name(), MarketDataBulkInsertMapping.toTimeframe(stock));
        assertEquals(MarketData.AssetType.STOCK.name(), MarketDataBulkInsertMapping.toAssetType(stock));
        assertEquals("STOCK", MarketDataBulkInsertMapping.toAssetType(nullAssetType));
    }
}
