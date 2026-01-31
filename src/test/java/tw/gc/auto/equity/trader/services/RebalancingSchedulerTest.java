package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyConfigRepository;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RebalancingSchedulerTest {

    @Mock
    private PortfolioAllocationService allocationService;
    @Mock
    private StrategyConfigRepository strategyConfigRepository;
    @Mock
    private MarketDataRepository marketDataRepository;
    @Mock
    private ContractScalingService contractScalingService;
    @Mock
    private TradingStateService tradingStateService;
    @Mock
    private OrderExecutionService orderExecutionService;
    @Mock
    private PositionManager positionManager;

    private RebalancingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RebalancingScheduler(
                allocationService,
                strategyConfigRepository,
                marketDataRepository,
                contractScalingService,
                tradingStateService,
                orderExecutionService,
                positionManager
        );
    }

    @Test
    void runRebalance_shouldSkipWhenNoTargetWeights() {
        when(strategyConfigRepository.findEnabledStrategiesOrderedByPriority()).thenReturn(List.of());

        scheduler.runRebalance();

        verify(allocationService, never()).buildAllocationPlan(org.mockito.ArgumentMatchers.any());
        verify(orderExecutionService, never()).executeOrderWithRetry(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}