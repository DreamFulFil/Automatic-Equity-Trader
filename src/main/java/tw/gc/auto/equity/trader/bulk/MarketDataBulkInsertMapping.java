package tw.gc.auto.equity.trader.bulk;

import de.bytefish.pgbulkinsert.mapping.AbstractMapping;
import tw.gc.auto.equity.trader.entities.MarketData;

/**
 * PgBulkInsert mapping for MarketData entity.
 * Uses PostgreSQL COPY protocol for high-performance bulk inserts.
 */
public class MarketDataBulkInsertMapping extends AbstractMapping<MarketData> {

    public MarketDataBulkInsertMapping() {
        super("public", "market_data");

        mapTimeStamp("timestamp", MarketData::getTimestamp);
        mapVarChar("symbol", MarketData::getSymbol);
        mapVarChar("timeframe", MarketDataBulkInsertMapping::toTimeframe);
        mapDouble("open_price", MarketData::getOpen);
        mapDouble("high_price", MarketData::getHigh);
        mapDouble("low_price", MarketData::getLow);
        mapDouble("close_price", MarketData::getClose);
        mapLong("volume", MarketData::getVolume);
        mapVarChar("asset_type", MarketDataBulkInsertMapping::toAssetType);
    }

    static String toTimeframe(MarketData md) {
        return md.getTimeframe().name();
    }

    static String toAssetType(MarketData md) {
        return md.getAssetType() != null ? md.getAssetType().name() : "STOCK";
    }
}
