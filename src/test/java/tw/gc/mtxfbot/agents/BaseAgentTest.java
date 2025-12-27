package tw.gc.mtxfbot.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.mtxfbot.entities.Agent.AgentStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BaseAgent functionality
 */
class BaseAgentTest {
    
    private TestAgent testAgent;
    
    @BeforeEach
    void setUp() {
        testAgent = new TestAgent();
    }
    
    @Test
    void testAgentInitialization() {
        assertEquals("TestAgent", testAgent.getName());
        assertEquals("Test agent for unit testing", testAgent.getDescription());
        assertEquals(AgentStatus.ACTIVE, testAgent.getStatus());
        assertEquals(2, testAgent.getCapabilities().size());
    }
    
    @Test
    void testSuccessfulExecution() {
        Map<String, Object> input = Map.of("value", 42);
        Map<String, Object> result = testAgent.safeExecute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals(42, result.get("value"));
    }
    
    @Test
    void testActivateDeactivate() {
        testAgent.deactivate();
        assertEquals(AgentStatus.INACTIVE, testAgent.getStatus());
        
        testAgent.activate();
        assertEquals(AgentStatus.ACTIVE, testAgent.getStatus());
    }
    
    @Test
    void testCircuitBreakerTrips() {
        testAgent.shouldFail = true;
        testAgent.circuitBreakerThreshold = 3;
        testAgent.maxRetries = 1;
        
        // Execute multiple times to trip circuit breaker
        for (int i = 0; i < 3; i++) {
            testAgent.safeExecute(Map.of());
        }
        
        assertEquals(AgentStatus.ERROR, testAgent.getStatus());
    }
    
    @Test
    void testCircuitBreakerReset() {
        testAgent.shouldFail = true;
        testAgent.circuitBreakerThreshold = 2;
        testAgent.maxRetries = 1;
        
        // Trip the circuit breaker
        testAgent.safeExecute(Map.of());
        testAgent.safeExecute(Map.of());
        assertEquals(AgentStatus.ERROR, testAgent.getStatus());
        
        // Reset and verify
        testAgent.resetCircuitBreaker();
        assertEquals(AgentStatus.ACTIVE, testAgent.getStatus());
    }
    
    @Test
    void testFallbackResponseWhenCircuitOpen() {
        testAgent.shouldFail = true;
        testAgent.circuitBreakerThreshold = 1;
        testAgent.maxRetries = 1;
        
        // Trip the breaker
        testAgent.safeExecute(Map.of());
        
        // Next call should return fallback
        Map<String, Object> result = testAgent.safeExecute(Map.of());
        assertFalse((boolean) result.get("success"));
        assertEquals("TestAgent", result.get("agent"));
    }
    
    @Test
    void testToEntity() {
        var entity = testAgent.toEntity();
        
        assertEquals("TestAgent", entity.getName());
        assertEquals("Test agent for unit testing", entity.getDescription());
        assertEquals(AgentStatus.ACTIVE, entity.getStatus());
        assertEquals("TestAgent", entity.getAgentType());
    }
    
    /**
     * Test implementation of BaseAgent
     */
    static class TestAgent extends BaseAgent {
        boolean shouldFail = false;
        
        public TestAgent() {
            super("TestAgent", "Test agent for unit testing", List.of("test", "mock"));
        }
        
        @Override
        public Map<String, Object> execute(Map<String, Object> input) {
            if (shouldFail) {
                throw new RuntimeException("Simulated failure");
            }
            return Map.of(
                "success", true,
                "value", input.getOrDefault("value", 0)
            );
        }
    }
}
