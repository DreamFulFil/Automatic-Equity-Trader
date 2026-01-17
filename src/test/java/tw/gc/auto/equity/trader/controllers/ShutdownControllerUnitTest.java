package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import tw.gc.auto.equity.trader.services.ShioajiSettingsService;
import tw.gc.auto.equity.trader.services.TradingEngineService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ShutdownControllerUnitTest {

    @Test
    void triggerShutdown_defaultSpringExit_shouldFlatten_andNotExit_whenExitDisabled() throws Exception {
        TradingEngineService tradingEngine = mock(TradingEngineService.class);
        ShioajiSettingsService shioajiSettingsService = mock(ShioajiSettingsService.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);

        ShutdownController controller = new ShutdownController(tradingEngine, shioajiSettingsService, applicationContext);
        controller.setExitEnabled(false);
        controller.setSpringExitHandler(null); // exercise default SpringApplication.exit path

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(tradingEngine).flattenPosition(anyString());

        controller.triggerShutdown();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(tradingEngine).flattenPosition(anyString());
    }

    @Test
    void triggerShutdown_exitEnabled_shouldInvokeExitHandler() throws Exception {
        TradingEngineService tradingEngine = mock(TradingEngineService.class);
        ShioajiSettingsService shioajiSettingsService = mock(ShioajiSettingsService.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);

        ShutdownController controller = new ShutdownController(tradingEngine, shioajiSettingsService, applicationContext);
        controller.setExitEnabled(true);
        controller.setSpringExitHandler(() -> 0);

        CountDownLatch latch = new CountDownLatch(1);
        controller.setExitHandler(code -> latch.countDown());

        controller.triggerShutdown();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
}
