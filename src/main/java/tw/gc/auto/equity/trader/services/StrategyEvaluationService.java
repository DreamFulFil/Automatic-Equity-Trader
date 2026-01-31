package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Strategy Evaluation Service - Calculate live Sharpe ratio and statistical significance.
 * 
 * Phase 6: Strategy Performance Culling
 * - Calculate live Sharpe ratio (not just backtest)
 * - Require minimum 30 trades before evaluation
 * - Statistical significance test for positive returns
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyEvaluationService {
    
    private final TradeRepository tradeRepository;
    private final StrategyPerformanceRepository performanceRepository;
    
    private static final int MINIMUM_TRADES_FOR_EVALUATION = 30;
    private static final double SIGNIFICANCE_LEVEL = 0.05; // 95% confidence
    
    /**
     * Evaluate a strategy for statistical significance and risk-adjusted returns.
     * Returns null if the strategy doesn't meet minimum requirements.
     */
    public EvaluationResult evaluateStrategy(String strategyName, LocalDateTime since) {
        List<Trade> trades = tradeRepository.findByStrategyNameAndTimestampAfter(strategyName, since);
        
        if (trades.size() < MINIMUM_TRADES_FOR_EVALUATION) {
            log.debug("Strategy {} has {} trades, needs {} for evaluation", 
                strategyName, trades.size(), MINIMUM_TRADES_FOR_EVALUATION);
            return EvaluationResult.builder()
                .strategyName(strategyName)
                .eligible(false)
                .reason("Insufficient trades: " + trades.size() + "/" + MINIMUM_TRADES_FOR_EVALUATION)
                .build();
        }
        
        // Extract P&L data
        List<Double> pnls = trades.stream()
            .filter(t -> t.getRealizedPnL() != null)
            .map(Trade::getRealizedPnL)
            .toList();
        
        if (pnls.isEmpty()) {
            return EvaluationResult.builder()
                .strategyName(strategyName)
                .eligible(false)
                .reason("No realized P&L data")
                .build();
        }
        
        // Calculate live Sharpe ratio
        double sharpeRatio = calculateSharpeRatio(pnls);
        
        // Calculate statistical significance
        StatisticalTest tTest = performTTest(pnls);
        
        // Get latest performance record
        List<StrategyPerformance> perfHistory = performanceRepository
            .findByStrategyNameOrderByPeriodEndDesc(strategyName);
        Double maxDrawdownPct = perfHistory.isEmpty() ? null : perfHistory.get(0).getMaxDrawdownPct();
        
        return EvaluationResult.builder()
            .strategyName(strategyName)
            .eligible(true)
            .tradeCount(trades.size())
            .sharpeRatio(sharpeRatio)
            .meanReturn(tTest.mean)
            .stdDeviation(tTest.stdDev)
            .tStatistic(tTest.tStat)
            .pValue(tTest.pValue)
            .statisticallySignificant(tTest.pValue < SIGNIFICANCE_LEVEL)
            .maxDrawdownPct(maxDrawdownPct)
            .evaluatedAt(LocalDateTime.now())
            .reason(buildEvaluationReason(sharpeRatio, tTest))
            .build();
    }
    
    /**
     * Calculate Sharpe Ratio: (Mean Return - Risk-Free Rate) / Std Dev
     * Assumes risk-free rate = 0 for simplicity
     */
    private double calculateSharpeRatio(List<Double> returns) {
        if (returns.size() < 2) return 0.0;
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        return stdDev > 0 ? mean / stdDev : 0.0;
    }
    
    /**
     * Perform one-sample t-test to check if mean return is significantly > 0
     * H0: mean = 0 (no profit)
     * H1: mean > 0 (profitable)
     */
    private StatisticalTest performTTest(List<Double> returns) {
        int n = returns.size();
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        double standardError = stdDev / Math.sqrt(n);
        
        // t-statistic for H0: μ = 0
        double tStat = standardError > 0 ? mean / standardError : 0.0;
        
        // Degrees of freedom
        int df = n - 1;
        
        // Calculate p-value using Student's t-distribution approximation
        double pValue = calculatePValue(tStat, df);
        
        return new StatisticalTest(mean, stdDev, tStat, pValue);
    }
    
    /**
     * Approximate p-value for one-tailed t-test using normal approximation.
     * For large samples (n > 30), t-distribution ≈ normal distribution.
     */
    private double calculatePValue(double tStat, int degreesOfFreedom) {
        if (degreesOfFreedom < 30) {
            log.warn("Sample size too small for accurate normal approximation");
        }
        
        // For one-tailed test: P(T > t)
        // Using complementary error function approximation
        double z = Math.abs(tStat);
        double pValue = 0.5 * (1.0 - erf(z / Math.sqrt(2.0)));
        
        return pValue;
    }
    
    /**
     * Error function approximation (Abramowitz and Stegun)
     */
    private double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        
        double erf = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t 
                     * Math.exp(-x * x);
        
        return x >= 0 ? erf : -erf;
    }
    
    private String buildEvaluationReason(double sharpeRatio, StatisticalTest tTest) {
        StringBuilder reason = new StringBuilder();
        
        if (tTest.pValue < SIGNIFICANCE_LEVEL) {
            reason.append("✅ Statistically significant positive returns (p=")
                  .append(String.format("%.4f", tTest.pValue))
                  .append("). ");
        } else {
            reason.append("❌ Not statistically significant (p=")
                  .append(String.format("%.4f", tTest.pValue))
                  .append("). ");
        }
        
        if (sharpeRatio > 1.0) {
            reason.append("Strong risk-adjusted returns (Sharpe=")
                  .append(String.format("%.2f", sharpeRatio))
                  .append(").");
        } else if (sharpeRatio > 0.5) {
            reason.append("Moderate risk-adjusted returns (Sharpe=")
                  .append(String.format("%.2f", sharpeRatio))
                  .append(").");
        } else if (sharpeRatio > 0) {
            reason.append("Weak risk-adjusted returns (Sharpe=")
                  .append(String.format("%.2f", sharpeRatio))
                  .append(").");
        } else {
            reason.append("Negative risk-adjusted returns (Sharpe=")
                  .append(String.format("%.2f", sharpeRatio))
                  .append(").");
        }
        
        return reason.toString();
    }
    
    private record StatisticalTest(double mean, double stdDev, double tStat, double pValue) {}
    
    /**
     * Result of strategy evaluation
     */
    @lombok.Builder
    @lombok.Data
    public static class EvaluationResult {
        private String strategyName;
        private boolean eligible;
        private String reason;
        private Integer tradeCount;
        private Double sharpeRatio;
        private Double meanReturn;
        private Double stdDeviation;
        private Double tStatistic;
        private Double pValue;
        private Boolean statisticallySignificant;
        private Double maxDrawdownPct;
        private LocalDateTime evaluatedAt;
    }
}
