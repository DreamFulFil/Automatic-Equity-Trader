package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.EventRepository;
import tw.gc.auto.equity.trader.repositories.SignalRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataAnalysisServiceSharpeEdgeTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private SignalRepository signalRepository;

    @Mock
    private EventRepository eventRepository;

    private DataAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DataAnalysisService(tradeRepository, signalRepository, eventRepository);
    }

    @Test
    void getTradingMetrics_whenVolatilityIsNaN_shouldReturnZeroSharpe() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        Trade.TradingMode mode = Trade.TradingMode.LIVE;

        when(tradeRepository.countTotalTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(-1L);
        when(tradeRepository.countWinningTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(0L);
        when(tradeRepository.sumPnLSince(mode, since)).thenReturn(1000.0);
        when(tradeRepository.maxDrawdownSince(mode, since)).thenReturn(-1.0);

        Map<String, Object> metrics = service.getTradingMetrics(mode, since);

        assertThat(metrics).containsEntry("sharpeRatio", 0.0);
    }
}
