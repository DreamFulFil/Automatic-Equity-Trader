package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.Event;
import tw.gc.auto.equity.trader.entities.Signal;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.EventRepository;
import tw.gc.auto.equity.trader.repositories.SignalRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

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
                .symbol("AUTO_EQUITY_TRADER")
                .mode(Trade.TradingMode.LIVE)
                .status(Trade.TradeStatus.OPEN)
                .entryPrice(20000.0)
                .build();

        when(tradeRepository.findFirstBySymbolAndModeAndStatusOrderByTimestampDesc("AUTO_EQUITY_TRADER", Trade.TradingMode.LIVE, Trade.TradeStatus.OPEN))
                .thenReturn(Optional.of(openTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trade closed = dataLoggingService.closeLatestTrade("AUTO_EQUITY_TRADER", Trade.TradingMode.LIVE, 20050.0, 1000.0, 5);

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

        Trade result = dataLoggingService.closeLatestTrade("AUTO_EQUITY_TRADER", Trade.TradingMode.LIVE, 20050.0, 1000.0, 5);

        assertNull(result);
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void closeLatestTrade_whenModeNotFound_shouldFallbackToSymbolOnly() {
        Trade openTrade = Trade.builder()
                .id(1L)
                .timestamp(LocalDateTime.now())
                .symbol("2454.TW")
                .status(Trade.TradeStatus.OPEN)
                .entryPrice(20000.0)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findFirstBySymbolAndModeAndStatusOrderByTimestampDesc("2454.TW", Trade.TradingMode.LIVE, Trade.TradeStatus.OPEN))
                .thenReturn(Optional.empty());
        when(tradeRepository.findFirstBySymbolAndStatusOrderByTimestampDesc("2454.TW", Trade.TradeStatus.OPEN))
                .thenReturn(Optional.of(openTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trade closed = dataLoggingService.closeLatestTrade("2454.TW", Trade.TradingMode.LIVE, 20100.0, 500.0, 10);

        assertNotNull(closed);
        assertEquals(Trade.TradeStatus.CLOSED, closed.getStatus());
        assertEquals(Trade.TradingMode.SIMULATION, closed.getMode());
    }

    @Test
    void closeLatestTrade_whenTradeHasNullMode_shouldSetMode() {
        Trade openTrade = Trade.builder()
                .id(1L)
                .timestamp(LocalDateTime.now())
                .symbol("2454.TW")
                .status(Trade.TradeStatus.OPEN)
                .entryPrice(20000.0)
                .mode(null)
                .build();

        when(tradeRepository.findFirstBySymbolAndModeAndStatusOrderByTimestampDesc(anyString(), any(), any()))
                .thenReturn(Optional.of(openTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trade closed = dataLoggingService.closeLatestTrade("2454.TW", Trade.TradingMode.SIMULATION, 19900.0, -100.0, 3);

        assertNotNull(closed);
        assertEquals(Trade.TradingMode.SIMULATION, closed.getMode());
    }

    @Test
    void logTrade_shouldSaveTradeToRepository() {
        Trade trade = Trade.builder()
                .timestamp(LocalDateTime.now())
                .action(Trade.TradeAction.BUY)
                .quantity(10)
                .symbol("2454.TW")
                .entryPrice(20000.0)
                .strategyName("TestStrategy")
                .status(Trade.TradeStatus.OPEN)
                .build();

        when(tradeRepository.save(trade)).thenReturn(trade);

        Trade result = dataLoggingService.logTrade(trade);

        assertNotNull(result);
        verify(tradeRepository).save(trade);
    }

    @Test
    void logSignal_shouldSaveSignalToRepository() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .direction(Signal.SignalDirection.LONG)
                .confidence(0.85)
                .currentPrice(20000.0)
                .symbol("2454.TW")
                .reason("Test signal")
                .build();

        when(signalRepository.save(signal)).thenReturn(signal);

        Signal result = dataLoggingService.logSignal(signal);

        assertNotNull(result);
        verify(signalRepository).save(signal);
    }

    @Test
    void logEvent_shouldSaveEventToRepository() {
        Event event = Event.builder()
                .timestamp(LocalDateTime.now())
                .type(Event.EventType.INFO)
                .severity(Event.EventSeverity.LOW)
                .category("TEST")
                .message("Test event")
                .build();

        when(eventRepository.save(event)).thenReturn(event);

        Event result = dataLoggingService.logEvent(event);

        assertNotNull(result);
        verify(eventRepository).save(event);
    }

    @Test
    void logApiCall_whenSuccess_shouldLogSuccessEvent() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = dataLoggingService.logApiCall("TestComponent", "/api/test", 150L, true, null);

        assertNotNull(result);
        verify(eventRepository).save(eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals(Event.EventType.SUCCESS, captured.getType());
        assertEquals(Event.EventSeverity.LOW, captured.getSeverity());
        assertEquals("API", captured.getCategory());
        assertEquals("TestComponent", captured.getComponent());
        assertEquals(150L, captured.getResponseTimeMs());
        assertNull(captured.getErrorCode());
    }

    @Test
    void logApiCall_whenFailure_shouldLogErrorEvent() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = dataLoggingService.logApiCall("TestComponent", "/api/test", 300L, false, "Connection timeout");

        assertNotNull(result);
        verify(eventRepository).save(eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals(Event.EventType.ERROR, captured.getType());
        assertEquals(Event.EventSeverity.MEDIUM, captured.getSeverity());
        assertEquals("API_ERROR", captured.getErrorCode());
        assertTrue(captured.getDetails().contains("Connection timeout"));
    }

    @Test
    void logTelegramCommand_shouldLogCommandEvent() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = dataLoggingService.logTelegramCommand("user123", "/start", "Initial setup");

        assertNotNull(result);
        verify(eventRepository).save(eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals(Event.EventType.COMMAND, captured.getType());
        assertEquals(Event.EventSeverity.LOW, captured.getSeverity());
        assertEquals("TELEGRAM", captured.getCategory());
        assertEquals("TelegramService", captured.getComponent());
        assertEquals("user123", captured.getUserId());
        assertTrue(captured.getDetails().contains("/start"));
    }

    @Test
    void logNewsVeto_shouldLogVetoEvent() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = dataLoggingService.logNewsVeto("Negative sentiment detected", 0.75);

        assertNotNull(result);
        verify(eventRepository).save(eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals(Event.EventType.VETO, captured.getType());
        assertEquals(Event.EventSeverity.MEDIUM, captured.getSeverity());
        assertEquals("NEWS", captured.getCategory());
        assertEquals("TradingEngine", captured.getComponent());
        assertTrue(captured.getDetails().contains("Negative sentiment detected"));
        assertTrue(captured.getDetails().contains("0.750"));
    }

    @Test
    void logBotStateChange_shouldLogStateChangeEvent() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = dataLoggingService.logBotStateChange("PAUSED", "User initiated pause", Event.EventSeverity.MEDIUM);

        assertNotNull(result);
        verify(eventRepository).save(eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals(Event.EventType.INFO, captured.getType());
        assertEquals(Event.EventSeverity.MEDIUM, captured.getSeverity());
        assertEquals("SYSTEM", captured.getCategory());
        assertEquals("TradingEngine", captured.getComponent());
        assertTrue(captured.getMessage().contains("PAUSED"));
        assertTrue(captured.getDetails().contains("User initiated pause"));
    }

    @Test
    void logRiskEvent_shouldLogRiskManagementEvent() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = dataLoggingService.logRiskEvent("Daily loss limit hit", "{\"limit\":-5000}", Event.EventSeverity.HIGH);

        assertNotNull(result);
        verify(eventRepository).save(eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals(Event.EventType.WARNING, captured.getType());
        assertEquals(Event.EventSeverity.HIGH, captured.getSeverity());
        assertEquals("RISK", captured.getCategory());
        assertEquals("RiskManagementService", captured.getComponent());
        assertTrue(captured.getMessage().contains("Daily loss limit hit"));
        assertEquals("{\"limit\":-5000}", captured.getDetails());
    }
}
