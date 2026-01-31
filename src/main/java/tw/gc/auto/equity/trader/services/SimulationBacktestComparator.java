package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * SimulationBacktestComparator
 *
 * Compares simulation trades vs backtest expectations for the same strategy/stock.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationBacktestComparator {

    private final TradeRepository tradeRepository;
    private final BacktestResultRepository backtestResultRepository;

    public ComparisonResult compare(String strategyName, String symbol, LocalDateTime start, LocalDateTime end) {
        List<Trade> trades = tradeRepository.findByStrategyNameAndTimestampBetween(strategyName, start, end).stream()
            .filter(trade -> trade.getMode() == Trade.TradingMode.SIMULATION)
            .filter(trade -> trade.getStatus() == Trade.TradeStatus.CLOSED)
            .filter(trade -> symbol == null || symbol.equalsIgnoreCase(trade.getSymbol()))
            .sorted(Comparator.comparing(Trade::getTimestamp))
            .toList();

        int totalTrades = trades.size();
        long winningTrades = trades.stream().filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0).count();
        double totalPnL = trades.stream().mapToDouble(t -> t.getRealizedPnL() == null ? 0.0 : t.getRealizedPnL()).sum();
        double avgTradePnL = totalTrades > 0 ? totalPnL / totalTrades : 0.0;
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;
        double maxDrawdown = calculateMaxDrawdown(trades);

        Optional<BacktestResult> backtestResult = latestBacktestResult(symbol, strategyName);

        Double backtestWinRate = backtestResult.map(BacktestResult::getWinRatePct).orElse(null);
        Double backtestReturn = backtestResult.map(BacktestResult::getTotalReturnPct).orElse(null);
        Double backtestSharpe = backtestResult.map(BacktestResult::getSharpeRatio).orElse(null);
        Double backtestDrawdown = backtestResult.map(BacktestResult::getMaxDrawdownPct).orElse(null);

        double winRateGap = backtestWinRate == null ? 0.0 : (winRate * 100.0) - backtestWinRate;
        double returnGap = backtestReturn == null ? 0.0 : totalPnL - backtestReturn;

        return new ComparisonResult(
            strategyName,
            symbol,
            start,
            end,
            totalTrades,
            winningTrades,
            winRate,
            totalPnL,
            avgTradePnL,
            maxDrawdown,
            backtestWinRate,
            backtestReturn,
            backtestSharpe,
            backtestDrawdown,
            winRateGap,
            returnGap
        );
    }

    private Optional<BacktestResult> latestBacktestResult(String symbol, String strategyName) {
        if (symbol == null || strategyName == null) {
            return Optional.empty();
        }
        return backtestResultRepository.findBySymbolAndStrategyName(symbol, strategyName).stream()
            .max(Comparator.comparing(BacktestResult::getCreatedAt));
    }

    private double calculateMaxDrawdown(List<Trade> trades) {
        double equity = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;

        for (Trade trade : trades) {
            double pnl = trade.getRealizedPnL() == null ? 0.0 : trade.getRealizedPnL();
            equity += pnl;
            if (equity > peak) {
                peak = equity;
            }
            double drawdown = peak - equity;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    public record ComparisonResult(
        String strategyName,
        String symbol,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        int simulationTrades,
        long simulationWinningTrades,
        double simulationWinRate,
        double simulationTotalPnL,
        double simulationAvgTradePnL,
        double simulationMaxDrawdown,
        Double backtestWinRatePct,
        Double backtestTotalReturnPct,
        Double backtestSharpeRatio,
        Double backtestMaxDrawdownPct,
        double winRateGapPct,
        double returnGap
    ) {
    }
}
