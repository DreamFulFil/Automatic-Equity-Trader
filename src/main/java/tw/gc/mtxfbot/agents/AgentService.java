package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.BotModeService;
import tw.gc.mtxfbot.TelegramService;
import tw.gc.mtxfbot.config.OllamaProperties;
import tw.gc.mtxfbot.config.TradingProperties;
import tw.gc.mtxfbot.entities.Agent;
import tw.gc.mtxfbot.entities.Agent.AgentStatus;
import tw.gc.mtxfbot.repositories.AgentInteractionRepository;
import tw.gc.mtxfbot.repositories.AgentRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentService - Central registry and manager for all agents.
 * 
 * Responsibilities:
 * - Initializes and registers all agents on startup
 * - Provides agent lookup by name
 * - Persists agents to database
 * - Handles /agent Telegram command
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentService {
    
    private final AgentRepository agentRepo;
    private final TradeRepository tradeRepo;
    private final AgentInteractionRepository interactionRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final OllamaProperties ollamaProperties;
    private final TelegramService telegramService;
    private final BotModeService botModeService;
    
    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("🤖 Initializing Agent Framework...");
        
        // Wire this service to TelegramService
        telegramService.setAgentService(this);
        telegramService.setBotModeService(botModeService);
        
        String bridgeUrl = tradingProperties.getBridge().getUrl();
        String ollamaUrl = ollamaProperties.getUrl();
        String ollamaModel = ollamaProperties.getModel();
        
        // Create and register all agents
        registerAgent(new NewsAnalyzerAgent(restTemplate, objectMapper, bridgeUrl));
        registerAgent(new TutorBotAgent(restTemplate, objectMapper, ollamaUrl, ollamaModel, interactionRepo));
        registerAgent(new SignalGeneratorAgent(restTemplate, objectMapper, bridgeUrl));
        registerAgent(new RiskManagerAgent(tradeRepo));
        
        // Persist agents to database
        saveAgentsToDatabase();
        
        log.info("✅ Agent Framework initialized with {} agents", agents.size());
    }
    
    /**
     * Register an agent
     */
    public void registerAgent(BaseAgent agent) {
        agents.put(agent.getName(), agent);
        log.info("📝 Registered agent: {} - {}", agent.getName(), agent.getDescription());
    }
    
    /**
     * Get agent by name
     */
    public BaseAgent getAgent(String name) {
        return agents.get(name);
    }
    
    /**
     * Get all registered agents
     */
    public List<BaseAgent> getAllAgents() {
        return List.copyOf(agents.values());
    }
    
    /**
     * Get agent status summary for Telegram /agent command
     */
    public String getAgentListMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 AVAILABLE AGENTS\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        
        int index = 1;
        for (BaseAgent agent : agents.values()) {
            String statusIcon = agent.getStatus() == AgentStatus.ACTIVE ? "🟢" :
                               agent.getStatus() == AgentStatus.ERROR ? "🔴" : "🟡";
            sb.append(String.format("%d. %s %s\n   %s\n   Capabilities: %s\n\n",
                    index++,
                    statusIcon,
                    agent.getName(),
                    agent.getDescription(),
                    String.join(", ", agent.getCapabilities())
            ));
        }
        
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Commands:\n");
        sb.append("/talk <question> - Ask TutorBot\n");
        sb.append("/insight - Get daily insight\n");
        sb.append("/golive - Check live eligibility");
        
        return sb.toString();
    }
    
    /**
     * Save all agents to database
     */
    private void saveAgentsToDatabase() {
        for (BaseAgent agent : agents.values()) {
            if (!agentRepo.existsByName(agent.getName())) {
                Agent entity = agent.toEntity();
                agentRepo.save(entity);
                log.info("💾 Saved agent to DB: {}", agent.getName());
            } else {
                log.debug("Agent already in DB: {}", agent.getName());
            }
        }
    }
    
    /**
     * Execute agent by name with input
     */
    public Map<String, Object> executeAgent(String agentName, Map<String, Object> input) {
        BaseAgent agent = agents.get(agentName);
        if (agent == null) {
            return Map.of(
                "success", false,
                "error", "Agent not found: " + agentName
            );
        }
        return agent.safeExecute(input);
    }
    
    /**
     * Get NewsAnalyzer agent directly
     */
    public NewsAnalyzerAgent getNewsAnalyzer() {
        return (NewsAnalyzerAgent) agents.get("NewsAnalyzer");
    }
    
    /**
     * Get TutorBot agent directly
     */
    public TutorBotAgent getTutorBot() {
        return (TutorBotAgent) agents.get("TutorBot");
    }
    
    /**
     * Get SignalGenerator agent directly
     */
    public SignalGeneratorAgent getSignalGenerator() {
        return (SignalGeneratorAgent) agents.get("SignalGenerator");
    }
    
    /**
     * Get RiskManager agent directly
     */
    public RiskManagerAgent getRiskManager() {
        return (RiskManagerAgent) agents.get("RiskManager");
    }
}
