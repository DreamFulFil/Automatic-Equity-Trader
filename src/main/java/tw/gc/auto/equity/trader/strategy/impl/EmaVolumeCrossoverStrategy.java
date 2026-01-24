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
 * EMA Volume Crossover Strategy
 *
 * Generates signals when the short EMA crosses the long EMA, with a volume filter.
 * - LONG on bullish crossover if volume exceeds SMA * multiplier
 * - EXIT/SHORT on bearish crossover
 */
@Slf4j
public class EmaVolumeCrossoverStrategy implements IStrategy {

    private final int shortPeriod;
    private final int longPeriod;
    private final int volumePeriod;
    private final double volumeMultiplier;

    private final Map<String, Double> shortEmaBySymbol = new HashMap<>();
    private final Map<String, Double> longEmaBySymbol = new HashMap<>();
    private final Map<String, Integer> sampleCount = new HashMap<>();
    private final Map<String, Boolean> previousShortAboveLong = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();

    public EmaVolumeCrossoverStrategy() {
        this(12, 26, 20, 1.2);
    }

    public EmaVolumeCrossoverStrategy(int shortPeriod, int longPeriod, int volumePeriod, double volumeMultiplier) {
        if (shortPeriod <= 0 || longPeriod <= 0 || volumePeriod <= 0) {
            throw new IllegalArgumentException("Periods must be positive");
        }
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("Short period must be less than long period");
        }
        if (volumeMultiplier <= 0) {
            throw new IllegalArgumentException("Volume multiplier must be positive");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        this.volumePeriod = volumePeriod;
        this.volumeMultiplier = volumeMultiplier;
        log.info("[EMA Volume Crossover] Initialized short={}, long={}, volumePeriod={}, volumeMultiplier={}",
            shortPeriod, longPeriod, volumePeriod, volumeMultiplier);
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            return TradeSignal.neutral("No market data");
        }

        String symbol = data.getSymbol();
        double price = data.getClose();
        if (!Double.isFinite(price) || price <= 0) {
            return TradeSignal.neutral("Invalid price");
        }

        int count = sampleCount.merge(symbol, 1, Integer::sum);

        double shortEma = updateEma(shortEmaBySymbol, symbol, price, shortPeriod);
        double longEma = updateEma(longEmaBySymbol, symbol, price, longPeriod);

        double volumeAverage = updateVolumeAverage(symbol, data.getVolume());

        if (count < longPeriod) {
            return TradeSignal.neutral(String.format("Warming up (%d/%d)", count, longPeriod));
        }

        boolean currentAbove = shortEma > longEma;
        Boolean previousAbove = previousShortAboveLong.get(symbol);
        previousShortAboveLong.put(symbol, currentAbove);

        if (previousAbove == null) {
            return TradeSignal.neutral("Establishing baseline");
        }

        int position = portfolio.getPosition(symbol);
        boolean volumeOk = volumeAverage > 0 && data.getVolume() >= (volumeAverage * volumeMultiplier);

        if (!previousAbove && currentAbove) {
            if (position <= 0 && volumeOk) {
                return TradeSignal.longSignal(0.7,
                    String.format("EMA bullish crossover with volume %d > %.0f", data.getVolume(), volumeAverage * volumeMultiplier));
            }
            if (position > 0) {
                return TradeSignal.neutral("Already long");
            }
            return TradeSignal.neutral("Volume below threshold");
        }

        if (previousAbove && !currentAbove) {
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.7, "EMA bearish crossover - exit");
            }
            if (position == 0) {
                return TradeSignal.shortSignal(0.6, "EMA bearish crossover");
            }
            return TradeSignal.neutral("Already short");
        }

        return TradeSignal.neutral("No crossover detected");
    }

    private double updateEma(Map<String, Double> emaBySymbol, String symbol, double price, int period) {
        double alpha = 2.0 / (period + 1.0);
        Double prev = emaBySymbol.get(symbol);
        double ema = (prev == null) ? price : (alpha * price + (1.0 - alpha) * prev);
        emaBySymbol.put(symbol, ema);
        return ema;
    }

    private double updateVolumeAverage(String symbol, long volume) {
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        volumes.addLast(volume);
        if (volumes.size() > volumePeriod) {
            volumes.removeFirst();
        }
        if (volumes.size() < volumePeriod) {
            return 0.0;
        }
        long sum = 0L;
        for (long v : volumes) {
            sum += v;
        }
        return (double) sum / volumes.size();
    }

    @Override
    public String getName() {
        return String.format("EMA Volume Crossover (%d/%d)", shortPeriod, longPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        shortEmaBySymbol.clear();
        longEmaBySymbol.clear();
        sampleCount.clear();
        previousShortAboveLong.clear();
        volumeHistory.clear();
    }
}
