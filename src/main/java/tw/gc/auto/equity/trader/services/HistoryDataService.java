package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class HistoryDataService {

    private final BarRepository barRepository;
    private final MarketDataRepository marketDataRepository;
    private final StrategyStockMappingRepository strategyStockMappingRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    
    // Flag to ensure truncation happens only once per backtest run
    private final AtomicBoolean tablesCleared = new AtomicBoolean(false);
    
    // Single-writer queue configuration - reduced sizes to prevent blocking
    private static final int QUEUE_CAPACITY = 5_000;
    private static final int BULK_INSERT_BATCH_SIZE = 1_000;

    @Value("${python.bridge.url:http://localhost:8888}")
    private String pythonBridgeUrl;
    
    public HistoryDataService(BarRepository barRepository, 
                              MarketDataRepository marketDataRepository,
                              StrategyStockMappingRepository strategyStockMappingRepository,
                              RestTemplate restTemplate, 
                              ObjectMapper objectMapper,
                              DataSource dataSource,
                              JdbcTemplate jdbcTemplate) {
        this.barRepository = barRepository;
        this.marketDataRepository = marketDataRepository;
        this.strategyStockMappingRepository = strategyStockMappingRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Download historical data by calling Python API asynchronously
     * Batches requests in 365-day chunks, defaults to 10 years
     * Uses single-writer queue pattern for efficient database writes
     * 
     * Performs TRUNCATE on historical data tables before first ingestion to ensure 
     * clean 10-year window for backtesting.
     * 
     * @param symbol Stock symbol (e.g., "2330.TW")
     * @param years Number of years of history (default: 10)
     * @return DownloadResult with statistics
     */
    public DownloadResult downloadHistoricalData(String symbol, int years) throws IOException {
        log.info("📥 Downloading {} years of historical data for {} via Python API", years, symbol);
        
        // Truncate tables once per backtest run for clean 10-year data window
        truncateTablesIfNeeded();

        // Calculate date ranges for batching (365 days per batch)
        LocalDateTime endDate = LocalDateTime.now();
        int daysPerBatch = 365;
        int totalBatches = years;
        
        // Single-writer queue pattern: producers (downloaders) → queue → consumer (writer)
        BlockingQueue<HistoricalDataPoint> dataQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        AtomicInteger totalDownloaded = new AtomicInteger(0);
        AtomicInteger totalInserted = new AtomicInteger(0);
        AtomicBoolean downloadComplete = new AtomicBoolean(false);
        CountDownLatch writerLatch = new CountDownLatch(1);
        
        // Start single writer thread
        Thread writerThread = new Thread(() -> {
            try {
                int inserted = runSingleWriter(symbol, dataQueue, downloadComplete);
                totalInserted.set(inserted);
            } catch (Exception e) {
                log.error("❌ Writer thread failed for {}: {}", symbol, e.getMessage());
            } finally {
                writerLatch.countDown();
            }
        }, "HistoryDataWriter-" + symbol);
        writerThread.start();
        
        // Launch async downloads for each batch using bounded thread pool
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(
            Math.min(totalBatches, Runtime.getRuntime().availableProcessors())
        );
        
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        
        for (int batch = 0; batch < totalBatches; batch++) {
            final int batchNumber = batch;
            final LocalDateTime batchEnd = endDate.minusDays((long) batch * daysPerBatch);
            final LocalDateTime batchStart = batchEnd.minusDays(daysPerBatch);
            
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    log.info("📥 Batch {}/{}: Downloading {} from {} to {}", 
                        batchNumber + 1, totalBatches, symbol, 
                        batchStart.format(DateTimeFormatter.ISO_DATE),
                        batchEnd.format(DateTimeFormatter.ISO_DATE));
                    
                    List<HistoricalDataPoint> batchData = downloadBatch(symbol, batchStart, batchEnd);
                    
                    // Push to queue (will block if queue is full - backpressure)
                    for (HistoricalDataPoint point : batchData) {
                        dataQueue.put(point);
                    }
                    
                    log.info("✅ Batch {}/{}: Downloaded {} records for {}", 
                        batchNumber + 1, totalBatches, batchData.size(), symbol);
                    
                    return batchData.size();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("❌ Batch {}/{}: Interrupted while queuing data for {}", 
                        batchNumber + 1, totalBatches, symbol);
                    return 0;
                } catch (Exception e) {
                    log.error("❌ Batch {}/{}: Failed to download data for {}: {}", 
                        batchNumber + 1, totalBatches, symbol, e.getMessage());
                    return 0;
                }
            }, downloadExecutor);
            
            futures.add(future);
        }
        
        // Wait for all downloads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Sum downloaded records
        for (CompletableFuture<Integer> future : futures) {
            try {
                totalDownloaded.addAndGet(future.get());
            } catch (Exception e) {
                log.error("❌ Failed to get download count: {}", e.getMessage());
            }
        }
        
        downloadExecutor.shutdown();
        
        // Signal writer that downloads are complete
        downloadComplete.set(true);
        
        // Wait for writer to finish
        try {
            if (!writerLatch.await(5, TimeUnit.MINUTES)) {
                log.warn("⚠️ Writer thread did not complete within timeout for {}", symbol);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Interrupted waiting for writer thread: {}", e.getMessage());
        }
        
        log.info("✅ All {} batches completed. Total: {} downloaded, {} inserted for {}", 
            totalBatches, totalDownloaded.get(), totalInserted.get(), symbol);
        
        int skipped = totalDownloaded.get() - totalInserted.get();
        return new DownloadResult(symbol, totalDownloaded.get(), totalInserted.get(), skipped);
    }
    
    /**
     * Single writer thread that drains the queue and performs bulk inserts.
     * This eliminates database contention by centralizing all write operations.
     */
    private int runSingleWriter(String symbol, BlockingQueue<HistoricalDataPoint> dataQueue, 
                                 AtomicBoolean downloadComplete) {
        List<Bar> barBatch = new ArrayList<>(BULK_INSERT_BATCH_SIZE);
        List<MarketData> marketDataBatch = new ArrayList<>(BULK_INSERT_BATCH_SIZE);
        int totalInserted = 0;
        
        while (!downloadComplete.get() || !dataQueue.isEmpty()) {
            try {
                // Poll with timeout to allow checking downloadComplete flag
                HistoricalDataPoint point = dataQueue.poll(100, TimeUnit.MILLISECONDS);
                
                if (point != null) {
                    // Convert to entities
                    Bar bar = Bar.builder()
                        .timestamp(point.getTimestamp())
                        .symbol(symbol)
                        .market("TSE")
                        .timeframe("1day")
                        .open(point.getOpen())
                        .high(point.getHigh())
                        .low(point.getLow())
                        .close(point.getClose())
                        .volume(point.getVolume())
                        .isComplete(true)
                        .build();
                    barBatch.add(bar);
                    
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
                    marketDataBatch.add(marketData);
                    
                    // Flush when batch is full
                    if (barBatch.size() >= BULK_INSERT_BATCH_SIZE) {
                        int inserted = flushBatch(barBatch, marketDataBatch);
                        totalInserted += inserted;
                        log.info("📊 Bulk inserted {} records for {} (total: {})", 
                            inserted, symbol, totalInserted);
                        barBatch.clear();
                        marketDataBatch.clear();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Flush remaining records
        if (!barBatch.isEmpty()) {
            int inserted = flushBatch(barBatch, marketDataBatch);
            totalInserted += inserted;
            log.info("📊 Final bulk insert: {} records for {} (total: {})", 
                inserted, symbol, totalInserted);
        }
        
        return totalInserted;
    }
    
    /**
     * Flush batch using JdbcTemplate batch insert.
     * PgBulkInsert disabled due to connection blocking issues.
     */
    private int flushBatch(List<Bar> bars, List<MarketData> marketDataList) {
        return jdbcBatchInsert(bars, marketDataList);
    }
    
    /**
     * JdbcTemplate batch insert - primary and only strategy.
     * PgBulkInsert disabled due to blocking/deadlock issues with connection pool.
     */
    private int jdbcBatchInsert(List<Bar> bars, List<MarketData> marketDataList) {
        String barSql = "INSERT INTO bar (timestamp, symbol, market, timeframe, open, high, low, close, volume, is_complete) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String marketDataSql = "INSERT INTO market_data (timestamp, symbol, timeframe, open_price, high_price, low_price, close_price, volume, asset_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            jdbcTemplate.batchUpdate(barSql, bars, bars.size(), (ps, bar) -> {
                ps.setObject(1, bar.getTimestamp());
                ps.setString(2, bar.getSymbol());
                ps.setString(3, bar.getMarket());
                ps.setString(4, bar.getTimeframe());
                ps.setDouble(5, bar.getOpen());
                ps.setDouble(6, bar.getHigh());
                ps.setDouble(7, bar.getLow());
                ps.setDouble(8, bar.getClose());
                ps.setLong(9, bar.getVolume());
                ps.setBoolean(10, bar.isComplete());
            });
            
            jdbcTemplate.batchUpdate(marketDataSql, marketDataList, marketDataList.size(), (ps, md) -> {
                ps.setObject(1, md.getTimestamp());
                ps.setString(2, md.getSymbol());
                ps.setString(3, md.getTimeframe().name());
                ps.setDouble(4, md.getOpen());
                ps.setDouble(5, md.getHigh());
                ps.setDouble(6, md.getLow());
                ps.setDouble(7, md.getClose());
                ps.setLong(8, md.getVolume());
                ps.setString(9, md.getAssetType() != null ? md.getAssetType().name() : "STOCK");
            });
            
            return bars.size();
        } catch (Exception e) {
            log.error("❌ JdbcTemplate batch insert failed: {}", e.getMessage());
            return 0;
        }
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
