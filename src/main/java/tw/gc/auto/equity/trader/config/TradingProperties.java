package tw.gc.auto.equity.trader.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private Bridge bridge = new Bridge();
    
    @Data
    public static class Bridge {
        private String url = "http://localhost:8888";
        private int timeoutMs = 3000;
    }
}