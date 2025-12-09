package tw.gc.mtxfbot.entities;

import org.junit.jupiter.api.Test;
import tw.gc.mtxfbot.entities.Agent.AgentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Entity classes
 */
class EntityTest {
    
    @Test
    void testAgentBuilder() {
        Agent agent = Agent.builder()
                .name("TestAgent")
                .description("Test description")
                .capabilities("test,mock")
                .status(AgentStatus.ACTIVE)
                .agentType("TestAgentType")
                .build();
        
        assertEquals("TestAgent", agent.getName());
        assertEquals("Test description", agent.getDescription());
        assertEquals("test,mock", agent.getCapabilities());
        assertEquals(AgentStatus.ACTIVE, agent.getStatus());
        assertNotNull(agent.getCreatedAt());
    }
    
    @Test
    void testBotSettingsBuilder() {
        BotSettings settings = BotSettings.builder()
                .key("test_key")
                .value("test_value")
                .description("Test description")
                .build();
        
        assertEquals("test_key", settings.getKey());
        assertEquals("test_value", settings.getValue());
        assertNotNull(settings.getUpdatedAt());
    }
    
    @Test
    void testBotSettingsConstants() {
        assertEquals("trading_mode", BotSettings.TRADING_MODE);
        assertEquals("ollama_model", BotSettings.OLLAMA_MODEL);
        assertEquals("daily_loss_limit", BotSettings.DAILY_LOSS_LIMIT);
        assertEquals("weekly_loss_limit", BotSettings.WEEKLY_LOSS_LIMIT);
    }
    
    @Test
    void testTradeBuilder() {
        LocalDateTime now = LocalDateTime.now();
        Trade trade = Trade.builder()
                .timestamp(now)
                .action(Trade.TradeAction.BUY)
                .quantity(55)
                .entryPrice(1445.0)
                .symbol("2454.TW")
                .reason("Momentum signal")
                .signalConfidence(0.85)
                .build();
        
        assertEquals(now, trade.getTimestamp());
        assertEquals(Trade.TradeAction.BUY, trade.getAction());
        assertEquals(55, trade.getQuantity());
        assertEquals(1445.0, trade.getEntryPrice());
        assertEquals("2454.TW", trade.getSymbol());
        assertEquals(Trade.TradingMode.SIMULATION, trade.getMode()); // Default
        assertEquals(Trade.TradeStatus.OPEN, trade.getStatus()); // Default
    }
    
    @Test
    void testTradeClosing() {
        Trade trade = Trade.builder()
                .timestamp(LocalDateTime.now())
                .action(Trade.TradeAction.BUY)
                .quantity(55)
                .entryPrice(1445.0)
                .mode(Trade.TradingMode.SIMULATION)
                .status(Trade.TradeStatus.OPEN)
                .build();
        
        // Simulate closing
        trade.setExitPrice(1460.0);
        trade.setRealizedPnL((1460.0 - 1445.0) * 55);
        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setHoldDurationMinutes(30);
        
        assertEquals(1460.0, trade.getExitPrice());
        assertEquals(825.0, trade.getRealizedPnL()); // 15 * 55
        assertEquals(Trade.TradeStatus.CLOSED, trade.getStatus());
        assertEquals(30, trade.getHoldDurationMinutes());
    }
    
    @Test
    void testAgentInteractionBuilder() {
        LocalDateTime now = LocalDateTime.now();
        AgentInteraction interaction = AgentInteraction.builder()
                .agentName("TutorBot")
                .timestamp(now)
                .type(AgentInteraction.InteractionType.QUESTION)
                .input("What is momentum trading?")
                .output("Momentum trading is...")
                .userId("12345")
                .responseTimeMs(150L)
                .build();
        
        assertEquals("TutorBot", interaction.getAgentName());
        assertEquals(now, interaction.getTimestamp());
        assertEquals(AgentInteraction.InteractionType.QUESTION, interaction.getType());
        assertEquals("What is momentum trading?", interaction.getInput());
        assertEquals("12345", interaction.getUserId());
        assertEquals(150L, interaction.getResponseTimeMs());
    }
    
    @Test
    void testInteractionTypes() {
        // Verify all interaction types exist
        assertNotNull(AgentInteraction.InteractionType.QUESTION);
        assertNotNull(AgentInteraction.InteractionType.INSIGHT);
        assertNotNull(AgentInteraction.InteractionType.NEWS_ANALYSIS);
        assertNotNull(AgentInteraction.InteractionType.SIGNAL);
        assertNotNull(AgentInteraction.InteractionType.RISK_CHECK);
        assertNotNull(AgentInteraction.InteractionType.COMMAND);
    }
    
    @Test
    void testMarketDataBuilder() {
        LocalDateTime now = LocalDateTime.now();
        MarketData marketData = MarketData.builder()
                .timestamp(now)
                .symbol("2454.TW")
                .timeframe(MarketData.Timeframe.MIN_1)
                .open(1100.0)
                .high(1105.0)
                .low(1095.0)
                .close(1102.0)
                .volume(50000L)
                .tickCount(120)
                .rsi(55.0)
                .sma20(1098.0)
                .macdLine(2.5)
                .vwap(1100.5)
                .build();
        
        assertEquals(now, marketData.getTimestamp());
        assertEquals("2454.TW", marketData.getSymbol());
        assertEquals(MarketData.Timeframe.MIN_1, marketData.getTimeframe());
        assertEquals(1100.0, marketData.getOpen());
        assertEquals(1105.0, marketData.getHigh());
        assertEquals(1095.0, marketData.getLow());
        assertEquals(1102.0, marketData.getClose());
        assertEquals(50000L, marketData.getVolume());
        assertEquals(120, marketData.getTickCount());
        assertEquals(55.0, marketData.getRsi());
        assertEquals(1098.0, marketData.getSma20());
        assertEquals(2.5, marketData.getMacdLine());
        assertEquals(1100.5, marketData.getVwap());
    }
    
    @Test
    void testMarketDataTimeframes() {
        assertNotNull(MarketData.Timeframe.TICK);
        assertNotNull(MarketData.Timeframe.MIN_1);
        assertNotNull(MarketData.Timeframe.MIN_5);
        assertNotNull(MarketData.Timeframe.MIN_15);
        assertNotNull(MarketData.Timeframe.MIN_30);
        assertNotNull(MarketData.Timeframe.HOUR_1);
        assertNotNull(MarketData.Timeframe.DAY_1);
    }
    
    @Test
    void testMarketDataIndicators() {
        MarketData marketData = MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol("MTXF")
                .timeframe(MarketData.Timeframe.MIN_5)
                .open(22000.0)
                .high(22100.0)
                .low(21950.0)
                .close(22050.0)
                .volume(1000L)
                .sma20(22000.0)
                .sma50(21900.0)
                .ema12(22025.0)
                .ema26(21980.0)
                .macdLine(45.0)
                .macdSignal(40.0)
                .macdHistogram(5.0)
                .bollingerUpper(22200.0)
                .bollingerMiddle(22000.0)
                .bollingerLower(21800.0)
                .atr(150.0)
                .momentumPct(0.05)
                .build();
        
        assertEquals(22000.0, marketData.getSma20());
        assertEquals(21900.0, marketData.getSma50());
        assertEquals(45.0, marketData.getMacdLine());
        assertEquals(40.0, marketData.getMacdSignal());
        assertEquals(5.0, marketData.getMacdHistogram());
        assertEquals(22200.0, marketData.getBollingerUpper());
        assertEquals(150.0, marketData.getAtr());
        assertEquals(0.05, marketData.getMomentumPct());
    }
    
    @Test
    void testDailyStatisticsBuilder() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(today)
                .calculatedAt(now)
                .symbol("2454.TW")
                .totalTrades(5)
                .winningTrades(3)
                .losingTrades(2)
                .winRate(60.0)
                .realizedPnL(1500.0)
                .maxDrawdown(500.0)
                .avgTradePnL(300.0)
                .profitFactor(2.5)
                .signalsGenerated(10)
                .avgHoldMinutes(20.0)
                .consecutiveWins(2)
                .tradingMode(Trade.TradingMode.SIMULATION)
                .build();
        
        assertEquals(today, stats.getTradeDate());
        assertEquals("2454.TW", stats.getSymbol());
        assertEquals(5, stats.getTotalTrades());
        assertEquals(3, stats.getWinningTrades());
        assertEquals(2, stats.getLosingTrades());
        assertEquals(60.0, stats.getWinRate());
        assertEquals(1500.0, stats.getRealizedPnL());
        assertEquals(500.0, stats.getMaxDrawdown());
        assertEquals(2.5, stats.getProfitFactor());
        assertEquals(20.0, stats.getAvgHoldMinutes());
        assertEquals(2, stats.getConsecutiveWins());
    }
    
    @Test
    void testDailyStatisticsSignalStats() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(LocalDate.now())
                .calculatedAt(LocalDateTime.now())
                .symbol("MTXF")
                .signalsGenerated(20)
                .signalsLong(12)
                .signalsShort(8)
                .signalsActedOn(5)
                .avgSignalConfidence(0.72)
                .newsVetoCount(3)
                .build();
        
        assertEquals(20, stats.getSignalsGenerated());
        assertEquals(12, stats.getSignalsLong());
        assertEquals(8, stats.getSignalsShort());
        assertEquals(5, stats.getSignalsActedOn());
        assertEquals(0.72, stats.getAvgSignalConfidence());
        assertEquals(3, stats.getNewsVetoCount());
    }
    
    @Test
    void testDailyStatisticsRiskMetrics() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(LocalDate.now())
                .calculatedAt(LocalDateTime.now())
                .symbol("2454.TW")
                .sharpeRatio(1.5)
                .sortinoRatio(2.0)
                .calmarRatio(3.0)
                .equityHighWatermark(150000.0)
                .cumulativePnL(50000.0)
                .cumulativeTrades(100)
                .cumulativeWinRate(58.0)
                .build();
        
        assertEquals(1.5, stats.getSharpeRatio());
        assertEquals(2.0, stats.getSortinoRatio());
        assertEquals(3.0, stats.getCalmarRatio());
        assertEquals(150000.0, stats.getEquityHighWatermark());
        assertEquals(50000.0, stats.getCumulativePnL());
        assertEquals(100, stats.getCumulativeTrades());
        assertEquals(58.0, stats.getCumulativeWinRate());
    }
    
    @Test
    void testDailyStatisticsInsight() {
        LocalDateTime now = LocalDateTime.now();
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(LocalDate.now())
                .calculatedAt(now)
                .symbol("2454.TW")
                .llamaInsight("Great day with strong momentum signals. Consider tightening stops tomorrow.")
                .insightGeneratedAt(now)
                .notes("Manual observation: High volume day")
                .build();
        
        assertNotNull(stats.getLlamaInsight());
        assertTrue(stats.getLlamaInsight().contains("momentum"));
        assertEquals(now, stats.getInsightGeneratedAt());
        assertEquals("Manual observation: High volume day", stats.getNotes());
    }
}
