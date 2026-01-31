package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.DailyStatistics;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValueAtRiskService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final int DEFAULT_MONTE_CARLO_SAMPLES = 10_000;

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final TradeRepository tradeRepository;

    public ValueAtRiskResult calculateForSymbol(String symbol, Trade.TradingMode mode, int days, double baseEquity) {
        List<Double> returns = loadDailyReturns(symbol, mode, days, baseEquity);
        if (returns.isEmpty()) {
            return new ValueAtRiskResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0);
        }

        double var95 = historicalVar(returns, 0.95) * baseEquity;
        double var99 = historicalVar(returns, 0.99) * baseEquity;
        double cvar95 = conditionalVar(returns, 0.95) * baseEquity;
        double cvar99 = conditionalVar(returns, 0.99) * baseEquity;

        MonteCarloStats mcStats = monteCarloStats(returns, DEFAULT_MONTE_CARLO_SAMPLES);
        double mcVar95 = mcStats.var95() * baseEquity;
        double mcVar99 = mcStats.var99() * baseEquity;

        return new ValueAtRiskResult(
                var95,
                var99,
                cvar95,
                cvar99,
                mcVar95,
                mcVar99,
                returns.size()
        );
    }

    private List<Double> loadDailyReturns(String symbol, Trade.TradingMode mode, int days, double baseEquity) {
        if (baseEquity <= 0) {
            return List.of();
        }

        List<DailyStatistics> dailyStats = dailyStatisticsRepository.findBySymbolAndMode(symbol, mode);
        List<Double> returns = dailyStats.stream()
                .sorted(Comparator.comparing(DailyStatistics::getTradeDate).reversed())
                .limit(days)
                .map(this::resolveDailyPnL)
                .filter(pnl -> pnl != null && pnl != 0.0)
                .map(pnl -> pnl / baseEquity)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!returns.isEmpty()) {
            return returns;
        }

        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusDays(days);
        List<Trade> trades = tradeRepository.findByModeAndTimestampBetween(mode, since, LocalDateTime.now(TAIPEI_ZONE));
        if (trades.isEmpty()) {
            return List.of();
        }

        Map<java.time.LocalDate, Double> dailyPnL = trades.stream()
                .filter(trade -> trade.getRealizedPnL() != null)
                .collect(Collectors.groupingBy(
                        trade -> trade.getTimestamp().toLocalDate(),
                        Collectors.summingDouble(Trade::getRealizedPnL)
                ));

        return dailyPnL.values().stream()
                .filter(pnl -> pnl != 0.0)
                .map(pnl -> pnl / baseEquity)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Double resolveDailyPnL(DailyStatistics stats) {
        if (stats.getTotalPnL() != null) {
            return stats.getTotalPnL();
        }
        return stats.getRealizedPnL();
    }

    private double historicalVar(List<Double> returns, double confidence) {
        List<Double> sorted = new ArrayList<>(returns);
        sorted.sort(Double::compareTo);
        int index = (int) Math.floor((1.0 - confidence) * sorted.size());
        index = Math.min(Math.max(index, 0), sorted.size() - 1);
        double quantile = sorted.get(index);
        return Math.max(0.0, -quantile);
    }

    private double conditionalVar(List<Double> returns, double confidence) {
        List<Double> sorted = new ArrayList<>(returns);
        sorted.sort(Double::compareTo);
        int index = (int) Math.floor((1.0 - confidence) * sorted.size());
        index = Math.min(Math.max(index, 0), sorted.size() - 1);
        double threshold = sorted.get(index);

        double sum = 0.0;
        int count = 0;
        for (double value : sorted) {
            if (value <= threshold) {
                sum += value;
                count++;
            }
        }

        if (count == 0) {
            return 0.0;
        }

        return Math.max(0.0, -(sum / count));
    }

    private MonteCarloStats monteCarloStats(List<Double> returns, int samples) {
        if (returns.size() < 2) {
            return new MonteCarloStats(0.0, 0.0);
        }

        DoubleSummaryStatistics stats = returns.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double mean = stats.getAverage();
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .sum() / (returns.size() - 1);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0.0) {
            return new MonteCarloStats(0.0, 0.0);
        }

        List<Double> simulated = new ArrayList<>(samples);
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < samples; i++) {
            double z = boxMuller(random);
            simulated.add(mean + stdDev * z);
        }

        double var95 = historicalVar(simulated, 0.95);
        double var99 = historicalVar(simulated, 0.99);
        return new MonteCarloStats(var95, var99);
    }

    private double boxMuller(java.util.Random random) {
        double u1 = random.nextDouble();
        double u2 = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(Math.max(u1, 1e-12))) * Math.cos(2.0 * Math.PI * u2);
    }

    private record MonteCarloStats(double var95, double var99) {
    }

    public record ValueAtRiskResult(
            double var95Twd,
            double var99Twd,
            double cvar95Twd,
            double cvar99Twd,
            double monteCarloVar95Twd,
            double monteCarloVar99Twd,
            int sampleSize
    ) {
    }
}
