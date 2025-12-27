package tw.gc.mtxfbot.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private Window window = new Window();
    private Bridge bridge = new Bridge();

    // Note: Stock and Risk settings are now stored in database
    // Use StockSettingsService and RiskSettingsService instead

    // Note: Earnings blackout dates are managed by EarningsBlackoutService and stored in DB
    @Data
    public static class Window {
        private String start = "11:30";
        private String end = "13:00";
    }

    @Data
    public static class Bridge {
        private String url = "http://localhost:8888";
        private int timeoutMs = 3000;
    }
}