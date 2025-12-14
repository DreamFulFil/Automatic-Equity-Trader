package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.config.EarningsProperties;
import tw.gc.auto.equity.trader.repositories.EarningsBlackoutMetaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningsBlackoutServiceTest {

    @Mock
    private EarningsBlackoutMetaRepository metaRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TelegramService telegramService;
    @Mock
    private EarningsProperties earningsProperties;
    @Mock
    private EarningsProperties.Refresh refreshConfig;

    private EarningsBlackoutService service;

    @BeforeEach
    void setUp() {
        service = new EarningsBlackoutService(
                metaRepository,
                restTemplate,
                objectMapper,
                telegramService,
                earningsProperties
        );

        // Mock refresh config to be enabled
        lenient().when(earningsProperties.getRefresh()).thenReturn(refreshConfig);
        lenient().when(refreshConfig.isEnabled()).thenReturn(true);
    }

    @Test
    void fetchEarningsForTickers_shouldCallPythonBridge() throws Exception {
        // Given
        String mockResponse = """
            {
                "last_updated": "2025-12-10T09:00:00",
                "source": "Yahoo Finance (yfinance)",
                "tickers_checked": ["TSM", "2454.TW"],
                "dates": ["2025-12-15", "2025-12-20"]
            }
        """;

        JsonNode mockJson = new ObjectMapper().readTree(mockResponse);
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn(mockResponse);
        when(objectMapper.readTree(mockResponse)).thenReturn(mockJson);

        // When
        Map<String, List<LocalDate>> result = ReflectionTestUtils.invokeMethod(
                service, "fetchEarningsForTickers", Set.of("TSM", "2454.TW"));

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsKey("aggregated");
        assertThat(result.get("aggregated")).hasSize(2);
        assertThat(result.get("aggregated").get(0)).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(result.get("aggregated").get(1)).isEqualTo(LocalDate.of(2025, 12, 20));
    }

    @Test
    void fetchEarningsForTickers_shouldHandleEmptyResponse() throws Exception {
        // Given
        String mockResponse = """
            {
                "last_updated": "2025-12-10T09:00:00",
                "source": "Yahoo Finance (yfinance)",
                "tickers_checked": [],
                "dates": []
            }
        """;

        JsonNode mockJson = new ObjectMapper().readTree(mockResponse);
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn(mockResponse);
        when(objectMapper.readTree(mockResponse)).thenReturn(mockJson);

        // When
        Map<String, List<LocalDate>> result = ReflectionTestUtils.invokeMethod(
                service, "fetchEarningsForTickers", Set.of("TSM"));

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsKey("aggregated");
        assertThat(result.get("aggregated")).isEmpty();
    }
}