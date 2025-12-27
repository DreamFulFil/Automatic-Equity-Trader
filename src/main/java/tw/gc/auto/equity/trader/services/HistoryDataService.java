package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoryDataService {

    private final BarRepository barRepository;
    private final MarketDataRepository marketDataRepository;

    @Transactional
    public DownloadResult downloadHistoricalData(String symbol, int years) throws IOException {
        log.info("📥 Downloading {} years of historical data for {}", years, symbol);

        Calendar from = Calendar.getInstance();
        from.add(Calendar.YEAR, -years);
        Calendar to = Calendar.getInstance();

        Stock stock = YahooFinance.get(symbol, from, to, Interval.DAILY);
        List<HistoricalQuote> history = stock.getHistory();

        if (history == null || history.isEmpty()) {
            throw new IOException("No historical data available for " + symbol);
        }

        int inserted = 0;
        int skipped = 0;

        for (HistoricalQuote quote : history) {
            LocalDateTime timestamp = quote.getDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (barRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, "1day")) {
                skipped++;
                continue;
            }

            BigDecimal open = quote.getOpen();
            BigDecimal high = quote.getHigh();
            BigDecimal low = quote.getLow();
            BigDecimal close = quote.getClose();
            Long volume = quote.getVolume();

            if (open == null || close == null) {
                skipped++;
                continue;
            }

            Bar bar = new Bar();
            bar.setTimestamp(timestamp);
            bar.setSymbol(symbol);
            bar.setMarket("TSE");
            bar.setTimeframe("1day");
            bar.setOpen(open.doubleValue());
            bar.setHigh(high != null ? high.doubleValue() : open.doubleValue());
            bar.setLow(low != null ? low.doubleValue() : open.doubleValue());
            bar.setClose(close.doubleValue());
            bar.setVolume(volume != null ? volume : 0L);
            bar.setComplete(true);
            barRepository.save(bar);

            if (!marketDataRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, MarketData.Timeframe.DAY_1)) {
                MarketData marketData = MarketData.builder()
                        .timestamp(timestamp)
                        .symbol(symbol)
                        .timeframe(MarketData.Timeframe.DAY_1)
                        .open(open.doubleValue())
                        .high(high != null ? high.doubleValue() : open.doubleValue())
                        .low(low != null ? low.doubleValue() : open.doubleValue())
                        .close(close.doubleValue())
                        .volume(volume != null ? volume : 0L)
                        .build();
                marketDataRepository.save(marketData);
            }

            inserted++;
        }

        log.info("✅ Downloaded {} records for {} ({} inserted, {} skipped)", history.size(), symbol, inserted, skipped);
        return new DownloadResult(symbol, history.size(), inserted, skipped);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DownloadResult {
        private String symbol;
        private int totalRecords;
        private int inserted;
        private int skipped;
    }
}
