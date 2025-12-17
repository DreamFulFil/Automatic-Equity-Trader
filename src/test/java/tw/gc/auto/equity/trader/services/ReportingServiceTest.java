package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportingServiceTest {

    @Mock
    private EndOfDayStatisticsService endOfDayStatisticsService;
    @Mock
    private DailyStatisticsRepository dailyStatisticsRepository;
    @Mock
    private StrategyPerformanceRepository strategyPerformanceRepository;
    @Mock
    private StrategyStockMappingRepository strategyStockMappingRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private RiskManagementService riskManagementService;
    @Mock
    private ContractScalingService contractScalingService;
    @Mock
    private StockSettingsService stockSettingsService;
    @Mock
    private TelegramService telegramService;
    @Mock
    private TradingStateService tradingStateService;
    @Mock
    private StrategyManager strategyManager;
    @Mock
    private ActiveStockService activeStockService;
    @Mock
    private ActiveStrategyService activeStrategyService;

    private ReportingService reportingService;

    @BeforeEach
    void setUp() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStrategyService.getActiveStrategyName()).thenReturn("TestStrategy");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(strategyPerformanceRepository.findAll()).thenReturn(Collections.emptyList());
        
        StockSettings stockSettings = StockSettings.builder()
                .shares(55)
                .shareIncrement(27)
                .build();
        when(stockSettingsService.getSettings()).thenReturn(stockSettings);
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(55);
        
        reportingService = new ReportingService(
            endOfDayStatisticsService, dailyStatisticsRepository, 
            strategyPerformanceRepository, strategyStockMappingRepository,
            llmService, riskManagementService, contractScalingService, stockSettingsService,
            telegramService, tradingStateService, strategyManager, activeStockService,
            activeStrategyService
        );
    }

    @Test
    void sendDailySummary_whenProfitable_shouldShowProfitableStatus() {
        // Given
        when(riskManagementService.getDailyPnL()).thenReturn(1500.0);

        // When
        reportingService.sendDailySummary();

        // Then
        verify(telegramService).sendMessage(contains("Profitable"));
        verify(telegramService).sendMessage(contains("Solid day"));
    }

    @Test
    void sendDailySummary_whenLoss_shouldShowLossStatus() {
        // Given
        when(riskManagementService.getDailyPnL()).thenReturn(-1000.0);

        // When
        reportingService.sendDailySummary();

        // Then
        verify(telegramService).sendMessage(contains("Loss"));
    }

    @Test
    void sendDailySummary_whenExceptionalDay_shouldCelebrate() {
        // Given
        when(riskManagementService.getDailyPnL()).thenReturn(5000.0);

        // When
        reportingService.sendDailySummary();

        // Then
        verify(telegramService).sendMessage(contains("EXCEPTIONAL DAY"));
    }
}
