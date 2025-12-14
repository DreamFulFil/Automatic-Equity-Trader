package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AccumulationDistributionStrategy implements IStrategy {
    
    private final Map<String, Double> adLine = new HashMap<>();
    private final Map<String, Double> previousAD = new HashMap<>();
    
    public AccumulationDistributionStrategy() {
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        
        double clv = ((data.getClose() - data.getLow()) - (data.getHigh() - data.getClose())) / 
                     (data.getHigh() - data.getLow() + 0.0001);
        double adValue = clv * data.getVolume();
        
        double currentAD = adLine.getOrDefault(symbol, 0.0) + adValue;
        Double prevAD = previousAD.get(symbol);
        
        adLine.put(symbol, currentAD);
        previousAD.put(symbol, currentAD);
        
        if (prevAD == null) {
            return TradeSignal.neutral("Initializing A/D");
        }
        
        int position = portfolio.getPosition(symbol);
        
        if (currentAD > prevAD && data.getClose() > data.getOpen() && position <= 0) {
            return TradeSignal.longSignal(0.75, "A/D Line accumulation");
        } else if (currentAD < prevAD && data.getClose() < data.getOpen() && position >= 0) {
            return TradeSignal.shortSignal(0.75, "A/D Line distribution");
        }
        
        return TradeSignal.neutral("A/D Line neutral");
    }

    @Override
    public String getName() {
        return "Accumulation/Distribution";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        adLine.clear();
        previousAD.clear();
    }
}
