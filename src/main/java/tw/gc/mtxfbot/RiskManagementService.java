package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String EARNINGS_BLACKOUT_FILE = "config/earnings-blackout-dates.json";
    
    private final ObjectMapper objectMapper;
    
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> weeklyPnL = new AtomicReference<>(0.0);
    
    private final Set<String> earningsBlackoutDates = new HashSet<>();
    private String earningsBlackoutStock = null;
    
    private volatile boolean weeklyLimitHit = false;
    private volatile boolean earningsBlackout = false;
    
    @PostConstruct
    public void initialize() {
        loadWeeklyPnL();
        loadEarningsBlackoutDates();
        checkEarningsBlackout();
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
        return earningsBlackout;
    }
    
    public String getEarningsBlackoutStock() {
        return earningsBlackoutStock;
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
    
    /**
     * Load earnings blackout dates from JSON file
     */
    public void loadEarningsBlackoutDates() {
        try {
            File file = new File(EARNINGS_BLACKOUT_FILE);
            if (!file.exists()) {
                log.warn("Earnings blackout file not found: {} - no blackout dates loaded", EARNINGS_BLACKOUT_FILE);
                return;
            }
            
            JsonNode root = objectMapper.readTree(file);
            JsonNode dates = root.path("dates");
            
            if (dates.isArray()) {
                earningsBlackoutDates.clear();
                for (JsonNode dateNode : dates) {
                    earningsBlackoutDates.add(dateNode.asText());
                }
            }
            
            String lastUpdated = root.path("last_updated").asText("unknown");
            log.info("Loaded {} earnings blackout dates (last updated: {})", 
                earningsBlackoutDates.size(), lastUpdated);
            
        } catch (Exception e) {
            log.error("Failed to load earnings blackout dates: {}", e.getMessage());
        }
    }
    
    /**
     * Check if today is an earnings blackout day
     */
    public void checkEarningsBlackout() {
        String today = LocalDate.now(TAIPEI_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (earningsBlackoutDates.contains(today)) {
            earningsBlackout = true;
            earningsBlackoutStock = "TSMC/major earnings";
            log.warn("EARNINGS BLACKOUT DAY: {}", today);
        }
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
