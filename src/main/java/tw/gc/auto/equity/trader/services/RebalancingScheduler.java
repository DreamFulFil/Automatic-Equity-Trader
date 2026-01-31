package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.StrategyConfig;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyConfigRepository;
import tw.gc.auto.equity.trader.services.PortfolioAllocationService.AllocationPlan;
import tw.gc.auto.equity.trader.services.PortfolioAllocationService.AllocationRequest;
import tw.gc.auto.equity.trader.services.PortfolioAllocationService.RebalanceAction;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RebalancingScheduler - Periodic portfolio rebalancing for multi-stock allocations.
 *
 * Uses StrategyConfig symbols and allocation limits to determine target weights.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RebalancingScheduler {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final double DEFAULT_EQUITY_FALLBACK = 80_000.0;

    private final PortfolioAllocationService allocationService;
    private final StrategyConfigRepository strategyConfigRepository;
    private final MarketDataRepository marketDataRepository;
    private final ContractScalingService contractScalingService;
    private final TradingStateService tradingStateService;
    private final OrderExecutionService orderExecutionService;
    private final PositionManager positionManager;

    /**
     * Weekly rebalancing on Monday mornings.
     */
    @Scheduled(cron = "0 10 9 * * MON", zone = "Asia/Taipei")
    public void weeklyRebalance() {
        if (!"stock".equals(tradingStateService.getTradingMode())) {
            return;
        }
        if (tradingStateService.isEmergencyShutdown() || tradingStateService.isTradingPaused()) {
            log.warn("⛔ Rebalancing skipped due to trading pause/shutdown");
            return;
        }
        runRebalance();
    }

    void runRebalance() {
        Map<String, Double> targetWeights = resolveTargetWeights();
        if (targetWeights.isEmpty()) {
            log.info("No target weights configured - skipping rebalancing");
            return;
        }

        Map<String, Double> latestPrices = loadLatestPrices(targetWeights.keySet());
        Map<String, Integer> currentPositions = positionManager.getPositionsSnapshot();

        double totalEquity = contractScalingService.getLastEquity();
        if (totalEquity <= 0.0) {
            totalEquity = DEFAULT_EQUITY_FALLBACK;
        }

        AllocationRequest request = new AllocationRequest(
                totalEquity,
                targetWeights,
                currentPositions,
                latestPrices,
                Map.of(),
                true
        );

        AllocationPlan plan = allocationService.buildAllocationPlan(request);
        if (!plan.warnings().isEmpty()) {
            plan.warnings().forEach(warning -> log.warn("⚠️ Allocation warning: {}", warning));
        }

        executePlan(plan);
    }

    private Map<String, Double> resolveTargetWeights() {
        List<StrategyConfig> configs = strategyConfigRepository.findEnabledStrategiesOrderedByPriority();
        Map<String, Double> weights = new HashMap<>();

        for (StrategyConfig config : configs) {
            if (config.getSymbols() == null || config.getSymbols().isBlank()) {
                continue;
            }
            String[] symbols = config.getSymbols().split(",");
            double maxAllocation = config.getMaxAllocationPct() != null
                    ? config.getMaxAllocationPct()
                    : 0.0;

            double perSymbol = symbols.length > 0
                    ? (maxAllocation > 0.0 ? maxAllocation / symbols.length : 0.0)
                    : 0.0;

            for (String rawSymbol : symbols) {
                String symbol = rawSymbol.trim();
                if (symbol.isEmpty()) {
                    continue;
                }
                double existing = weights.getOrDefault(symbol, 0.0);
                double candidate = perSymbol > 0.0 ? perSymbol : 0.0;
                if (candidate > existing) {
                    weights.put(symbol, candidate);
                }
            }
        }

        if (weights.isEmpty()) {
            return Map.of();
        }

        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum == 0.0) {
            double equalWeight = 1.0 / weights.size();
            weights.replaceAll((symbol, weight) -> equalWeight);
            return weights;
        }

        if (sum > 1.0) {
            weights.replaceAll((symbol, weight) -> weight / sum);
        }

        return weights;
    }

    private Map<String, Double> loadLatestPrices(Iterable<String> symbols) {
        Map<String, Double> prices = new HashMap<>();
        for (String symbol : symbols) {
            marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(
                    symbol, MarketData.Timeframe.DAY_1
            ).ifPresent(data -> prices.put(symbol, data.getClose()));
        }
        return prices;
    }

    private void executePlan(AllocationPlan plan) {
        if (plan.actions().isEmpty()) {
            log.info("No rebalance actions required");
            return;
        }

        String tradingMode = tradingStateService.getTradingMode();
        boolean emergencyShutdown = tradingStateService.isEmergencyShutdown();

        for (RebalanceAction action : plan.actions()) {
            if (action.quantity() <= 0) {
                continue;
            }

            int adjustedQty = orderExecutionService.checkBalanceAndAdjustQuantity(
                    action.action(),
                    action.quantity(),
                    action.price(),
                    action.symbol(),
                    tradingMode
            );

            if (adjustedQty <= 0) {
                continue;
            }

            orderExecutionService.executeOrderWithRetry(
                    action.action(),
                    adjustedQty,
                    action.price(),
                    action.symbol(),
                    action.isExit(),
                    emergencyShutdown,
                    "PortfolioRebalance"
            );

            log.info("📊 Rebalance {} {} {} @ {}", action.action(), adjustedQty, action.symbol(), action.price());
        }
    }
}