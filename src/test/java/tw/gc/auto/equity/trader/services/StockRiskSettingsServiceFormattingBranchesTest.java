package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.repositories.StockRiskSettingsRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockRiskSettingsServiceFormattingBranchesTest {

    @Mock
    private StockRiskSettingsRepository repo;

    private StockRiskSettingsService service;

    @BeforeEach
    void setUp() {
        service = new StockRiskSettingsService(repo);
    }

    @Test
    void getAllStockRiskSettingsFormatted_shouldCoverBothCheckmarkBranches() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(1)
                .dailyLossLimitTwd(1)
                .weeklyLossLimitTwd(1)
                .stopLossTwdPerTrade(1)
                .maxDailyTrades(1)
                .minHoldMinutes(0)
                .maxHoldMinutes(1)
                .minSharpeRatio(0.0)
                .minWinRate(0.0)
                .maxDrawdownPercent(0.0)
                .strategyBacktestDays(30)
                .minTotalTradesInBacktest(10)
                .enableAiVeto(false)
                .enableVolatilityFilter(true)
                .volatilityThresholdMultiplier(1.0)
                .updatedAt(LocalDateTime.now())
                .build();

        when(repo.findFirst()).thenReturn(settings);

        String out = service.getAllStockRiskSettingsFormatted();

        assertTrue(out.contains("enable_ai_veto = ❌"));
        assertTrue(out.contains("enable_volatility_filter = ✅"));
    }
}
