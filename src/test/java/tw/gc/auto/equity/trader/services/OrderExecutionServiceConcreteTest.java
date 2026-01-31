package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceConcreteTest {

    @Mock
    RestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    TradingProperties tradingProperties;

    @Mock
    TelegramService telegramService;

    @Mock
    DataLoggingService dataLoggingService;

    @Mock
    PositionManager positionManager;

    @Mock
    RiskManagementService riskManagementService;

    @Mock
    StockRiskSettingsService stockRiskSettingsService;

    @Mock
    EarningsBlackoutService earningsBlackoutService;

    @Mock
    TaiwanStockComplianceService taiwanComplianceService;

    @Mock
    LlmService llmService;

    @Mock
    TradeRiskScorer tradeRiskScorer;

    @Mock
    FundamentalFilter fundamentalFilter;

    OrderExecutionService svc;

    @BeforeEach
    void setup() {
        tradingProperties = new TradingProperties();
        tradingProperties.getBridge().setUrl("http://bridge");

        svc = new OrderExecutionService(restTemplate, objectMapper, tradingProperties,
                telegramService, dataLoggingService, positionManager, riskManagementService,
                stockRiskSettingsService, earningsBlackoutService, taiwanComplianceService, llmService,
                tradeRiskScorer, fundamentalFilter);
    }

    @Test
    void checkBalanceAndAdjustQuantity_buy_reduces_when_insufficient() throws Exception {
        String accountJson = "{\"status\":\"ok\", \"available_margin\":1000.0}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(accountJson);

        int adjusted = svc.checkBalanceAndAdjustQuantity("BUY", 5, 500.0, "2454.TW", "stock");

        assertEquals(2, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_buy_proceeds_when_status_not_ok() throws Exception {
        String accountJson = "{\"status\":\"error\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(accountJson);

        int adjusted = svc.checkBalanceAndAdjustQuantity("BUY", 5, 500.0, "2454.TW", "stock");

        assertEquals(5, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_sell_reduces_when_not_enough_shares() {
        when(positionManager.getPosition("2330.TW")).thenReturn(2);
        int adjusted = svc.checkBalanceAndAdjustQuantity("SELL", 5, 100.0, "2330.TW", "stock");
        assertEquals(2, adjusted);
    }

    @Test
    void isInEarningsBlackout_delegates_to_service() {
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(true);
        boolean r = svc.isInEarningsBlackout();
        assertEquals(true, r);
    }

    @Test
    void flattenPosition_executes_and_logs_closure() throws Exception {
        String instrument = "2454.TW";
        when(positionManager.getPosition(instrument)).thenReturn(3);
        when(restTemplate.getForObject(contains("/signal"), eq(String.class)))
                .thenReturn("{\"current_price\":150.0}");
        when(positionManager.getEntryPrice(instrument)).thenReturn(100.0);
        when(positionManager.getEntryTime(instrument)).thenReturn(LocalDateTime.now().minusMinutes(10));
        when(stockRiskSettingsService.getWeeklyLossLimit()).thenReturn(10000);
        when(riskManagementService.getDailyPnL()).thenReturn(50.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(200.0);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);

        // Ensure order post succeeds
        when(restTemplate.postForObject(contains("/order"), any(), eq(String.class))).thenReturn("ok");

        svc.flattenPosition("Test reason", instrument, "stock", false);

        verify(dataLoggingService, atLeastOnce()).closeLatestTrade(eq(instrument), any(), eq(150.0), anyDouble(), anyInt());
        verify(positionManager).setPosition(instrument, 0);
        verify(positionManager).clearEntry(instrument);
        verify(telegramService, atLeastOnce()).sendMessage(ArgumentMatchers.contains("POSITION CLOSED"));
    }
}
