package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing strategy-stock performance mappings
 * 
 * Helps users understand:
 * - Which strategies work best for which stocks
 * - Optimal strategy-stock combinations for their risk profile
 * - Performance history and trends
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyStockMappingService {
    
    private final StrategyStockMappingRepository mappingRepository;
    
    /**
     * Update or create strategy-stock mapping from backtest results
     */
    @Transactional
    public StrategyStockMapping updateMapping(String symbol, String stockName, String strategyName,
                                              Double sharpeRatio, Double totalReturnPct,
                                              Double winRatePct, Double maxDrawdownPct,
                                              Integer totalTrades, Double avgProfitPerTrade,
                                              LocalDateTime backtestStart, LocalDateTime backtestEnd) {
        
        StrategyStockMapping mapping = mappingRepository
            .findBySymbolAndStrategyName(symbol, strategyName)
            .orElse(StrategyStockMapping.builder()
                .symbol(symbol)
                .stockName(stockName)
                .strategyName(strategyName)
                .build());
        
        mapping.setSharpeRatio(sharpeRatio);
        mapping.setTotalReturnPct(totalReturnPct);
        mapping.setWinRatePct(winRatePct);
        mapping.setMaxDrawdownPct(maxDrawdownPct);
        mapping.setTotalTrades(totalTrades);
        mapping.setAvgProfitPerTrade(avgProfitPerTrade);
        mapping.setBacktestStart(backtestStart);
        mapping.setBacktestEnd(backtestEnd);
        
        return mappingRepository.save(mapping);
    }
    
    /**
     * Get best strategy for a specific stock
     */
    public StrategyStockMapping getBestStrategyForStock(String symbol) {
        List<StrategyStockMapping> mappings = mappingRepository.findBySymbolOrderBySharpeRatioDesc(symbol);
        return mappings.isEmpty() ? null : mappings.get(0);
    }
    
    /**
     * Get all strategies for a stock, ranked by performance
     */
    public List<StrategyStockMapping> getStrategiesForStock(String symbol) {
        return mappingRepository.findBySymbolOrderBySharpeRatioDesc(symbol);
    }
    
    /**
     * Mark a strategy as recommended for a stock
     */
    @Transactional
    public void setRecommendedStrategy(String symbol, String strategyName) {
        // Clear all recommendations for this stock
        List<StrategyStockMapping> mappings = mappingRepository.findBySymbolOrderBySharpeRatioDesc(symbol);
        mappings.forEach(m -> {
            m.setRecommended(false);
            mappingRepository.save(m);
        });
        
        // Set new recommendation
        mappingRepository.findBySymbolAndStrategyName(symbol, strategyName)
            .ifPresent(m -> {
                m.setRecommended(true);
                mappingRepository.save(m);
                log.info("✅ Set {} as recommended strategy for {}", strategyName, symbol);
            });
    }
    
    /**
     * Get top performing combinations overall
     */
    public List<StrategyStockMapping> getTopCombinations(int limit) {
        return mappingRepository.findTopPerformingCombinations()
            .stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get low-risk combinations suitable for risk-averse users
     */
    public List<StrategyStockMapping> getLowRiskCombinations(int limit) {
        return mappingRepository.findLowRiskCombinations()
            .stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get combinations with steady returns (for 5% monthly goal)
     */
    public List<StrategyStockMapping> getSteadyReturnCombinations(int limit) {
        return mappingRepository.findSteadyReturnCombinations()
            .stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Generate performance summary for a stock
     */
    public Map<String, Object> getStockPerformanceSummary(String symbol) {
        List<StrategyStockMapping> mappings = getStrategiesForStock(symbol);
        
        if (mappings.isEmpty()) {
            return Map.of(
                "symbol", symbol,
                "message", "No performance data available yet"
            );
        }
        
        StrategyStockMapping best = mappings.get(0);
        double avgSharpe = mappings.stream()
            .mapToDouble(m -> m.getSharpeRatio() != null ? m.getSharpeRatio() : 0)
            .average()
            .orElse(0);
        
        long lowRiskCount = mappings.stream()
            .filter(m -> "LOW".equals(m.getRiskLevel()))
            .count();
        
        return Map.of(
            "symbol", symbol,
            "bestStrategy", best.getStrategyName(),
            "bestSharpe", best.getSharpeRatio(),
            "averageSharpe", avgSharpe,
            "totalStrategiesTested", mappings.size(),
            "lowRiskStrategiesCount", lowRiskCount,
            "recommendedRiskLevel", best.getRiskLevel()
        );
    }
    
    /**
     * Add AI insights to a mapping
     */
    @Transactional
    public void addAIInsights(String symbol, String strategyName, String insights) {
        mappingRepository.findBySymbolAndStrategyName(symbol, strategyName)
            .ifPresent(m -> {
                m.setAiInsights(insights);
                mappingRepository.save(m);
                log.info("📝 Added AI insights for {}-{}", symbol, strategyName);
            });
    }
}
