package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ChaikinMoneyFlowStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> mfvHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public ChaikinMoneyFlowStrategy() {
        this(20);
    }
    
    public ChaikinMoneyFlowStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> mfvs = mfvHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double mfMultiplier = ((data.getClose() - data.getLow()) - (data.getHigh() - data.getClose())) / 
                              (data.getHigh() - data.getLow() + 0.0001);
        double mfv = mfMultiplier * data.getVolume();
        
        mfvs.addLast(mfv);
        volumes.addLast(data.getVolume());
        
        if (mfvs.size() > period) {
            mfvs.removeFirst();
            volumes.removeFirst();
        }
        
        if (mfvs.size() < period) {
            return TradeSignal.neutral("Warming up CMF");
        }
        
        double sumMFV = mfvs.stream().mapToDouble(Double::doubleValue).sum();
        double sumVolume = volumes.stream().mapToLong(Long::longValue).sum();
        double cmf = sumVolume > 0 ? sumMFV / sumVolume : 0;
        
        int position = portfolio.getPosition(symbol);
        
        if (cmf > 0.05 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("CMF bullish (%.3f)", cmf));
        } else if (cmf < -0.05 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("CMF bearish (%.3f)", cmf));
        }
        
        return TradeSignal.neutral(String.format("CMF=%.3f", cmf));
    }

    @Override
    public String getName() {
        return "Chaikin Money Flow";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        mfvHistory.clear();
        volumeHistory.clear();
    }
}
