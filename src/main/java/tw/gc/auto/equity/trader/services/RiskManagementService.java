package tw.gc.auto.equity.trader.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.EarningsBlackoutService;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Risk Management Service
 *
 * Responsible for:
 * - Daily and weekly P&L tracking
 * - Loss limit enforcement
 * - Earnings blackout date management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskManagementService {
    
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final String WEEKLY_PNL_FILE = "logs/weekly-pnl.txt";
    
    @NonNull
    private final EarningsBlackoutService earningsBlackoutService;
    @NonNull
    private final PositionManager positionManager;
    @NonNull
    private final MarketDataRepository marketDataRepository;
    @NonNull
    private final SectorUniverseService sectorUniverseService;
    
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> weeklyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> intradayPeakPnL = new AtomicReference<>(0.0);

    private final Map<String, AtomicReference<Double>> dailyPnLBySymbol = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> weeklyPnLBySymbol = new ConcurrentHashMap<>();
    
    private String earningsBlackoutStock = null;
    
    private volatile boolean weeklyLimitHit = false;
    private volatile boolean earningsBlackout = false;
    
    @PostConstruct
    public void initialize() {
        loadWeeklyPnL();
        refreshEarningsBlackoutState();
    }
    
    public double getDailyPnL() {
        return dailyPnL.get();
    }
    
    public double getWeeklyPnL() {
        return weeklyPnL.get();
    }
    
    public boolean isWeeklyLimitHit() {
        return weeklyLimitHit;
    }

    public double getDailyPnL(String symbol) {
        return dailyPnLBySymbol.getOrDefault(symbol, new AtomicReference<>(0.0)).get();
    }

    public double getWeeklyPnL(String symbol) {
        return weeklyPnLBySymbol.getOrDefault(symbol, new AtomicReference<>(0.0)).get();
    }
    
    public boolean isEarningsBlackout() {
        refreshEarningsBlackoutState();
        return earningsBlackout;
    }
    
    public String getEarningsBlackoutStock() {
        refreshEarningsBlackoutState();
        return earningsBlackoutStock;
    }

    public boolean isEarningsBlackoutDataStale() {
        return earningsBlackoutService.isLatestStale();
    }
    
    /**
     * Record a trade P&L and check limits
     */
    public void recordPnL(double pnl, int weeklyLossLimit) {
        recordPnL("GLOBAL", pnl, weeklyLossLimit);
    }

    public void recordPnL(String symbol, double pnl, int weeklyLossLimit) {
        double updatedDaily = dailyPnL.updateAndGet(v -> v + pnl);
        weeklyPnL.updateAndGet(v -> v + pnl);
        intradayPeakPnL.updateAndGet(v -> Math.max(v, updatedDaily));

        dailyPnLBySymbol.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0))
                .updateAndGet(v -> v + pnl);
        weeklyPnLBySymbol.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0))
                .updateAndGet(v -> v + pnl);

        saveWeeklyPnL();
        checkWeeklyLossLimit(weeklyLossLimit);
    }
    
    /**
     * Check if daily loss limit is exceeded
     */
    public boolean isDailyLimitExceeded(int dailyLossLimit) {
        return dailyPnL.get() <= -dailyLossLimit;
    }

    public boolean isIntradayLossLimitExceeded(int intradayLossLimit) {
        return getIntradayDrawdownTwd() >= intradayLossLimit;
    }

    public double getIntradayDrawdownTwd() {
        double peak = intradayPeakPnL.get();
        double current = dailyPnL.get();
        return Math.max(0.0, peak - current);
    }
    
    private void refreshEarningsBlackoutState() {
        try {
            // Ensure latest snapshot is loaded (seeds from legacy JSON or refreshes if empty)
            var blackoutDates = earningsBlackoutService.getCurrentBlackoutDates();
            EarningsBlackoutMeta latest = earningsBlackoutService.getLatestMeta().orElse(null);
            if (latest == null || blackoutDates.isEmpty()) {
                earningsBlackout = false;
                earningsBlackoutStock = null;
                return;
            }
            earningsBlackoutStock = deriveEarningsBlackoutStock(latest);
            LocalDate today = LocalDate.now(TAIPEI_ZONE);
            earningsBlackout = blackoutDates.contains(today);
            if (earningsBlackout) {
                log.warn("EARNINGS BLACKOUT DAY: {}", today.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        } catch (Exception e) {
            log.error("Failed to refresh earnings blackout state: {}", e.getMessage());
        }
    }
    
    private String deriveEarningsBlackoutStock(EarningsBlackoutMeta latest) {
        if (latest == null || latest.getTickersChecked() == null) {
            return null;
        }
        return latest.getTickersChecked().stream()
                .filter(ticker -> ticker != null && !ticker.isBlank())
                .limit(3)
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Load weekly P&L from persistent storage
     */
    public void loadWeeklyPnL() {
        try {
            Path path = Paths.get(WEEKLY_PNL_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                String[] parts = content.split(",");
                if (parts.length >= 2) {
                    LocalDate savedDate = LocalDate.parse(parts[0]);
                    double savedPnL = Double.parseDouble(parts[1]);
                    
                    LocalDate today = LocalDate.now(TAIPEI_ZONE);
                    LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    
                    if (!savedDate.isBefore(startOfWeek)) {
                        weeklyPnL.set(savedPnL);
                        log.info("Loaded weekly P&L: {} TWD (from {})", savedPnL, savedDate);
                    } else {
                        weeklyPnL.set(0.0);
                        saveWeeklyPnL();
                        log.info("New week started - Weekly P&L reset to 0");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load weekly P&L: {}", e.getMessage());
        }
    }
    
    /**
     * Save weekly P&L to persistent storage
     */
    public void saveWeeklyPnL() {
        try {
            Path path = Paths.get(WEEKLY_PNL_FILE);
            Files.createDirectories(path.getParent());
            String content = LocalDate.now(TAIPEI_ZONE) + "," + weeklyPnL.get();
            Files.writeString(path, content);
        } catch (IOException e) {
            log.warn("Could not save weekly P&L: {}", e.getMessage());
        }
    }
    
    /**
     * Check if weekly loss limit is hit
     */
    public void checkWeeklyLossLimit(int weeklyLossLimit) {
        if (weeklyPnL.get() <= -weeklyLossLimit) {
            weeklyLimitHit = true;
            log.error("WEEKLY LOSS LIMIT HIT: {} TWD (limit: -{})", weeklyPnL.get(), weeklyLossLimit);
        }
    }
    
    /**
     * Reset daily P&L (called at start of trading day)
     */
    public void resetDailyPnL() {
        dailyPnL.set(0.0);
        intradayPeakPnL.set(0.0);
    }
    
    // =====================================================================
    // Phase 5: Additional methods for calibrated risk scoring
    // =====================================================================
    
    // Track trades and streaks for risk scoring
    private final AtomicInteger tradesToday = new AtomicInteger(0);
    private final AtomicInteger winStreak = new AtomicInteger(0);
    private final AtomicInteger lossStreak = new AtomicInteger(0);
    private volatile double peakEquity = 0.0;
    private volatile double currentEquity = 0.0;
    
    /**
     * Get current drawdown percentage from peak equity
     */
    public double getCurrentDrawdownPercent() {
        if (peakEquity <= 0) {
            return 0.0;
        }
        double drawdown = (peakEquity - currentEquity) / peakEquity * 100;
        return Math.max(0, drawdown);
    }
    
    /**
     * Get number of trades executed today
     */
    public int getTradesToday() {
        return tradesToday.get();
    }
    
    /**
     * Get current winning streak count
     */
    public int getWinStreak() {
        return winStreak.get();
    }
    
    /**
     * Get current losing streak count
     */
    public int getLossStreak() {
        return lossStreak.get();
    }
    
    /**
     * Record a completed trade for streak tracking
     */
    public void recordTradeResult(boolean profitable) {
        tradesToday.incrementAndGet();
        
        if (profitable) {
            winStreak.incrementAndGet();
            lossStreak.set(0);
        } else {
            lossStreak.incrementAndGet();
            winStreak.set(0);
        }
    }
    
    /**
     * Update equity for drawdown calculation
     */
    public void updateEquity(double equity) {
        this.currentEquity = equity;
        if (equity > peakEquity) {
            this.peakEquity = equity;
        }
    }
    
    /**
     * Reset daily tracking (called at start of trading day)
     */
    public void resetDailyTracking() {
        tradesToday.set(0);
        // Note: Don't reset streaks - they continue across days
    }

    public PreTradeRiskResult evaluatePreTradeRisk(
            String symbol,
            int requestedQuantity,
            double price,
            double maxSectorExposurePct,
            double maxAdvParticipationPct,
            long minAverageDailyVolume,
            double baseEquity
    ) {
        if (requestedQuantity <= 0) {
            return new PreTradeRiskResult(false, 0, "Requested quantity is zero", "NO_QUANTITY");
        }

        LiquidityResult liquidityResult = applyLiquidityLimit(symbol, requestedQuantity, maxAdvParticipationPct, minAverageDailyVolume);
        if (!liquidityResult.allowed()) {
            return new PreTradeRiskResult(false, 0, liquidityResult.reason(), "LIQUIDITY_LIMIT");
        }

        int adjustedQuantity = liquidityResult.adjustedQuantity();
        SectorExposureResult sectorResult = checkSectorExposure(symbol, adjustedQuantity, price, maxSectorExposurePct, baseEquity);
        if (!sectorResult.allowed()) {
            return new PreTradeRiskResult(false, adjustedQuantity, sectorResult.reason(), "SECTOR_LIMIT");
        }

        return new PreTradeRiskResult(true, adjustedQuantity, "OK", "OK");
    }

    private LiquidityResult applyLiquidityLimit(
            String symbol,
            int requestedQuantity,
            double maxAdvParticipationPct,
            long minAverageDailyVolume
    ) {
        if (maxAdvParticipationPct <= 0) {
            return new LiquidityResult(true, requestedQuantity, "Liquidity limit disabled");
        }

        Optional<Double> avgVolume = Optional.ofNullable(
                marketDataRepository.averageVolumeSince(
                        symbol,
                        MarketData.Timeframe.DAY_1,
                        LocalDateTime.now(TAIPEI_ZONE).minusDays(20)
                )
        );

        if (avgVolume.isEmpty()) {
            return new LiquidityResult(true, requestedQuantity, "No volume data");
        }

        double volume = avgVolume.get();
        if (minAverageDailyVolume > 0 && volume < minAverageDailyVolume) {
            return new LiquidityResult(false, 0, String.format("Average daily volume %.0f below minimum %d", volume, minAverageDailyVolume));
        }

        int maxShares = (int) Math.floor(volume * maxAdvParticipationPct);
        if (maxShares <= 0) {
            return new LiquidityResult(false, 0, "Liquidity cap resulted in zero shares");
        }

        if (requestedQuantity > maxShares) {
            return new LiquidityResult(true, maxShares, String.format("Reduced to %d shares (ADV %.0f)", maxShares, volume));
        }

        return new LiquidityResult(true, requestedQuantity, "Liquidity OK");
    }

    private SectorExposureResult checkSectorExposure(
            String symbol,
            int quantity,
            double price,
            double maxSectorExposurePct,
            double baseEquity
    ) {
        if (maxSectorExposurePct <= 0 || baseEquity <= 0) {
            return new SectorExposureResult(true, "Sector limit disabled");
        }

        String sector = sectorUniverseService.findSector(symbol).orElse("UNKNOWN");
        Map<String, Integer> positions = positionManager.getPositionsSnapshot();
        Map<String, Double> entryPrices = positionManager.getEntryPriceSnapshot();

        double currentExposure = 0.0;
        for (Map.Entry<String, Integer> entry : positions.entrySet()) {
            String posSymbol = entry.getKey();
            int posQuantity = entry.getValue();
            if (posQuantity == 0) {
                continue;
            }

            String posSector = sectorUniverseService.findSector(posSymbol).orElse("UNKNOWN");
            if (!sector.equals(posSector)) {
                continue;
            }

            double posPrice = latestPrice(posSymbol, entryPrices.get(posSymbol));
            if (posPrice <= 0) {
                continue;
            }

            currentExposure += Math.abs(posQuantity) * posPrice;
        }

        double proposedExposure = currentExposure + (Math.abs(quantity) * price);
        double exposurePct = proposedExposure / baseEquity;

        if (exposurePct > maxSectorExposurePct) {
            return new SectorExposureResult(false, String.format(
                    "Sector %s exposure %.1f%% exceeds %.1f%%",
                    sector,
                    exposurePct * 100.0,
                    maxSectorExposurePct * 100.0
            ));
        }

        return new SectorExposureResult(true, "Sector exposure OK");
    }

    private double latestPrice(String symbol, Double fallback) {
        return marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, MarketData.Timeframe.DAY_1)
                .map(MarketData::getClose)
                .orElseGet(() -> fallback != null ? fallback : 0.0);
    }

    private record LiquidityResult(boolean allowed, int adjustedQuantity, String reason) {
    }

    private record SectorExposureResult(boolean allowed, String reason) {
    }

    public record PreTradeRiskResult(boolean allowed, int adjustedQuantity, String reason, String code) {
    }
}
