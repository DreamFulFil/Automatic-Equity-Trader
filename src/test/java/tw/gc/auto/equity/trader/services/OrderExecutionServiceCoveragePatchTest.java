package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceCoveragePatchTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private PositionManager positionManager;
    @Mock
    private TelegramService telegramService;
    @Mock
    private DataLoggingService dataLoggingService;
    @Mock
    private RiskManagementService riskManagementService;
    @Mock
    private StockRiskSettingsService stockRiskSettingsService;
    @Mock
    private EarningsBlackoutService earningsBlackoutService;
    @Mock
    private LlmService llmService;
    @Mock
    private TradeRiskScorer tradeRiskScorer;
    @Mock
    private FundamentalFilter fundamentalFilter;

    private OrderExecutionService service;

    @BeforeEach
    void setUp() {
        service = new OrderExecutionService(
            restTemplate,
            objectMapper,
            new TradingProperties(),
            telegramService,
            dataLoggingService,
            positionManager,
            riskManagementService,
            stockRiskSettingsService,
            earningsBlackoutService,
            new TaiwanStockComplianceService(restTemplate),
            llmService,
            tradeRiskScorer,
            fundamentalFilter
        );
        when(stockRiskSettingsService.getSettings()).thenReturn(StockRiskSettings.builder().build());
        when(riskManagementService.evaluatePreTradeRisk(anyString(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyLong(), anyDouble()))
                .thenAnswer(invocation -> {
                    int qty = invocation.getArgument(1, Integer.class);
                    return new RiskManagementService.PreTradeRiskResult(true, qty, "OK", "OK");
                });
    }

    @Test
    void executeOrderWithRetry_interruptDuringBackoff() throws Exception {
        doThrow(new RuntimeException("fail")).when(restTemplate).postForObject(anyString(), any(), eq(String.class));

        Thread.currentThread().interrupt();
        try {
            service.executeOrderWithRetry("BUY", 1, 100.0, "2330.TW", false, false, "Strat");
        } finally {
            Thread.interrupted();
        }
    }
}
