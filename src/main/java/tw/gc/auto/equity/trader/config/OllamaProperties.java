package tw.gc.auto.equity.trader.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    private String url = "http://localhost:11434";
    private String model = "mistral:7b-instruct-v0.2-q5_K_M";
}