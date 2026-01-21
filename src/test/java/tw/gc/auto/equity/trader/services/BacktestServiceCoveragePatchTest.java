package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BacktestServiceCoveragePatchTest {

    @Mock
    private BacktestResultRepository backtestResultRepository;
    @Mock
    private MarketDataRepository marketDataRepository;
    @Mock
    private HistoryDataService historyDataService;
    @Mock
    private SystemStatusService systemStatusService;
    @Mock
    private DataSource dataSource;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StrategyStockMappingService strategyStockMappingService;

    private BacktestService service;

    @BeforeEach
    void setUp() {
        service = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService
        );
    }

    @Test
    void stockCandidate_equalsHashCodeAndScore() throws Exception {
        Class<?> clazz = Class.forName("tw.gc.auto.equity.trader.services.BacktestService$StockCandidate");
        var ctor = clazz.getDeclaredConstructor(String.class, String.class, double.class, double.class, String.class);
        ctor.setAccessible(true);
        Object a = ctor.newInstance("2330.TW", "TSMC", 100.0, 200.0, "src");
        Object b = ctor.newInstance("2330.TW", "Other", 50.0, 50.0, "src");
        Object c = ctor.newInstance("2454.TW", "MediaTek", 50.0, 50.0, "src");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        var score = clazz.getDeclaredMethod("getScore");
        score.setAccessible(true);
        double computed = (double) score.invoke(a);
        assertThat(computed).isEqualTo(100.0 * 0.7 + 200.0 * 0.3);
    }

    @Test
    void jdbcBatchInsertBacktestResults_handlesNullFieldsAndSuccess() throws Exception {
        BacktestResult result = BacktestResult.builder()
            .backtestRunId("BT-1")
            .symbol("2330.TW")
            .stockName(null)
            .strategyName(null)
            .initialCapital(null)
            .finalEquity(null)
            .totalReturnPct(null)
            .sharpeRatio(null)
            .maxDrawdownPct(null)
            .totalTrades(null)
            .winningTrades(null)
            .winRatePct(null)
            .avgProfitPerTrade(null)
            .backtestPeriodStart(null)
            .backtestPeriodEnd(null)
            .dataPoints(null)
            .createdAt(null)
            .build();

        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any()))
            .thenReturn(new int[][]{{1}});

        Method method = BacktestService.class.getDeclaredMethod("jdbcBatchInsertBacktestResults", List.class);
        method.setAccessible(true);
        int inserted = (int) method.invoke(service, List.of(result));
        assertThat(inserted).isEqualTo(1);
    }
}
