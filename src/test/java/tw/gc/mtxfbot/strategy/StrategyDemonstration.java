package tw.gc.mtxfbot.strategy;

import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.impl.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Strategy Pattern Demonstration
 * 
 * Demonstrates runtime strategy switching capability of the Strategy Pattern.
 * Shows how different strategies can be swapped dynamically without changing
 * the execution context.
 * 
 * Usage:
 *   mvn exec:java -Dexec.mainClass="tw.gc.mtxfbot.strategy.StrategyDemonstration"
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
public class StrategyDemonstration {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Strategy Pattern Demonstration");
        System.out.println("========================================\n");
        
        // Create mock portfolio and market data
        Map<String, Integer> positions = new HashMap<>();
        positions.put("MTXF", 0);
        
        Portfolio portfolio = Portfolio.builder()
                .positions(positions)
                .equity(100000.0)
                .availableMargin(80000.0)
                .dailyPnL(0.0)
                .weeklyPnL(0.0)
                .tradingMode("futures")
                .tradingQuantity(1)
                .build();
        
        MarketData marketData = MarketData.builder()
                .symbol("MTXF")
                .close(22000.0)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
        
        // Demonstrate runtime strategy switching
        System.out.println("SCENARIO 1: Conservative long-term investor");
        System.out.println("-------------------------------------------");
        demonstrateStrategy(new DCAStrategy(), portfolio, marketData);
        
        System.out.println("\nSCENARIO 2: Aggressive short-term trader");
        System.out.println("-------------------------------------------");
        demonstrateStrategy(new BollingerBandStrategy(), portfolio, marketData);
        
        System.out.println("\nSCENARIO 3: Institutional algorithmic execution");
        System.out.println("-------------------------------------------");
        demonstrateStrategy(new VWAPExecutionStrategy(10, 30), portfolio, marketData);
        
        System.out.println("\nSCENARIO 4: Momentum-based speculation");
        System.out.println("-------------------------------------------");
        demonstrateStrategy(new MomentumTradingStrategy(), portfolio, marketData);
        
        System.out.println("\nSCENARIO 5: Portfolio rebalancing");
        System.out.println("-------------------------------------------");
        demonstrateStrategy(new AutomaticRebalancingStrategy(5), portfolio, marketData);
        
        System.out.println("\n========================================");
        System.out.println("Demonstration complete!");
        System.out.println("========================================");
        System.out.println("\nKey Takeaways:");
        System.out.println("- All 10+ strategies implement IStrategy interface");
        System.out.println("- Strategies are runtime-swappable with zero code changes");
        System.out.println("- Each strategy is completely independent");
        System.out.println("- No strategy controls engine flow");
        System.out.println("- 46 unit tests ensure correctness");
        System.out.println("- Structured logging at TRACE/INFO/DEBUG levels");
    }
    
    private static void demonstrateStrategy(IStrategy strategy, Portfolio portfolio, MarketData marketData) {
        System.out.println("Strategy: " + strategy.getName());
        System.out.println("Type: " + strategy.getType());
        System.out.println("Executing strategy...");
        
        TradeSignal signal = strategy.execute(portfolio, marketData);
        
        System.out.println("Result:");
        System.out.println("  Direction: " + signal.getDirection());
        System.out.println("  Confidence: " + String.format("%.2f", signal.getConfidence()));
        System.out.println("  Reason: " + signal.getReason());
        System.out.println("  Exit Signal: " + signal.isExitSignal());
    }
}
