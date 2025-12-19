package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Phaser;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoryDataService {

    private final BarRepository barRepository;
    private final MarketDataRepository marketDataRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${python.bridge.url:http://localhost:8888}")
    private String pythonBridgeUrl;

    /**
     * Download historical data by calling Python API asynchronously
     * Batches requests in 365-day chunks, defaults to 10 years
     * Uses Phaser for synchronization
     * 
     * @param symbol Stock symbol (e.g., "2330.TW")
     * @param years Number of years of history (default: 10)
     * @return DownloadResult with statistics
     */
    @Transactional
    public DownloadResult downloadHistoricalData(String symbol, int years) throws IOException {
        log.info("📥 Downloading {} years of historical data for {} via Python API", years, symbol);

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
     */
    private int batchInsertToDatabase(String symbol, List<HistoricalDataPoint> data) {
        int inserted = 0;
        
        for (HistoricalDataPoint point : data) {
            try {
                // Check if already exists in Bar table
                if (barRepository.existsBySymbolAndTimestampAndTimeframe(
                        symbol, point.getTimestamp(), "1day")) {
                    continue; // Skip duplicates
                }
                
                // Insert into Bar table
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
                barRepository.save(bar);
                
                // Check if already exists in MarketData table
                if (!marketDataRepository.existsBySymbolAndTimestampAndTimeframe(
                        symbol, point.getTimestamp(), MarketData.Timeframe.DAY_1)) {
                    // Insert into MarketData table
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
                    marketDataRepository.save(marketData);
                }
                
                inserted++;
                
            } catch (Exception e) {
                log.error("❌ Failed to insert data point for {}: {}", symbol, e.getMessage());
            }
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
}
