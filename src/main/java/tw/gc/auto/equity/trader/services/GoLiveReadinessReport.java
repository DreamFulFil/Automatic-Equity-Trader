package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.ActiveStrategyConfig;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * GoLiveReadinessReport
 *
 * Aggregates simulation vs backtest data for go-live decisions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoLiveReadinessReport {

    private static final int DEFAULT_LOOKBACK_MONTHS = 6;
    private static final int MIN_SIM_TRADES = 20;
    private static final double MIN_WIN_RATE = 0.55;

    private final SimulationBacktestComparator comparator;
    private final ActiveStrategyService activeStrategyService;
    private final ActiveStockService activeStockService;

    public ReadinessReport generate() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMonths(DEFAULT_LOOKBACK_MONTHS);

        ActiveStrategyConfig config = activeStrategyService.getActiveStrategy();
        String strategyName = config.getStrategyName();
        String symbol = Optional.ofNullable(config.getStockSymbol())
            .filter(s -> !s.isBlank())
            .orElse(activeStockService.getActiveStock());

        SimulationBacktestComparator.ComparisonResult comparison =
            comparator.compare(strategyName, symbol, start, end);

        boolean hasEnoughTrades = comparison.simulationTrades() >= MIN_SIM_TRADES;
        boolean winRateOk = comparison.simulationWinRate() >= MIN_WIN_RATE;

        String recommendation = buildRecommendation(hasEnoughTrades, winRateOk, comparison);

        return new ReadinessReport(
            strategyName,
            symbol,
            start,
            end,
            comparison,
            hasEnoughTrades,
            winRateOk,
            recommendation
        );
    }

    private String buildRecommendation(boolean hasEnoughTrades, boolean winRateOk,
                                       SimulationBacktestComparator.ComparisonResult comparison) {
        if (!hasEnoughTrades) {
            return "Hold: Need more simulation trades before go-live";
        }
        if (!winRateOk) {
            return "Hold: Simulation win rate below threshold";
        }
        if (comparison.backtestSharpeRatio() != null && comparison.backtestSharpeRatio() < 0.5) {
            return "Caution: Backtest Sharpe ratio remains weak";
        }
        return "Proceed: Simulation metrics meet minimum thresholds";
    }

    public record ReadinessReport(
        String strategyName,
        String symbol,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        SimulationBacktestComparator.ComparisonResult comparison,
        boolean hasEnoughSimulationTrades,
        boolean winRateOk,
        String recommendation
    ) {
    }
}
