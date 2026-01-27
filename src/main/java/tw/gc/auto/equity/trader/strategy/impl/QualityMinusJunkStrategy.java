package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.FundamentalData;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.FundamentalDataProvider;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * QualityMinusJunkStrategy
 * Type: Quality Factor
 * 
 * Academic Foundation:
 * - Asness, Frazzini & Pedersen (2019) - 'Quality Minus Junk'
 * 
 * Logic:
 * Long quality stocks (high ROE, stable margins, low debt), short junk stocks.
 * Uses real fundamental data when available (ROE, profit margins, debt ratios),
 * falling back to price-based proxy (win rate, growth, stability).
 * 
 * @since 2026-01-26 - Updated to use real fundamental data
 */
@Slf4j
public class QualityMinusJunkStrategy implements IStrategy {
    
    private final int profitabilityWindow;
    private final int growthWindow;
    private final FundamentalDataProvider fundamentalDataProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    /**
     * Constructor with fundamental data provider for real quality metrics.
     */
    public QualityMinusJunkStrategy(int profitabilityWindow, int growthWindow,
                                    FundamentalDataProvider fundamentalDataProvider) {
        this.profitabilityWindow = profitabilityWindow;
        this.growthWindow = growthWindow;
        this.fundamentalDataProvider = fundamentalDataProvider;
    }
    
    /**
     * Legacy constructor - uses price-based proxy (backward compatible).
     */
    public QualityMinusJunkStrategy(int profitabilityWindow, int growthWindow) {
        this(profitabilityWindow, growthWindow, FundamentalDataProvider.noOp());
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        int maxWindow = Math.max(profitabilityWindow, growthWindow);
        if (prices.size() > maxWindow + 10) prices.removeFirst();
        
        if (prices.size() < maxWindow) {
            return TradeSignal.neutral("Warming up QMJ");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Try to get real quality metrics from fundamental data
        Optional<FundamentalData> fundamentalOpt = fundamentalDataProvider.apply(symbol);
        double qualityScore;
        boolean usingRealData = false;
        String qualityDetails;
        
        if (fundamentalOpt.isPresent()) {
            FundamentalData fd = fundamentalOpt.get();
            Double realScore = calculateRealQualityScore(fd);
            
            if (realScore != null) {
                qualityScore = realScore;
                usingRealData = true;
                qualityDetails = buildRealQualityDetails(fd);
                log.trace("Using real QMJ for {}: {:.2f}", symbol, qualityScore);
            } else {
                // Fall back to proxy
                qualityScore = calculateProxyQualityScore(priceArray, currentPrice);
                qualityDetails = buildProxyQualityDetails(priceArray, currentPrice);
            }
        } else {
            // Fall back to proxy
            qualityScore = calculateProxyQualityScore(priceArray, currentPrice);
            qualityDetails = buildProxyQualityDetails(priceArray, currentPrice);
        }
        
        int position = portfolio.getPosition(symbol);
        
        // Quality stock signal
        double qualityThreshold = usingRealData ? 0.65 : 0.6;
        if (qualityScore > qualityThreshold && position <= 0) {
            String dataSource = usingRealData ? "real metrics" : "proxy";
            return TradeSignal.longSignal(usingRealData ? 0.80 : 0.70,
                String.format("Quality (%s): score=%.2f %s", dataSource, qualityScore, qualityDetails));
        }
        
        // Junk stock (short signal)
        double junkThreshold = usingRealData ? 0.25 : 0.3;
        if (qualityScore < junkThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Junk: score=%.2f", qualityScore));
        }
        
        // Exit quality if deteriorating
        double exitThreshold = usingRealData ? 0.45 : 0.4;
        if (position > 0 && qualityScore < exitThreshold) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Quality declining: %.2f", qualityScore));
        }
        
        return TradeSignal.neutral(String.format("QMJ score: %.2f", qualityScore));
    }
    
    /**
     * Calculate quality score from real fundamental data.
     * Components: Profitability (ROE, margins), Growth (revenue, earnings), Safety (debt ratios)
     */
    private Double calculateRealQualityScore(FundamentalData fd) {
        int validMetrics = 0;
        double totalScore = 0;
        
        // Profitability component (40% weight)
        Double roe = fd.getRoe();
        Double profitMargin = fd.getNetMargin();
        if (roe != null || profitMargin != null) {
            double profitScore = 0;
            int profitCount = 0;
            
            if (roe != null) {
                // ROE > 15% is good, > 25% is excellent
                profitScore += Math.min(roe / 0.25, 1.0);
                profitCount++;
            }
            if (profitMargin != null) {
                // Margin > 10% is good, > 20% is excellent
                profitScore += Math.min(profitMargin / 0.20, 1.0);
                profitCount++;
            }
            
            if (profitCount > 0) {
                totalScore += (profitScore / profitCount) * 0.40;
                validMetrics++;
            }
        }
        
        // Growth component (30% weight)
        Double revenueGrowth = fd.getRevenueGrowth();
        Double earningsGrowth = fd.getEarningsGrowth();
        if (revenueGrowth != null || earningsGrowth != null) {
            double growthScore = 0;
            int growthCount = 0;
            
            if (revenueGrowth != null) {
                // Revenue growth > 10% is good
                growthScore += Math.min(Math.max(revenueGrowth / 0.10, 0), 1.0);
                growthCount++;
            }
            if (earningsGrowth != null) {
                // Earnings growth > 15% is good
                growthScore += Math.min(Math.max(earningsGrowth / 0.15, 0), 1.0);
                growthCount++;
            }
            
            if (growthCount > 0) {
                totalScore += (growthScore / growthCount) * 0.30;
                validMetrics++;
            }
        }
        
        // Safety component (30% weight)
        Double debtToEquity = fd.getDebtToEquity();
        Double currentRatio = fd.getCurrentRatio();
        if (debtToEquity != null || currentRatio != null) {
            double safetyScore = 0;
            int safetyCount = 0;
            
            if (debtToEquity != null) {
                // D/E < 0.5 is good, < 0.3 is excellent
                safetyScore += Math.max(1 - debtToEquity / 1.0, 0);
                safetyCount++;
            }
            if (currentRatio != null) {
                // Current ratio > 1.5 is good, > 2.0 is excellent
                safetyScore += Math.min(currentRatio / 2.0, 1.0);
                safetyCount++;
            }
            
            if (safetyCount > 0) {
                totalScore += (safetyScore / safetyCount) * 0.30;
                validMetrics++;
            }
        }
        
        // Need at least 2 components to be valid
        return validMetrics >= 2 ? totalScore : null;
    }
    
    /**
     * Build details string for real quality metrics.
     */
    private String buildRealQualityDetails(FundamentalData fd) {
        StringBuilder sb = new StringBuilder("(");
        if (fd.getRoe() != null) {
            sb.append(String.format("ROE=%.1f%%", fd.getRoe() * 100));
        }
        if (fd.getDebtToEquity() != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(String.format("D/E=%.2f", fd.getDebtToEquity()));
        }
        if (fd.getRevenueGrowth() != null) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(String.format("growth=%.1f%%", fd.getRevenueGrowth() * 100));
        }
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Calculate proxy quality score from price data.
     */
    private double calculateProxyQualityScore(Double[] priceArray, double currentPrice) {
        // Profitability: win rate
        int positiveReturns = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            if (i > 0 && priceArray[i] > priceArray[i-1]) positiveReturns++;
        }
        double profitability = (double) positiveReturns / profitabilityWindow;
        
        // Growth: price trend
        double startPrice = priceArray[priceArray.length - growthWindow];
        double growth = (currentPrice - startPrice) / startPrice;
        
        // Stability: low volatility
        double avgPrice = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            avgPrice += priceArray[i];
        }
        avgPrice /= profitabilityWindow;
        double variance = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            variance += Math.pow(priceArray[i] - avgPrice, 2);
        }
        double stability = 1 / (1 + Math.sqrt(variance / profitabilityWindow) / avgPrice * 5);
        
        return profitability * 0.4 + (growth > 0 ? 0.3 : 0) + stability * 0.3;
    }
    
    /**
     * Build details string for proxy quality metrics.
     */
    private String buildProxyQualityDetails(Double[] priceArray, double currentPrice) {
        int positiveReturns = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            if (i > 0 && priceArray[i] > priceArray[i-1]) positiveReturns++;
        }
        double profitability = (double) positiveReturns / profitabilityWindow;
        
        double startPrice = priceArray[priceArray.length - growthWindow];
        double growth = (currentPrice - startPrice) / startPrice;
        
        return String.format("(prof=%.1f%%, growth=%.1f%%)", profitability * 100, growth * 100);
    }

    @Override
    public String getName() {
        return String.format("Quality Minus Junk (%d/%d)", profitabilityWindow, growthWindow);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
