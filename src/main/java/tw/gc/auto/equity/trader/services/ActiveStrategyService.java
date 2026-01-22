package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.ActiveStrategyConfig;
import tw.gc.auto.equity.trader.repositories.ActiveStrategyConfigRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing the active strategy configuration.
 * Handles strategy selection, parameter management, and persistence.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActiveStrategyService {
    
    private final ActiveStrategyConfigRepository repository;
    private final ObjectMapper objectMapper;
    
    /**
     * Get the current active strategy configuration
     */
    public ActiveStrategyConfig getActiveStrategy() {
        return repository.findFirstByOrderByIdAsc()
            .orElseGet(() -> {
                // Create default if not exists
                ActiveStrategyConfig defaultConfig = ActiveStrategyConfig.builder()
                    .strategyName("RSIStrategy")
                    .parametersJson("{}")
                    .lastUpdated(LocalDateTime.now())
                    .switchReason("System initialization")
                    .autoSwitched(false)
                    .build();
                return repository.save(defaultConfig);
            });
    }
    
    /**
     * Get the name of the active strategy
     */
    public String getActiveStrategyName() {
        return getActiveStrategy().getStrategyName();
    }
    
    /**
     * Get parameters for the active strategy
     */
    public Map<String, Object> getActiveStrategyParameters() {
        ActiveStrategyConfig config = getActiveStrategy();
        try {
            if (config.getParametersJson() == null || config.getParametersJson().isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(config.getParametersJson(), Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse strategy parameters JSON", e);
            return new HashMap<>();
        }
    }
    
    /**
     * Switch to a new strategy
     */
    @Transactional
    public void switchStrategy(
        String strategyName, 
        Map<String, Object> parameters,
        String reason,
        boolean autoSwitched
    ) {
        switchStrategy(strategyName, parameters, reason, autoSwitched, null, null, null, null);
    }
    
    /**
     * Switch to a new strategy with performance metrics
     */
    @Transactional
    public void switchStrategy(
        String strategyName, 
        Map<String, Object> parameters,
        String reason,
        boolean autoSwitched,
        Double sharpeRatio,
        Double maxDrawdownPct,
        Double totalReturnPct,
        Double winRatePct
    ) {
        switchStrategyWithStock(
            strategyName,
            parameters,
            reason,
            autoSwitched,
            sharpeRatio,
            maxDrawdownPct,
            totalReturnPct,
            winRatePct,
            null,
            null
        );
    }

    /**
     * Switch to a new strategy with performance metrics and persist the active stock selection.
     */
    @Transactional
    public void switchStrategyWithStock(
        String strategyName,
        Map<String, Object> parameters,
        String reason,
        boolean autoSwitched,
        Double sharpeRatio,
        Double maxDrawdownPct,
        Double totalReturnPct,
        Double winRatePct,
        String stockSymbol,
        String stockName
    ) {
        ActiveStrategyConfig config = getActiveStrategy();
        
        log.info("🔄 Switching strategy: {} → {} ({})", 
            config.getStrategyName(), strategyName, reason);
        
        config.setStrategyName(strategyName);
        config.setLastUpdated(LocalDateTime.now());
        config.setSwitchReason(reason);
        config.setAutoSwitched(autoSwitched);
        config.setSharpeRatio(sharpeRatio);
        config.setMaxDrawdownPct(maxDrawdownPct);
        config.setTotalReturnPct(totalReturnPct);
        config.setWinRatePct(winRatePct);
        if (stockSymbol != null && !stockSymbol.isBlank()) {
            config.setStockSymbol(stockSymbol);
            config.setStockName(stockName);
        }
        
        try {
            String parametersJson = objectMapper.writeValueAsString(parameters);
            config.setParametersJson(parametersJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize strategy parameters", e);
            config.setParametersJson("{}");
        }
        
        repository.save(config);
        
        log.info("✅ Strategy switched successfully");
    }
    
    /**
     * Update parameters for the current strategy
     */
    @Transactional
    public void updateParameters(Map<String, Object> parameters) {
        ActiveStrategyConfig config = getActiveStrategy();
        
        try {
            String parametersJson = objectMapper.writeValueAsString(parameters);
            config.setParametersJson(parametersJson);
            config.setLastUpdated(LocalDateTime.now());
            repository.save(config);
            
            log.info("✅ Strategy parameters updated for {}", config.getStrategyName());
        } catch (JsonProcessingException e) {
            log.error("Failed to update strategy parameters", e);
        }
    }
}
