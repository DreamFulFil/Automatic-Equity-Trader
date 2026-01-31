package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedOrderServiceTest {

    @Mock
    private OrderExecutionService orderExecutionService;

    @Mock
    private TradingStateService tradingStateService;

    private AdvancedOrderService advancedOrderService;

    @BeforeEach
    void setUp() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.isEmergencyShutdown()).thenReturn(false);
        advancedOrderService = new AdvancedOrderService(orderExecutionService, tradingStateService);
    }

    @Test
    void evaluateOrders_shouldTriggerOcoTakeProfit() {
        advancedOrderService.placeOcoOrder(new AdvancedOrderService.OcoRequest(
                "2330.TW",
                AdvancedOrderService.PositionSide.LONG,
                110.0,
                90.0
        ));

        advancedOrderService.evaluateOrders("2330.TW", 112.0);

        verify(orderExecutionService).flattenPosition(contains("OCO take profit"), eq("2330.TW"), eq("stock"), eq(false));
    }

    @Test
    void evaluateOrders_shouldTriggerTrailingStop() {
        advancedOrderService.placeTrailingStopOrder(new AdvancedOrderService.TrailingStopRequest(
                "2454.TW",
                AdvancedOrderService.PositionSide.LONG,
                0.05,
                100.0
        ));

        advancedOrderService.evaluateOrders("2454.TW", 110.0);
        advancedOrderService.evaluateOrders("2454.TW", 104.0);

        verify(orderExecutionService, times(1)).flattenPosition(contains("Trailing stop"), eq("2454.TW"), eq("stock"), eq(false));
    }
}