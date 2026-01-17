package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.Agent.AgentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

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
                .symbol("AUTO_EQUITY_TRADER")
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
                .symbol("AUTO_EQUITY_TRADER")
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
    
    @Test
    void testFutureSettingsBuilder() {
        FutureSettings settings = FutureSettings.builder()
                .contractCode("MTX")
                .contractsPerTrade(2)
                .maxOpenContracts(5)
                .leverage(5.0)
                .minMarginPerContract(50000.0)
                .orderType("LIMIT")
                .timeInForce("GTC")
                .stopLoss(1000.0)
                .takeProfit(2000.0)
                .dailyLossLimit(10000.0)
                .weeklyLossLimit(30000.0)
                .commissionPerContract(100.0)
                .estimatedSlippage(50.0)
                .autoRollover(true)
                .allowHedging(false)
                .tradingSession("DAY")
                .description("Test settings")
                .build();
        
        assertEquals("MTX", settings.getContractCode());
        assertEquals(2, settings.getContractsPerTrade());
        assertEquals(5, settings.getMaxOpenContracts());
        assertEquals(5.0, settings.getLeverage());
        assertEquals(50000.0, settings.getMinMarginPerContract());
        assertEquals("LIMIT", settings.getOrderType());
        assertEquals("GTC", settings.getTimeInForce());
        assertEquals(1000.0, settings.getStopLoss());
        assertEquals(2000.0, settings.getTakeProfit());
        assertEquals(10000.0, settings.getDailyLossLimit());
        assertEquals(30000.0, settings.getWeeklyLossLimit());
        assertEquals(100.0, settings.getCommissionPerContract());
        assertEquals(50.0, settings.getEstimatedSlippage());
        assertTrue(settings.getAutoRollover());
        assertFalse(settings.getAllowHedging());
        assertEquals("DAY", settings.getTradingSession());
        assertEquals("Test settings", settings.getDescription());
        assertNotNull(settings.getUpdatedAt());
    }
    
    @Test
    void testFutureSettingsPreUpdate() {
        FutureSettings settings = FutureSettings.builder()
                .contractCode("MTX")
                .contractsPerTrade(2)
                .maxOpenContracts(5)
                .leverage(5.0)
                .minMarginPerContract(50000.0)
                .orderType("LIMIT")
                .timeInForce("GTC")
                .stopLoss(1000.0)
                .takeProfit(2000.0)
                .dailyLossLimit(10000.0)
                .weeklyLossLimit(30000.0)
                .commissionPerContract(100.0)
                .estimatedSlippage(50.0)
                .autoRollover(true)
                .allowHedging(false)
                .tradingSession("DAY")
                .build();
        
        LocalDateTime beforeUpdate = settings.getUpdatedAt();
        settings.preUpdate();
        assertNotEquals(beforeUpdate, settings.getUpdatedAt());
    }
    
    @Test
    void testStockUniverseBuilder() {
        LocalDateTime now = LocalDateTime.now();
        StockUniverse stock = StockUniverse.builder()
                .symbol("2454")
                .stockName("MediaTek")
                .sector("Technology")
                .marketCap(1000000000.0)
                .avgDailyVolume(5000000L)
                .selectionReason("High liquidity and strong fundamentals")
                .selectionScore(8.5)
                .enabled(true)
                .build();
        
        assertEquals("2454", stock.getSymbol());
        assertEquals("MediaTek", stock.getStockName());
        assertEquals("Technology", stock.getSector());
        assertEquals(1000000000.0, stock.getMarketCap());
        assertEquals(5000000L, stock.getAvgDailyVolume());
        assertEquals("High liquidity and strong fundamentals", stock.getSelectionReason());
        assertEquals(8.5, stock.getSelectionScore());
        assertTrue(stock.getEnabled());
        assertNotNull(stock.getCreatedAt());
        assertNotNull(stock.getUpdatedAt());
    }
    
    @Test
    void testStockUniverseOnUpdate() {
        StockUniverse stock = StockUniverse.builder()
                .symbol("2454")
                .stockName("MediaTek")
                .sector("Technology")
                .marketCap(1000000000.0)
                .avgDailyVolume(5000000L)
                .selectionScore(8.5)
                .build();
        
        LocalDateTime beforeUpdate = stock.getUpdatedAt();
        stock.onUpdate();
        assertNotEquals(beforeUpdate, stock.getUpdatedAt());
    }
    
    @Test
    void testAgentPreUpdate() {
        Agent agent = Agent.builder()
                .name("TestAgent")
                .description("Test")
                .agentType("TEST")
                .build();
        
        assertNull(agent.getUpdatedAt());
        agent.preUpdate();
        assertNotNull(agent.getUpdatedAt());
    }
    
    @Test
    void testAgentStatusEnum() {
        assertEquals(3, Agent.AgentStatus.values().length);
        assertNotNull(Agent.AgentStatus.ACTIVE);
        assertNotNull(Agent.AgentStatus.INACTIVE);
        assertNotNull(Agent.AgentStatus.ERROR);
    }
    
    @Test
    void testActiveShadowSelectionBuilder() {
        LocalDateTime now = LocalDateTime.now();
        ActiveShadowSelection selection = ActiveShadowSelection.builder()
                .rankPosition(1)
                .isActive(true)
                .symbol("2330")
                .stockName("TSMC")
                .strategyName("MomentumStrategy")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .sharpeRatio(1.5)
                .totalReturnPct(15.5)
                .winRatePct(60.0)
                .maxDrawdownPct(-5.5)
                .compositeScore(85.0)
                .build();
        
        assertEquals(1, selection.getRankPosition());
        assertTrue(selection.getIsActive());
        assertEquals("2330", selection.getSymbol());
        assertEquals("TSMC", selection.getStockName());
        assertEquals("MomentumStrategy", selection.getStrategyName());
        assertEquals(ActiveShadowSelection.SelectionSource.BACKTEST, selection.getSource());
        assertEquals(1.5, selection.getSharpeRatio());
        assertEquals(15.5, selection.getTotalReturnPct());
        assertEquals(60.0, selection.getWinRatePct());
        assertEquals(-5.5, selection.getMaxDrawdownPct());
        assertEquals(85.0, selection.getCompositeScore());
        assertNotNull(selection.getSelectedAt());
        assertNotNull(selection.getUpdatedAt());
    }
    
    @Test
    void testActiveShadowSelectionOnUpdate() {
        ActiveShadowSelection selection = ActiveShadowSelection.builder()
                .rankPosition(1)
                .symbol("2330")
                .stockName("TSMC")
                .strategyName("Test")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .build();
        
        LocalDateTime beforeUpdate = selection.getUpdatedAt();
        selection.onUpdate();
        assertNotEquals(beforeUpdate, selection.getUpdatedAt());
    }
    
    @Test
    void testActiveShadowSelectionSourceEnum() {
        assertEquals(3, ActiveShadowSelection.SelectionSource.values().length);
        assertNotNull(ActiveShadowSelection.SelectionSource.BACKTEST);
        assertNotNull(ActiveShadowSelection.SelectionSource.FRONTTEST);
        assertNotNull(ActiveShadowSelection.SelectionSource.COMBINATION);
    }
    
    @Test
    void testBotSettingsPreUpdate() {
        BotSettings settings = BotSettings.builder()
                .key("test")
                .value("value")
                .build();
        
        LocalDateTime beforeUpdate = settings.getUpdatedAt();
        settings.preUpdate();
        assertNotEquals(beforeUpdate, settings.getUpdatedAt());
    }
    
    @Test
    void testBotSettingsAllConstants() {
        assertEquals("trading_mode", BotSettings.TRADING_MODE);
        assertEquals("market_mode", BotSettings.MARKET_MODE);
        assertEquals("ollama_model", BotSettings.OLLAMA_MODEL);
        assertEquals("daily_loss_limit", BotSettings.DAILY_LOSS_LIMIT);
        assertEquals("weekly_loss_limit", BotSettings.WEEKLY_LOSS_LIMIT);
        assertEquals("monthly_loss_limit", BotSettings.MONTHLY_LOSS_LIMIT);
        assertEquals("weekly_profit_limit", BotSettings.WEEKLY_PROFIT_LIMIT);
        assertEquals("monthly_profit_limit", BotSettings.MONTHLY_PROFIT_LIMIT);
        assertEquals("tutor_questions_per_day", BotSettings.TUTOR_QUESTIONS_PER_DAY);
        assertEquals("tutor_insights_per_day", BotSettings.TUTOR_INSIGHTS_PER_DAY);
    }
    
    @Test
    void testEarningsBlackoutMetaBuilder() {
        OffsetDateTime now = OffsetDateTime.now();
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(now)
                .source("test-source")
                .ttlDays(7)
                .build();
        
        assertEquals(now, meta.getLastUpdated());
        assertEquals("test-source", meta.getSource());
        assertEquals(7, meta.getTtlDays());
        assertNotNull(meta.getTickersChecked());
        assertNotNull(meta.getDates());
    }
    
    @Test
    void testEarningsBlackoutMetaOnCreate() {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .source("test")
                .build();
        
        assertNull(meta.getCreatedAt());
        meta.onCreate();
        assertNotNull(meta.getCreatedAt());
        assertNotNull(meta.getUpdatedAt());
        assertNotNull(meta.getLastUpdated());
    }
    
    @Test
    void testEarningsBlackoutMetaOnUpdate() {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .source("test")
                .build();
        
        meta.onCreate();
        OffsetDateTime beforeUpdate = meta.getUpdatedAt();
        meta.onUpdate();
        assertNotEquals(beforeUpdate, meta.getUpdatedAt());
    }
    
    @Test
    void testEarningsBlackoutMetaAddDate() {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .source("test")
                .build();
        
        EarningsBlackoutDate date = EarningsBlackoutDate.builder()
                .blackoutDate(LocalDate.now())
                .build();
        
        meta.addDate(date);
        assertTrue(meta.getDates().contains(date));
        assertEquals(meta, date.getMeta());
    }
    
    @Test
    void testEarningsBlackoutMetaEquals() {
        OffsetDateTime now = OffsetDateTime.now();
        EarningsBlackoutMeta meta1 = EarningsBlackoutMeta.builder()
                .lastUpdated(now)
                .source("test")
                .build();
        
        EarningsBlackoutMeta meta2 = EarningsBlackoutMeta.builder()
                .lastUpdated(now)
                .source("test")
                .build();
        
        assertEquals(meta1, meta2);
        assertEquals(meta1.hashCode(), meta2.hashCode());
    }
    
    @Test
    void testEarningsBlackoutDateBuilder() {
        LocalDate date = LocalDate.now();
        EarningsBlackoutDate blackoutDate = EarningsBlackoutDate.builder()
                .blackoutDate(date)
                .build();
        
        assertEquals(date, blackoutDate.getBlackoutDate());
    }
    
    @Test
    void testEarningsBlackoutDateOnCreate() {
        EarningsBlackoutDate date = EarningsBlackoutDate.builder()
                .blackoutDate(LocalDate.now())
                .build();
        
        assertNull(date.getCreatedAt());
        date.onCreate();
        assertNotNull(date.getCreatedAt());
    }
    
    @Test
    void testEarningsBlackoutDateEquals() {
        LocalDate today = LocalDate.now();
        EarningsBlackoutDate date1 = EarningsBlackoutDate.builder()
                .blackoutDate(today)
                .build();
        
        EarningsBlackoutDate date2 = EarningsBlackoutDate.builder()
                .blackoutDate(today)
                .build();
        
        assertEquals(date1, date2);
        assertEquals(date1.hashCode(), date2.hashCode());
    }
    
    @Test
    void testStrategyStockMappingBuilder() {
        StrategyStockMapping mapping = StrategyStockMapping.builder()
                .symbol("2330.TW")
                .stockName("TSMC")
                .strategyName("MomentumStrategy")
                .sharpeRatio(1.5)
                .totalReturnPct(15.5)
                .winRatePct(60.0)
                .maxDrawdownPct(-5.0)
                .totalTrades(100)
                .avgProfitPerTrade(150.0)
                .recommended(true)
                .build();
        
        assertEquals("2330.TW", mapping.getSymbol());
        assertEquals("TSMC", mapping.getStockName());
        assertEquals("MomentumStrategy", mapping.getStrategyName());
        assertEquals(1.5, mapping.getSharpeRatio());
        assertEquals(15.5, mapping.getTotalReturnPct());
        assertEquals(60.0, mapping.getWinRatePct());
        assertEquals(-5.0, mapping.getMaxDrawdownPct());
        assertEquals(100, mapping.getTotalTrades());
        assertEquals(150.0, mapping.getAvgProfitPerTrade());
        assertTrue(mapping.isRecommended());
    }
    
    @Test
    void testStrategyStockMappingRiskLevelCalculation() {
        // LOW risk
        StrategyStockMapping lowRisk = StrategyStockMapping.builder()
                .symbol("2330")
                .strategyName("Test")
                .maxDrawdownPct(-5.0)
                .sharpeRatio(1.5)
                .build();
        lowRisk.onUpdate();
        assertEquals("LOW", lowRisk.getRiskLevel());
        
        // MEDIUM risk
        StrategyStockMapping mediumRisk = StrategyStockMapping.builder()
                .symbol("2330")
                .strategyName("Test")
                .maxDrawdownPct(-12.0)
                .sharpeRatio(0.8)
                .build();
        mediumRisk.onUpdate();
        assertEquals("MEDIUM", mediumRisk.getRiskLevel());
        
        // HIGH risk
        StrategyStockMapping highRisk = StrategyStockMapping.builder()
                .symbol("2330")
                .strategyName("Test")
                .maxDrawdownPct(-20.0)
                .sharpeRatio(0.3)
                .build();
        highRisk.onUpdate();
        assertEquals("HIGH", highRisk.getRiskLevel());
    }
    
    @Test
    void testStrategyStockMappingUpdatedAtSet() {
        StrategyStockMapping mapping = StrategyStockMapping.builder()
                .symbol("2330")
                .strategyName("Test")
                .maxDrawdownPct(-5.0)
                .sharpeRatio(1.5)
                .build();
        
        assertNull(mapping.getUpdatedAt());
        mapping.onUpdate();
        assertNotNull(mapping.getUpdatedAt());
    }
}
