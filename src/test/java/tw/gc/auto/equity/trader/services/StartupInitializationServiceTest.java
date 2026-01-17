package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.ActiveShadowSelection;
import tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository;
import tw.gc.auto.equity.trader.repositories.BacktestRankingRepository;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;

import java.util.List;

import static org.mockito.Mockito.*;

class StartupInitializationServiceTest {

    @Test
    void initializeOnStartup_firstStartup_sendsInstructionsAndReturns() {
        BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
        BacktestRankingRepository backtestRankingRepository = mock(BacktestRankingRepository.class);
        ActiveShadowSelectionRepository activeShadowSelectionRepository = mock(ActiveShadowSelectionRepository.class);
        AutoStrategySelector autoStrategySelector = mock(AutoStrategySelector.class);
        TelegramService telegramService = mock(TelegramService.class);

        when(backtestResultRepository.count()).thenReturn(0L);
        when(activeShadowSelectionRepository.count()).thenReturn(0L);

        StartupInitializationService svc = new StartupInitializationService(
            backtestResultRepository,
            backtestRankingRepository,
            activeShadowSelectionRepository,
            autoStrategySelector,
            telegramService
        );

        svc.initializeOnStartup();

        verify(telegramService).sendMessage(contains("First Startup Detected"));
        verifyNoInteractions(autoStrategySelector);
    }

    @Test
    void initializeOnStartup_noSelection_runsAutoSelection() {
        BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
        BacktestRankingRepository backtestRankingRepository = mock(BacktestRankingRepository.class);
        ActiveShadowSelectionRepository activeShadowSelectionRepository = mock(ActiveShadowSelectionRepository.class);
        AutoStrategySelector autoStrategySelector = mock(AutoStrategySelector.class);
        TelegramService telegramService = mock(TelegramService.class);

        when(backtestResultRepository.count()).thenReturn(10L);
        when(activeShadowSelectionRepository.count()).thenReturn(0L);

        StartupInitializationService svc = new StartupInitializationService(
            backtestResultRepository,
            backtestRankingRepository,
            activeShadowSelectionRepository,
            autoStrategySelector,
            telegramService
        );

        svc.initializeOnStartup();

        verify(autoStrategySelector).selectBestStrategyAndStock();
        verifyNoInteractions(telegramService);
    }

    @Test
    void initializeOnStartup_existingSelection_logsCurrentSelection() {
        BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
        BacktestRankingRepository backtestRankingRepository = mock(BacktestRankingRepository.class);
        ActiveShadowSelectionRepository activeShadowSelectionRepository = mock(ActiveShadowSelectionRepository.class);
        AutoStrategySelector autoStrategySelector = mock(AutoStrategySelector.class);
        TelegramService telegramService = mock(TelegramService.class);

        when(backtestResultRepository.count()).thenReturn(10L);
        when(activeShadowSelectionRepository.count()).thenReturn(1L);
        when(activeShadowSelectionRepository.findAllByOrderByRankPosition()).thenReturn(List.of(
            ActiveShadowSelection.builder()
                .rankPosition(1)
                .isActive(true)
                .symbol("2454.TW")
                .stockName("MediaTek")
                .strategyName("RSI")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .build()
        ));

        StartupInitializationService svc = new StartupInitializationService(
            backtestResultRepository,
            backtestRankingRepository,
            activeShadowSelectionRepository,
            autoStrategySelector,
            telegramService
        );

        svc.initializeOnStartup();

        verifyNoInteractions(autoStrategySelector);
        verifyNoInteractions(telegramService);
    }

    @Test
    void initializeOnStartup_autoSelectionFails_sendsTelegramError() {
        BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
        BacktestRankingRepository backtestRankingRepository = mock(BacktestRankingRepository.class);
        ActiveShadowSelectionRepository activeShadowSelectionRepository = mock(ActiveShadowSelectionRepository.class);
        AutoStrategySelector autoStrategySelector = mock(AutoStrategySelector.class);
        TelegramService telegramService = mock(TelegramService.class);

        when(backtestResultRepository.count()).thenReturn(10L);
        when(activeShadowSelectionRepository.count()).thenReturn(0L);
        doThrow(new RuntimeException("Test error")).when(autoStrategySelector).selectBestStrategyAndStock();

        StartupInitializationService svc = new StartupInitializationService(
            backtestResultRepository,
            backtestRankingRepository,
            activeShadowSelectionRepository,
            autoStrategySelector,
            telegramService
        );

        svc.initializeOnStartup();

        verify(autoStrategySelector).selectBestStrategyAndStock();
        verify(telegramService).sendMessage(contains("Auto-selection failed"));
    }

    @Test
    void initializeOnStartup_emptySelectionsList_returnsEarly() {
        BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
        BacktestRankingRepository backtestRankingRepository = mock(BacktestRankingRepository.class);
        ActiveShadowSelectionRepository activeShadowSelectionRepository = mock(ActiveShadowSelectionRepository.class);
        AutoStrategySelector autoStrategySelector = mock(AutoStrategySelector.class);
        TelegramService telegramService = mock(TelegramService.class);

        when(backtestResultRepository.count()).thenReturn(10L);
        when(activeShadowSelectionRepository.count()).thenReturn(1L);
        when(activeShadowSelectionRepository.findAllByOrderByRankPosition()).thenReturn(List.of());

        StartupInitializationService svc = new StartupInitializationService(
            backtestResultRepository,
            backtestRankingRepository,
            activeShadowSelectionRepository,
            autoStrategySelector,
            telegramService
        );

        svc.initializeOnStartup();

        verifyNoInteractions(autoStrategySelector);
        verifyNoInteractions(telegramService);
    }

    @Test
    void initializeOnStartup_multipleShadowModeSelections_logsAll() {
        BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
        BacktestRankingRepository backtestRankingRepository = mock(BacktestRankingRepository.class);
        ActiveShadowSelectionRepository activeShadowSelectionRepository = mock(ActiveShadowSelectionRepository.class);
        AutoStrategySelector autoStrategySelector = mock(AutoStrategySelector.class);
        TelegramService telegramService = mock(TelegramService.class);

        when(backtestResultRepository.count()).thenReturn(10L);
        when(activeShadowSelectionRepository.count()).thenReturn(3L);
        when(activeShadowSelectionRepository.findAllByOrderByRankPosition()).thenReturn(List.of(
            ActiveShadowSelection.builder()
                .rankPosition(1)
                .isActive(true)
                .symbol("2330.TW")
                .stockName("TSMC")
                .strategyName("RSI")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .build(),
            ActiveShadowSelection.builder()
                .rankPosition(2)
                .isActive(false)
                .symbol("2454.TW")
                .stockName("MediaTek")
                .strategyName("MACD")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .build(),
            ActiveShadowSelection.builder()
                .rankPosition(3)
                .isActive(false)
                .symbol("2317.TW")
                .stockName("HonHai")
                .strategyName("BollingerBand")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .build()
        ));

        StartupInitializationService svc = new StartupInitializationService(
            backtestResultRepository,
            backtestRankingRepository,
            activeShadowSelectionRepository,
            autoStrategySelector,
            telegramService
        );

        svc.initializeOnStartup();

        verifyNoInteractions(autoStrategySelector);
        verifyNoInteractions(telegramService);
    }
}
