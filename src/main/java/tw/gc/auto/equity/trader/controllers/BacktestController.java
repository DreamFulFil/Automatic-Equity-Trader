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
import tw.gc.auto.equity.trader.services.AutoStrategySelector;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.services.DataOperationsService;
import tw.gc.auto.equity.trader.services.TaiwanStockNameService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.*;
import tw.gc.auto.equity.trader.strategy.impl.AcceleratingMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.AccrualAnomalyStrategy;
import tw.gc.auto.equity.trader.strategy.impl.AnchoredVWAPStrategy;
import tw.gc.auto.equity.trader.strategy.impl.AssetGrowthAnomalyStrategy;
import tw.gc.auto.equity.trader.strategy.impl.BasketArbitrageStrategy;
import tw.gc.auto.equity.trader.strategy.impl.BettingAgainstBetaStrategy;
import tw.gc.auto.equity.trader.strategy.impl.BidAskSpreadStrategy;
import tw.gc.auto.equity.trader.strategy.impl.BollingerSqueezeStrategy;
import tw.gc.auto.equity.trader.strategy.impl.BookToMarketStrategy;
import tw.gc.auto.equity.trader.strategy.impl.BreakoutMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.CalendarSpreadStrategy;
import tw.gc.auto.equity.trader.strategy.impl.CashFlowValueStrategy;
import tw.gc.auto.equity.trader.strategy.impl.CointegrationPairsStrategy;
import tw.gc.auto.equity.trader.strategy.impl.ConversionArbitrageStrategy;
import tw.gc.auto.equity.trader.strategy.impl.CrossSectionalMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.DualMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.EarningsYieldStrategy;
import tw.gc.auto.equity.trader.strategy.impl.EnterpriseValueMultipleStrategy;
import tw.gc.auto.equity.trader.strategy.impl.ExecutionShortfallStrategy;
import tw.gc.auto.equity.trader.strategy.impl.FinancialDistressStrategy;
import tw.gc.auto.equity.trader.strategy.impl.GrahamDefensiveStrategy;
import tw.gc.auto.equity.trader.strategy.impl.IndexArbitrageStrategy;
import tw.gc.auto.equity.trader.strategy.impl.InvestmentFactorStrategy;
import tw.gc.auto.equity.trader.strategy.impl.LimitOrderBookStrategy;
import tw.gc.auto.equity.trader.strategy.impl.LowVolatilityAnomalyStrategy;
import tw.gc.auto.equity.trader.strategy.impl.MarketMakingStrategy;
import tw.gc.auto.equity.trader.strategy.impl.MeanReversionStrategy;
import tw.gc.auto.equity.trader.strategy.impl.MultiFactorRankingStrategy;
import tw.gc.auto.equity.trader.strategy.impl.NetPayoutYieldStrategy;
import tw.gc.auto.equity.trader.strategy.impl.NetStockIssuanceStrategy;
import tw.gc.auto.equity.trader.strategy.impl.NewsRevisionMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.OrderFlowImbalanceStrategy;
import tw.gc.auto.equity.trader.strategy.impl.PairsCorrelationStrategy;
import tw.gc.auto.equity.trader.strategy.impl.PriceTrendStrengthStrategy;
import tw.gc.auto.equity.trader.strategy.impl.ProfitabilityFactorStrategy;
import tw.gc.auto.equity.trader.strategy.impl.PutCallParityArbitrageStrategy;
import tw.gc.auto.equity.trader.strategy.impl.QualityMinusJunkStrategy;
import tw.gc.auto.equity.trader.strategy.impl.QualityValueStrategy;
import tw.gc.auto.equity.trader.strategy.impl.ResidualMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.SalesGrowthValueStrategy;
import tw.gc.auto.equity.trader.strategy.impl.SeasonalMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.SmartOrderRoutingStrategy;
import tw.gc.auto.equity.trader.strategy.impl.TimeSeriesMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.TradeVelocityStrategy;
import tw.gc.auto.equity.trader.strategy.impl.TriangularArbitrageStrategy;
import tw.gc.auto.equity.trader.strategy.impl.VolatilityAdjustedMomentumStrategy;
import tw.gc.auto.equity.trader.strategy.impl.VolatilityArbitrageStrategy;
import tw.gc.auto.equity.trader.strategy.impl.VolumeProfileStrategy;

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
    private final AutoStrategySelector autoStrategySelector;

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
        
        // Additional 50 strategies (single-stock compatible with default parameters)
        strategies.add(new MeanReversionStrategy(20, 2.0, 0.5));
        strategies.add(new BreakoutMomentumStrategy(20, 0.02));
        strategies.add(new DualMomentumStrategy(60, 120, 0.0));
        strategies.add(new TimeSeriesMomentumStrategy(60, 0.0));
        strategies.add(new CrossSectionalMomentumStrategy(60, 10));
        strategies.add(new ResidualMomentumStrategy(60, 1.0));
        strategies.add(new SeasonalMomentumStrategy(new String[]{"Nov","Dec","Jan"}, new String[]{"May","Jun","Jul"}));
        strategies.add(new NewsRevisionMomentumStrategy(30, 0.05));
        strategies.add(new AcceleratingMomentumStrategy(30, 10));
        strategies.add(new VolatilityAdjustedMomentumStrategy(30, 20));
        strategies.add(new BookToMarketStrategy(0.7, 90));
        strategies.add(new EarningsYieldStrategy(0.05, 60));
        strategies.add(new CashFlowValueStrategy(0.05));
        strategies.add(new EnterpriseValueMultipleStrategy(10.0));
        strategies.add(new SalesGrowthValueStrategy(2.0, 0.10));
        strategies.add(new QualityValueStrategy(0.7, 7));
        strategies.add(new DividendYieldStrategy(0.03, 3));
        strategies.add(new NetPayoutYieldStrategy(0.04));
        strategies.add(new ProfitabilityFactorStrategy(0.35));
        strategies.add(new InvestmentFactorStrategy(0.15));
        strategies.add(new AssetGrowthAnomalyStrategy(1, 0.15));
        strategies.add(new AccrualAnomalyStrategy(0.05));
        strategies.add(new NetStockIssuanceStrategy(12, 0.02));
        strategies.add(new FinancialDistressStrategy(0.15));
        strategies.add(new LowVolatilityAnomalyStrategy(60, 20));
        strategies.add(new BettingAgainstBetaStrategy(60, 0.8));
        strategies.add(new QualityMinusJunkStrategy(60, 30));
        strategies.add(new MultiFactorRankingStrategy(new double[]{0.3, 0.3, 0.2, 0.2}, 30));
        strategies.add(new PriceTrendStrengthStrategy(30, 0.7));
        strategies.add(new BollingerSqueezeStrategy(20, 2.0));
        strategies.add(new VolumeProfileStrategy(20, 0.70));
        strategies.add(new PairsCorrelationStrategy(symbol, "2330.TW", 60, 2.0));
        strategies.add(new CointegrationPairsStrategy(symbol, "2330.TW", 2.0));
        strategies.add(new BasketArbitrageStrategy(new String[]{symbol}, "^TWII"));
        strategies.add(new IndexArbitrageStrategy("^TWII", 0.01));
        strategies.add(new ConversionArbitrageStrategy("BOND", symbol, 0.02));
        strategies.add(new CalendarSpreadStrategy(1, 3, 0.05));
        strategies.add(new TriangularArbitrageStrategy(new String[]{symbol, "2330.TW", "2454.TW"}, 0.01));
        strategies.add(new PutCallParityArbitrageStrategy(0.01));
        strategies.add(new VolatilityArbitrageStrategy(20, 0.05));
        strategies.add(new MarketMakingStrategy(0.001, 1000));
        strategies.add(new LimitOrderBookStrategy(5, 1.5));
        strategies.add(new OrderFlowImbalanceStrategy(30, 0.6));
        strategies.add(new BidAskSpreadStrategy(0.001, 0.005));
        strategies.add(new TradeVelocityStrategy(30, 2.0));
        strategies.add(new SmartOrderRoutingStrategy(new String[]{"TWSE"}, new double[]{0.001}));
        strategies.add(new ExecutionShortfallStrategy(1000, 10, 0.5));
        strategies.add(new AnchoredVWAPStrategy("market_open", 0.02));
        strategies.add(new GrahamDefensiveStrategy(2.0, 15.0, 0.03));

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
        
        // Additional 50 strategies (single-stock compatible with default parameters)
        String defaultSymbol = "2330.TW";  // Default for pair/basket strategies
        strategies.add(new MeanReversionStrategy(20, 2.0, 0.5));
        strategies.add(new BreakoutMomentumStrategy(20, 0.02));
        strategies.add(new DualMomentumStrategy(60, 120, 0.0));
        strategies.add(new TimeSeriesMomentumStrategy(60, 0.0));
        strategies.add(new CrossSectionalMomentumStrategy(60, 10));
        strategies.add(new ResidualMomentumStrategy(60, 1.0));
        strategies.add(new SeasonalMomentumStrategy(new String[]{"Nov","Dec","Jan"}, new String[]{"May","Jun","Jul"}));
        strategies.add(new NewsRevisionMomentumStrategy(30, 0.05));
        strategies.add(new AcceleratingMomentumStrategy(30, 10));
        strategies.add(new VolatilityAdjustedMomentumStrategy(30, 20));
        strategies.add(new BookToMarketStrategy(0.7, 90));
        strategies.add(new EarningsYieldStrategy(0.05, 60));
        strategies.add(new CashFlowValueStrategy(0.05));
        strategies.add(new EnterpriseValueMultipleStrategy(10.0));
        strategies.add(new SalesGrowthValueStrategy(2.0, 0.10));
        strategies.add(new QualityValueStrategy(0.7, 7));
        strategies.add(new DividendYieldStrategy(0.03, 3));
        strategies.add(new NetPayoutYieldStrategy(0.04));
        strategies.add(new ProfitabilityFactorStrategy(0.35));
        strategies.add(new InvestmentFactorStrategy(0.15));
        strategies.add(new AssetGrowthAnomalyStrategy(1, 0.15));
        strategies.add(new AccrualAnomalyStrategy(0.05));
        strategies.add(new NetStockIssuanceStrategy(12, 0.02));
        strategies.add(new FinancialDistressStrategy(0.15));
        strategies.add(new LowVolatilityAnomalyStrategy(60, 20));
        strategies.add(new BettingAgainstBetaStrategy(60, 0.8));
        strategies.add(new QualityMinusJunkStrategy(60, 30));
        strategies.add(new MultiFactorRankingStrategy(new double[]{0.3, 0.3, 0.2, 0.2}, 30));
        strategies.add(new PriceTrendStrengthStrategy(30, 0.7));
        strategies.add(new BollingerSqueezeStrategy(20, 2.0));
        strategies.add(new VolumeProfileStrategy(20, 0.70));
        strategies.add(new PairsCorrelationStrategy(defaultSymbol, "2454.TW", 60, 2.0));
        strategies.add(new CointegrationPairsStrategy(defaultSymbol, "2454.TW", 2.0));
        strategies.add(new BasketArbitrageStrategy(new String[]{defaultSymbol}, "^TWII"));
        strategies.add(new IndexArbitrageStrategy("^TWII", 0.01));
        strategies.add(new ConversionArbitrageStrategy("BOND", defaultSymbol, 0.02));
        strategies.add(new CalendarSpreadStrategy(1, 3, 0.05));
        strategies.add(new TriangularArbitrageStrategy(new String[]{defaultSymbol, "2454.TW", "2317.TW"}, 0.01));
        strategies.add(new PutCallParityArbitrageStrategy(0.01));
        strategies.add(new VolatilityArbitrageStrategy(20, 0.05));
        strategies.add(new MarketMakingStrategy(0.001, 1000));
        strategies.add(new LimitOrderBookStrategy(5, 1.5));
        strategies.add(new OrderFlowImbalanceStrategy(30, 0.6));
        strategies.add(new BidAskSpreadStrategy(0.001, 0.005));
        strategies.add(new TradeVelocityStrategy(30, 2.0));
        strategies.add(new SmartOrderRoutingStrategy(new String[]{"TWSE"}, new double[]{0.001}));
        strategies.add(new ExecutionShortfallStrategy(1000, 10, 0.5));
        strategies.add(new AnchoredVWAPStrategy("market_open", 0.02));
        strategies.add(new GrahamDefensiveStrategy(2.0, 15.0, 0.03));
        
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
    
    @PostMapping("/select-strategy-direct")
    public Map<String, Object> autoSelectStrategyDirect() {
        log.info("🎯 Auto-selecting best strategy (direct Java call)");
        try {
            autoStrategySelector.selectBestStrategyAndStock();
            return Map.of(
                "status", "success",
                "message", "Strategy selection completed successfully"
            );
        } catch (Exception e) {
            log.error("Failed to select strategy", e);
            return Map.of(
                "status", "error",
                "message", "Failed to select strategy: " + e.getMessage()
            );
        }
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
