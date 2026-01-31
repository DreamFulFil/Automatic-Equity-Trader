package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.services.PortfolioAllocationService.AllocationPlan;
import tw.gc.auto.equity.trader.services.PortfolioAllocationService.AllocationRequest;
import tw.gc.auto.equity.trader.services.positionsizing.CorrelationTracker;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAllocationServiceTest {

    @Mock
    private CorrelationTracker correlationTracker;

    @Mock
    private StockRiskSettingsService stockRiskSettingsService;

    private PortfolioAllocationService allocationService;

    @BeforeEach
    void setUp() {
        allocationService = new PortfolioAllocationService(correlationTracker, stockRiskSettingsService);
    }

    @Test
    void buildAllocationPlan_shouldCreateTargetsAndActions() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(2000)
                .build();
        when(stockRiskSettingsService.getSettings()).thenReturn(settings);

        AllocationRequest request = new AllocationRequest(
                100_000.0,
                Map.of("AAA", 0.6, "BBB", 0.4),
                Map.of("AAA", 100, "BBB", 0),
                Map.of("AAA", 100.0, "BBB", 50.0),
                Map.of(),
                true
        );

        AllocationPlan plan = allocationService.buildAllocationPlan(request);

        assertThat(plan.targets()).hasSize(2);
        assertThat(plan.actions()).hasSize(2);
        assertThat(plan.totalAllocated()).isGreaterThan(0.0);
        assertThat(plan.actions()).allMatch(action -> action.quantity() > 0);
    }

    @Test
    void buildAllocationPlan_shouldReduceWeightsForCriticalCorrelation() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(500)
                .build();
        when(stockRiskSettingsService.getSettings()).thenReturn(settings);

        CorrelationTracker.CorrelationEstimate estimate = new CorrelationTracker.CorrelationEstimate(
                "AAA", "BBB", 0.92, 60, System.currentTimeMillis(), CorrelationTracker.CorrelationLevel.CRITICAL
        );
        when(correlationTracker.getCachedCorrelation(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(estimate));

        AllocationRequest request = new AllocationRequest(
                80_000.0,
                Map.of("AAA", 0.5, "BBB", 0.5),
                Map.of(),
                Map.of("AAA", 100.0, "BBB", 100.0),
                Map.of(),
                true
        );

        AllocationPlan plan = allocationService.buildAllocationPlan(request);

        assertThat(plan.adjustedWeights().get("BBB")).isLessThan(0.5);
    }
}