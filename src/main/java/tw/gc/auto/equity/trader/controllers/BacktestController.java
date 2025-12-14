package tw.gc.auto.equity.trader.controllers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.*;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;
    private final BarRepository barRepository;

    @GetMapping("/run")
    public Map<String, BacktestService.BacktestResult> runBacktest(
            @RequestParam(defaultValue = "2454.TW") String symbol,
            @RequestParam(defaultValue = "1m") String timeframe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "80000") double capital) {

        log.info("Running backtest for {} from {} to {}", symbol, start, end);

        // 1. Fetch Historical Data
        List<Bar> bars = barRepository.findBySymbolAndTimeframeAndTimestampBetween(symbol, timeframe, start, end);
        
        if (bars.isEmpty()) {
            throw new RuntimeException("No data found for " + symbol + " in range");
        }
        
        // Convert Bars to MarketData
        List<MarketData> history = bars.stream().map(bar -> MarketData.builder()
                .symbol(bar.getSymbol())
                .timestamp(bar.getTimestamp())
                .open(bar.getOpen())
                .high(bar.getHigh())
                .low(bar.getLow())
                .close(bar.getClose())
                .volume(bar.getVolume())
                .build())
                .collect(Collectors.toList());

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
        return backtestService.runBacktest(strategies, history, capital);
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
}
