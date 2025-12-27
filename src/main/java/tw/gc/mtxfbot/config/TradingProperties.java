package tw.gc.mtxfbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {
    
    private Window window = new Window();
    private Risk risk = new Risk();
    private Bridge bridge = new Bridge();
    
    @Data
    public static class Window {
        private String start = "11:30";
        private String end = "13:00";
    }
    
    @Data
    public static class Risk {
        private int maxPosition = 1;
        private int dailyLossLimit = 4500;
    }
    
    @Data
    public static class Bridge {
        private String url = "http://localhost:8888";
        private int timeoutMs = 3000;
    }
}