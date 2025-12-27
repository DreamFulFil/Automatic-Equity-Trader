package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.repositories.ShadowModeStockRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Data Operations Service
 * 
 * Orchestrates data population, backtesting, and strategy selection
 * by calling Python bridge REST APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataOperationsService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MarketDataRepository marketDataRepository;
    private final StrategyStockMappingRepository mappingRepository;
    private final ShadowModeStockRepository shadowModeRepository;

    @Value("${python.bridge.url:http://localhost:8888}")
    private String pythonBridgeUrl;

    /**
     * Populate historical data for all stocks
     */
    public Map<String, Object> populateHistoricalData(int days) {
        try {
            String url = pythonBridgeUrl + "/data/populate";
            
            Map<String, Object> request = new HashMap<>();
            request.put("days", days);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.info("📊 Calling Python bridge to populate historical data...");
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> result = response.getBody();
            log.info("✅ Data population complete: {}", result);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Failed to populate historical data", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to populate data: " + e.getMessage());
            return error;
        }
    }

    /**
     * Run combinatorial backtests
     */
    public Map<String, Object> runCombinationalBacktests(double capital, int days) {
        try {
            String url = pythonBridgeUrl + "/data/backtest";
            
            Map<String, Object> request = new HashMap<>();
            request.put("capital", capital);
            request.put("days", days);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.info("🧪 Calling Python bridge to run combinatorial backtests...");
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> result = response.getBody();
            log.info("✅ Backtests complete: {}", result);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Failed to run backtests", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to run backtests: " + e.getMessage());
            return error;
        }
    }

    /**
     * Auto-select best strategy
     */
    public Map<String, Object> autoSelectBestStrategy(double minSharpe, double minReturn, double minWinRate) {
        try {
            String url = pythonBridgeUrl + "/data/select-strategy";
            
            Map<String, Object> request = new HashMap<>();
            request.put("min_sharpe", minSharpe);
            request.put("min_return", minReturn);
            request.put("min_win_rate", minWinRate);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.info("🎯 Calling Python bridge to auto-select strategy...");
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> result = response.getBody();
            log.info("✅ Strategy selection complete: {}", result);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Failed to select strategy", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to select strategy: " + e.getMessage());
            return error;
        }
    }

    /**
     * Run full pipeline: populate + backtest + select
     */
    public Map<String, Object> runFullPipeline(int days) {
        try {
            String url = pythonBridgeUrl + "/data/full-pipeline";
            
            Map<String, Object> request = new HashMap<>();
            request.put("days", days);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.info("🚀 Calling Python bridge to run full pipeline...");
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            Map<String, Object> result = response.getBody();
            log.info("✅ Full pipeline complete: {}", result);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Failed to run full pipeline", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to run pipeline: " + e.getMessage());
            return error;
        }
    }

    /**
     * Get current data status
     */
    public Map<String, Object> getDataStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Count market data records
            long marketDataCount = marketDataRepository.count();
            status.put("market_data_records", marketDataCount);
            
            // Count backtest results
            long backtestResultsCount = mappingRepository.count();
            status.put("backtest_results", backtestResultsCount);
            
            // Count shadow mode stocks
            long shadowModeCount = shadowModeRepository.count();
            status.put("shadow_mode_stocks", shadowModeCount);
            
            status.put("status", "success");
            
        } catch (Exception e) {
            log.error("Failed to get data status", e);
            status.put("status", "error");
            status.put("message", e.getMessage());
        }
        
        return status;
    }
}
