package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StressTestService {

    private static final double DEFAULT_BASE_EQUITY = 1_000_000.0;

    private final PositionManager positionManager;
    private final MarketDataRepository marketDataRepository;

    public List<StressTestResult> runDefaultScenarios() {
        return runDefaultScenarios(DEFAULT_BASE_EQUITY);
    }

    public List<StressTestResult> runDefaultScenarios(double baseEquity) {
        List<StressScenario> scenarios = List.of(
                new StressScenario("2008_FINANCIAL_CRISIS", -0.45, "Global financial crisis-style drawdown"),
                new StressScenario("2020_PANDEMIC_SHOCK", -0.30, "COVID liquidity shock"),
                new StressScenario("2022_RATE_SHOCK", -0.22, "Rate hike and growth drawdown")
        );

        return scenarios.stream()
                .map(scenario -> runScenario(scenario, baseEquity))
                .toList();
    }

    public StressTestResult runScenario(StressScenario scenario, double baseEquity) {
        Map<String, Integer> positions = positionManager.getPositionsSnapshot();
        Map<String, Double> entryPrices = positionManager.getEntryPriceSnapshot();

        if (positions.isEmpty()) {
            return new StressTestResult(scenario.name(), scenario.shockPct(), 0.0, 0.0, Map.of());
        }

        Map<String, Double> symbolLosses = new LinkedHashMap<>();
        double totalLoss = 0.0;

        for (Map.Entry<String, Integer> entry : positions.entrySet()) {
            String symbol = entry.getKey();
            int quantity = entry.getValue();
            if (quantity == 0) {
                continue;
            }

            double price = latestPrice(symbol, entryPrices.get(symbol));
            if (price <= 0.0) {
                continue;
            }

            double exposure = Math.abs(quantity) * price;
            double loss = exposure * Math.abs(scenario.shockPct());
            totalLoss += loss;
            symbolLosses.put(symbol, loss);
        }

        double lossPct = baseEquity > 0 ? (totalLoss / baseEquity) * 100.0 : 0.0;
        return new StressTestResult(scenario.name(), scenario.shockPct(), totalLoss, lossPct, symbolLosses);
    }

    private double latestPrice(String symbol, Double fallback) {
        return marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, MarketData.Timeframe.DAY_1)
                .map(MarketData::getClose)
                .orElseGet(() -> fallback != null ? fallback : 0.0);
    }

    public record StressScenario(String name, double shockPct, String description) {
    }

    public record StressTestResult(
            String scenario,
            double shockPct,
            double totalLossTwd,
            double lossPctOfEquity,
            Map<String, Double> symbolLosses
    ) {
    }
}
