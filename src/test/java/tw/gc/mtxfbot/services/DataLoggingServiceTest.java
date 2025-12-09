package tw.gc.mtxfbot.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.repositories.EventRepository;
import tw.gc.mtxfbot.repositories.SignalRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataLoggingServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private SignalRepository signalRepository;

    @Mock
    private EventRepository eventRepository;

    private DataLoggingService dataLoggingService;

    @BeforeEach
    void setUp() {
        dataLoggingService = new DataLoggingService(tradeRepository, signalRepository, eventRepository);
    }

    @Test
    void closeLatestTrade_whenOpenTradeExists_updatesExitAndStatus() {
        Trade openTrade = Trade.builder()
                .id(1L)
                .timestamp(LocalDateTime.now())
                .symbol("MTXF")
                .mode(Trade.TradingMode.LIVE)
                .status(Trade.TradeStatus.OPEN)
                .entryPrice(20000.0)
                .build();

        when(tradeRepository.findFirstBySymbolAndModeAndStatusOrderByTimestampDesc("MTXF", Trade.TradingMode.LIVE, Trade.TradeStatus.OPEN))
                .thenReturn(Optional.of(openTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trade closed = dataLoggingService.closeLatestTrade("MTXF", Trade.TradingMode.LIVE, 20050.0, 1000.0, 5);

        assertNotNull(closed);
        assertEquals(Trade.TradeStatus.CLOSED, closed.getStatus());
        assertEquals(20050.0, closed.getExitPrice());
        assertEquals(1000.0, closed.getRealizedPnL());
        assertEquals(5, closed.getHoldDurationMinutes());
        assertEquals(Trade.TradingMode.LIVE, closed.getMode());
        verify(tradeRepository).save(openTrade);
    }

    @Test
    void closeLatestTrade_whenNoOpenTrade_returnsNull() {
        when(tradeRepository.findFirstBySymbolAndModeAndStatusOrderByTimestampDesc(anyString(), any(), any()))
                .thenReturn(Optional.empty());
        when(tradeRepository.findFirstBySymbolAndStatusOrderByTimestampDesc(anyString(), any()))
                .thenReturn(Optional.empty());

        Trade result = dataLoggingService.closeLatestTrade("MTXF", Trade.TradingMode.LIVE, 20050.0, 1000.0, 5);

        assertNull(result);
        verify(tradeRepository, never()).save(any());
    }
}
