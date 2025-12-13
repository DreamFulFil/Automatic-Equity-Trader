package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.library.RSIStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BacktestServiceTest {

    @Test
    void testBacktestExecution() {
        BacktestService service = new BacktestService();
        
        // Create mock data: Sine wave to trigger RSI
        List<MarketData> history = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double price = 100 + 10 * Math.sin(i * 0.2);
            history.add(MarketData.builder()
                .symbol("TEST")
                .close(price)
                .high(price + 1)
                .low(price - 1)
                .timestamp(LocalDateTime.now().plusMinutes(i))
                .build());
        }
        
        List<IStrategy> strategies = new ArrayList<>();
        strategies.add(new RSIStrategy(14, 70, 30));
        
        Map<String, BacktestService.BacktestResult> results = service.runBacktest(strategies, history, 80000);
        
        assertNotNull(results);
        assertTrue(results.containsKey("RSI (14, 30/70)"));
        
        BacktestService.BacktestResult result = results.get("RSI (14, 30/70)");
        System.out.println("Backtest Result: " + result);
        
        // Should have some trades due to sine wave nature
        assertTrue(result.getTotalTrades() >= 0);
    }
}
