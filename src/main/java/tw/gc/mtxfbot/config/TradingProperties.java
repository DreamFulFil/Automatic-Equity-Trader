package tw.gc.mtxfbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {
    
    private Window window = new Window();
    private Risk risk = new Risk();
    private Bridge bridge = new Bridge();
    
    // Note: Earnings blackout dates are now loaded from config/earnings-blackout-dates.json
    // See TradingEngine.loadEarningsBlackoutDates()
    
    @Data
    public static class Window {
        private String start = "11:30";
        private String end = "13:00";
    }
    
    @Data
    public static class Risk {
        private int maxPosition = 1;
        private int dailyLossLimit = 4500;
        /** Weekly loss limit in TWD - triggers pause until next Monday */
        private int weeklyLossLimit = 15000;
        /** Max minutes to hold a position before forced exit */
        private int maxHoldMinutes = 45;
    }
    
    @Data
    public static class Bridge {
        private String url = "http://localhost:8888";
        private int timeoutMs = 3000;
    }
}