package tw.gc.mtxfbot.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Component
@ConfigurationProperties(prefix = "earnings")
public class EarningsProperties {

    private Refresh refresh = new Refresh();

    @Data
    public static class Refresh {
        private boolean enabled = true;
    }
}