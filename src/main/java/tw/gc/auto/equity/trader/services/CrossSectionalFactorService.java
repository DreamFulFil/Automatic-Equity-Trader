package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.StockUniverse;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StockUniverseRepository;
import tw.gc.auto.equity.trader.strategy.CrossSectionalFactorProvider;
import tw.gc.auto.equity.trader.strategy.CrossSectionalFactorScore;
import tw.gc.auto.equity.trader.strategy.impl.CrossSectionalMomentumStrategy;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CrossSectionalFactorService
 *
 * Computes cross-sectional factor scores across the stock universe.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrossSectionalFactorService implements CrossSectionalFactorProvider {

    private static final int DEFAULT_LOOKBACK_DAYS = 60;

    private final MarketDataRepository marketDataRepository;
    private final StockUniverseRepository stockUniverseRepository;
    private final SectorUniverseService sectorUniverseService;

    private final AtomicReference<FactorSnapshot> latestSnapshot = new AtomicReference<>();

    @PostConstruct
    void registerProvider() {
        CrossSectionalMomentumStrategy.setFactorProvider(this);
    }

    @Override
    public Optional<CrossSectionalFactorScore> getMomentumScore(String symbol) {
        FactorSnapshot snapshot = latestSnapshot.get();
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.scores().get(normalizeSymbol(symbol)));
    }

    @Override
    public Optional<LocalDateTime> getSnapshotTime() {
        FactorSnapshot snapshot = latestSnapshot.get();
        return snapshot == null ? Optional.empty() : Optional.of(snapshot.asOf());
    }

    @Scheduled(cron = "0 15 18 * * MON-FRI", zone = "Asia/Taipei")
    @Transactional(readOnly = true)
    public void refreshDailyMomentumRanks() {
        refreshMomentumRanks(DEFAULT_LOOKBACK_DAYS);
    }

    @Transactional(readOnly = true)
    public FactorSnapshot refreshMomentumRanks(int lookbackDays) {
        List<StockUniverse> universe = stockUniverseRepository.findByEnabledTrueOrderBySelectionScoreDesc();
        if (universe.isEmpty()) {
            FactorSnapshot snapshot = new FactorSnapshot(LocalDateTime.now(), lookbackDays, Map.of());
            latestSnapshot.set(snapshot);
            return snapshot;
        }

        List<ScoreSeed> seeds = new ArrayList<>();
        for (StockUniverse stock : universe) {
            String symbol = normalizeSymbol(stock.getSymbol());
            List<MarketData> data = marketDataRepository.findRecentBySymbolAndTimeframe(
                stock.getSymbol(),
                MarketData.Timeframe.DAY_1,
                lookbackDays + 1
            );
            if (data.size() < 2) {
                continue;
            }
            List<MarketData> sortedData = new ArrayList<>(data);
            sortedData.sort(Comparator.comparing(MarketData::getTimestamp));
            double startPrice = sortedData.getFirst().getClose();
            double endPrice = sortedData.getLast().getClose();
            if (startPrice <= 0) {
                continue;
            }
            double momentum = (endPrice - startPrice) / startPrice;
            String sector = sectorUniverseService.findSector(symbol).orElse(null);
            seeds.add(new ScoreSeed(symbol, momentum, sector));
        }

        seeds.sort(Comparator.comparingDouble(ScoreSeed::momentum).reversed());
        int total = seeds.size();
        Map<String, CrossSectionalFactorScore> scores = new HashMap<>();

        for (int i = 0; i < total; i++) {
            ScoreSeed seed = seeds.get(i);
            int rank = i + 1;
            double percentile = total <= 1 ? 1.0 : 1.0 - (double) i / (double) (total - 1);
            scores.put(seed.symbol(), new CrossSectionalFactorScore(
                seed.momentum(),
                percentile,
                rank,
                total,
                seed.sector()
            ));
        }

        FactorSnapshot snapshot = new FactorSnapshot(LocalDateTime.now(), lookbackDays, Map.copyOf(scores));
        latestSnapshot.set(snapshot);
        log.info("📈 Cross-sectional momentum updated for {} symbols", total);
        return snapshot;
    }

    public Optional<FactorSnapshot> getLatestSnapshot() {
        return Optional.ofNullable(latestSnapshot.get());
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        String trimmed = symbol.trim().toUpperCase();
        if (trimmed.endsWith(".TW")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }

    private record ScoreSeed(String symbol, double momentum, String sector) {
    }

    public record FactorSnapshot(LocalDateTime asOf, int lookbackDays, Map<String, CrossSectionalFactorScore> scores) {
    }
}
