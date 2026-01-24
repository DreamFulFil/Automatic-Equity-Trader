package tw.gc.auto.equity.trader.indicators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for calculating technical indicators.
 */
public final class TechnicalIndicatorCalculator {

    private static final double EPSILON = 1e-12;

    private TechnicalIndicatorCalculator() {
        throw new AssertionError("Utility class");
    }

    public static Optional<Double> simpleMovingAverage(List<Double> prices, int period) {
        Objects.requireNonNull(prices, "prices");
        validatePositive(period, "period");
        if (prices.size() < period) {
            return Optional.empty();
        }
        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return Optional.of(sum / period);
    }

    public static Optional<Double> exponentialMovingAverage(List<Double> prices, int period) {
        Objects.requireNonNull(prices, "prices");
        validatePositive(period, "period");
        if (prices.size() < period) {
            return Optional.empty();
        }
        double k = 2.0 / (period + 1.0);
        double sma = average(prices.subList(0, period));
        double ema = sma;
        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) * k) + (ema * (1.0 - k));
        }
        return Optional.of(ema);
    }

    public static Optional<Double> relativeStrengthIndex(List<Double> prices, int period) {
        Objects.requireNonNull(prices, "prices");
        validatePositive(period, "period");
        if (prices.size() < period + 1) {
            return Optional.empty();
        }
        double gainSum = 0.0;
        double lossSum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum += Math.abs(change);
            }
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0.0) {
            return Optional.of(100.0);
        }
        if (avgGain == 0.0) {
            return Optional.of(0.0);
        }
        double rs = avgGain / avgLoss;
        double rsi = 100.0 - (100.0 / (1.0 + rs));
        return Optional.of(rsi);
    }

    public static Optional<MacdResult> macd(List<Double> prices, int fastPeriod, int slowPeriod, int signalPeriod) {
        Objects.requireNonNull(prices, "prices");
        validatePositive(fastPeriod, "fastPeriod");
        validatePositive(slowPeriod, "slowPeriod");
        validatePositive(signalPeriod, "signalPeriod");
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("fastPeriod must be less than slowPeriod");
        }
        if (prices.size() < slowPeriod + signalPeriod) {
            return Optional.empty();
        }

        List<Double> fastEma = calculateEmaSeries(prices, fastPeriod);
        List<Double> slowEma = calculateEmaSeries(prices, slowPeriod);

        List<Double> macdSeries = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            Double fast = fastEma.get(i);
            Double slow = slowEma.get(i);
            if (fast != null && slow != null) {
                macdSeries.add(fast - slow);
            }
        }

        Optional<Double> signalOpt = exponentialMovingAverage(macdSeries, signalPeriod);
        if (signalOpt.isEmpty()) {
            return Optional.empty();
        }

        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double signalLine = signalOpt.get();
        double histogram = macdLine - signalLine;
        return Optional.of(new MacdResult(macdLine, signalLine, histogram));
    }

    public static Optional<BollingerBands> bollingerBands(List<Double> prices, int period, double stdDevMultiplier) {
        Objects.requireNonNull(prices, "prices");
        validatePositive(period, "period");
        if (stdDevMultiplier <= 0.0) {
            throw new IllegalArgumentException("stdDevMultiplier must be positive");
        }
        if (prices.size() < period) {
            return Optional.empty();
        }
        List<Double> window = prices.subList(prices.size() - period, prices.size());
        double mean = average(window);
        double stdDev = standardDeviation(window, mean);
        double upper = mean + stdDevMultiplier * stdDev;
        double lower = mean - stdDevMultiplier * stdDev;
        return Optional.of(new BollingerBands(mean, upper, lower, stdDev));
    }

    public static Optional<Long> onBalanceVolume(List<Double> closes, List<Long> volumes) {
        Objects.requireNonNull(closes, "closes");
        Objects.requireNonNull(volumes, "volumes");
        if (closes.size() != volumes.size()) {
            throw new IllegalArgumentException("closes and volumes must be the same length");
        }
        if (closes.size() < 2) {
            return Optional.empty();
        }
        long obv = 0L;
        for (int i = 1; i < closes.size(); i++) {
            double current = closes.get(i);
            double previous = closes.get(i - 1);
            long volume = volumes.get(i);
            if (current > previous) {
                obv += volume;
            } else if (current < previous) {
                obv -= volume;
            }
        }
        return Optional.of(obv);
    }

    public static Optional<ArimaForecast> arimaForecast(List<Double> values, int p, int d, int q, int steps) {
        Objects.requireNonNull(values, "values");
        validatePositive(p, "p");
        if (d < 0) {
            throw new IllegalArgumentException("d must be non-negative");
        }
        if (q < 0) {
            throw new IllegalArgumentException("q must be non-negative");
        }
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }
        if (q != 0) {
            throw new IllegalArgumentException("Only q=0 is supported in this ARIMA implementation");
        }
        if (values.size() <= p + d) {
            return Optional.empty();
        }

        List<Double> differenced = difference(values, d);
        if (differenced.size() <= p) {
            return Optional.empty();
        }

        List<Double> coefficients = estimateArCoefficients(differenced, p);
        if (coefficients.isEmpty()) {
            return Optional.empty();
        }

        List<Double> forecastDiffs = forecastAr(differenced, coefficients, steps);
        List<Double> forecast = integrate(values, forecastDiffs, d);

        return Optional.of(new ArimaForecast(List.copyOf(forecast), List.copyOf(coefficients), p, d, q));
    }

    private static void validatePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static double average(List<Double> values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double standardDeviation(List<Double> values, double mean) {
        double variance = 0.0;
        for (double value : values) {
            double diff = value - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / values.size());
    }

    private static List<Double> calculateEmaSeries(List<Double> prices, int period) {
        if (prices.size() < period) {
            return new ArrayList<>(Collections.nCopies(prices.size(), null));
        }
        List<Double> emaSeries = new ArrayList<>(Collections.nCopies(prices.size(), null));
        double k = 2.0 / (period + 1.0);
        double sma = average(prices.subList(0, period));
        double ema = sma;
        emaSeries.set(period - 1, ema);
        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) * k) + (ema * (1.0 - k));
            emaSeries.set(i, ema);
        }
        return emaSeries;
    }

    private static List<Double> difference(List<Double> values, int d) {
        List<Double> current = new ArrayList<>(values);
        for (int i = 0; i < d; i++) {
            if (current.size() < 2) {
                return List.of();
            }
            List<Double> diff = new ArrayList<>(current.size() - 1);
            for (int j = 1; j < current.size(); j++) {
                diff.add(current.get(j) - current.get(j - 1));
            }
            current = diff;
        }
        return current;
    }

    private static List<Double> estimateArCoefficients(List<Double> series, int p) {
        int n = series.size();
        int rows = n - p;
        if (rows <= p) {
            return List.of();
        }
        double[][] xtx = new double[p][p];
        double[] xty = new double[p];

        for (int t = p; t < n; t++) {
            double y = series.get(t);
            for (int i = 0; i < p; i++) {
                double x_i = series.get(t - i - 1);
                xty[i] += x_i * y;
                for (int j = 0; j < p; j++) {
                    double x_j = series.get(t - j - 1);
                    xtx[i][j] += x_i * x_j;
                }
            }
        }

        double[] beta = solveLinearSystem(xtx, xty);
        if (beta == null) {
            return List.of();
        }
        List<Double> coefficients = new ArrayList<>(p);
        for (double value : beta) {
            coefficients.add(value);
        }
        return coefficients;
    }

    private static double[] solveLinearSystem(double[][] matrix, double[] vector) {
        int n = vector.length;
        double[][] augmented = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, n);
            augmented[i][n] = vector[i];
        }

        for (int pivot = 0; pivot < n; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < n; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[maxRow][pivot])) {
                    maxRow = row;
                }
            }
            if (Math.abs(augmented[maxRow][pivot]) < EPSILON) {
                return null;
            }
            if (maxRow != pivot) {
                double[] temp = augmented[pivot];
                augmented[pivot] = augmented[maxRow];
                augmented[maxRow] = temp;
            }

            double pivotValue = augmented[pivot][pivot];
            for (int col = pivot; col <= n; col++) {
                augmented[pivot][col] /= pivotValue;
            }

            for (int row = 0; row < n; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = augmented[row][pivot];
                for (int col = pivot; col <= n; col++) {
                    augmented[row][col] -= factor * augmented[pivot][col];
                }
            }
        }

        double[] solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = augmented[i][n];
        }
        return solution;
    }

    private static List<Double> forecastAr(List<Double> series, List<Double> coefficients, int steps) {
        List<Double> lags = new ArrayList<>();
        for (int i = 0; i < coefficients.size(); i++) {
            lags.add(series.get(series.size() - 1 - i));
        }

        List<Double> forecast = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            double next = 0.0;
            for (int j = 0; j < coefficients.size(); j++) {
                next += coefficients.get(j) * lags.get(j);
            }
            forecast.add(next);
            lags.add(0, next);
            lags.remove(lags.size() - 1);
        }
        return forecast;
    }

    private static List<Double> integrate(List<Double> original, List<Double> forecastDiffs, int d) {
        if (d == 0) {
            return new ArrayList<>(forecastDiffs);
        }
        List<Double> lastValues = new ArrayList<>();
        lastValues.add(original.get(original.size() - 1));
        List<Double> current = new ArrayList<>(original);
        for (int level = 0; level < d; level++) {
            current = difference(current, 1);
            lastValues.add(current.get(current.size() - 1));
        }

        List<Double> forecast = new ArrayList<>(forecastDiffs.size());
        for (double diffValue : forecastDiffs) {
            double value = diffValue;
            for (int level = d; level >= 1; level--) {
                double base = lastValues.get(level - 1);
                value = base + value;
                lastValues.set(level - 1, value);
            }
            forecast.add(value);
        }
        return forecast;
    }

    public record BollingerBands(double middle, double upper, double lower, double stdDev) {
        public BollingerBands {
            if (stdDev < 0.0) {
                throw new IllegalArgumentException("stdDev must be non-negative");
            }
        }
    }

    public record MacdResult(double macdLine, double signalLine, double histogram) {
    }

    public record ArimaForecast(List<Double> forecast, List<Double> coefficients, int p, int d, int q) {
        public ArimaForecast {
            forecast = List.copyOf(forecast);
            coefficients = List.copyOf(coefficients);
        }
    }
}
