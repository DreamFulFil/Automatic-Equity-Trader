package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.IndexData;
import tw.gc.auto.equity.trader.repositories.IndexDataRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for fetching and managing market index data.
 * 
 * <h3>Data Source:</h3>
 * Fetches data from Python bridge which uses yfinance to retrieve
 * index values from Yahoo Finance.
 * 
 * <h3>Supported Indices:</h3>
 * <ul>
 *   <li>^TWII - TAIEX (Taiwan Capitalization Weighted Stock Index)</li>
 *   <li>^TWOII - Taiwan OTC Index</li>
 *   <li>0050.TW - Taiwan Top 50 ETF</li>
 * </ul>
 * 
 * <h3>Refresh Schedule:</h3>
 * <ul>
 *   <li>Every 5 minutes during trading hours (9:00-13:30 Taiwan time)</li>
 *   <li>Once daily after market close</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexDataService {

    private static final String PYTHON_BRIDGE_URL = "http://localhost:8888";
    private static final int MAX_RETRIES = 3;
    private static final Duration[] BACKOFFS = {
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8)
    };

    /**
     * Primary index for Taiwan market.
     */
    public static final String TAIEX = "^TWII";
    
    /**
     * OTC market index.
     */
    public static final String TWOII = "^TWOII";
    
    /**
     * Top 50 ETF (useful as tradeable index proxy).
     */
    public static final String TW50 = "0050.TW";

    /**
     * Default indices to track.
     */
    private static final List<String> DEFAULT_INDICES = List.of(TAIEX, TWOII, TW50);

    private final IndexDataRepository indexDataRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // ========== Public API ==========

    /**
     * Get the latest index data for a symbol.
     */
    @Transactional(readOnly = true)
    public Optional<IndexData> getLatest(String indexSymbol) {
        return indexDataRepository.findFirstByIndexSymbolOrderByTradeDateDesc(indexSymbol);
    }

    /**
     * Get the latest TAIEX data.
     */
    @Transactional(readOnly = true)
    public Optional<IndexData> getLatestTaiex() {
        return getLatest(TAIEX);
    }

    /**
     * Get index data for a specific date.
     */
    @Transactional(readOnly = true)
    public Optional<IndexData> getByDate(String indexSymbol, LocalDate date) {
        return indexDataRepository.findByIndexSymbolAndTradeDate(indexSymbol, date);
    }

    /**
     * Get historical index data for beta calculation.
     * 
     * @param indexSymbol Index symbol
     * @param days Number of trading days to retrieve
     * @return List of IndexData ordered by date ascending
     */
    @Transactional(readOnly = true)
    public List<IndexData> getHistorical(String indexSymbol, int days) {
        return indexDataRepository.findRecentBySymbol(indexSymbol, days);
    }

    /**
     * Get historical index data within a date range.
     */
    @Transactional(readOnly = true)
    public List<IndexData> getByDateRange(String indexSymbol, LocalDate startDate, LocalDate endDate) {
        return indexDataRepository.findBySymbolAndDateRange(indexSymbol, startDate, endDate);
    }

    /**
     * Get the current index value (close price).
     * 
     * @param indexSymbol Index symbol
     * @return Current index value or null if unavailable
     */
    @Transactional(readOnly = true)
    public Double getCurrentValue(String indexSymbol) {
        return getLatest(indexSymbol)
                .map(IndexData::getCloseValue)
                .orElse(null);
    }

    /**
     * Get the current TAIEX value.
     */
    @Transactional(readOnly = true)
    public Double getCurrentTaiexValue() {
        return getCurrentValue(TAIEX);
    }

    /**
     * Get daily return for an index.
     */
    @Transactional(readOnly = true)
    public Double getDailyReturn(String indexSymbol) {
        return getLatest(indexSymbol)
                .map(IndexData::getDailyReturn)
                .orElse(null);
    }

    /**
     * Calculate average daily return over a period.
     */
    @Transactional(readOnly = true)
    public Double getAverageReturn(String indexSymbol, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        return indexDataRepository.calculateAverageReturn(indexSymbol, startDate, endDate);
    }

    /**
     * Calculate realized volatility (annualized).
     */
    @Transactional(readOnly = true)
    public Double getVolatility(String indexSymbol, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        Double stdDev = indexDataRepository.calculateReturnStdDev(indexSymbol, startDate, endDate);
        if (stdDev == null || stdDev == 0) {
            return null;
        }
        // Annualize: multiply by sqrt(252 trading days)
        return stdDev * Math.sqrt(252);
    }

    /**
     * Check if the market is in a bull trend (above 50 and 200 MA).
     */
    @Transactional(readOnly = true)
    public boolean isBullMarket() {
        return getLatest(TAIEX)
                .map(IndexData::isBullMarket)
                .orElse(false);
    }

    /**
     * Check if the market is bearish (below 200 MA).
     */
    @Transactional(readOnly = true)
    public boolean isBearMarket() {
        return getLatest(TAIEX)
                .map(data -> !data.isAboveMa200())
                .orElse(false);
    }

    /**
     * Get market trend direction.
     * @return 1 for bullish, -1 for bearish, 0 for neutral
     */
    @Transactional(readOnly = true)
    public int getMarketTrend() {
        return getLatest(TAIEX)
                .map(data -> {
                    if (data.isBullMarket()) return 1;
                    if (!data.isAboveMa200()) return -1;
                    return 0;
                })
                .orElse(0);
    }

    /**
     * Manually trigger index data refresh.
     */
    public RefreshResult manualRefresh() {
        return refreshAllIndices("manual");
    }

    /**
     * Manually trigger refresh for specific indices.
     */
    public RefreshResult manualRefresh(List<String> indices) {
        return refreshIndices(indices, "manual");
    }

    // ========== Scheduled Refresh ==========

    /**
     * Scheduled refresh every 5 minutes during trading hours.
     * Taiwan market: 9:00 - 13:30
     */
    @Scheduled(cron = "0 */5 9-13 * * MON-FRI", zone = "Asia/Taipei")
    public void scheduledIntradayRefresh() {
        // Only run during market hours (9:00-13:30)
        int hour = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Taipei")).getHour();
        int minute = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Taipei")).getMinute();
        
        if (hour == 13 && minute > 30) {
            return; // Market closed
        }
        
        log.debug("📈 Intraday index data refresh...");
        refreshAllIndices("intraday");
    }

    /**
     * Scheduled daily refresh after market close.
     */
    @Scheduled(cron = "0 45 13 * * MON-FRI", zone = "Asia/Taipei")
    public void scheduledDailyRefresh() {
        log.info("📈 Starting daily index data refresh...");
        RefreshResult result = refreshAllIndices("daily");
        
        if (result.failed > 0) {
            telegramService.sendMessage(String.format(
                    "⚠️ Index data refresh completed with errors\n" +
                    "✅ Success: %d\n❌ Failed: %d",
                    result.success, result.failed));
        }
    }

    /**
     * Fetch historical data on startup if needed.
     */
    @jakarta.annotation.PostConstruct
    public void initializeOnStartup() {
        log.info("📈 Checking index data on startup...");
        
        for (String indexSymbol : DEFAULT_INDICES) {
            if (!indexDataRepository.existsByIndexSymbol(indexSymbol)) {
                log.info("📈 No data for {}, fetching historical data...", indexSymbol);
                fetchHistoricalData(indexSymbol, 252); // 1 year of trading days
            } else {
                LocalDate latestDate = indexDataRepository.findLatestDateBySymbol(indexSymbol);
                if (latestDate != null && latestDate.isBefore(LocalDate.now().minusDays(1))) {
                    log.info("📈 Updating {} from {} to today...", indexSymbol, latestDate);
                    fetchAndSaveForSymbol(indexSymbol);
                }
            }
        }
    }

    // ========== Private Methods ==========

    private RefreshResult refreshAllIndices(String trigger) {
        return refreshIndices(DEFAULT_INDICES, trigger);
    }

    private RefreshResult refreshIndices(List<String> indices, String trigger) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.debug("Index refresh already in progress, skipping");
            return new RefreshResult(0, 0, 0.0);
        }

        long startTime = System.currentTimeMillis();
        int success = 0;
        int failed = 0;

        try {
            for (String indexSymbol : indices) {
                try {
                    Optional<IndexData> data = fetchAndSaveForSymbol(indexSymbol);
                    if (data.isPresent()) {
                        success++;
                        log.debug("✅ Fetched index data for {}: {}", 
                                indexSymbol, data.get().getCloseValue());
                    } else {
                        failed++;
                        log.warn("⚠️ No data returned for index {}", indexSymbol);
                    }
                    
                    // Rate limiting: 1 second between requests
                    Thread.sleep(1000);
                } catch (Exception e) {
                    failed++;
                    log.error("❌ Failed to fetch index data for {}: {}", indexSymbol, e.getMessage());
                }
            }
        } finally {
            refreshInProgress.set(false);
        }

        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
        if (!"intraday".equals(trigger)) {
            log.info("📈 Index data refresh ({}) completed: {} success, {} failed, {:.1f}s",
                    trigger, success, failed, duration);
        }

        return new RefreshResult(success, failed, duration);
    }

    @Transactional
    public Optional<IndexData> fetchAndSaveForSymbol(String indexSymbol) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String url = PYTHON_BRIDGE_URL + "/api/index/" + indexSymbol.replace("^", "%5E");
                String response = restTemplate.getForObject(url, String.class);
                
                if (response == null || response.isBlank()) {
                    log.warn("Empty response for index {}", indexSymbol);
                    return Optional.empty();
                }

                JsonNode json = objectMapper.readTree(response);
                
                if (json.has("error") && !json.get("error").isNull()) {
                    log.warn("Error fetching index {}: {}", indexSymbol, json.get("error").asText());
                    return Optional.empty();
                }

                IndexData data = parseFromJson(indexSymbol, json);
                
                // Check if we already have data for this date
                if (indexDataRepository.existsByIndexSymbolAndTradeDate(
                        data.getIndexSymbol(), data.getTradeDate())) {
                    // Update existing record
                    Optional<IndexData> existing = indexDataRepository.findByIndexSymbolAndTradeDate(
                            data.getIndexSymbol(), data.getTradeDate());
                    if (existing.isPresent()) {
                        IndexData toUpdate = existing.get();
                        updateFromNew(toUpdate, data);
                        return Optional.of(indexDataRepository.save(toUpdate));
                    }
                }
                
                // Save new record
                IndexData saved = indexDataRepository.save(data);
                return Optional.of(saved);

            } catch (RestClientException e) {
                log.warn("Attempt {}/{} failed for index {}: {}", 
                        attempt + 1, MAX_RETRIES, indexSymbol, e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(BACKOFFS[attempt].toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing index data for {}: {}", indexSymbol, e.getMessage());
                return Optional.empty();
            }
        }
        
        return Optional.empty();
    }

    /**
     * Fetch historical data for an index.
     */
    @Transactional
    public int fetchHistoricalData(String indexSymbol, int days) {
        try {
            String url = PYTHON_BRIDGE_URL + "/api/index/" + indexSymbol.replace("^", "%5E") + 
                         "/history?days=" + days;
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isBlank()) {
                log.warn("Empty response for historical index data {}", indexSymbol);
                return 0;
            }

            JsonNode json = objectMapper.readTree(response);
            
            if (json.has("error") && !json.get("error").isNull()) {
                log.warn("Error fetching historical index {}: {}", indexSymbol, json.get("error").asText());
                return 0;
            }

            JsonNode dataArray = json.has("data") ? json.get("data") : json;
            if (!dataArray.isArray()) {
                log.warn("Expected array for historical data");
                return 0;
            }

            List<IndexData> dataList = new ArrayList<>();
            for (JsonNode item : dataArray) {
                try {
                    IndexData data = parseHistoricalFromJson(indexSymbol, item);
                    if (!indexDataRepository.existsByIndexSymbolAndTradeDate(
                            data.getIndexSymbol(), data.getTradeDate())) {
                        dataList.add(data);
                    }
                } catch (Exception e) {
                    log.debug("Skipping invalid historical record: {}", e.getMessage());
                }
            }

            if (!dataList.isEmpty()) {
                indexDataRepository.saveAll(dataList);
                log.info("📈 Saved {} historical records for {}", dataList.size(), indexSymbol);
            }

            return dataList.size();

        } catch (Exception e) {
            log.error("Error fetching historical index data for {}: {}", indexSymbol, e.getMessage());
            return 0;
        }
    }

    private IndexData parseFromJson(String indexSymbol, JsonNode json) {
        LocalDate tradeDate = LocalDate.now();
        if (json.has("trade_date") && !json.get("trade_date").isNull()) {
            tradeDate = LocalDate.parse(json.get("trade_date").asText().substring(0, 10));
        }

        return IndexData.builder()
                .indexSymbol(indexSymbol)
                .indexName(getOptionalText(json, "name"))
                .tradeDate(tradeDate)
                .fetchedAt(OffsetDateTime.now())
                .openValue(getOptionalDouble(json, "open"))
                .highValue(getOptionalDouble(json, "high"))
                .lowValue(getOptionalDouble(json, "low"))
                .closeValue(getRequiredDouble(json, "close", "regularMarketPrice", "price"))
                .previousClose(getOptionalDouble(json, "previous_close", "regularMarketPreviousClose"))
                .changePoints(getOptionalDouble(json, "change", "regularMarketChange"))
                .changePercent(getOptionalDouble(json, "change_percent", "regularMarketChangePercent"))
                .volume(getOptionalLong(json, "volume", "regularMarketVolume"))
                .tradingValue(getOptionalDouble(json, "trading_value"))
                .yearHigh(getOptionalDouble(json, "year_high", "fiftyTwoWeekHigh"))
                .yearLow(getOptionalDouble(json, "year_low", "fiftyTwoWeekLow"))
                .ma20(getOptionalDouble(json, "ma_20"))
                .ma50(getOptionalDouble(json, "ma_50", "fiftyDayAverage"))
                .ma200(getOptionalDouble(json, "ma_200", "twoHundredDayAverage"))
                .volatility20d(getOptionalDouble(json, "volatility_20d"))
                .volatility60d(getOptionalDouble(json, "volatility_60d"))
                .build();
    }

    private IndexData parseHistoricalFromJson(String indexSymbol, JsonNode json) {
        LocalDate tradeDate = LocalDate.parse(json.get("date").asText().substring(0, 10));

        return IndexData.builder()
                .indexSymbol(indexSymbol)
                .tradeDate(tradeDate)
                .fetchedAt(OffsetDateTime.now())
                .openValue(getOptionalDouble(json, "Open", "open"))
                .highValue(getOptionalDouble(json, "High", "high"))
                .lowValue(getOptionalDouble(json, "Low", "low"))
                .closeValue(getRequiredDouble(json, "Close", "close", "Adj Close"))
                .volume(getOptionalLong(json, "Volume", "volume"))
                .build();
    }

    private void updateFromNew(IndexData existing, IndexData newData) {
        existing.setCloseValue(newData.getCloseValue());
        existing.setOpenValue(newData.getOpenValue());
        existing.setHighValue(newData.getHighValue());
        existing.setLowValue(newData.getLowValue());
        existing.setPreviousClose(newData.getPreviousClose());
        existing.setChangePoints(newData.getChangePoints());
        existing.setChangePercent(newData.getChangePercent());
        existing.setVolume(newData.getVolume());
        existing.setTradingValue(newData.getTradingValue());
        existing.setYearHigh(newData.getYearHigh());
        existing.setYearLow(newData.getYearLow());
        existing.setMa20(newData.getMa20());
        existing.setMa50(newData.getMa50());
        existing.setMa200(newData.getMa200());
        existing.setVolatility20d(newData.getVolatility20d());
        existing.setVolatility60d(newData.getVolatility60d());
        existing.setFetchedAt(OffsetDateTime.now());
    }

    private String getOptionalText(JsonNode json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && !json.get(key).isNull()) {
                return json.get(key).asText();
            }
        }
        return null;
    }

    private Double getOptionalDouble(JsonNode json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && !json.get(key).isNull()) {
                return json.get(key).asDouble();
            }
        }
        return null;
    }

    private Double getRequiredDouble(JsonNode json, String... keys) {
        Double value = getOptionalDouble(json, keys);
        if (value == null) {
            throw new IllegalArgumentException("Required field missing: " + String.join("/", keys));
        }
        return value;
    }

    private Long getOptionalLong(JsonNode json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && !json.get(key).isNull()) {
                return json.get(key).asLong();
            }
        }
        return null;
    }

    /**
     * Result of a refresh operation.
     */
    public record RefreshResult(int success, int failed, double durationSeconds) {}
}
