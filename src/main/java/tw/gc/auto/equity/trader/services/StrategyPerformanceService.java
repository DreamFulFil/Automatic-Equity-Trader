package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating and managing strategy performance metrics.
 * Implements Rolling Window Analysis (RWA) for continuous performance evaluation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyPerformanceService {
    
    private final StrategyPerformanceRepository performanceRepository;
    private final TradeRepository tradeRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Calculate performance metrics for a strategy over a time period
     */
    @Transactional
    public StrategyPerformance calculatePerformance(
        String strategyName,
        StrategyPerformance.PerformanceMode mode,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String symbol,
        Map<String, Object> parameters
    ) {
        // Fetch all trades for this strategy in the period
        List<Trade> trades = tradeRepository.findByStrategyNameAndTimestampBetween(
            strategyName, periodStart, periodEnd
        );
        
        if (trades.isEmpty()) {
            log.debug("No trades found for strategy {} in period {} to {}", 
                strategyName, periodStart, periodEnd);
            return null;
        }
        
        // Calculate metrics
        int totalTrades = trades.size();
        int winningTrades = (int) trades.stream()
            .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0)
            .count();
        
        double winRate = totalTrades > 0 ? (winningTrades * 100.0 / totalTrades) : 0.0;
        
        double totalPnl = trades.stream()
            .filter(t -> t.getRealizedPnL() != null)
            .mapToDouble(Trade::getRealizedPnL)
            .sum();
        
        double avgTradePnl = totalTrades > 0 ? totalPnl / totalTrades : 0.0;
        
        List<Double> winningPnls = trades.stream()
            .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0)
            .map(Trade::getRealizedPnL)
            .collect(Collectors.toList());
        
        List<Double> losingPnls = trades.stream()
            .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() < 0)
            .map(Trade::getRealizedPnL)
            .collect(Collectors.toList());
        
        double avgWin = winningPnls.isEmpty() ? 0.0 : 
            winningPnls.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double avgLoss = losingPnls.isEmpty() ? 0.0 : 
            losingPnls.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double grossProfit = winningPnls.stream().mapToDouble(Double::doubleValue).sum();
        double grossLoss = Math.abs(losingPnls.stream().mapToDouble(Double::doubleValue).sum());
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : 0.0;
        
        // Calculate Sharpe Ratio
        double sharpeRatio = calculateSharpeRatio(trades);
        
        // Calculate Maximum Drawdown
        double maxDrawdownPct = calculateMaxDrawdown(trades);
        
        // Calculate Total Return (assuming starting capital of 80,000 TWD per strategy)
        double startingCapital = 80000.0;
        double totalReturnPct = (totalPnl / startingCapital) * 100.0;
        
        // Serialize parameters
        String parametersJson = "{}";
        try {
            parametersJson = objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize parameters", e);
        }
        
        // Create and save performance record
        StrategyPerformance performance = StrategyPerformance.builder()
            .strategyName(strategyName)
            .performanceMode(mode)
            .symbol(symbol)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .totalTrades(totalTrades)
            .winningTrades(winningTrades)
            .winRatePct(winRate)
            .totalPnl(totalPnl)
            .sharpeRatio(sharpeRatio)
            .maxDrawdownPct(maxDrawdownPct)
            .totalReturnPct(totalReturnPct)
            .avgTradePnl(avgTradePnl)
            .avgWin(avgWin)
            .avgLoss(avgLoss)
            .profitFactor(profitFactor)
            .parametersJson(parametersJson)
            .calculatedAt(LocalDateTime.now())
            .build();
        
        return performanceRepository.save(performance);
    }
    
    /**
     * Calculate Sharpe Ratio (risk-adjusted return)
     * Sharpe = (Mean Return - Risk-Free Rate) / Standard Deviation of Returns
     */
    private double calculateSharpeRatio(List<Trade> trades) {
        if (trades.size() < 2) return 0.0;
        
        List<Double> returns = trades.stream()
            .filter(t -> t.getRealizedPnL() != null)
            .map(Trade::getRealizedPnL)
            .collect(Collectors.toList());
        
        if (returns.isEmpty()) return 0.0;
        
        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - meanReturn, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        
        if (stdDev == 0) return 0.0;
        
        // Assume risk-free rate is 0 for simplicity
        double riskFreeRate = 0.0;
        
        return (meanReturn - riskFreeRate) / stdDev;
    }
    
    /**
     * Calculate Maximum Drawdown (%)
     */
    private double calculateMaxDrawdown(List<Trade> trades) {
        if (trades.isEmpty()) return 0.0;
        
        double peak = 0.0;
        double maxDrawdown = 0.0;
        double runningPnl = 0.0;
        
        for (Trade trade : trades) {
            if (trade.getRealizedPnL() != null) {
                runningPnl += trade.getRealizedPnL();
                
                if (runningPnl > peak) {
                    peak = runningPnl;
                }
                
                double drawdown = peak - runningPnl;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
        }
        
        // Convert to percentage
        double startingCapital = 80000.0;
        return peak > 0 ? (maxDrawdown / (startingCapital + peak)) * 100.0 : 0.0;
    }
    
    /**
     * Get best performing strategy based on recent performance
     */
    public StrategyPerformance getBestPerformer(int lookbackDays) {
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(lookbackDays);
        List<StrategyPerformance> performers = performanceRepository
            .findTopPerformersBySharpeRatio(sinceDate);
        
        return performers.isEmpty() ? null : performers.get(0);
    }
    
    /**
     * Get all strategies ranked by performance
     */
    public List<StrategyPerformance> getRankedStrategies() {
        return performanceRepository.findLatestPerformanceForAllStrategies();
    }
}
