package tw.gc.mtxfbot;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.mtxfbot.entities.EarningsBlackoutMeta;
import tw.gc.mtxfbot.services.EarningsBlackoutService;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
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
    
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> weeklyPnL = new AtomicReference<>(0.0);
    
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
        dailyPnL.updateAndGet(v -> v + pnl);
        weeklyPnL.updateAndGet(v -> v + pnl);
        saveWeeklyPnL();
        checkWeeklyLossLimit(weeklyLossLimit);
    }
    
    /**
     * Check if daily loss limit is exceeded
     */
    public boolean isDailyLimitExceeded(int dailyLossLimit) {
        return dailyPnL.get() <= -dailyLossLimit;
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
    }
}
