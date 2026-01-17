package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import tw.gc.auto.equity.trader.entities.StockSettings;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Section C (TelegramCommandHandler): Focused tests for /status formatting.
 * Includes the edge case verification for "Position Hold Time" on active trades.
 */
@ExtendWith(MockitoExtension.class)
class TelegramCommandHandlerStatusTest {

    @Mock private TelegramService telegramService;
    @Mock private TradingStateService tradingStateService;
    @Mock private PositionManager positionManager;
    @Mock private RiskManagementService riskManagementService;
    @Mock private ContractScalingService contractScalingService;
    @Mock private StockSettingsService stockSettingsService;
    @Mock private ShioajiSettingsService shioajiSettingsService;
    @Mock private LlmService llmService;
    @Mock private OrderExecutionService orderExecutionService;
    @Mock private ApplicationContext applicationContext;
    @Mock private StockRiskSettingsService stockRiskSettingsService;
    @Mock private ActiveStrategyService activeStrategyService;
    @Mock private StrategyPerformanceService strategyPerformanceService;
    @Mock private ActiveStockService activeStockService;
    @Mock private BacktestService backtestService;
    @Mock private HistoryDataService historyDataService;
    @Mock private tw.gc.auto.equity.trader.repositories.MarketDataRepository marketDataRepository;
    @Mock private AutoStrategySelector autoStrategySelector;
    @Mock private SystemConfigService systemConfigService;

    private TelegramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramCommandHandler(
            telegramService,
            tradingStateService,
            positionManager,
            riskManagementService,
            contractScalingService,
            stockSettingsService,
            shioajiSettingsService,
            llmService,
            orderExecutionService,
            applicationContext,
            stockRiskSettingsService,
            activeStrategyService,
            strategyPerformanceService,
            activeStockService,
            backtestService,
            historyDataService,
            marketDataRepository,
            autoStrategySelector,
            systemConfigService
        );
    }

    @Test
    void statusCommand_withActivePosition_includesHoldTimeMinutes() {
        List<String> messages = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(telegramService).sendMessage(anyString());

        handler.registerCommands(List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Void>> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(statusCaptor.capture(), any(), any(), any(), any());

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");

        when(positionManager.positionFor("2330.TW")).thenReturn(new AtomicInteger(100));
        when(positionManager.entryPriceFor("2330.TW")).thenReturn(new AtomicReference<>(500.0));

        LocalDateTime entry = LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(30);
        when(positionManager.entryTimeFor("2330.TW")).thenReturn(new AtomicReference<>(entry));

        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(100).shareIncrement(10).build());
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(100);
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(contractScalingService.getLast30DayProfit()).thenReturn(1234.0);
        when(riskManagementService.getDailyPnL()).thenReturn(10.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(20.0);
        when(tradingStateService.isNewsVeto()).thenReturn(false);

        statusCaptor.getValue().accept(null);

        assertThat(messages).isNotEmpty();
        String msg = messages.get(messages.size() - 1);

        assertThat(msg).contains("BOT STATUS");
        assertThat(msg).contains("Position: 100 @ 500");

        Matcher m = Pattern.compile("held (\\d+) min").matcher(msg);
        assertThat(m.find()).as("Expected hold time segment").isTrue();

        long minutes = Long.parseLong(m.group(1));
        assertThat(minutes).isBetween(29L, 31L);
    }

    @Test
    void statusCommand_inFuturesMode_includesContractCount() {
        List<String> messages = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(telegramService).sendMessage(anyString());

        handler.registerCommands(List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Void>> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(statusCaptor.capture(), any(), any(), any(), any());

        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(activeStockService.getActiveSymbol("futures")).thenReturn("MTXF");

        when(positionManager.positionFor("MTXF")).thenReturn(new AtomicInteger(0));
        when(positionManager.entryPriceFor("MTXF")).thenReturn(new AtomicReference<>(0.0));
        when(positionManager.entryTimeFor("MTXF")).thenReturn(new AtomicReference<>(null));

        when(contractScalingService.getMaxContracts()).thenReturn(3);
        when(contractScalingService.getLastEquity()).thenReturn(200000.0);
        when(contractScalingService.getLast30DayProfit()).thenReturn(0.0);
        when(riskManagementService.getDailyPnL()).thenReturn(0.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(0.0);
        when(tradingStateService.isNewsVeto()).thenReturn(false);

        statusCaptor.getValue().accept(null);

        assertThat(messages).isNotEmpty();
        String msg = messages.get(messages.size() - 1);

        assertThat(msg)
            .contains("Mode: FUTURES (MTXF)")
            .contains("Contracts: 3");
    }
}
