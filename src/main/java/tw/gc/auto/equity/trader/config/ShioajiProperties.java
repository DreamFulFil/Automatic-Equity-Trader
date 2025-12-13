package tw.gc.auto.equity.trader.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shioaji")
public class ShioajiProperties {
    private Account stock;
    private Account future;
    private String caPath;
    private String caPassword;
    private String personId;
    private boolean simulation = false;

    @Data
    public static class Account {
        private String apiKey;
        private String secretKey;
    }
}