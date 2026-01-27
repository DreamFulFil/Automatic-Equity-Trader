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
 * EarningsYieldStrategy
 * Type: Value Factor
 * 
 * Academic Foundation:
 * - Basu (1977) - 'Investment Performance of Common Stocks'
 * 
 * Logic:
 * Buy stocks with high earnings yield (E/P ratio = EPS / Price).
 * Uses real fundamental data when available (via FundamentalDataProvider),
 * falling back to price-based proxy when fundamental data is unavailable.
 * 
 * @since 2026-01-26 - Updated to use real fundamental data
 */
@Slf4j
public class EarningsYieldStrategy implements IStrategy {
    
    private final double minEarningsYield;
    private final int holdingPeriod;
    private final FundamentalDataProvider fundamentalDataProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> holdingDays = new HashMap<>();
    
    /**
     * Constructor with fundamental data provider for real E/P calculation.
     */
    public EarningsYieldStrategy(double minEarningsYield, int holdingPeriod, 
                                 FundamentalDataProvider fundamentalDataProvider) {
        this.minEarningsYield = minEarningsYield;
        this.holdingPeriod = holdingPeriod;
        this.fundamentalDataProvider = fundamentalDataProvider;
    }
    
    /**
     * Legacy constructor - uses price-based proxy (backward compatible).
     */
    public EarningsYieldStrategy(double minEarningsYield, int holdingPeriod) {
        this(minEarningsYield, holdingPeriod, FundamentalDataProvider.noOp());
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 120) {
            prices.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days of prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Try to get real earnings yield from fundamental data
        Optional<FundamentalData> fundamentalOpt = fundamentalDataProvider.apply(symbol);
        double earningsYield;
        double annualizedVol;
        boolean usingRealData = false;
        
        if (fundamentalOpt.isPresent()) {
            FundamentalData fd = fundamentalOpt.get();
            Double realEarningsYield = fd.getEarningsYield();
            
            if (realEarningsYield != null && realEarningsYield > 0) {
                earningsYield = realEarningsYield;
                usingRealData = true;
                log.trace("Using real E/P for {}: {:.2%}", symbol, earningsYield);
            } else {
                // Fall back to proxy
                earningsYield = calculateProxyEarningsYield(priceArray, currentPrice);
            }
        } else {
            // Fall back to proxy
            earningsYield = calculateProxyEarningsYield(priceArray, currentPrice);
        }
        
        // Calculate volatility for quality assessment
        annualizedVol = calculateAnnualizedVolatility(priceArray);
        
        int position = portfolio.getPosition(symbol);
        int days = holdingDays.getOrDefault(symbol, 0);
        
        if (position != 0) {
            holdingDays.put(symbol, days + 1);
        }
        
        // High earnings yield signal
        boolean highYield = earningsYield > minEarningsYield;
        boolean lowVol = annualizedVol < 0.30;
        
        if (highYield && lowVol && position <= 0) {
            holdingDays.put(symbol, 0);
            String dataSource = usingRealData ? "real E/P" : "proxy";
            return TradeSignal.longSignal(usingRealData ? 0.80 : 0.70,
                String.format("High earnings yield (%s): E/P=%.2f%%, vol=%.1f%%", 
                    dataSource, earningsYield * 100, annualizedVol * 100));
        }
        
        // Exit after holding period or if yield becomes too low
        if (position > 0) {
            if (days >= holdingPeriod || earningsYield < minEarningsYield / 2) {
                holdingDays.put(symbol, 0);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                    String.format("Yield exit: held %d days, yield=%.2f%%", 
                        days, earningsYield * 100));
            }
        }
        
        return TradeSignal.neutral(String.format("Earnings yield: %.2f%%", earningsYield * 100));
    }
    
    /**
     * Calculate proxy earnings yield from price data.
     * Uses price stability and volatility as proxy for earnings quality.
     */
    private double calculateProxyEarningsYield(Double[] priceArray, double currentPrice) {
        double avgPrice = 0;
        for (Double p : priceArray) {
            avgPrice += p;
        }
        avgPrice /= priceArray.length;
        
        double priceToAvg = currentPrice / avgPrice;
        double annualizedVol = calculateAnnualizedVolatility(priceArray);
        
        return (1 / priceToAvg - 1) + (0.15 / Math.max(annualizedVol, 0.1));
    }
    
    /**
     * Calculate annualized volatility from price history.
     */
    private double calculateAnnualizedVolatility(Double[] priceArray) {
        double sumReturns = 0;
        double sumSqReturns = 0;
        for (int i = 1; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i - 1]) / priceArray[i - 1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgReturn = sumReturns / (priceArray.length - 1);
        double variance = (sumSqReturns / (priceArray.length - 1)) - (avgReturn * avgReturn);
        double volatility = Math.sqrt(Math.max(variance, 0));
        return volatility * Math.sqrt(252);
    }

    @Override
    public String getName() {
        return String.format("Earnings Yield (%.1f%%, %dd)", minEarningsYield * 100, holdingPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        holdingDays.clear();
    }
}
