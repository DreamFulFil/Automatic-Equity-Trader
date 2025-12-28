package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * On-Balance Volume (OBV) Strategy
 *
 * Simple trend-following approach using OBV slope over a lookback window.
 */
@Slf4j
public class OnBalanceVolumeStrategy implements IStrategy {

    private final int lookbackPeriod;
    private final double slopeThreshold; // minimal slope to consider trend

    private final Map<String, Long> lastVolume = new HashMap<>();
    private final Map<String, Double> lastClose = new HashMap<>();
    private final Map<String, Deque<Double>> obvHistory = new HashMap<>();

    public OnBalanceVolumeStrategy(int lookbackPeriod, double slopeThreshold) {
        this.lookbackPeriod = lookbackPeriod;
        this.slopeThreshold = slopeThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null) {
            return TradeSignal.neutral("No market data");
        }
        String symbol = data.getSymbol();
        double close = data.getClose();
        long volume = data.getVolume();

        Double prevClose = lastClose.get(symbol);
        Double prevObv = obvHistory.getOrDefault(symbol, new ArrayDeque<>()).peekLast();
        double obv = prevObv == null ? 0.0 : prevObv;

        if (prevClose != null) {
            if (close > prevClose) obv += volume;
            else if (close < prevClose) obv -= volume;
        }

        lastClose.put(symbol, close);

        Deque<Double> deque = obvHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        deque.addLast(obv);
        // need lookbackPeriod + 1 samples to compute slope across lookbackPeriod intervals
        if (deque.size() > (lookbackPeriod + 1)) deque.removeFirst();

        if (deque.size() < (lookbackPeriod + 1)) {
            return TradeSignal.neutral("Warming up - need " + (lookbackPeriod + 1) + " OBV points");
        }

        // Simple slope: (latest - earliest) / lookbackPeriod (n intervals)
        double earliest = deque.peekFirst();
        double latest = deque.peekLast();
        double slope = (latest - earliest) / (double) lookbackPeriod;

        int position = portfolio.getPosition(symbol);

        if (slope > slopeThreshold && position <= 0) {
            return TradeSignal.longSignal(0.6, String.format("OBV slope %.4f > %.4f", slope, slopeThreshold));
        }
        if (slope < -slopeThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.6, String.format("OBV slope %.4f < -%.4f", slope, slopeThreshold));
        }

        return TradeSignal.neutral(String.format("OBV slope %.4f", slope));
    }

    @Override
    public String getName() {
        return String.format("OnBalanceVolume (%d)", lookbackPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        lastVolume.clear();
        lastClose.clear();
        obvHistory.clear();
    }
}
