package tw.gc.auto.equity.trader.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.Signal;
import tw.gc.auto.equity.trader.services.OrderExecutionService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class CommandTest {

    @Mock
    private OrderExecutionService orderExecutionService;

    @Test
    void testCloseCommandInstantiation() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2454")
                .direction(Signal.SignalDirection.SHORT)
                .confidence(0.8)
                .currentPrice(100.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(100, 100.0, "2454", false);
        CloseCommand command = new CloseCommand(signal, params);
        
        assertNotNull(command);
        assertDoesNotThrow(command::execute);
    }

    @Test
    void testHoldCommandInstantiation() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2454")
                .direction(Signal.SignalDirection.HOLD)
                .confidence(0.5)
                .currentPrice(100.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(100, 100.0, "2454", false);
        HoldCommand command = new HoldCommand(signal, params);
        
        assertNotNull(command);
        assertDoesNotThrow(command::execute);
    }

    @Test
    void testModifyCommandInstantiation() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2454")
                .direction(Signal.SignalDirection.LONG)
                .confidence(0.9)
                .currentPrice(100.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(100, 100.0, "2454", false);
        ModifyCommand command = new ModifyCommand(signal, params);
        
        assertNotNull(command);
        assertDoesNotThrow(command::execute);
    }

    @Test
    void testCancelCommandInstantiation() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2454")
                .direction(Signal.SignalDirection.SHORT)
                .confidence(0.7)
                .currentPrice(100.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(100, 100.0, "2454", false);
        CancelCommand command = new CancelCommand(signal, params);
        
        assertNotNull(command);
        assertDoesNotThrow(command::execute);
    }
    
    @Test
    void testSellCommandExecution() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2454")
                .direction(Signal.SignalDirection.SHORT)
                .confidence(0.8)
                .currentPrice(100.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(100, 100.0, "2454", false);
        SellCommand command = new SellCommand(orderExecutionService, signal, params);
        
        command.execute();
        
        verify(orderExecutionService).executeOrderWithRetry("SELL", 100, 100.0, "2454", false, false);
    }

    @Test
    void testSellCommandWithEmergencyShutdown() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2454")
                .direction(Signal.SignalDirection.SHORT)
                .confidence(0.8)
                .currentPrice(100.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(200, 95.5, "2330", true);
        SellCommand command = new SellCommand(orderExecutionService, signal, params);
        
        command.execute();
        
        verify(orderExecutionService).executeOrderWithRetry("SELL", 200, 95.5, "2330", false, true);
    }

    @Test
    void testBuyCommandExecution() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("2330")
                .direction(Signal.SignalDirection.LONG)
                .confidence(0.9)
                .currentPrice(500.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(50, 500.0, "2330", false);
        BuyCommand command = new BuyCommand(orderExecutionService, signal, params);
        
        command.execute();
        
        verify(orderExecutionService).executeOrderWithRetry("BUY", 50, 500.0, "2330", false, false);
    }

    @Test
    void testBuyCommandWithEmergencyShutdown() {
        Signal signal = Signal.builder()
                .timestamp(LocalDateTime.now())
                .symbol("1234")
                .direction(Signal.SignalDirection.LONG)
                .confidence(0.75)
                .currentPrice(150.0)
                .build();
        
        StockOrderCommandParams params = new StockOrderCommandParams(100, 150.0, "1234", true);
        BuyCommand command = new BuyCommand(orderExecutionService, signal, params);
        
        command.execute();
        
        verify(orderExecutionService).executeOrderWithRetry("BUY", 100, 150.0, "1234", false, true);
    }

    @Test
    void testStockOrderCommandParams() {
        StockOrderCommandParams params = new StockOrderCommandParams(100, 200.0, "2454", false);
        
        assertEquals(100, params.getQuantity());
        assertEquals(200.0, params.getPrice());
        assertEquals("2454", params.getInstrument());
        assertFalse(params.isEmergencyShutdown());
        
        params.setQuantity(150);
        params.setPrice(250.0);
        params.setInstrument("2330");
        params.setEmergencyShutdown(true);
        
        assertEquals(150, params.getQuantity());
        assertEquals(250.0, params.getPrice());
        assertEquals("2330", params.getInstrument());
        assertTrue(params.isEmergencyShutdown());
    }
}
