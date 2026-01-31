package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.DailyStatistics;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValueAtRiskServiceTest {

    @Mock
    private DailyStatisticsRepository dailyStatisticsRepository;

    @Mock
    private TradeRepository tradeRepository;

    private ValueAtRiskService service;

    @BeforeEach
    void setUp() {
        service = new ValueAtRiskService(dailyStatisticsRepository, tradeRepository);
    }

    @Test
    void calculateForSymbol_usesHistoricalDailyStats() {
        List<DailyStatistics> stats = List.of(
                DailyStatistics.builder().tradeDate(LocalDate.now().minusDays(1)).totalPnL(-1000.0).build(),
                DailyStatistics.builder().tradeDate(LocalDate.now().minusDays(2)).totalPnL(500.0).build(),
                DailyStatistics.builder().tradeDate(LocalDate.now().minusDays(3)).totalPnL(-200.0).build(),
                DailyStatistics.builder().tradeDate(LocalDate.now().minusDays(4)).totalPnL(300.0).build()
        );

        when(dailyStatisticsRepository.findBySymbolAndMode(anyString(), any())).thenReturn(stats);

        ValueAtRiskService.ValueAtRiskResult result = service.calculateForSymbol(
                "2330.TW",
                Trade.TradingMode.SIMULATION,
                10,
                100_000.0
        );

        assertEquals(1000.0, result.var95Twd(), 0.01);
        assertEquals(1000.0, result.cvar95Twd(), 0.01);
        assertEquals(4, result.sampleSize());
        assertTrue(result.monteCarloVar95Twd() >= 0.0);
    }

    @Test
    void calculateForSymbol_fallsBackToTrades_whenNoDailyStats() {
        when(dailyStatisticsRepository.findBySymbolAndMode(anyString(), any())).thenReturn(List.of());

        List<Trade> trades = List.of(
                Trade.builder().timestamp(LocalDateTime.now().minusDays(1)).realizedPnL(-300.0).build(),
                Trade.builder().timestamp(LocalDateTime.now().minusDays(1)).realizedPnL(100.0).build(),
                Trade.builder().timestamp(LocalDateTime.now().minusDays(2)).realizedPnL(200.0).build()
        );

        when(tradeRepository.findByModeAndTimestampBetween(any(), any(), any())).thenReturn(trades);

        ValueAtRiskService.ValueAtRiskResult result = service.calculateForSymbol(
                "2330.TW",
                Trade.TradingMode.SIMULATION,
                5,
                100_000.0
        );

        assertTrue(result.sampleSize() >= 1);
    }
}
