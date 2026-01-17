package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bytefish.pgbulkinsert.PgBulkInsert;
import de.bytefish.pgbulkinsert.mapping.AbstractMapping;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private final SystemStatusService systemStatusService;
    private final BacktestService backtestService;
    private final TelegramService telegramService;
    private final TaiwanStockNameService taiwanStockNameService;
    
    // Flag to ensure truncation happens only once per backtest run
    private final AtomicBoolean tablesCleared = new AtomicBoolean(false);
    
    // Global queue for multi-stock concurrent downloads
    private static final int GLOBAL_QUEUE_CAPACITY = 10_000;
    private static final int BULK_INSERT_BATCH_SIZE = 2_000;
    private static final int MAX_CONCURRENT_DOWNLOADS = 8;
    private static final int FLUSH_TIMEOUT_MS = 500;
    
    // Single-stock queue capacity (legacy compatibility)
    private static final int QUEUE_CAPACITY = 5_000;
    
    // PgBulkInsert mappings (lazy-initialized)
    private volatile PgBulkInsert<Bar> barBulkInsert;
    private volatile PgBulkInsert<MarketData> marketDataBulkInsert;

    @Value("${python.bridge.url:http://localhost:8888}")
    private String pythonBridgeUrl;
    
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    
    public HistoryDataService(BarRepository barRepository, 
                              MarketDataRepository marketDataRepository,
                              StrategyStockMappingRepository strategyStockMappingRepository,
                              RestTemplate restTemplate, 
                              ObjectMapper objectMapper,
                              DataSource dataSource,
                              JdbcTemplate jdbcTemplate,
                              org.springframework.transaction.PlatformTransactionManager transactionManager,
                              SystemStatusService systemStatusService,
                              @Lazy BacktestService backtestService,
                              @Lazy TelegramService telegramService,
                              TaiwanStockNameService taiwanStockNameService) {
        this.barRepository = barRepository;
        this.marketDataRepository = marketDataRepository;
        this.strategyStockMappingRepository = strategyStockMappingRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.systemStatusService = systemStatusService;
        this.backtestService = backtestService;
        this.telegramService = telegramService;
        this.taiwanStockNameService = taiwanStockNameService;
    }
    
    /**
     * Download historical data for multiple stocks concurrently using Virtual Threads.
     * Uses a single global writer thread to prevent database connection contention.
     * Updates SystemStatusService when download starts and completes.
     * 
     * @param symbols List of stock symbols to download
     * @param years Number of years of history
     * @return Map of symbol to download results
     */
    public Map<String, DownloadResult> downloadHistoricalDataForMultipleStocks(List<String> symbols, int years) {
        return downloadHistoricalDataForMultipleStocks(symbols, years, 10, TimeUnit.MINUTES);
    }

    Map<String, DownloadResult> downloadHistoricalDataForMultipleStocks(List<String> symbols, int years, long writerTimeout, TimeUnit writerTimeoutUnit) {
        log.info("🚀 Starting concurrent download for {} stocks, {} years each", symbols.size(), years);

        systemStatusService.startHistoryDownload();

        // Notify that a bulk download is starting with a summary of symbols and years
        try {
            notifyDownloadStartedForAll(symbols, years);
        } catch (Exception e) {
            log.warn("⚠️ Failed to send download start notification: {}", e.getMessage());
        }

        try {
            truncateTablesIfNeeded();

            BlockingQueue<HistoricalDataPoint> globalQueue = new ArrayBlockingQueue<>(GLOBAL_QUEUE_CAPACITY);
            AtomicBoolean allDownloadsComplete = new AtomicBoolean(false);
            AtomicInteger totalInserted = new AtomicInteger(0);
            CountDownLatch writerLatch = new CountDownLatch(1);
            // Map to track inserted counts per symbol
            java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> insertedBySymbol = new java.util.concurrent.ConcurrentHashMap<>();
            symbols.forEach(s -> insertedBySymbol.put(s, new java.util.concurrent.atomic.AtomicInteger(0)));

            // Start single global writer thread
            Thread globalWriter = Thread.ofPlatform()
                .name("GlobalHistoryWriter")
                .start(() -> {
                    try {
                        int inserted = runGlobalWriter(globalQueue, allDownloadsComplete, insertedBySymbol);
                        totalInserted.set(inserted);
                        log.info("✅ Global writer completed. Total inserted: {}", inserted);
                    } catch (Exception e) {
                        log.error("❌ Global writer failed: {}", e.getMessage(), e);
                    } finally {
                        writerLatch.countDown();
                    }
                });

            // Semaphore to limit concurrent downloads
            Semaphore downloadPermits = new Semaphore(MAX_CONCURRENT_DOWNLOADS);
            Map<String, DownloadResult> results = new HashMap<>();
            Map<String, AtomicInteger> symbolCounts = new HashMap<>();

            symbols.forEach(s -> symbolCounts.put(s, new AtomicInteger(0)));

            // Launch Virtual Threads for each stock download
            try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = symbols.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> runMultiStockDownloadTask(symbol, years, globalQueue, downloadPermits, results, symbolCounts.get(symbol)), virtualExecutor))
                    .collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            allDownloadsComplete.set(true);

            awaitWriterLatch(writerLatch, writerTimeout, writerTimeoutUnit);

            // Update results with actual inserted counts
            int totalDownloaded = results.values().stream().mapToInt(DownloadResult::getTotalRecords).sum();
            // populate inserted counts from insertedBySymbol map
            for (Map.Entry<String, java.util.concurrent.atomic.AtomicInteger> e : insertedBySymbol.entrySet()) {
                String sym = e.getKey();
                int inserted = e.getValue().get();
                DownloadResult prev = results.get(sym);
                if (prev != null) {
                    results.put(sym, new DownloadResult(sym, prev.getTotalRecords(), inserted, prev.getSkipped()));
                } else {
                    results.put(sym, new DownloadResult(sym, 0, inserted, 0));
                }
            }

            log.info("✅ Multi-stock download complete. {} stocks, {} downloaded, {} inserted",
                symbols.size(), totalDownloaded, totalInserted.get());

            // Send Telegram summary notification (if configured)
            try {
                notifyDownloadSummary(results);
            } catch (Exception e) {
                log.warn("⚠️ Failed to send download summary notification: {}", e.getMessage());
            }

            return results;
        } finally {
            systemStatusService.completeHistoryDownload();
        }
    }

    void runMultiStockDownloadTask(String symbol, int years, BlockingQueue<HistoricalDataPoint> globalQueue, Semaphore downloadPermits,
                                  Map<String, DownloadResult> results, AtomicInteger symbolCounter) {
        boolean acquired = false;
        try {
            downloadPermits.acquire();
            acquired = true;
            int downloaded = downloadStockData(symbol, years, globalQueue, symbolCounter);
            synchronized (results) {
                results.put(symbol, new DownloadResult(symbol, downloaded, 0, 0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Download interrupted for {}", symbol);
            synchronized (results) {
                results.put(symbol, new DownloadResult(symbol, 0, 0, 0));
            }
        } catch (Exception e) {
            log.error("❌ Download failed for {}: {}", symbol, e.getMessage());
            synchronized (results) {
                results.put(symbol, new DownloadResult(symbol, 0, 0, 0));
            }
        } finally {
            if (acquired) {
                downloadPermits.release();
            }
        }
    }

    boolean awaitWriterLatch(CountDownLatch writerLatch, long timeout, TimeUnit unit) {
        try {
            if (!writerLatch.await(timeout, unit)) {
                log.warn("⚠️ Global writer timed out");
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Download data for a single stock, streaming to global queue.
     */
    private int downloadStockData(String symbol, int years, BlockingQueue<HistoricalDataPoint> queue, 
                                   AtomicInteger counter) {
        log.info("📥 Starting download for {} ({} years)", symbol, years);

        // Notify that a per-symbol download is starting
        try {
            notifyDownloadStartedForSymbol(symbol, years);
        } catch (Exception e) {
            log.warn("⚠️ Failed to send per-symbol start notification for {}: {}", symbol, e.getMessage());
        }
        
        LocalDateTime endDate = LocalDateTime.now();
        int daysPerBatch = 365;
        int totalDownloaded = 0;
        
        for (int batch = 0; batch < years; batch++) {
            LocalDateTime batchEnd = endDate.minusDays((long) batch * daysPerBatch);
            LocalDateTime batchStart = batchEnd.minusDays(daysPerBatch);
            
            try {
                List<HistoricalDataPoint> batchData = downloadBatch(symbol, batchStart, batchEnd);
                
                for (HistoricalDataPoint point : batchData) {
                    point.setSymbol(symbol);
                    queue.put(point);
                    counter.incrementAndGet();
                }
                
                totalDownloaded += batchData.size();
                
                if (batch % 3 == 0) {
                    log.info("📊 {} batch {}/{}: {} records (queue: {})", 
                        symbol, batch + 1, years, batchData.size(), queue.size());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("⚠️ Batch {}/{} failed for {}: {}", batch + 1, years, symbol, e.getMessage());
            }
        }
        
        log.info("✅ {} download complete: {} records", symbol, totalDownloaded);

        // Notify per-symbol completion
        try {
            DownloadResult r = new DownloadResult(symbol, totalDownloaded, 0, 0);
            notifyDownloadComplete(r);
        } catch (Exception e) {
            log.warn("⚠️ Failed to send per-symbol download notification for {}: {}", symbol, e.getMessage());
        }

        return totalDownloaded;
    }
    
    /**
     * Global writer thread that drains from all producers and performs bulk inserts.
     * Uses PgBulkInsert (COPY protocol) as primary, JdbcTemplate as fallback.
     */
    private int runGlobalWriter(BlockingQueue<HistoricalDataPoint> queue, AtomicBoolean complete, java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> insertedBySymbol) {
        List<Bar> barBatch = new ArrayList<>(BULK_INSERT_BATCH_SIZE);
        List<MarketData> marketDataBatch = new ArrayList<>(BULK_INSERT_BATCH_SIZE);
        int totalInserted = 0;
        long lastFlush = System.currentTimeMillis();
        
        while (!complete.get() || !queue.isEmpty()) {
            try {
                HistoricalDataPoint point = queue.poll(100, TimeUnit.MILLISECONDS);
                
                if (point != null) {
                    barBatch.add(toBar(point));
                    marketDataBatch.add(toMarketData(point));
                }
                
                boolean batchFull = barBatch.size() >= BULK_INSERT_BATCH_SIZE;
                boolean timeoutReached = System.currentTimeMillis() - lastFlush > FLUSH_TIMEOUT_MS;
                
                if ((batchFull || (timeoutReached && !barBatch.isEmpty())) && !barBatch.isEmpty()) {
                    // compute per-symbol counts for this batch
                    Map<String, Integer> batchCounts = new HashMap<>();
                    for (Bar b : barBatch) {
                        batchCounts.merge(b.getSymbol(), 1, Integer::sum);
                    }

                    int inserted = flushBatchWithFallback(barBatch, marketDataBatch);
                    totalInserted += inserted;

                    // attribute inserted counts by symbol (best-effort: attribute by counts in batch)
                    for (Map.Entry<String, Integer> e : batchCounts.entrySet()) {
                        insertedBySymbol.computeIfAbsent(e.getKey(), k -> new java.util.concurrent.atomic.AtomicInteger(0)).addAndGet(e.getValue());
                    }
                    
                    if (totalInserted % 10_000 == 0 || inserted > 1000) {
                        log.info("📊 Global writer: flushed {} (total: {}, queue: {})", 
                            inserted, totalInserted, queue.size());
                    }
                    
                    barBatch.clear();
                    marketDataBatch.clear();
                    lastFlush = System.currentTimeMillis();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Final flush
        if (!barBatch.isEmpty()) {
            Map<String, Integer> batchCounts = new HashMap<>();
            for (Bar b : barBatch) {
                batchCounts.merge(b.getSymbol(), 1, Integer::sum);
            }
            int inserted = flushBatchWithFallback(barBatch, marketDataBatch);
            totalInserted += inserted;
            for (Map.Entry<String, Integer> e : batchCounts.entrySet()) {
                insertedBySymbol.computeIfAbsent(e.getKey(), k -> new java.util.concurrent.atomic.AtomicInteger(0)).addAndGet(e.getValue());
            }
            log.info("📊 Global writer: final flush {} (total: {})", inserted, totalInserted);
        }
        
        return totalInserted;
    }
    
    private Bar toBar(HistoricalDataPoint point) {
        return Bar.builder()
            .timestamp(point.getTimestamp())
            .symbol(point.getSymbol())
            .name(point.getName())  // Populate from source
            .market("TSE")
            .timeframe("1day")
            .open(point.getOpen())
            .high(point.getHigh())
            .low(point.getLow())
            .close(point.getClose())
            .volume(point.getVolume())
            .isComplete(true)
            .build();
    }
    
    private MarketData toMarketData(HistoricalDataPoint point) {
        return MarketData.builder()
            .timestamp(point.getTimestamp())
            .symbol(point.getSymbol())
            .name(point.getName())  // Populate from source
            .timeframe(MarketData.Timeframe.DAY_1)
            .open(point.getOpen())
            .high(point.getHigh())
            .low(point.getLow())
            .close(point.getClose())
            .volume(point.getVolume())
            .build();
    }
    
    /**
     * Flush batch using PgBulkInsert (COPY protocol) with JdbcTemplate fallback.
     */
    private int flushBatchWithFallback(List<Bar> bars, List<MarketData> marketDataList) {
        try {
            return pgBulkInsert(bars, marketDataList);
        } catch (Exception e) {
            log.warn("⚠️ PgBulkInsert failed, falling back to JdbcTemplate: {}", e.getMessage());
            return jdbcBatchInsert(bars, marketDataList);
        }
    }
    
    /**
     * High-performance PostgreSQL COPY protocol insert using PgBulkInsert.
     */
    private int pgBulkInsert(List<Bar> bars, List<MarketData> marketDataList) throws SQLException {
        initBulkInsertMappings();
        try (Connection conn = dataSource.getConnection()) {
            PGConnection pgConn = conn.unwrap(PGConnection.class);

            barBulkInsert.saveAll(pgConn, bars.stream());
            marketDataBulkInsert.saveAll(pgConn, marketDataList.stream());

            return bars.size();
        }
    }

    // -------------------- Telegram notifications --------------------

    void notifyDownloadComplete(DownloadResult r) {
        // Intentionally no-op: do not send per-symbol completion messages to reduce noise.
        // Use notifyDownloadSummary for an aggregated completion message instead.
        if (r == null) return;
        log.debug("Per-symbol completion: {} records (inserted/skipped: {}/{}) — telegram suppressed", r.getSymbol(), r.getTotalRecords(), r.getInserted(), r.getSkipped());
    }

    void notifyDownloadSummary(Map<String, DownloadResult> results) {
        if (results == null || results.isEmpty() || telegramService == null) return;
        String msg = formatSummaryForAll(results);
        log.info("📣 Telegram summary (pre-send): {}", msg.replaceAll("\n", " "));
        telegramService.sendMessage(msg);
    }

    String formatIndividualSummary(DownloadResult r) {
        return String.format("📥 Download complete: %s — records: %d, inserted: %d, skipped: %d", r.getSymbol(), r.getTotalRecords(), r.getInserted(), r.getSkipped());
    }

    String formatSummaryForAll(Map<String, DownloadResult> results) {
        int symbols = results.size();
        int totalRecords = results.values().stream().mapToInt(DownloadResult::getTotalRecords).sum();
        int totalInserted = results.values().stream().mapToInt(DownloadResult::getInserted).sum();
        int totalSkipped = results.values().stream().mapToInt(DownloadResult::getSkipped).sum();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📥 Historical download complete: symbols=%d, records=%d, inserted=%d, skipped=%d", symbols, totalRecords, totalInserted, totalSkipped));
        sb.append('\n');
        sb.append("Details:\n");
        results.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append(String.format("%s - inserted %d\n", e.getKey(), e.getValue().getInserted())));
        return sb.toString();
    }

    // Start notifications
    void notifyDownloadStartedForSymbol(String symbol, int years) {
        // Intentionally no-op: suppress per-symbol start notifications to reduce noise.
        log.debug("Per-symbol start: {} ({} years) — telegram suppressed", symbol, years);
    }

    void notifyDownloadStartedForAll(java.util.List<String> symbols, int years) {
        if (telegramService == null || symbols == null || symbols.isEmpty()) return;
        int n = symbols.size();
        // Provide full listing of symbols (do not truncate)
        String listFull = String.join(", ", symbols);
        String msg = String.format("📥 Starting historical download for %d symbols (%d years): %s", n, years, listFull);
        log.info("📣 Telegram start (pre-send): {}", msg);
        telegramService.sendMessage(msg);
    }
    
    private synchronized void initBulkInsertMappings() {
        if (barBulkInsert == null) {
            barBulkInsert = new PgBulkInsert<>(new BarBulkInsertMapping());
        }
        if (marketDataBulkInsert == null) {
            marketDataBulkInsert = new PgBulkInsert<>(new MarketDataBulkInsertMapping());
        }
    }
    
    /**
     * PgBulkInsert mapping for Bar entity.
     */
    private static class BarBulkInsertMapping extends AbstractMapping<Bar> {
        public BarBulkInsertMapping() {
            super("public", "bar");
            mapTimeStamp("timestamp", Bar::getTimestamp);
            mapText("symbol", Bar::getSymbol);
            mapText("name", Bar::getName);
            mapText("market", Bar::getMarket);
            mapText("timeframe", Bar::getTimeframe);
            mapDouble("open", Bar::getOpen);
            mapDouble("high", Bar::getHigh);
            mapDouble("low", Bar::getLow);
            mapDouble("close", Bar::getClose);
            mapLong("volume", Bar::getVolume);
            mapBoolean("is_complete", Bar::isComplete);
        }
    }
    
    /**
     * PgBulkInsert mapping for MarketData entity.
     */
    private static class MarketDataBulkInsertMapping extends AbstractMapping<MarketData> {
        public MarketDataBulkInsertMapping() {
            super("public", "market_data");
            mapTimeStamp("timestamp", MarketData::getTimestamp);
            mapText("symbol", MarketData::getSymbol);
            mapText("name", MarketData::getName);
            mapText("timeframe", md -> md.getTimeframe().name());
            mapDouble("open_price", MarketData::getOpen);
            mapDouble("high_price", MarketData::getHigh);
            mapDouble("low_price", MarketData::getLow);
            mapDouble("close_price", MarketData::getClose);
            mapLong("volume", MarketData::getVolume);
            mapText("asset_type", md -> md.getAssetType() != null ? md.getAssetType().name() : "STOCK");
        }
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
                    // Log raw point name for diagnostics
                    if (point.getName() == null || point.getName().trim().isEmpty()) {
                        log.debug("⚠️ Downloaded point for {} @ {} missing name (open={}, close={}, vol={})", symbol, point.getTimestamp(), point.getOpen(), point.getClose(), point.getVolume());
                    } else {
                        log.debug("Downloaded point for {} @ {} name='{}'", symbol, point.getTimestamp(), point.getName());
                    }

                    // Convert to entities
                    Bar bar = Bar.builder()
                        .timestamp(point.getTimestamp())
                        .symbol(symbol)
                        .name(point.getName())  // Add stock name
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
                        .name(point.getName())  // Add stock name
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
                        long missing = barBatch.stream().filter(b -> b.getName() == null || b.getName().trim().isEmpty()).count();
                        log.info("📊 Flushing {} records for {} ({} missing names)", barBatch.size(), symbol, missing);

                        // Fill any missing names from TaiwanStockNameService as a fallback
                        if (missing > 0) {
                            fillMissingNamesIfMissing(barBatch, marketDataBatch, symbol);
                            long missingAfter = barBatch.stream().filter(b -> b.getName() == null || b.getName().trim().isEmpty()).count();
                            log.info("🔧 After fallback, {} records still missing names for {}", missingAfter, symbol);
                        }

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
            long missing = barBatch.stream().filter(b -> b.getName() == null || b.getName().trim().isEmpty()).count();
            log.info("📊 Final flushing {} records for {} ({} missing names)", barBatch.size(), symbol, missing);

            // Fill missing names before final flush
            if (missing > 0) {
                fillMissingNamesIfMissing(barBatch, marketDataBatch, symbol);
                long missingAfter = barBatch.stream().filter(b -> b.getName() == null || b.getName().trim().isEmpty()).count();
                log.info("🔧 After fallback, {} records still missing names for {}", missingAfter, symbol);
            }

            int inserted = flushBatch(barBatch, marketDataBatch);
            totalInserted += inserted;
            log.info("📊 Final bulk insert: {} records for {} (total: {})", 
                inserted, symbol, totalInserted);
        }
        
        return totalInserted;
    }
    
    /**
     * Flush batch using PgBulkInsert with JdbcTemplate fallback.
     */
    private int flushBatch(List<Bar> bars, List<MarketData> marketDataList) {
        return flushBatchWithFallback(bars, marketDataList);
    }

    /**
     * Fill missing Bar/MarketData names using TaiwanStockNameService as a fallback.
     * Package-private for testing.
     */
    void fillMissingNamesIfMissing(List<Bar> bars, List<MarketData> marketDataList, String symbol) {
        if (bars == null || bars.isEmpty()) {
            return;
        }

        boolean anyMissing = bars.stream().anyMatch(b -> b.getName() == null || b.getName().trim().isEmpty());
        if (!anyMissing) {
            return;
        }

        try {
            if (taiwanStockNameService != null && taiwanStockNameService.hasStockName(symbol)) {
                String fallback = taiwanStockNameService.getStockName(symbol);
                bars.forEach(b -> { if (b.getName() == null || b.getName().trim().isEmpty()) b.setName(fallback); });
                marketDataList.forEach(m -> { if (m.getName() == null || m.getName().trim().isEmpty()) m.setName(fallback); });
                log.info("🔧 Filled missing names for {} using fallback '{}'", symbol, fallback);
            } else {
                log.info("🔍 No fallback name available for {}", symbol);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to fill missing names for {}: {}", symbol, e.getMessage());
        }
    }
    
    /**
     * JdbcTemplate batch insert - primary and only strategy.
     * PgBulkInsert disabled due to blocking/deadlock issues with connection pool.
     */
    private int jdbcBatchInsert(List<Bar> bars, List<MarketData> marketDataList) {
        String barSql = "INSERT INTO bar (timestamp, symbol, name, market, timeframe, open, high, low, close, volume, is_complete) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String marketDataSql = "INSERT INTO market_data (timestamp, symbol, name, timeframe, open_price, high_price, low_price, close_price, volume, asset_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            jdbcTemplate.batchUpdate(barSql, bars, bars.size(), (ps, bar) -> {
                ps.setObject(1, bar.getTimestamp());
                ps.setString(2, bar.getSymbol());
                ps.setString(3, bar.getName());
                ps.setString(4, bar.getMarket());
                ps.setString(5, bar.getTimeframe());
                ps.setDouble(6, bar.getOpen());
                ps.setDouble(7, bar.getHigh());
                ps.setDouble(8, bar.getLow());
                ps.setDouble(9, bar.getClose());
                ps.setLong(10, bar.getVolume());
                ps.setBoolean(11, bar.isComplete());
            });
            
            jdbcTemplate.batchUpdate(marketDataSql, marketDataList, marketDataList.size(), (ps, md) -> {
                ps.setObject(1, md.getTimestamp());
                ps.setString(2, md.getSymbol());
                ps.setString(3, md.getName());
                ps.setString(4, md.getTimeframe().name());
                ps.setDouble(5, md.getOpen());
                ps.setDouble(6, md.getHigh());
                ps.setDouble(7, md.getLow());
                ps.setDouble(8, md.getClose());
                ps.setLong(9, md.getVolume());
                ps.setString(10, md.getAssetType() != null ? md.getAssetType().name() : "STOCK");
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
            
            // Fetch Taiwan stock symbols from BacktestService
            List<String> stocks = backtestService.fetchTop50Stocks();
            
            Map<String, Object> request = new HashMap<>();
            request.put("symbol", symbol);
            request.put("start_date", start.format(DateTimeFormatter.ISO_DATE_TIME));
            request.put("end_date", end.format(DateTimeFormatter.ISO_DATE_TIME));
            request.put("stocks", stocks);
            
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
                dataPoint.setName((String) point.get("name"));  // Stock name from Python
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
    public static class HistoricalDataPoint {
        private String symbol;
        private String name;  // Stock name (e.g., "Taiwan Semiconductor Manufacturing")
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
     * Uses programmatic transaction to ensure proper transaction boundaries.
     */
    public void truncateTablesIfNeeded() {
        if (tablesCleared.compareAndSet(false, true)) {
            log.info("🗑️ Truncating historical data tables for clean 10-year backtest window...");
            
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    barRepository.truncateTable();
                    log.info("   ✅ Truncated bar table");
                    
                    marketDataRepository.truncateTable();
                    log.info("   ✅ Truncated market_data table");
                    
                    strategyStockMappingRepository.truncateTable();
                    log.info("   ✅ Truncated strategy_stock_mapping table");
                });
                
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
    
    /**
     * Get stock name from historical data (Bar or MarketData tables).
     * Returns null if not found.
     */
    public String getStockNameFromHistory(String symbol) {
        try {
            // Try Bar table first (most common for backtests)
            Optional<Bar> latestBar = barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day");
            if (latestBar.isPresent() && latestBar.get().getName() != null) {
                return latestBar.get().getName();
            }
            
            // Try MarketData table as fallback
            Optional<MarketData> latestData = marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, MarketData.Timeframe.DAY_1);
            if (latestData.isPresent() && latestData.get().getName() != null) {
                return latestData.get().getName();
            }
            
            return null; // Not found
        } catch (Exception e) {
            log.debug("Could not fetch stock name for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
}
