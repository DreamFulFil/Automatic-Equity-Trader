package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.Event;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorporateActionServiceTest {

    @Mock
    private DataLoggingService dataLoggingService;

    private PositionManager positionManager;
    private CorporateActionService service;

    @BeforeEach
    void setUp() {
        positionManager = new PositionManager();
        service = new CorporateActionService(positionManager, dataLoggingService);
        when(dataLoggingService.logEvent(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void applyStockSplit_shouldAdjustPositionAndEntryPrice() {
        positionManager.setPosition("2330.TW", 100);
        positionManager.updateEntry("2330.TW", 200.0, null);

        CorporateActionService.SplitAdjustmentResult result = service.applyStockSplit("2330.TW", 2, 1);

        assertThat(result.oldQuantity()).isEqualTo(100);
        assertThat(result.newQuantity()).isEqualTo(200);
        assertThat(positionManager.getPosition("2330.TW")).isEqualTo(200);
        assertThat(positionManager.getEntryPrice("2330.TW")).isEqualTo(100.0);
        verify(dataLoggingService).logEvent(any(Event.class));
    }

    @Test
    void applyCashDividend_shouldReturnTotalDividend() {
        positionManager.setPosition("2454.TW", 50);

        CorporateActionService.CashDividendResult result = service.applyCashDividend("2454.TW", 3.5, LocalDate.now());

        assertThat(result.totalDividend()).isEqualTo(175.0);
        verify(dataLoggingService).logEvent(any(Event.class));
    }
}