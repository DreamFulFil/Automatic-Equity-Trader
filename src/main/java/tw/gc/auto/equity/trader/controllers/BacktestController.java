package tw.gc.auto.equity.trader.controllers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.services.DataOperationsService;
import tw.gc.auto.equity.trader.services.TaiwanStockNameService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.*;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;
    private final MarketDataRepository marketDataRepository;
    private final StrategyStockMappingRepository mappingRepository;
    private final DataOperationsService dataOperationsService;
    private final TaiwanStockNameService stockNameService;

    // ========================================================================
    // SINGLE STOCK BACKTEST
    // ========================================================================
    
    @GetMapping("/run")
    public Map<String, BacktestService.InMemoryBacktestResult> runBacktest(
            @RequestParam(defaultValue = "2454.TW") String symbol,
            @RequestParam(defaultValue = "1m") String timeframe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "80000") double capital) {

        log.info("Running backtest for {} from {} to {}", symbol, start, end);

        // 1. Fetch Historical Data from market_data table
        MarketData.Timeframe tf = "1D".equals(timeframe) ? MarketData.Timeframe.DAY_1 : MarketData.Timeframe.MIN_1;
        List<MarketData> history = marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            symbol, tf, start, end);
        
        if (history.isEmpty()) {
            throw new RuntimeException("No data found for " + symbol + " in range " + start + " to " + end);
        }
        
        log.info("Found {} data points for {}", history.size(), symbol);

        // 2. Initialize Strategies - ALL 50 strategies
        List<IStrategy> strategies = new ArrayList<>();
        
        // Original strategies
        strategies.add(new RSIStrategy(14, 70, 30));
        strategies.add(new MACDStrategy(12, 26, 9));
        strategies.add(new BollingerBandStrategy());
        strategies.add(new StochasticStrategy(14, 3, 80, 20));
        strategies.add(new ATRChannelStrategy(20, 2.0));
        strategies.add(new PivotPointStrategy());
        strategies.add(new MovingAverageCrossoverStrategy(5, 20, 0.001));
        strategies.add(new AggressiveRSIStrategy());
        strategies.add(new ArbitragePairsTradingStrategy());
        strategies.add(new AutomaticRebalancingStrategy(5));
        strategies.add(new DCAStrategy());
        strategies.add(new DividendReinvestmentStrategy());
        strategies.add(new MomentumTradingStrategy());
        strategies.add(new NewsSentimentStrategy());
        strategies.add(new TWAPExecutionStrategy(100, 30));
        strategies.add(new TaxLossHarvestingStrategy());
        strategies.add(new VWAPExecutionStrategy(100, 30, 0.003, 1));
        
        // New strategies (33 more)
        strategies.add(new KeltnerChannelStrategy());
        strategies.add(new IchimokuCloudStrategy());
        strategies.add(new ParabolicSARStrategy());
        strategies.add(new ADXTrendStrategy());
        strategies.add(new WilliamsRStrategy());
        strategies.add(new CCIStrategy());
        strategies.add(new VolumeWeightedStrategy());
        strategies.add(new TripleEMAStrategy());
        strategies.add(new FibonacciRetracementStrategy());
        strategies.add(new DonchianChannelStrategy());
        strategies.add(new SupertrendStrategy());
        strategies.add(new HullMovingAverageStrategy());
        strategies.add(new ChaikinMoneyFlowStrategy());
        strategies.add(new VortexIndicatorStrategy());
        strategies.add(new AroonOscillatorStrategy());
        strategies.add(new ElderRayStrategy());
        strategies.add(new KaufmanAdaptiveMAStrategy());
        strategies.add(new ZigZagStrategy());
        strategies.add(new PriceActionStrategy());
        strategies.add(new BalanceOfPowerStrategy());
        strategies.add(new KlingerOscillatorStrategy());
        strategies.add(new UltimateOscillatorStrategy());
        strategies.add(new MassIndexStrategy());
        strategies.add(new TrixStrategy());
        strategies.add(new RelativeVigorIndexStrategy());
        strategies.add(new DPOStrategy());
        strategies.add(new ForceIndexStrategy());
        strategies.add(new EnvelopeStrategy());
        strategies.add(new PriceVolumeRankStrategy());
        strategies.add(new AccumulationDistributionStrategy());
        strategies.add(new PriceRateOfChangeStrategy());
        strategies.add(new StandardDeviationStrategy());
        strategies.add(new LinearRegressionStrategy());

        // 3. Run Backtest
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(strategies, history, capital);
        
        // 4. Save results to database
        saveBacktestResults(symbol, results, capital);
        
        return results;
    }
    
    private void saveBacktestResults(String symbol, Map<String, BacktestService.InMemoryBacktestResult> results, double capital) {
        for (Map.Entry<String, BacktestService.InMemoryBacktestResult> entry : results.entrySet()) {
            String strategyName = entry.getKey();
            BacktestService.InMemoryBacktestResult result = entry.getValue();
            
            try {
                // Check if mapping already exists
                StrategyStockMapping mapping = mappingRepository
                    .findBySymbolAndStrategyName(symbol, strategyName)
                    .orElse(new StrategyStockMapping());
                
                // Update mapping
                mapping.setSymbol(symbol);
                mapping.setStockName(stockNameService.getStockName(symbol));
                mapping.setStrategyName(strategyName);
                mapping.setTotalReturnPct(result.getTotalReturnPercentage());
                mapping.setSharpeRatio(result.getSharpeRatio());
                mapping.setWinRatePct(result.getWinRate());
                mapping.setMaxDrawdownPct(-result.getMaxDrawdownPercentage());
                mapping.setTotalTrades(result.getTotalTrades());
                mapping.setUpdatedAt(LocalDateTime.now());
                
                mappingRepository.save(mapping);
                
                log.debug("Saved backtest result: {} + {} = {}%", symbol, strategyName, 
                    String.format("%.2f", result.getTotalReturnPercentage()));
                
            } catch (Exception e) {
                log.error("Failed to save backtest result for {} + {}", symbol, strategyName, e);
            }
        }
    }
    
    @GetMapping("/strategies")
    public List<String> listStrategies() {
        List<IStrategy> allStrategies = getAllStrategies();
        return allStrategies.stream()
            .map(IStrategy::getName)
            .collect(Collectors.toList());
    }
    
    private List<IStrategy> getAllStrategies() {
        List<IStrategy> strategies = new ArrayList<>();
        
        // Original strategies
        strategies.add(new RSIStrategy(14, 70, 30));
        strategies.add(new MACDStrategy(12, 26, 9));
        strategies.add(new BollingerBandStrategy());
        strategies.add(new StochasticStrategy(14, 3, 80, 20));
        strategies.add(new ATRChannelStrategy(20, 2.0));
        strategies.add(new PivotPointStrategy());
        strategies.add(new MovingAverageCrossoverStrategy(5, 20, 0.001));
        strategies.add(new AggressiveRSIStrategy());
        strategies.add(new ArbitragePairsTradingStrategy());
        strategies.add(new AutomaticRebalancingStrategy(5));
        strategies.add(new DCAStrategy());
        strategies.add(new DividendReinvestmentStrategy());
        strategies.add(new MomentumTradingStrategy());
        strategies.add(new NewsSentimentStrategy());
        strategies.add(new TWAPExecutionStrategy(100, 30));
        strategies.add(new TaxLossHarvestingStrategy());
        strategies.add(new VWAPExecutionStrategy(100, 30, 0.003, 1));
        
        // New strategies (33 more)
        strategies.add(new KeltnerChannelStrategy());
        strategies.add(new IchimokuCloudStrategy());
        strategies.add(new ParabolicSARStrategy());
        strategies.add(new ADXTrendStrategy());
        strategies.add(new WilliamsRStrategy());
        strategies.add(new CCIStrategy());
        strategies.add(new VolumeWeightedStrategy());
        strategies.add(new TripleEMAStrategy());
        strategies.add(new FibonacciRetracementStrategy());
        strategies.add(new DonchianChannelStrategy());
        strategies.add(new SupertrendStrategy());
        strategies.add(new HullMovingAverageStrategy());
        strategies.add(new ChaikinMoneyFlowStrategy());
        strategies.add(new VortexIndicatorStrategy());
        strategies.add(new AroonOscillatorStrategy());
        strategies.add(new ElderRayStrategy());
        strategies.add(new KaufmanAdaptiveMAStrategy());
        strategies.add(new ZigZagStrategy());
        strategies.add(new PriceActionStrategy());
        strategies.add(new BalanceOfPowerStrategy());
        strategies.add(new KlingerOscillatorStrategy());
        strategies.add(new UltimateOscillatorStrategy());
        strategies.add(new MassIndexStrategy());
        strategies.add(new TrixStrategy());
        strategies.add(new RelativeVigorIndexStrategy());
        strategies.add(new DPOStrategy());
        strategies.add(new ForceIndexStrategy());
        strategies.add(new EnvelopeStrategy());
        strategies.add(new PriceVolumeRankStrategy());
        strategies.add(new AccumulationDistributionStrategy());
        strategies.add(new PriceRateOfChangeStrategy());
        strategies.add(new StandardDeviationStrategy());
        strategies.add(new LinearRegressionStrategy());
        
        return strategies;
    }
    
    // ========================================================================
    // DATA OPERATIONS (Multi-Stock Pipeline)
    // ========================================================================
    
    @PostMapping("/populate-data")
    public Map<String, Object> populateHistoricalData(@RequestParam(defaultValue = "730") int days) {
        log.info("📊 Populating historical data for {} days", days);
        return dataOperationsService.populateHistoricalData(days);
    }

    @PostMapping("/run-all")
    public Map<String, Object> runCombinationalBacktests(
            @RequestParam(defaultValue = "80000") double capital,
            @RequestParam(defaultValue = "730") int days) {
        log.info("🧪 Running combinatorial backtests (capital={}, days={})", capital, days);
        return dataOperationsService.runCombinationalBacktests(capital, days);
    }

    @PostMapping("/select-strategy")
    public Map<String, Object> autoSelectStrategy(
            @RequestParam(defaultValue = "0.5") double minSharpe,
            @RequestParam(defaultValue = "10.0") double minReturn,
            @RequestParam(defaultValue = "50.0") double minWinRate) {
        log.info("🎯 Auto-selecting best strategy (sharpe>={}, return>={}, winRate>={})", 
                minSharpe, minReturn, minWinRate);
        return dataOperationsService.autoSelectBestStrategy(minSharpe, minReturn, minWinRate);
    }

    @PostMapping("/full-pipeline")
    public Map<String, Object> runFullPipeline(@RequestParam(defaultValue = "730") int days) {
        log.info("🚀 Running full data pipeline ({} days)", days);
        return dataOperationsService.runFullPipeline(days);
    }

    @GetMapping("/data-status")
    public Map<String, Object> getDataStatus() {
        log.debug("📈 Getting data operations status");
        return dataOperationsService.getDataStatus();
    }
}
