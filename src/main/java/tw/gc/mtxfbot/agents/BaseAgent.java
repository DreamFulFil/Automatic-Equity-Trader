package tw.gc.mtxfbot.agents;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.Agent;
import tw.gc.mtxfbot.entities.Agent.AgentStatus;

import java.util.List;
import java.util.Map;

/**
 * Base class for all agents in the Lunch Investor Bot.
 * 
 * Design patterns inspired by:
 * - Anthropic's tool use guidelines (structured input/output)
 * - OpenAI function calling (JSON schemas)
 * - LangChain agent framework (modular, composable)
 * - CrewAI (role-based agents with capabilities)
 * 
 * Best Practices:
 * - Modularity: Inherit and override execute() for custom behavior
 * - Error Handling: Circuit breaker pattern with fallback responses
 * - Async-ready: Can be extended for async execution
 * - Testable: Clear input/output contracts
 */
@Slf4j
public abstract class BaseAgent {
    
    protected String name;
    protected String description;
    protected List<String> capabilities;
    protected AgentStatus status;
    protected int maxRetries = 3;
    protected int circuitBreakerThreshold = 5;
    protected int consecutiveFailures = 0;
    
    public BaseAgent(String name, String description, List<String> capabilities) {
        this.name = name;
        this.description = description;
        this.capabilities = capabilities;
        this.status = AgentStatus.ACTIVE;
    }
    
    /**
     * Main execution method. Override in subclasses for specific behavior.
     * @param input Map of input parameters
     * @return Map of output results
     */
    public abstract Map<String, Object> execute(Map<String, Object> input);
    
    /**
     * Execute with circuit breaker protection
     */
    public Map<String, Object> safeExecute(Map<String, Object> input) {
        if (status == AgentStatus.ERROR && consecutiveFailures >= circuitBreakerThreshold) {
            log.warn("🔴 Agent {} circuit breaker OPEN - returning fallback", name);
            return getFallbackResponse();
        }
        
        try {
            Map<String, Object> result = executeWithRetry(input);
            consecutiveFailures = 0;
            return result;
        } catch (Exception e) {
            consecutiveFailures++;
            log.error("❌ Agent {} execution failed ({}/{}): {}", 
                    name, consecutiveFailures, circuitBreakerThreshold, e.getMessage());
            
            if (consecutiveFailures >= circuitBreakerThreshold) {
                status = AgentStatus.ERROR;
                log.error("🔴 Agent {} circuit breaker TRIPPED - too many failures", name);
            }
            return getFallbackResponse();
        }
    }
    
    /**
     * Execute with retry logic
     */
    protected Map<String, Object> executeWithRetry(Map<String, Object> input) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return execute(input);
            } catch (Exception e) {
                lastException = e;
                log.warn("⚠️ Agent {} attempt {}/{} failed: {}", name, attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100L * (1L << (attempt - 1))); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new RuntimeException("Agent execution failed after " + maxRetries + " attempts", lastException);
    }
    
    /**
     * Fallback response when circuit breaker is open or execution fails
     */
    protected Map<String, Object> getFallbackResponse() {
        return Map.of(
            "success", false,
            "error", "Agent temporarily unavailable",
            "agent", name
        );
    }
    
    /**
     * Activate the agent
     */
    public void activate() {
        this.status = AgentStatus.ACTIVE;
        this.consecutiveFailures = 0;
        log.info("✅ Agent {} activated", name);
    }
    
    /**
     * Deactivate the agent
     */
    public void deactivate() {
        this.status = AgentStatus.INACTIVE;
        log.info("⏸️ Agent {} deactivated", name);
    }
    
    /**
     * Reset circuit breaker
     */
    public void resetCircuitBreaker() {
        this.status = AgentStatus.ACTIVE;
        this.consecutiveFailures = 0;
        log.info("🔄 Agent {} circuit breaker reset", name);
    }
    
    /**
     * Convert to entity for persistence
     */
    public Agent toEntity() {
        return Agent.builder()
                .name(name)
                .description(description)
                .capabilities(String.join(",", capabilities))
                .status(status)
                .agentType(this.getClass().getSimpleName())
                .build();
    }
    
    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getCapabilities() { return capabilities; }
    public AgentStatus getStatus() { return status; }
}
