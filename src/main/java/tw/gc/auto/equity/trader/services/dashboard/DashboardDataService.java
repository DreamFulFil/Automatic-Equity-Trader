package tw.gc.auto.equity.trader.services.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.TradeRepository;
import tw.gc.auto.equity.trader.services.PositionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard Data Service - Phase 9
 * 
 * Aggregates data for real-time monitoring dashboard:
 * - Overview metrics (P&L, positions, trades)
 * - Position summaries
 * - Strategy performance
 * - Recent trade activity
 * - Equity curve
 * - Risk metrics
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardDataService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    
    private final TradeRepository tradeRepository;
    private final PositionManager positionManager;

    /**
     * Get comprehensive dashboard overview
     */
    public DashboardOverview getDashboardOverview() {
        LocalDateTime now = LocalDateTime.now(TAIPEI_ZONE);
        
        // Get all closed trades
        List<Trade> allTrades = tradeRepository.findAll();
        List<Trade> closedTrades = allTrades.stream()
            .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED && t.getRealizedPnL() != null)
            .collect(Collectors.toList());
        
        // Calculate P&L metrics
        double totalPnl = closedTrades.stream()
            .mapToDouble(t -> t.getRealizedPnL())
            .sum();
        
        double dailyPnl = closedTrades.stream()
            .filter(t -> t.getTimestamp().isAfter(now.minusDays(1)))
            .mapToDouble(t -> t.getRealizedPnL())
            .sum();
        
        double weeklyPnl = closedTrades.stream()
            .filter(t -> t.getTimestamp().isAfter(now.minusWeeks(1)))
            .mapToDouble(t -> t.getRealizedPnL())
            .sum();
        
        double monthlyPnl = closedTrades.stream()
            .filter(t -> t.getTimestamp().isAfter(now.minusMonths(1)))
            .mapToDouble(t -> t.getRealizedPnL())
            .sum();
        
        // Count open positions (unique symbols with non-zero positions)
        long openPositions = allTrades.stream()
            .filter(t -> t.getStatus() == Trade.TradeStatus.OPEN)
            .map(Trade::getSymbol)
            .distinct()
            .count();
        
        // Count active strategies
        long activeStrategies = closedTrades.stream()
            .map(Trade::getStrategyName)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        
        // Risk metrics
        RiskMetrics riskMetrics = getRiskMetrics();
        
        return DashboardOverview.builder()
            .timestamp(now)
            .totalEquity(1000000.0 + totalPnl) // Base equity + realized P&L
            .dailyPnl(dailyPnl)
            .weeklyPnl(weeklyPnl)
            .monthlyPnl(monthlyPnl)
            .totalPnl(totalPnl)
            .openPositions((int) openPositions)
            .activeStrategies((int) activeStrategies)
            .totalTrades(closedTrades.size())
            .sharpeRatio(riskMetrics.getSharpeRatio())
            .maxDrawdown(riskMetrics.getMaxDrawdown())
            .winRate(riskMetrics.getWinRate())
            .build();
    }

    /**
     * Get current positions summary
     */
    public List<PositionSummary> getCurrentPositions() {
        List<Trade> openTrades = tradeRepository.findAll().stream()
            .filter(t -> t.getStatus() == Trade.TradeStatus.OPEN)
            .collect(Collectors.toList());
        
        // Group by symbol
        Map<String, List<Trade>> bySymbol = openTrades.stream()
            .collect(Collectors.groupingBy(Trade::getSymbol));
        
        return bySymbol.entrySet().stream()
            .map(entry -> {
                String symbol = entry.getKey();
                List<Trade> trades = entry.getValue();
                
                int quantity = trades.stream()
                    .mapToInt(t -> t.getAction() == Trade.TradeAction.BUY ? t.getQuantity() : -t.getQuantity())
                    .sum();
                
                double avgEntry = trades.stream()
                    .mapToDouble(Trade::getEntryPrice)
                    .average()
                    .orElse(0.0);
                
                // Mock current price (in real system, fetch from market data)
                double currentPrice = avgEntry * 1.02;
                double unrealizedPnl = quantity * (currentPrice - avgEntry);
                double unrealizedPnlPercent = ((currentPrice / avgEntry) - 1.0) * 100.0;
                
                return PositionSummary.builder()
                    .symbol(symbol)
                    .quantity(quantity)
                    .averageEntryPrice(avgEntry)
                    .currentPrice(currentPrice)
                    .unrealizedPnl(unrealizedPnl)
                    .unrealizedPnlPercent(unrealizedPnlPercent)
                    .build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Get top strategies by performance
     */
    public List<StrategyPerformance> getTopStrategies(int limit) {
        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusDays(30);
        
        List<Trade> recentTrades = tradeRepository.findAll().stream()
            .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED && 
                         t.getRealizedPnL() != null &&
                         t.getTimestamp().isAfter(since))
            .collect(Collectors.toList());
        
        // Group by strategy
        Map<String, List<Trade>> byStrategy = recentTrades.stream()
            .filter(t -> t.getStrategyName() != null)
            .collect(Collectors.groupingBy(Trade::getStrategyName));
        
        return byStrategy.entrySet().stream()
            .map(entry -> {
                String strategyName = entry.getKey();
                List<Trade> trades = entry.getValue();
                
                double totalPnl = trades.stream()
                    .mapToDouble(t -> t.getRealizedPnL())
                    .sum();
                
                long winningTrades = trades.stream()
                    .filter(t -> t.getRealizedPnL() > 0)
                    .count();
                
                double winRate = trades.isEmpty() ? 0.0 : (winningTrades * 100.0 / trades.size());
                double avgPnl = trades.isEmpty() ? 0.0 : totalPnl / trades.size();
                
                return StrategyPerformance.builder()
                    .strategyName(strategyName)
                    .totalPnl(totalPnl)
                    .tradeCount(trades.size())
                    .winRate(winRate)
                    .averagePnl(avgPnl)
                    .build();
            })
            .sorted(Comparator.comparingDouble(StrategyPerformance::getTotalPnl).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get recent trade activity
     */
    public List<TradeActivity> getRecentTrades(int limit) {
        return tradeRepository.findAll().stream()
            .sorted(Comparator.comparing(Trade::getTimestamp).reversed())
            .limit(limit)
            .map(trade -> TradeActivity.builder()
                .symbol(trade.getSymbol())
                .action(trade.getAction().name())
                .quantity(trade.getQuantity())
                .price(trade.getEntryPrice())
                .timestamp(trade.getTimestamp())
                .strategyName(trade.getStrategyName())
                .pnl(trade.getRealizedPnL() != null ? trade.getRealizedPnL() : 0.0)
                .status(trade.getStatus().name())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Get equity curve data
     */
    public List<EquityDataPoint> getEquityCurve(int days) {
        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusDays(days);
        
        List<Trade> trades = tradeRepository.findAll().stream()
            .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED &&
                         t.getRealizedPnL() != null &&
                         t.getTimestamp().isAfter(since))
            .sorted(Comparator.comparing(Trade::getTimestamp))
            .collect(Collectors.toList());
        
        // Group by date and calculate cumulative equity
        Map<LocalDate, Double> dailyPnl = trades.stream()
            .collect(Collectors.groupingBy(
                t -> t.getTimestamp().toLocalDate(),
                Collectors.summingDouble(t -> t.getRealizedPnL())
            ));
        
        double cumulativeEquity = 1000000.0; // Starting equity
        List<EquityDataPoint> points = new ArrayList<>();
        
        for (Map.Entry<LocalDate, Double> entry : dailyPnl.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList())) {
            cumulativeEquity += entry.getValue();
            points.add(EquityDataPoint.builder()
                .date(entry.getKey())
                .equity(cumulativeEquity)
                .dailyPnl(entry.getValue())
                .build());
        }
        
        return points;
    }

    /**
     * Get risk metrics
     */
    public RiskMetrics getRiskMetrics() {
        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusDays(90);
        
        List<Trade> trades = tradeRepository.findAll().stream()
            .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED &&
                         t.getRealizedPnL() != null &&
                         t.getTimestamp().isAfter(since))
            .collect(Collectors.toList());
        
        if (trades.isEmpty()) {
            return RiskMetrics.builder()
                .sharpeRatio(0.0)
                .maxDrawdown(0.0)
                .maxDrawdownPercent(0.0)
                .winRate(0.0)
                .profitFactor(0.0)
                .winningTrades(0)
                .losingTrades(0)
                .averageWin(0.0)
                .averageLoss(0.0)
                .build();
        }
        
        // Calculate win rate
        long winningTrades = trades.stream().filter(t -> t.getRealizedPnL() > 0).count();
        long losingTrades = trades.stream().filter(t -> t.getRealizedPnL() < 0).count();
        double winRate = (winningTrades * 100.0) / trades.size();
        
        // Calculate average win/loss
        double avgWin = trades.stream()
            .filter(t -> t.getRealizedPnL() > 0)
            .mapToDouble(t -> t.getRealizedPnL())
            .average()
            .orElse(0.0);
        
        double avgLoss = trades.stream()
            .filter(t -> t.getRealizedPnL() < 0)
            .mapToDouble(t -> t.getRealizedPnL())
            .average()
            .orElse(0.0);
        
        // Calculate profit factor
        double totalWins = trades.stream()
            .filter(t -> t.getRealizedPnL() > 0)
            .mapToDouble(t -> t.getRealizedPnL())
            .sum();
        
        double totalLosses = Math.abs(trades.stream()
            .filter(t -> t.getRealizedPnL() < 0)
            .mapToDouble(t -> t.getRealizedPnL())
            .sum());
        
        double profitFactor = totalLosses > 0 ? totalWins / totalLosses : 0.0;
        
        // Calculate Sharpe ratio (simplified)
        double avgReturn = trades.stream()
            .mapToDouble(t -> t.getRealizedPnL())
            .average()
            .orElse(0.0);
        
        double stdDev = calculateStdDev(trades.stream()
            .mapToDouble(t -> t.getRealizedPnL())
            .toArray());
        
        double sharpeRatio = stdDev > 0 ? (avgReturn / stdDev) * Math.sqrt(252) : 0.0;
        
        // Calculate max drawdown
        double maxDrawdown = trades.stream()
            .filter(t -> t.getRealizedPnL() < 0)
            .mapToDouble(t -> Math.abs(t.getRealizedPnL()))
            .max()
            .orElse(0.0);
        
        double maxDrawdownPercent = (maxDrawdown / 1000000.0) * 100.0;
        
        return RiskMetrics.builder()
            .sharpeRatio(sharpeRatio)
            .maxDrawdown(maxDrawdown)
            .maxDrawdownPercent(maxDrawdownPercent)
            .winRate(winRate)
            .profitFactor(profitFactor)
            .winningTrades((int) winningTrades)
            .losingTrades((int) losingTrades)
            .averageWin(avgWin)
            .averageLoss(avgLoss)
            .build();
    }

    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0.0;
        
        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }

    // DTO Classes

    @lombok.Builder
    @lombok.Value
    public static class DashboardOverview {
        LocalDateTime timestamp;
        double totalEquity;
        double dailyPnl;
        double weeklyPnl;
        double monthlyPnl;
        double totalPnl;
        int openPositions;
        int activeStrategies;
        int totalTrades;
        double sharpeRatio;
        double maxDrawdown;
        double winRate;
    }

    @lombok.Builder
    @lombok.Value
    public static class PositionSummary {
        String symbol;
        int quantity;
        double averageEntryPrice;
        double currentPrice;
        double unrealizedPnl;
        double unrealizedPnlPercent;
    }

    @lombok.Builder
    @lombok.Value
    public static class StrategyPerformance {
        String strategyName;
        double totalPnl;
        int tradeCount;
        double winRate;
        double averagePnl;
    }

    @lombok.Builder
    @lombok.Value
    public static class TradeActivity {
        String symbol;
        String action;
        int quantity;
        double price;
        LocalDateTime timestamp;
        String strategyName;
        double pnl;
        String status;
    }

    @lombok.Builder
    @lombok.Value
    public static class EquityDataPoint {
        LocalDate date;
        double equity;
        double dailyPnl;
    }

    @lombok.Builder
    @lombok.Value
    public static class RiskMetrics {
        double sharpeRatio;
        double maxDrawdown;
        double maxDrawdownPercent;
        double winRate;
        double profitFactor;
        int winningTrades;
        int losingTrades;
        double averageWin;
        double averageLoss;
    }
}
