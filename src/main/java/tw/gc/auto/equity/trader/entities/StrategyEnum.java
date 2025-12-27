package tw.gc.auto.equity.trader.entities;

import tw.gc.auto.equity.trader.strategy.StrategyType;

/**
 * Enumeration of all implemented trading strategies.
 * Provides type-safe strategy identification and metadata.
 */
public enum StrategyEnum {
    // Long-term strategies
    DCA("DCA", "Dollar Cost Averaging", StrategyType.LONG_TERM, true, true),
    AUTOMATIC_REBALANCING("AutomaticRebalancing", "Portfolio Rebalancing", StrategyType.LONG_TERM, true, false),
    DIVIDEND_REINVESTMENT("DividendReinvestment", "DRIP Strategy", StrategyType.LONG_TERM, true, false),
    TAX_LOSS_HARVESTING("TaxLossHarvesting", "Tax-Loss Harvesting", StrategyType.LONG_TERM, true, false),
    
    // Short-term strategies
    MOVING_AVERAGE_CROSSOVER("MovingAverageCrossover", "MA Crossover", StrategyType.SHORT_TERM, true, true),
    BOLLINGER_BAND("BollingerBand", "Bollinger Bands", StrategyType.SHORT_TERM, true, true),
    RSI("RSI", "Relative Strength Index", StrategyType.SHORT_TERM, true, true),
    AGGRESSIVE_RSI("AggressiveRSI", "Aggressive RSI", StrategyType.SHORT_TERM, true, true),
    MACD("MACD", "Moving Average Convergence Divergence", StrategyType.SHORT_TERM, true, true),
    STOCHASTIC("Stochastic", "Stochastic Oscillator", StrategyType.SHORT_TERM, true, true),
    ATR_CHANNEL("ATRChannel", "ATR Channel Breakout", StrategyType.SHORT_TERM, true, true),
    PIVOT_POINT("PivotPoint", "Pivot Point Strategy", StrategyType.SHORT_TERM, true, true),
    MOMENTUM("Momentum", "Momentum Trading", StrategyType.SHORT_TERM, true, true),
    ARBITRAGE_PAIRS("ArbitragePairs", "Pairs Trading Arbitrage", StrategyType.SHORT_TERM, true, false),
    
    // Intraday strategies
    VWAP_EXECUTION("VWAPExecution", "VWAP Execution", StrategyType.INTRADAY, true, true),
    TWAP_EXECUTION("TWAPExecution", "TWAP Execution", StrategyType.INTRADAY, true, true),
    
    // News/sentiment strategies
    NEWS_SENTIMENT("NewsSentiment", "News Sentiment Analysis", StrategyType.SHORT_TERM, true, false);
    
    private final String className;
    private final String displayName;
    private final StrategyType type;
    private final boolean supportsStock;
    private final boolean supportsFutures;
    
    StrategyEnum(String className, String displayName, StrategyType type, 
                 boolean supportsStock, boolean supportsFutures) {
        this.className = className;
        this.displayName = displayName;
        this.type = type;
        this.supportsStock = supportsStock;
        this.supportsFutures = supportsFutures;
    }
    
    public String getClassName() {
        return className;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public StrategyType getType() {
        return type;
    }
    
    public boolean supportsStock() {
        return supportsStock;
    }
    
    public boolean supportsFutures() {
        return supportsFutures;
    }
    
    /**
     * Check if strategy is compatible with given trading mode
     */
    public boolean isCompatibleWith(TradingModeEnum mode) {
        if (mode.includesStock() && supportsStock) {
            return true;
        }
        if (mode.includesFutures() && supportsFutures) {
            return true;
        }
        return false;
    }
    
    /**
     * Parse from class name (e.g., "MovingAverageCrossover", "DCA")
     */
    public static StrategyEnum fromClassName(String className) {
        for (StrategyEnum strategy : values()) {
            if (strategy.className.equalsIgnoreCase(className)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown strategy class name: " + className);
    }
    
    /**
     * Parse from display name
     */
    public static StrategyEnum fromDisplayName(String displayName) {
        for (StrategyEnum strategy : values()) {
            if (strategy.displayName.equalsIgnoreCase(displayName)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown strategy display name: " + displayName);
    }
}
