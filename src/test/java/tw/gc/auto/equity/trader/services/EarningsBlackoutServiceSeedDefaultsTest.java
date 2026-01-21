package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.EarningsProperties;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.repositories.EarningsBlackoutMetaRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningsBlackoutServiceSeedDefaultsTest {

    @Mock
    private EarningsBlackoutMetaRepository metaRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TelegramService telegramService;

    private EarningsBlackoutService service;

    @BeforeEach
    void setUp() {
        EarningsProperties props = new EarningsProperties();
        props.getRefresh().setEnabled(false);

        service = new EarningsBlackoutService(metaRepository, restTemplate, new ObjectMapper(), telegramService, props);
        when(metaRepository.save(any(EarningsBlackoutMeta.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void seedFromJson_whenTickersEmptyAndTtlInvalid_shouldUseDefaults() throws Exception {
        String payload = "{" +
                "\"tickers_checked\":[]," +
                "\"dates\":[\"2026-01-05\"]," +
                "\"ttl_days\":-1" +
                "}";

        Optional<EarningsBlackoutMeta> opt = service.seedFromJson(new ObjectMapper().readTree(payload));
        assertTrue(opt.isPresent());

        EarningsBlackoutMeta meta = opt.get();
        assertTrue(meta.getTickersChecked().contains("2454.TW")); // from DEFAULT_TICKERS
        assertEquals(7, meta.getTtlDays());
    }

    @Test
    void seedFromJson_whenTtlIsZero_shouldUseDefaultTtl() throws Exception {
        // Test for line 138: ttlDays > 0 check - when ttl is 0, use DEFAULT_TTL_DAYS
        String payload = "{" +
                "\"tickers_checked\":[\"2330.TW\"]," +
                "\"dates\":[\"2026-01-05\"]," +
                "\"ttl_days\":0" +
                "}";

        Optional<EarningsBlackoutMeta> opt = service.seedFromJson(new ObjectMapper().readTree(payload));
        assertTrue(opt.isPresent());

        EarningsBlackoutMeta meta = opt.get();
        assertEquals(7, meta.getTtlDays()); // DEFAULT_TTL_DAYS = 7
    }

    @Test
    void seedFromJson_whenTickersProvidedButEmpty_shouldUseDefaultTickers() throws Exception {
        // Test for line 135: tickers.isEmpty() check
        String payload = "{" +
                "\"tickers_checked\":[]," +
                "\"dates\":[\"2026-01-05\"]," +
                "\"ttl_days\":5" +
                "}";

        Optional<EarningsBlackoutMeta> opt = service.seedFromJson(new ObjectMapper().readTree(payload));
        assertTrue(opt.isPresent());

        EarningsBlackoutMeta meta = opt.get();
        // When tickers is empty, DEFAULT_TICKERS should be used
        assertTrue(meta.getTickersChecked().contains("TSM"));
        assertTrue(meta.getTickersChecked().contains("2454.TW"));
        assertEquals(5, meta.getTtlDays()); // Valid TTL should be used
    }
}
