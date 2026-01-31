package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationBacktestComparatorTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @InjectMocks
    private SimulationBacktestComparator comparator;

    @Test
    void compare_shouldComputeSimulationStatsAndBacktestGaps() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        Trade winTrade = Trade.builder()
            .timestamp(start.plusDays(1))
            .mode(Trade.TradingMode.SIMULATION)
            .status(Trade.TradeStatus.CLOSED)
            .strategyName("RSIStrategy")
            .symbol("2330.TW")
            .realizedPnL(100.0)
            .build();

        Trade lossTrade = Trade.builder()
            .timestamp(start.plusDays(2))
            .mode(Trade.TradingMode.SIMULATION)
            .status(Trade.TradeStatus.CLOSED)
            .strategyName("RSIStrategy")
            .symbol("2330.TW")
            .realizedPnL(-50.0)
            .build();

        when(tradeRepository.findByStrategyNameAndTimestampBetween("RSIStrategy", start, end))
            .thenReturn(List.of(winTrade, lossTrade));

        BacktestResult backtest = BacktestResult.builder()
            .symbol("2330.TW")
            .strategyName("RSIStrategy")
            .winRatePct(60.0)
            .totalReturnPct(12.0)
            .sharpeRatio(1.1)
            .maxDrawdownPct(8.0)
            .createdAt(LocalDateTime.now())
            .build();

        when(backtestResultRepository.findBySymbolAndStrategyName("2330.TW", "RSIStrategy"))
            .thenReturn(List.of(backtest));

        SimulationBacktestComparator.ComparisonResult result =
            comparator.compare("RSIStrategy", "2330.TW", start, end);

        assertThat(result.simulationTrades()).isEqualTo(2);
        assertThat(result.simulationWinningTrades()).isEqualTo(1);
        assertThat(result.simulationWinRate()).isEqualTo(0.5);
        assertThat(result.simulationTotalPnL()).isEqualTo(50.0);
        assertThat(result.simulationMaxDrawdown()).isEqualTo(50.0);
        assertThat(result.backtestSharpeRatio()).isEqualTo(1.1);
        assertThat(result.backtestWinRatePct()).isEqualTo(60.0);
    }
}
