package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.library.*;
import tw.gc.auto.equity.trader.strategy.impl.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // 2. Initialize Strategies
        List<IStrategy> strategies = new ArrayList<>();
        strategies.add(new RSIStrategy(14, 70, 30));
        strategies.add(new MACDStrategy(12, 26, 9));
        strategies.add(new BollingerBandStrategy());
        strategies.add(new StochasticStrategy(14, 3, 80, 20));
        strategies.add(new ATRChannelStrategy(20, 2.0));
        strategies.add(new PivotPointStrategy());
        strategies.add(new MovingAverageCrossoverStrategy(5, 20, 0.001));

        // 3. Run Backtest
        return backtestService.runBacktest(strategies, history, capital);
    }
    
    @GetMapping("/strategies")
    public List<String> listStrategies() {
        List<String> names = new ArrayList<>();
        names.add("RSI (14, 70/30)");
        names.add("MACD (12, 26, 9)");
        names.add("Bollinger Bands (20, 2.0)");
        names.add("Stochastic (14, 3, 80/20)");
        names.add("ATR Channel (20, 2.0)");
        names.add("Pivot Points");
        names.add("MA Crossover (5/20)");
        return names;
    }
}
