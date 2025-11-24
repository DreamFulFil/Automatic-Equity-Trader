package tw.gc.mtxfbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shioaji")
public class ShioajiProperties {
    private String apiKey;
    private String secretKey;
    private String caPath;
    private String caPassword;
    private String personId;
    private boolean simulation = false;
}