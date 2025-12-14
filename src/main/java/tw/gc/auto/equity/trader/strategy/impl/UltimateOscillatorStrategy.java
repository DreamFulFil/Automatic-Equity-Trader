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
public class UltimateOscillatorStrategy implements IStrategy {
    
    private final Map<String, Deque<Double>> bpHistory = new HashMap<>();
    private final Map<String, Deque<Double>> trHistory = new HashMap<>();
    private final Map<String, Double> previousClose = new HashMap<>();
    
    public UltimateOscillatorStrategy() {
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> bps = bpHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> trs = trHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        Double prevClose = previousClose.get(symbol);
        if (prevClose == null) {
            previousClose.put(symbol, data.getClose());
            return TradeSignal.neutral("Initializing Ultimate Oscillator");
        }
        
        double bp = data.getClose() - Math.min(data.getLow(), prevClose);
        double tr = Math.max(data.getHigh(), prevClose) - Math.min(data.getLow(), prevClose);
        
        bps.addLast(bp);
        trs.addLast(tr);
        
        if (bps.size() > 28) {
            bps.removeFirst();
            trs.removeFirst();
        }
        
        previousClose.put(symbol, data.getClose());
        
        if (bps.size() < 28) {
            return TradeSignal.neutral("Warming up Ultimate Oscillator");
        }
        
        Double[] bpArr = bps.toArray(new Double[0]);
        Double[] trArr = trs.toArray(new Double[0]);
        
        double avg7BP = 0, avg7TR = 0;
        double avg14BP = 0, avg14TR = 0;
        double avg28BP = 0, avg28TR = 0;
        
        for (int i = Math.max(0, bpArr.length - 7); i < bpArr.length; i++) {
            avg7BP += bpArr[i];
            avg7TR += trArr[i];
        }
        for (int i = Math.max(0, bpArr.length - 14); i < bpArr.length; i++) {
            avg14BP += bpArr[i];
            avg14TR += trArr[i];
        }
        for (int i = 0; i < bpArr.length; i++) {
            avg28BP += bpArr[i];
            avg28TR += trArr[i];
        }
        
        double uo = 100 * ((4 * (avg7BP / avg7TR)) + (2 * (avg14BP / avg14TR)) + (avg28BP / avg28TR)) / 7;
        
        int position = portfolio.getPosition(symbol);
        
        if (uo > 70 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Ultimate Oscillator overbought (%.1f)", uo));
        } else if (uo < 30 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Ultimate Oscillator oversold (%.1f)", uo));
        }
        
        return TradeSignal.neutral(String.format("UO=%.1f", uo));
    }

    @Override
    public String getName() {
        return "Ultimate Oscillator";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        bpHistory.clear();
        trHistory.clear();
        previousClose.clear();
    }
}
