package tw.gc.auto.equity.trader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void testRestTemplateBean() {
        RestTemplate restTemplate = appConfig.restTemplate();
        
        assertNotNull(restTemplate);
        assertNotNull(restTemplate.getRequestFactory());
    }

    @Test
    void testObjectMapperBean() {
        ObjectMapper objectMapper = appConfig.objectMapper();
        
        assertNotNull(objectMapper);
        // Verify JavaTimeModule is registered
        assertFalse(objectMapper.getRegisteredModuleIds().isEmpty());
    }
}
