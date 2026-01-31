package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.services.positionsizing.CorrelationTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PortfolioAllocationService - Portfolio-level allocation and rebalancing planner.
 *
 * Provides:
 * - Target weight normalization
 * - Correlation-aware diversification adjustment
 * - Target share calculation per symbol
 * - Rebalance action generation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioAllocationService {

    private static final double MIN_WEIGHT_EPSILON = 1e-6;
    private static final double CORRELATION_REDUCTION_FACTOR = 0.90;
    private static final double CONFLICT_TOLERANCE = 0.10;

    private final CorrelationTracker correlationTracker;
    private final StockRiskSettingsService stockRiskSettingsService;

    /**
     * Input parameters for allocation planning.
     */
    public record AllocationRequest(
            double totalEquity,
            Map<String, Double> targetWeights,
            Map<String, Integer> currentPositions,
            Map<String, Double> latestPrices,
            Map<String, String> sectors,
            boolean applyCorrelationAdjustments
    ) {}

    /**
     * Target allocation per symbol.
     */
    public record AllocationTarget(
            String symbol,
            double targetWeight,
            double targetValue,
            int targetQuantity
    ) {}

    /**
     * Rebalance action describing a single trade adjustment.
     */
    public record RebalanceAction(
            String symbol,
            String action,
            int quantity,
            double price,
            boolean isExit,
            String reason
    ) {}

    /**
     * Output plan including targets, actions, and warnings.
     */
    public record AllocationPlan(
            Map<String, AllocationTarget> targets,
            List<RebalanceAction> actions,
            Map<String, Double> adjustedWeights,
            double totalAllocated,
            double cashRemaining,
            List<String> warnings
    ) {}

    /**
     * Build a portfolio allocation plan from the provided request.
     */
    public AllocationPlan buildAllocationPlan(AllocationRequest request) {
        Objects.requireNonNull(request, "request");

        Map<String, Double> normalizedWeights = normalizeWeights(request.targetWeights());
        if (normalizedWeights.isEmpty()) {
            return new AllocationPlan(Map.of(), List.of(), Map.of(), 0.0, request.totalEquity(), List.of(
                    "No target weights provided - skipping allocation plan"
            ));
        }

        Map<String, Double> adjustedWeights = request.applyCorrelationAdjustments()
                ? applyCorrelationAdjustments(normalizedWeights)
                : normalizedWeights;

        Map<String, Double> prices = request.latestPrices() != null
                ? new HashMap<>(request.latestPrices())
                : Map.of();
        Map<String, Integer> currentPositions = request.currentPositions() != null
                ? request.currentPositions()
                : Map.of();

        List<String> warnings = new ArrayList<>();
        Map<String, AllocationTarget> targets = new LinkedHashMap<>();
        List<RebalanceAction> actions = new ArrayList<>();

        int maxSharesPerTrade = stockRiskSettingsService.getSettings().getMaxSharesPerTrade();
        double totalAllocated = 0.0;

        for (Map.Entry<String, Double> entry : adjustedWeights.entrySet()) {
            String symbol = entry.getKey();
            double weight = entry.getValue();
            Double price = prices.get(symbol);

            if (price == null || price <= 0.0) {
                warnings.add(String.format("Missing or invalid price for %s - skipping target", symbol));
                continue;
            }

            double targetValue = request.totalEquity() * weight;
            int targetQuantity = (int) Math.floor(targetValue / price);
            int currentQuantity = currentPositions.getOrDefault(symbol, 0);
            int delta = targetQuantity - currentQuantity;

            totalAllocated += targetValue;
            targets.put(symbol, new AllocationTarget(symbol, weight, targetValue, targetQuantity));

            if (delta == 0) {
                continue;
            }

            String action = delta > 0 ? "BUY" : "SELL";
            int quantity = Math.abs(delta);
            boolean isExit = delta < 0;

            if (quantity > maxSharesPerTrade) {
                warnings.add(String.format(
                        "Rebalance quantity capped for %s: %d -> %d (max shares per trade)",
                        symbol, quantity, maxSharesPerTrade
                ));
                quantity = maxSharesPerTrade;
            }

            actions.add(new RebalanceAction(
                    symbol,
                    action,
                    quantity,
                    price,
                    isExit,
                    String.format("Rebalance to %.1f%% target weight", weight * 100)
            ));
        }

        double cashRemaining = Math.max(0.0, request.totalEquity() - totalAllocated);
        if (request.totalEquity() > 0.0 && cashRemaining / request.totalEquity() > CONFLICT_TOLERANCE) {
            warnings.add(String.format("Cash buffer %.1f%% remains unallocated", (cashRemaining / request.totalEquity()) * 100));
        }

        return new AllocationPlan(targets, actions, adjustedWeights, totalAllocated, cashRemaining, warnings);
    }

    private Map<String, Double> normalizeWeights(Map<String, Double> targetWeights) {
        if (targetWeights == null || targetWeights.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> sanitized = new LinkedHashMap<>();
        double sum = 0.0;
        for (Map.Entry<String, Double> entry : targetWeights.entrySet()) {
            double weight = entry.getValue() != null ? entry.getValue() : 0.0;
            if (weight > MIN_WEIGHT_EPSILON) {
                sanitized.put(entry.getKey(), weight);
                sum += weight;
            }
        }

        if (sum <= MIN_WEIGHT_EPSILON) {
            return Map.of();
        }

        if (sum > 1.0) {
            Map<String, Double> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : sanitized.entrySet()) {
                normalized.put(entry.getKey(), entry.getValue() / sum);
            }
            return normalized;
        }

        return sanitized;
    }

    private Map<String, Double> applyCorrelationAdjustments(Map<String, Double> targetWeights) {
        Map<String, Double> adjusted = new LinkedHashMap<>(targetWeights);
        List<String> symbols = new ArrayList<>(adjusted.keySet());

        for (int i = 0; i < symbols.size(); i++) {
            for (int j = i + 1; j < symbols.size(); j++) {
                String symbolA = symbols.get(i);
                String symbolB = symbols.get(j);

                Optional<CorrelationTracker.CorrelationEstimate> cached = correlationTracker.getCachedCorrelation(symbolA, symbolB);
                if (cached.isEmpty()) {
                    continue;
                }

                CorrelationTracker.CorrelationEstimate estimate = cached.get();
                if (estimate.level() != CorrelationTracker.CorrelationLevel.CRITICAL) {
                    continue;
                }

                double weightA = adjusted.getOrDefault(symbolA, 0.0);
                double weightB = adjusted.getOrDefault(symbolB, 0.0);
                if (weightA <= MIN_WEIGHT_EPSILON || weightB <= MIN_WEIGHT_EPSILON) {
                    continue;
                }

                if (weightA >= weightB) {
                    adjusted.put(symbolB, weightB * CORRELATION_REDUCTION_FACTOR);
                    log.info("🔻 Correlation adjustment: {} reduced to {:.4f} (corr {:.2f})", symbolB, adjusted.get(symbolB), estimate.correlation());
                } else {
                    adjusted.put(symbolA, weightA * CORRELATION_REDUCTION_FACTOR);
                    log.info("🔻 Correlation adjustment: {} reduced to {:.4f} (corr {:.2f})", symbolA, adjusted.get(symbolA), estimate.correlation());
                }
            }
        }

        return normalizeWeights(adjusted);
    }
}