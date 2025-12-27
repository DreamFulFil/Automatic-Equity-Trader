package tw.gc.auto.equity.trader.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.*;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * Run parallelized backtest across top 50 stocks with all strategies.
     * This is the ONLY endpoint for backtest operations.
     * 
     * Flow:
     * 1. Fetch top 50 Taiwan stocks dynamically from web sources
     * 2. Download 10 years of historical data for each stock (batched by 365 days)
     * 3. Run backtests in parallel across all stocks and all strategies
     * 4. Store results in database
     * 
     * @param initialCapital Starting capital for backtest (default: 80000 TWD)
     * @return Map of stock symbol to strategy results
     */
    @PostMapping("/run")
    public Map<String, Map<String, BacktestService.InMemoryBacktestResult>> runParallelizedBacktest(
            @RequestParam(defaultValue = "80000") double initialCapital) {
        
        log.info("🚀 Running parallelized backtest with initial capital: {} TWD", initialCapital);
        
        // Initialize all 100 strategies
        List<IStrategy> strategies = getAllStrategies();
        
        log.info("📊 Testing {} strategies across top 50 stocks", strategies.size());
        
        // Run parallelized backtest (fetches stocks, downloads data, runs tests)
        return backtestService.runParallelizedBacktest(strategies, initialCapital);
    }
    
    /**
     * Get all 100 strategies for backtesting
     */
    private List<IStrategy> getAllStrategies() {
        List<IStrategy> strategies = new ArrayList<>();
        String defaultSymbol = "2330.TW";  // Default for pair/basket strategies
        
        // Original strategies (17)
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
        
        // Technical indicators (33)
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
        
        // Factor-based strategies (50)
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
}
