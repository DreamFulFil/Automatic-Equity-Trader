package tw.gc.auto.equity.trader.bulk;

import de.bytefish.pgbulkinsert.mapping.AbstractMapping;
import tw.gc.auto.equity.trader.entities.Bar;

/**
 * PgBulkInsert mapping for Bar entity.
 * Uses PostgreSQL COPY protocol for high-performance bulk inserts.
 */
public class BarBulkInsertMapping extends AbstractMapping<Bar> {

    public BarBulkInsertMapping() {
        super("public", "bar");

        mapTimeStamp("timestamp", Bar::getTimestamp);
        mapVarChar("symbol", Bar::getSymbol);
        mapVarChar("market", Bar::getMarket);
        mapVarChar("timeframe", Bar::getTimeframe);
        mapDouble("open", Bar::getOpen);
        mapDouble("high", Bar::getHigh);
        mapDouble("low", Bar::getLow);
        mapDouble("close", Bar::getClose);
        mapLong("volume", Bar::getVolume);
        mapBoolean("is_complete", Bar::isComplete);
    }
}
