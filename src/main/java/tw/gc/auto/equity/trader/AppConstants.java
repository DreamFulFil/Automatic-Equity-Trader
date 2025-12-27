package tw.gc.auto.equity.trader;

import java.time.ZoneId;

/**
 * Application-wide constants
 */
public final class AppConstants {
    
    // Timezone configuration
    public static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    public static final String TAIPEI_ZONE_ID = "Asia/Taipei";
    
    // Trading window times (Taipei timezone)
    public static final String TRADING_WINDOW_START = "11:30";
    public static final String TRADING_WINDOW_END = "13:00";
    
    // Scheduled task cron zones
    public static final String SCHEDULER_TIMEZONE = "Asia/Taipei";
    
    private AppConstants() {
        // Utility class
    }
}