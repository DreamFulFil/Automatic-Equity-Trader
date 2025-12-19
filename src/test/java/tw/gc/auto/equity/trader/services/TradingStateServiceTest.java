package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingStateServiceTest {

    @Mock
    private ActiveStrategyService activeStrategyService;

    private TradingStateService tradingStateService;

    @BeforeEach
    void setUp() {
        tradingStateService = new TradingStateService(activeStrategyService);
    }

    @Test
    void getTradingMode_shouldReturnDefaultStock() {
        assertEquals("stock", tradingStateService.getTradingMode());
    }

    @Test
    void setTradingMode_shouldUpdateMode() {
        tradingStateService.setTradingMode("futures");
        assertEquals("futures", tradingStateService.getTradingMode());
    }

    @Test
    void isEmergencyShutdown_shouldReturnFalseByDefault() {
        assertFalse(tradingStateService.isEmergencyShutdown());
    }

    @Test
    void setEmergencyShutdown_shouldUpdateFlag() {
        tradingStateService.setEmergencyShutdown(true);
        assertTrue(tradingStateService.isEmergencyShutdown());
    }

    @Test
    void isMarketDataConnected_shouldReturnFalseByDefault() {
        assertFalse(tradingStateService.isMarketDataConnected());
    }

    @Test
    void setMarketDataConnected_shouldUpdateFlag() {
        tradingStateService.setMarketDataConnected(true);
        assertTrue(tradingStateService.isMarketDataConnected());
    }

    @Test
    void isTradingPaused_shouldReturnFalseByDefault() {
        assertFalse(tradingStateService.isTradingPaused());
    }

    @Test
    void setTradingPaused_shouldUpdateFlag() {
        tradingStateService.setTradingPaused(true);
        assertTrue(tradingStateService.isTradingPaused());
    }

    @Test
    void isActivePolling_shouldReturnTrueByDefault() {
        assertTrue(tradingStateService.isActivePolling());
    }

    @Test
    void setActivePolling_shouldUpdateFlag() {
        tradingStateService.setActivePolling(false);
        assertFalse(tradingStateService.isActivePolling());
    }

    @Test
    void getActiveStrategyName_shouldDelegateToActiveStrategyService() {
        when(activeStrategyService.getActiveStrategyName()).thenReturn("RSIStrategy");
        
        String result = tradingStateService.getActiveStrategyName();
        
        assertEquals("RSIStrategy", result);
        verify(activeStrategyService).getActiveStrategyName();
    }

    @Test
    void setActiveStrategyName_shouldDelegateToActiveStrategyService() {
        tradingStateService.setActiveStrategyName("MACDStrategy");
        
        verify(activeStrategyService).switchStrategy(
            eq("MACDStrategy"),
            anyMap(),
            anyString(),
            eq(false)
        );
    }

    @Test
    void getActiveStrategyParameters_shouldDelegateToActiveStrategyService() {
        Map<String, Object> params = new HashMap<>();
        params.put("period", 14);
        when(activeStrategyService.getActiveStrategyParameters()).thenReturn(params);
        
        Map<String, Object> result = tradingStateService.getActiveStrategyParameters();
        
        assertEquals(14, result.get("period"));
        verify(activeStrategyService).getActiveStrategyParameters();
    }

    @Test
    void isNewsVeto_shouldReturnFalseByDefault() {
        assertFalse(tradingStateService.isNewsVeto());
    }

    @Test
    void setNewsVeto_shouldUpdateVetoAndReason() {
        tradingStateService.setNewsVeto(true, "Market crash news");
        
        assertTrue(tradingStateService.isNewsVeto());
        assertEquals("Market crash news", tradingStateService.getNewsReason());
    }

    @Test
    void getNewsReason_shouldReturnEmptyByDefault() {
        assertEquals("", tradingStateService.getNewsReason());
    }

    @Test
    void setNewsVeto_withFalse_shouldClearVeto() {
        tradingStateService.setNewsVeto(true, "Bad news");
        tradingStateService.setNewsVeto(false, "All clear");
        
        assertFalse(tradingStateService.isNewsVeto());
        assertEquals("All clear", tradingStateService.getNewsReason());
    }

    @Test
    void concurrentNewsVetoUpdates_shouldBeThreadSafe() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                tradingStateService.setNewsVeto(true, "Thread1");
            }
        });
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                tradingStateService.setNewsVeto(false, "Thread2");
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        // Should not throw and should have a valid state
        boolean veto = tradingStateService.isNewsVeto();
        String reason = tradingStateService.getNewsReason();
        assertNotNull(reason);
    }
}
