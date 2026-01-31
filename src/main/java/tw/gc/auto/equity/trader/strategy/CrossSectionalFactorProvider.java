package tw.gc.auto.equity.trader.strategy;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CrossSectionalFactorProvider {

    Optional<CrossSectionalFactorScore> getMomentumScore(String symbol);

    Optional<LocalDateTime> getSnapshotTime();
}

