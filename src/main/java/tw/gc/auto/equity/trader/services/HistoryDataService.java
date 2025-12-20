package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
     * Uses JDBC batch with 10K rows per commit for optimal performance
     */
    private int batchInsertToDatabase(String symbol, List<HistoricalDataPoint> data) {
        int batchSize = 10000;
        int totalInserted = 0;
        
        String barSql = "INSERT INTO bar (timestamp, symbol, market, timeframe, open, high, low, close, volume, complete) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String marketDataSql = "INSERT INTO market_data (timestamp, symbol, timeframe, open_price, high_price, low_price, close_price, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try (var barStmt = conn.prepareStatement(barSql);
                 var marketDataStmt = conn.prepareStatement(marketDataSql)) {
                
                int count = 0;
                for (HistoricalDataPoint point : data) {
                    // Bar insert
                    barStmt.setObject(1, point.getTimestamp());
                    barStmt.setString(2, symbol);
                    barStmt.setString(3, "TSE");
                    barStmt.setString(4, "1day");
                    barStmt.setDouble(5, point.getOpen());
                    barStmt.setDouble(6, point.getHigh());
                    barStmt.setDouble(7, point.getLow());
                    barStmt.setDouble(8, point.getClose());
                    barStmt.setLong(9, point.getVolume());
                    barStmt.setBoolean(10, true);
                    barStmt.addBatch();
                    
                    // MarketData insert
                    marketDataStmt.setObject(1, point.getTimestamp());
                    marketDataStmt.setString(2, symbol);
                    marketDataStmt.setString(3, "DAY_1");
                    marketDataStmt.setDouble(4, point.getOpen());
                    marketDataStmt.setDouble(5, point.getHigh());
                    marketDataStmt.setDouble(6, point.getLow());
                    marketDataStmt.setDouble(7, point.getClose());
                    marketDataStmt.setLong(8, point.getVolume());
                    marketDataStmt.addBatch();
                    
                    count++;
                    totalInserted++;
                    
                    // Execute and commit every 10K rows
                    if (count >= batchSize) {
                        barStmt.executeBatch();
                        marketDataStmt.executeBatch();
                        conn.commit();
                        log.info("📊 JDBC batch: {} records for {} (total: {}/{})", 
                            count, symbol, totalInserted, data.size());
                        count = 0;
                    }
                }
                
                // Execute remaining
                if (count > 0) {
                    barStmt.executeBatch();
                    marketDataStmt.executeBatch();
                    conn.commit();
                    log.info("📊 JDBC final batch: {} records for {} (total: {}/{})", 
                        count, symbol, totalInserted, data.size());
                }
            }
            
        } catch (Exception e) {
            log.error("❌ JDBC batch insert failed for {}: {}", symbol, e.getMessage());
            return fallbackBatchInsert(symbol, data);
        }
        
        return totalInserted;
    }
    
    /**
     * Fallback batch insert using JPA saveAll (slowest but most compatible)
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
