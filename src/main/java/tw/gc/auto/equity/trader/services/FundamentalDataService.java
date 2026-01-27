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
import tw.gc.auto.equity.trader.entities.FundamentalData;
import tw.gc.auto.equity.trader.repositories.FundamentalDataRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for fetching and managing fundamental financial data.
 * 
 * <h3>Data Source:</h3>
 * Fetches data from Python bridge which uses yfinance to retrieve
 * fundamental metrics from Yahoo Finance.
 * 
 * <h3>Refresh Schedule:</h3>
 * <ul>
 *   <li>Daily at 14:30 Taiwan time (after market close)</li>
 *   <li>On-demand via manual refresh</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 1 Data Improvement Plan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FundamentalDataService {

    private static final String PYTHON_BRIDGE_URL = "http://localhost:8888";
    private static final Duration FRESH_DATA_THRESHOLD = Duration.ofHours(24);
    private static final int MAX_RETRIES = 3;
    private static final Duration[] BACKOFFS = {
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8)
    };

    /**
     * Default tickers to track for fundamental data.
     * These are major Taiwan stocks commonly traded.
     */
    private static final List<String> DEFAULT_TICKERS = List.of(
            "2330.TW",  // TSMC
            "2454.TW",  // MediaTek
            "2317.TW",  // Hon Hai (Foxconn)
            "2303.TW",  // UMC
            "2412.TW",  // Chunghwa Telecom
            "2882.TW",  // Cathay Financial
            "2881.TW",  // Fubon Financial
            "1301.TW",  // Formosa Plastics
            "2002.TW",  // China Steel
            "2308.TW",  // Delta Electronics
            "3008.TW",  // Largan Precision
            "2912.TW",  // President Chain Store
            "1303.TW",  // Nan Ya Plastics
            "2891.TW",  // CTBC Financial
            "2886.TW"   // Mega Financial
    );

    private final FundamentalDataRepository fundamentalDataRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // ========== Public API ==========

    /**
     * Get the latest fundamental data for a symbol.
     * Returns cached data if fresh, otherwise triggers a refresh.
     */
    @Transactional(readOnly = true)
    public Optional<FundamentalData> getLatestBySymbol(String symbol) {
        return fundamentalDataRepository.findFirstBySymbolOrderByReportDateDesc(symbol);
    }

    /**
     * Get fresh fundamental data for a symbol.
     * If cached data is stale (>24h), fetches new data.
     */
    @Transactional
    public Optional<FundamentalData> getFreshDataForSymbol(String symbol) {
        OffsetDateTime threshold = OffsetDateTime.now().minus(FRESH_DATA_THRESHOLD);
        
        // Check if we have fresh data
        if (fundamentalDataRepository.existsFreshDataForSymbol(symbol, threshold)) {
            return fundamentalDataRepository.findFirstBySymbolOrderByReportDateDesc(symbol);
        }
        
        // Fetch fresh data
        return fetchAndSaveForSymbol(symbol);
    }

    /**
     * Get latest fundamental data for multiple symbols.
     */
    @Transactional(readOnly = true)
    public List<FundamentalData> getLatestBySymbols(List<String> symbols) {
        return fundamentalDataRepository.findLatestBySymbols(symbols);
    }

    /**
     * Get all symbols with fundamental data.
     */
    @Transactional(readOnly = true)
    public List<String> getAllTrackedSymbols() {
        return fundamentalDataRepository.findAllSymbols();
    }

    /**
     * Manually trigger a refresh for all default tickers.
     */
    public RefreshResult manualRefresh() {
        return refreshAllTickers("manual");
    }

    /**
     * Manually trigger a refresh for specific symbols.
     */
    public RefreshResult manualRefresh(List<String> symbols) {
        return refreshTickers(symbols, "manual");
    }

    // ========== Value Factor Queries ==========

    @Transactional(readOnly = true)
    public List<FundamentalData> findHighEarningsYieldStocks(double maxPeRatio) {
        return fundamentalDataRepository.findHighEarningsYield(maxPeRatio);
    }

    @Transactional(readOnly = true)
    public List<FundamentalData> findHighBookToMarketStocks(double maxPbRatio) {
        return fundamentalDataRepository.findHighBookToMarket(maxPbRatio);
    }

    @Transactional(readOnly = true)
    public List<FundamentalData> findHighDividendYieldStocks(double minYield) {
        return fundamentalDataRepository.findHighDividendYield(minYield);
    }

    // ========== Quality Factor Queries ==========

    @Transactional(readOnly = true)
    public List<FundamentalData> findQualityStocks(double minRoe, double maxDebtToEquity) {
        return fundamentalDataRepository.findQualityStocks(minRoe, maxDebtToEquity);
    }

    @Transactional(readOnly = true)
    public List<FundamentalData> findHighProfitabilityStocks(double minGrossMargin) {
        return fundamentalDataRepository.findHighProfitability(minGrossMargin);
    }

    // ========== Distress Factor Queries ==========

    @Transactional(readOnly = true)
    public List<FundamentalData> findDistressedStocks(double minDebtToEquity, double maxCurrentRatio) {
        return fundamentalDataRepository.findDistressedStocks(minDebtToEquity, maxCurrentRatio);
    }

    // ========== Growth Queries ==========

    @Transactional(readOnly = true)
    public List<FundamentalData> findHighGrowthStocks(double minRevenueGrowth) {
        return fundamentalDataRepository.findHighGrowthStocks(minRevenueGrowth);
    }

    // ========== Accrual Queries ==========

    @Transactional(readOnly = true)
    public List<FundamentalData> findLowAccrualStocks(double maxAccrualsRatio) {
        return fundamentalDataRepository.findLowAccrualStocks(maxAccrualsRatio);
    }

    // ========== Scheduled Refresh ==========

    /**
     * Scheduled daily refresh at 14:30 Taiwan time (after market close).
     * Fetches fundamental data for all default tickers.
     */
    @Scheduled(cron = "0 30 14 * * MON-FRI", zone = "Asia/Taipei")
    public void scheduledRefresh() {
        log.info("📊 Starting scheduled fundamental data refresh...");
        RefreshResult result = refreshAllTickers("scheduled");
        
        if (result.failed > 0) {
            telegramService.sendMessage(String.format(
                    "⚠️ Fundamental data refresh completed with errors\n" +
                    "✅ Success: %d\n❌ Failed: %d\n⏱️ Duration: %.1fs",
                    result.success, result.failed, result.durationSeconds));
        } else {
            log.info("✅ Scheduled refresh completed: {} symbols updated in {:.1f}s",
                    result.success, result.durationSeconds);
        }
    }

    /**
     * Refresh on application startup.
     * Only refreshes if data is stale (>24h old).
     */
    @jakarta.annotation.PostConstruct
    public void refreshOnStartup() {
        log.info("📊 Checking fundamental data freshness on startup...");
        
        OffsetDateTime threshold = OffsetDateTime.now().minus(FRESH_DATA_THRESHOLD);
        List<String> staleSymbols = fundamentalDataRepository.findSymbolsWithStaleData(threshold);
        
        // Also add symbols that have no data at all
        List<String> allTracked = fundamentalDataRepository.findAllSymbols();
        List<String> missingSymbols = DEFAULT_TICKERS.stream()
                .filter(s -> !allTracked.contains(s))
                .toList();
        
        List<String> toRefresh = new ArrayList<>();
        toRefresh.addAll(staleSymbols);
        toRefresh.addAll(missingSymbols);
        
        if (toRefresh.isEmpty()) {
            log.info("✅ All fundamental data is fresh, no refresh needed");
            return;
        }
        
        log.info("📊 Refreshing {} symbols with stale/missing data...", toRefresh.size());
        refreshTickers(toRefresh, "startup");
    }

    // ========== Private Methods ==========

    private RefreshResult refreshAllTickers(String trigger) {
        return refreshTickers(DEFAULT_TICKERS, trigger);
    }

    private RefreshResult refreshTickers(List<String> symbols, String trigger) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.warn("Refresh already in progress, skipping");
            return new RefreshResult(0, 0, 0.0);
        }

        long startTime = System.currentTimeMillis();
        int success = 0;
        int failed = 0;

        try {
            for (String symbol : symbols) {
                try {
                    Optional<FundamentalData> data = fetchAndSaveForSymbol(symbol);
                    if (data.isPresent()) {
                        success++;
                        log.debug("✅ Fetched fundamental data for {}", symbol);
                    } else {
                        failed++;
                        log.warn("⚠️ No data returned for {}", symbol);
                    }
                    
                    // Rate limiting: 3 seconds between requests
                    Thread.sleep(3000);
                } catch (Exception e) {
                    failed++;
                    log.error("❌ Failed to fetch fundamental data for {}: {}", symbol, e.getMessage());
                }
            }
        } finally {
            refreshInProgress.set(false);
        }

        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
        log.info("📊 Fundamental data refresh ({}) completed: {} success, {} failed, {:.1f}s",
                trigger, success, failed, duration);

        return new RefreshResult(success, failed, duration);
    }

    @Transactional
    private Optional<FundamentalData> fetchAndSaveForSymbol(String symbol) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String url = PYTHON_BRIDGE_URL + "/api/fundamentals/" + symbol;
                String response = restTemplate.getForObject(url, String.class);
                
                if (response == null || response.isBlank()) {
                    log.warn("Empty response for symbol {}", symbol);
                    return Optional.empty();
                }

                JsonNode json = objectMapper.readTree(response);
                
                if (json.has("error")) {
                    log.warn("Error fetching fundamentals for {}: {}", symbol, json.get("error").asText());
                    return Optional.empty();
                }

                FundamentalData data = parseFromJson(symbol, json);
                
                // Save to database
                FundamentalData saved = fundamentalDataRepository.save(data);
                return Optional.of(saved);

            } catch (RestClientException e) {
                log.warn("Attempt {}/{} failed for {}: {}", attempt + 1, MAX_RETRIES, symbol, e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(BACKOFFS[attempt].toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse fundamental data for {}: {}", symbol, e.getMessage());
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private FundamentalData parseFromJson(String symbol, JsonNode json) {
        return FundamentalData.builder()
                .symbol(symbol)
                .name(getTextOrNull(json, "name"))
                .reportDate(LocalDate.now())
                .fetchedAt(OffsetDateTime.now())
                // Valuation
                .eps(getDoubleOrNull(json, "eps"))
                .peRatio(getDoubleOrNull(json, "pe_ratio"))
                .forwardPe(getDoubleOrNull(json, "forward_pe"))
                .pbRatio(getDoubleOrNull(json, "pb_ratio"))
                .psRatio(getDoubleOrNull(json, "ps_ratio"))
                .bookValue(getDoubleOrNull(json, "book_value"))
                .marketCap(getDoubleOrNull(json, "market_cap"))
                .enterpriseValue(getDoubleOrNull(json, "enterprise_value"))
                .evToEbitda(getDoubleOrNull(json, "ev_to_ebitda"))
                // Profitability
                .roe(getDoubleOrNull(json, "roe"))
                .roa(getDoubleOrNull(json, "roa"))
                .roic(getDoubleOrNull(json, "roic"))
                .grossMargin(getDoubleOrNull(json, "gross_margin"))
                .operatingMargin(getDoubleOrNull(json, "operating_margin"))
                .netMargin(getDoubleOrNull(json, "net_margin"))
                // Financial Health
                .debtToEquity(getDoubleOrNull(json, "debt_to_equity"))
                .currentRatio(getDoubleOrNull(json, "current_ratio"))
                .quickRatio(getDoubleOrNull(json, "quick_ratio"))
                .totalDebt(getDoubleOrNull(json, "total_debt"))
                .totalCash(getDoubleOrNull(json, "total_cash"))
                // Dividends
                .dividendYield(getDoubleOrNull(json, "dividend_yield"))
                .payoutRatio(getDoubleOrNull(json, "payout_ratio"))
                .dividendRate(getDoubleOrNull(json, "dividend_rate"))
                .dividendYears(getIntOrNull(json, "dividend_years"))
                // Cash Flow
                .operatingCashFlow(getDoubleOrNull(json, "operating_cash_flow"))
                .freeCashFlow(getDoubleOrNull(json, "free_cash_flow"))
                .fcfPerShare(getDoubleOrNull(json, "fcf_per_share"))
                // Growth
                .revenue(getDoubleOrNull(json, "revenue"))
                .revenueGrowth(getDoubleOrNull(json, "revenue_growth"))
                .earningsGrowth(getDoubleOrNull(json, "earnings_growth"))
                .totalAssets(getDoubleOrNull(json, "total_assets"))
                .assetGrowth(getDoubleOrNull(json, "asset_growth"))
                // Quality
                .accrualsRatio(getDoubleOrNull(json, "accruals_ratio"))
                .sharesOutstanding(getLongOrNull(json, "shares_outstanding"))
                .netStockIssuance(getDoubleOrNull(json, "net_stock_issuance"))
                // Analyst
                .analystCount(getIntOrNull(json, "analyst_count"))
                .targetPrice(getDoubleOrNull(json, "target_price"))
                .recommendationMean(getDoubleOrNull(json, "recommendation_mean"))
                // Beta & Range
                .beta(getDoubleOrNull(json, "beta"))
                .fiftyTwoWeekHigh(getDoubleOrNull(json, "fifty_two_week_high"))
                .fiftyTwoWeekLow(getDoubleOrNull(json, "fifty_two_week_low"))
                // Metadata
                .dataSource("yfinance")
                .currency(getTextOrNull(json, "currency"))
                .build();
    }

    private String getTextOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    private Double getDoubleOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull() && node.isNumber()) ? node.asDouble() : null;
    }

    private Integer getIntOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull() && node.isNumber()) ? node.asInt() : null;
    }

    private Long getLongOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull() && node.isNumber()) ? node.asLong() : null;
    }

    // ========== Result Record ==========

    public record RefreshResult(int success, int failed, double durationSeconds) {}
}
