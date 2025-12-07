package tw.gc.mtxfbot.entities;

import org.junit.jupiter.api.Test;
import tw.gc.mtxfbot.entities.Agent.AgentStatus;

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
}
