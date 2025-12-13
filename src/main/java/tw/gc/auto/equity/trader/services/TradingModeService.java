package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.SystemConfig;
import tw.gc.auto.equity.trader.repositories.SystemConfigRepository;
import tw.gc.auto.equity.trader.strategy.StrategyFactory;
import tw.gc.auto.equity.trader.strategy.factory.FuturesStrategyFactory;
import tw.gc.auto.equity.trader.strategy.factory.StockStrategyFactory;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingModeService {

    private final SystemConfigRepository systemConfigRepository;
    private final TradingStateService tradingStateService;
    private final StrategyManager strategyManager;
    private final RestTemplate restTemplate;

    private static final String CONFIG_KEY_MODE = "CURRENT_TRADING_MODE";

    @PostConstruct
    public void init() {
        // Load mode from DB or default to stock
        String mode = systemConfigRepository.findByKey(CONFIG_KEY_MODE)
                .map(SystemConfig::getValue)
                .orElse("stock");
        
        log.info("🚀 Initializing Trading Mode: {}", mode);
        switchMode(mode, true); // true = force init
    }

    public void switchMode(String newMode) {
        switchMode(newMode, false);
    }

    private void switchMode(String newMode, boolean force) {
        if (!force && newMode.equals(tradingStateService.getTradingMode())) {
            log.info("⚠️ Already in {} mode", newMode);
            return;
        }

        log.info("🔄 Switching to {} mode...", newMode);

        // 1. Call Python Bridge (skip if force init, as bridge might not be ready)
        // Actually, we should try, but fail gracefully
        if (!force) {
            try {
                Map<String, String> request = Map.of("mode", newMode);
                restTemplate.postForObject("http://localhost:8888/mode", request, Map.class);
                log.info("✅ Python bridge switched to {}", newMode);
            } catch (Exception e) {
                log.error("❌ Failed to switch Python bridge mode: {}", e.getMessage());
                throw new RuntimeException("Failed to switch Python bridge mode: " + e.getMessage());
            }
        }

        // 2. Update DB
        SystemConfig config = systemConfigRepository.findByKey(CONFIG_KEY_MODE)
                .orElse(SystemConfig.builder().key(CONFIG_KEY_MODE).build());
        config.setValue(newMode);
        systemConfigRepository.save(config);

        // 3. Update In-Memory State
        tradingStateService.setTradingMode(newMode);

        // 4. Re-initialize Strategies
        StrategyFactory factory;
        if ("futures".equalsIgnoreCase(newMode)) {
            factory = new FuturesStrategyFactory();
        } else {
            factory = new StockStrategyFactory();
        }
        strategyManager.reinitializeStrategies(factory);
        
        log.info("✅ Trading Mode switched to {} successfully", newMode);
    }
}
