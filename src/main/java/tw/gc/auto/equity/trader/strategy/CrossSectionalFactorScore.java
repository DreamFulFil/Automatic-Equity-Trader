package tw.gc.auto.equity.trader.strategy;

public record CrossSectionalFactorScore(
    double momentum,
    double percentile,
    int rank,
    int total,
    String sector
) {
}
