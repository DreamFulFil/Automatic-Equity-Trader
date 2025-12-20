package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class HistoryDataService {

    private final BarRepository barRepository;
    private final MarketDataRepository marketDataRepository;
    private final StrategyStockMappingRepository strategyStockMappingRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    
    // Flag to ensure truncation happens only once per backtest run
    private final AtomicBoolean tablesCleared = new AtomicBoolean(false);

    @Value("${python.bridge.url:http://localhost:8888}")
    private String pythonBridgeUrl;
    
    public HistoryDataService(BarRepository barRepository, 
                              MarketDataRepository marketDataRepository,
                              StrategyStockMappingRepository strategyStockMappingRepository,
                              RestTemplate restTemplate, 
                              ObjectMapper objectMapper,
                              DataSource dataSource) {
        this.barRepository = barRepository;
        this.marketDataRepository = marketDataRepository;
        this.strategyStockMappingRepository = strategyStockMappingRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    /**
     * Download historical data by calling Python API asynchronously
     * Batches requests in 365-day chunks, defaults to 10 years
     * Uses Phaser for synchronization
     * 
     * Performs TRUNCATE on historical data tables before first ingestion to ensure 
     * clean 10-year window for backtesting.
     * 
     * @param symbol Stock symbol (e.g., "2330.TW")
     * @param years Number of years of history (default: 10)
     * @return DownloadResult with statistics
     */
    @Transactional
    public DownloadResult downloadHistoricalData(String symbol, int years) throws IOException {
        log.info("📥 Downloading {} years of historical data for {} via Python API", years, symbol);
        
        // Truncate tables once per backtest run for clean 10-year data window
        truncateTablesIfNeeded();

        // Calculate date ranges for batching (365 days per batch)
        LocalDateTime endDate = LocalDateTime.now();
        int daysPerBatch = 365;
        int totalBatches = years; // 10 years = 10 batches of 365 days
        
        // Phaser for synchronizing all batches
        Phaser phaser = new Phaser(1); // Register main thread
        
        // Store all downloaded data from all batches
        List<HistoricalDataPoint> allData = new ArrayList<>();
        Object dataLock = new Object();
        
        // Launch async downloads for each batch
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int batch = 0; batch < totalBatches; batch++) {
            final int batchNumber = batch;
            final LocalDateTime batchEnd = endDate.minusDays((long) batch * daysPerBatch);
            final LocalDateTime batchStart = batchEnd.minusDays(daysPerBatch);
            
            phaser.register(); // Register this batch task
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    log.info("📥 Batch {}/{}: Downloading {} from {} to {}", 
                        batchNumber + 1, totalBatches, symbol, 
                        batchStart.format(DateTimeFormatter.ISO_DATE),
                        batchEnd.format(DateTimeFormatter.ISO_DATE));
                    
                    List<HistoricalDataPoint> batchData = downloadBatch(symbol, batchStart, batchEnd);
                    
                    synchronized (dataLock) {
                        allData.addAll(batchData);
                    }
                    
                    log.info("✅ Batch {}/{}: Downloaded {} records for {}", 
                        batchNumber + 1, totalBatches, batchData.size(), symbol);
                    
                } catch (Exception e) {
                    log.error("❌ Batch {}/{}: Failed to download data for {}: {}", 
                        batchNumber + 1, totalBatches, symbol, e.getMessage());
                } finally {
                    phaser.arriveAndDeregister(); // Signal batch completion
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all batches to complete
        phaser.arriveAndAwaitAdvance(); // Wait for all registered parties
        
        // Wait for all futures to ensure completion
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("✅ All {} batches completed. Total records downloaded: {}", totalBatches, allData.size());
        
        // Sort aggregated data by timestamp (descending order - newest first)
        allData.sort(Comparator.comparing(HistoricalDataPoint::getTimestamp).reversed());
        
        log.info("📊 Sorted {} records by timestamp (descending)", allData.size());
        
        // Batch insert into database
        int inserted = batchInsertToDatabase(symbol, allData);
        int skipped = allData.size() - inserted;
        
        log.info("✅ Downloaded {} records for {} ({} inserted, {} skipped)", 
            allData.size(), symbol, inserted, skipped);
        
        return new DownloadResult(symbol, allData.size(), inserted, skipped);
    }

    /**
     * Download a single batch of data by calling Python API
     */
    private List<HistoricalDataPoint> downloadBatch(String symbol, LocalDateTime start, LocalDateTime end) {
        try {
            String url = pythonBridgeUrl + "/data/download-batch";
            
            Map<String, Object> request = new HashMap<>();
            request.put("symbol", symbol);
            request.put("start_date", start.format(DateTimeFormatter.ISO_DATE_TIME));
            request.put("end_date", end.format(DateTimeFormatter.ISO_DATE_TIME));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseHistoricalData(response.getBody());
            } else {
                log.warn("⚠️ Python API returned non-success status: {}", response.getStatusCode());
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to download batch for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse historical data from Python API response
     */
    private List<HistoricalDataPoint> parseHistoricalData(Map<String, Object> responseBody) {
        List<HistoricalDataPoint> dataPoints = new ArrayList<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            
            if (data == null) {
                log.warn("⚠️ No data field in Python API response");
                return dataPoints;
            }
            
            for (Map<String, Object> point : data) {
                HistoricalDataPoint dataPoint = new HistoricalDataPoint();
                dataPoint.setTimestamp(LocalDateTime.parse((String) point.get("timestamp")));
                dataPoint.setOpen(((Number) point.get("open")).doubleValue());
                dataPoint.setHigh(((Number) point.get("high")).doubleValue());
                dataPoint.setLow(((Number) point.get("low")).doubleValue());
                dataPoint.setClose(((Number) point.get("close")).doubleValue());
                dataPoint.setVolume(((Number) point.get("volume")).longValue());
                
                dataPoints.add(dataPoint);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to parse historical data: {}", e.getMessage());
        }
        
        return dataPoints;
    }

    /**
     * Batch insert historical data into Bar and MarketData tables
     * Uses PostgreSQL COPY for ultra-fast bulk inserts (10K rows per batch)
     */
    private int batchInsertToDatabase(String symbol, List<HistoricalDataPoint> data) {
        int batchSize = 10000; // 10K rows per COPY operation
        int totalInserted = 0;
        
        try (Connection conn = dataSource.getConnection()) {
            BaseConnection pgConn = conn.unwrap(BaseConnection.class);
            CopyManager copyManager = new CopyManager(pgConn);
            
            // Process in batches of 10K
            for (int i = 0; i < data.size(); i += batchSize) {
                int end = Math.min(i + batchSize, data.size());
                List<HistoricalDataPoint> batch = data.subList(i, end);
                
                // Build CSV for bar table
                StringBuilder barCsv = new StringBuilder();
                StringBuilder marketDataCsv = new StringBuilder();
                
                for (HistoricalDataPoint point : batch) {
                    // Bar CSV: timestamp, symbol, market, timeframe, open, high, low, close, volume, complete
                    barCsv.append(point.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                          .append("\t").append(symbol)
                          .append("\t").append("TSE")
                          .append("\t").append("1day")
                          .append("\t").append(point.getOpen())
                          .append("\t").append(point.getHigh())
                          .append("\t").append(point.getLow())
                          .append("\t").append(point.getClose())
                          .append("\t").append(point.getVolume())
                          .append("\t").append("true")
                          .append("\n");
                    
                    // MarketData CSV: timestamp, symbol, timeframe, open_price, high_price, low_price, close_price, volume
                    marketDataCsv.append(point.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                 .append("\t").append(symbol)
                                 .append("\t").append("DAY_1")
                                 .append("\t").append(point.getOpen())
                                 .append("\t").append(point.getHigh())
                                 .append("\t").append(point.getLow())
                                 .append("\t").append(point.getClose())
                                 .append("\t").append(point.getVolume())
                                 .append("\n");
                }
                
                // COPY to bar table
                String barCopyCmd = "COPY bar (timestamp, symbol, market, timeframe, open, high, low, close, volume, complete) FROM STDIN";
                copyManager.copyIn(barCopyCmd, new StringReader(barCsv.toString()));
                
                // COPY to market_data table
                String marketDataCopyCmd = "COPY market_data (timestamp, symbol, timeframe, open_price, high_price, low_price, close_price, volume) FROM STDIN";
                copyManager.copyIn(marketDataCopyCmd, new StringReader(marketDataCsv.toString()));
                
                totalInserted += batch.size();
                log.info("📊 COPY batch: {} records for {} (total: {}/{})", 
                    batch.size(), symbol, totalInserted, data.size());
            }
            
        } catch (Exception e) {
            log.error("❌ PostgreSQL COPY failed for {}: {}", symbol, e.getMessage());
            // Fallback to JPA saveAll if COPY fails
            log.info("🔄 Falling back to JPA batch insert...");
            return fallbackBatchInsert(symbol, data);
        }
        
        return totalInserted;
    }
    
    /**
     * Fallback batch insert using JPA saveAll (slower but more compatible)
     */
    private int fallbackBatchInsert(String symbol, List<HistoricalDataPoint> data) {
        int inserted = 0;
        int batchSize = 1000;
        
        List<Bar> barsToSave = new ArrayList<>();
        List<MarketData> marketDataToSave = new ArrayList<>();
        
        for (HistoricalDataPoint point : data) {
            Bar bar = new Bar();
            bar.setTimestamp(point.getTimestamp());
            bar.setSymbol(symbol);
            bar.setMarket("TSE");
            bar.setTimeframe("1day");
            bar.setOpen(point.getOpen());
            bar.setHigh(point.getHigh());
            bar.setLow(point.getLow());
            bar.setClose(point.getClose());
            bar.setVolume(point.getVolume());
            bar.setComplete(true);
            barsToSave.add(bar);
            
            MarketData marketData = MarketData.builder()
                    .timestamp(point.getTimestamp())
                    .symbol(symbol)
                    .timeframe(MarketData.Timeframe.DAY_1)
                    .open(point.getOpen())
                    .high(point.getHigh())
                    .low(point.getLow())
                    .close(point.getClose())
                    .volume(point.getVolume())
                    .build();
            marketDataToSave.add(marketData);
            
            inserted++;
            
            if (barsToSave.size() >= batchSize) {
                barRepository.saveAll(barsToSave);
                marketDataRepository.saveAll(marketDataToSave);
                barsToSave.clear();
                marketDataToSave.clear();
            }
        }
        
        if (!barsToSave.isEmpty()) {
            barRepository.saveAll(barsToSave);
            marketDataRepository.saveAll(marketDataToSave);
        }
        
        return inserted;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class DownloadResult {
        private String symbol;
        private int totalRecords;
        private int inserted;
        private int skipped;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class HistoricalDataPoint {
        private LocalDateTime timestamp;
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;
    }
    
    /**
     * Truncate historical data tables once per backtest run to ensure clean 10-year data window.
     * Uses atomic flag to ensure truncation happens only once even with parallel stock downloads.
     */
    @Transactional
    public void truncateTablesIfNeeded() {
        if (tablesCleared.compareAndSet(false, true)) {
            log.info("🗑️ Truncating historical data tables for clean 10-year backtest window...");
            
            try {
                barRepository.truncateTable();
                log.info("   ✅ Truncated bar table");
                
                marketDataRepository.truncateTable();
                log.info("   ✅ Truncated market_data table");
                
                strategyStockMappingRepository.truncateTable();
                log.info("   ✅ Truncated strategy_stock_mapping table");
                
                log.info("🗑️ All historical data tables truncated successfully");
            } catch (Exception e) {
                log.error("❌ Failed to truncate tables: {}", e.getMessage());
                tablesCleared.set(false); // Reset flag to allow retry
                throw e;
            }
        }
    }
    
    /**
     * Reset the truncation flag. Useful for testing or manual reset scenarios.
     */
    public void resetTruncationFlag() {
        tablesCleared.set(false);
    }
}
