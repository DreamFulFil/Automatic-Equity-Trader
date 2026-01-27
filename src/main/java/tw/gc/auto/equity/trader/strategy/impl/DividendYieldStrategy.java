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
 * DividendYieldStrategy
 * Type: Income / Value
 * 
 * Academic Foundation:
 * - Litzenberger & Ramaswamy (1979) - 'The Effect of Personal Taxes'
 * 
 * Logic:
 * Buy stocks with high dividend yield. Uses real dividend yield from fundamental data
 * when available, falling back to price-based proxy (stability + low volatility).
 * 
 * @since 2026-01-26 - Updated to use real fundamental data
 */
@Slf4j
public class DividendYieldStrategy implements IStrategy {
    
    private final double minDividendYield;
    private final int minConsecutiveYears;
    private final FundamentalDataProvider fundamentalDataProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    /**
     * Constructor with fundamental data provider for real dividend yield.
     */
    public DividendYieldStrategy(double minDividendYield, int minConsecutiveYears,
                                 FundamentalDataProvider fundamentalDataProvider) {
        this.minDividendYield = minDividendYield;
        this.minConsecutiveYears = minConsecutiveYears;
        this.fundamentalDataProvider = fundamentalDataProvider;
    }
    
    /**
     * Legacy constructor - uses price-based proxy (backward compatible).
     */
    public DividendYieldStrategy(double minDividendYield, int minConsecutiveYears) {
        this(minDividendYield, minConsecutiveYears, FundamentalDataProvider.noOp());
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        int lookback = minConsecutiveYears * 252;
        if (prices.size() > lookback) {
            prices.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days of prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Try to get real dividend yield from fundamental data
        Optional<FundamentalData> fundamentalOpt = fundamentalDataProvider.apply(symbol);
        double dividendYield;
        double annualizedVol;
        boolean usingRealData = false;
        Double payoutRatio = null;
        
        if (fundamentalOpt.isPresent()) {
            FundamentalData fd = fundamentalOpt.get();
            Double realYield = fd.getDividendYield();
            payoutRatio = fd.getPayoutRatio();
            
            if (realYield != null && realYield > 0) {
                dividendYield = realYield;
                usingRealData = true;
                log.trace("Using real dividend yield for {}: {:.2%}", symbol, dividendYield);
            } else {
                // Fall back to proxy
                dividendYield = calculateProxyDividendYield(priceArray, currentPrice);
            }
        } else {
            // Fall back to proxy
            dividendYield = calculateProxyDividendYield(priceArray, currentPrice);
        }
        
        // Calculate volatility for risk assessment
        annualizedVol = calculateAnnualizedVolatility(priceArray);
        
        int position = portfolio.getPosition(symbol);
        
        // High yield signal with sustainable payout
        boolean highYield = dividendYield > minDividendYield;
        boolean lowVol = annualizedVol < 0.30;
        boolean sustainablePayout = payoutRatio == null || payoutRatio < 0.9; // < 90% payout is sustainable
        
        if (highYield && lowVol && sustainablePayout && position <= 0) {
            String dataSource = usingRealData ? "real yield" : "proxy";
            String payoutInfo = payoutRatio != null ? String.format(", payout=%.0f%%", payoutRatio * 100) : "";
            return TradeSignal.longSignal(usingRealData ? 0.80 : 0.70,
                String.format("High dividend yield (%s): yield=%.2f%%, vol=%.1f%%%s", 
                    dataSource, dividendYield * 100, annualizedVol * 100, payoutInfo));
        }
        
        // Exit if yield deteriorates or volatility increases
        if (position > 0 && (dividendYield < minDividendYield / 2 || annualizedVol > 0.40)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Yield exit: yield=%.2f%%, vol=%.1f%%", 
                    dividendYield * 100, annualizedVol * 100));
        }
        
        return TradeSignal.neutral(String.format("Dividend yield: %.2f%%", dividendYield * 100));
    }
    
    /**
     * Calculate proxy dividend yield from price data.
     * Uses price stability + low volatility as proxy for dividend-paying stocks.
     */
    private double calculateProxyDividendYield(Double[] priceArray, double currentPrice) {
        double avgPrice = 0;
        for (Double p : priceArray) {
            avgPrice += p;
        }
        avgPrice /= priceArray.length;
        
        // Price stability
        int daysAbove = 0, daysBelow = 0;
        for (Double p : priceArray) {
            if (p > avgPrice) daysAbove++;
            else daysBelow++;
        }
        double stability = 1 - Math.abs(daysAbove - daysBelow) / (double) priceArray.length;
        
        double annualizedVol = calculateAnnualizedVolatility(priceArray);
        double valueDiscount = (avgPrice - currentPrice) / avgPrice;
        
        return (valueDiscount > 0 ? valueDiscount * 0.5 : 0) + 
               (0.05 / Math.max(annualizedVol, 0.1)) * stability * 0.05;
    }
    
    /**
     * Calculate annualized volatility from price history.
     */
    private double calculateAnnualizedVolatility(Double[] priceArray) {
        double avgPrice = 0;
        for (Double p : priceArray) {
            avgPrice += p;
        }
        avgPrice /= priceArray.length;
        
        double sumSqDiff = 0;
        for (Double p : priceArray) {
            sumSqDiff += Math.pow(p - avgPrice, 2);
        }
        double stdDev = Math.sqrt(sumSqDiff / priceArray.length);
        return (stdDev / avgPrice) * Math.sqrt(252);
    }

    @Override
    public String getName() {
        return String.format("Dividend Yield (%.1f%%, %dy)", minDividendYield * 100, minConsecutiveYears);
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
