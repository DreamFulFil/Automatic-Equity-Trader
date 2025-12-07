package tw.gc.mtxfbot.config;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
public class TradingProperties {

    private Window window = new Window();
    private Bridge bridge = new Bridge();

    // Note: Stock and Risk settings are now stored in database
    // Use StockSettingsService and RiskSettingsService instead

    // Note: Earnings blackout dates are now loaded from config/earnings-blackout-dates.json
    // See TradingEngine.loadEarningsBlackoutDates()

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