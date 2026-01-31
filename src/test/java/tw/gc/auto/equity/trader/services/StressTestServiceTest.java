package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StressTestServiceTest {

    @Mock
    private MarketDataRepository marketDataRepository;

    private PositionManager positionManager;
    private StressTestService service;

    @BeforeEach
    void setUp() {
        positionManager = new PositionManager();
        service = new StressTestService(positionManager, marketDataRepository);
    }

    @Test
    void runScenario_calculatesLossFromPositions() {
        positionManager.setPosition("2330.TW", 10);
        positionManager.updateEntry("2330.TW", 100.0, LocalDateTime.now());

        MarketData data = MarketData.builder()
                .symbol("2330.TW")
                .timeframe(MarketData.Timeframe.DAY_1)
                .close(100.0)
                .timestamp(LocalDateTime.now())
                .build();

        when(marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(eq("2330.TW"), any()))
                .thenReturn(Optional.of(data));

        StressTestService.StressScenario scenario = new StressTestService.StressScenario(
                "TEST_SHOCK",
                -0.30,
                "Test shock"
        );

        StressTestService.StressTestResult result = service.runScenario(scenario, 1000.0);

        assertEquals(300.0, result.totalLossTwd(), 0.01);
        assertEquals(30.0, result.lossPctOfEquity(), 0.01);
    }

    @Test
    void runScenario_withNoPositions_returnsZeroLoss() {
        StressTestService.StressScenario scenario = new StressTestService.StressScenario(
                "NO_POSITIONS",
                -0.25,
                "No positions"
        );

        StressTestService.StressTestResult result = service.runScenario(scenario, 1000.0);

        assertEquals(0.0, result.totalLossTwd(), 0.01);
        assertEquals(0.0, result.lossPctOfEquity(), 0.01);
    }
}
