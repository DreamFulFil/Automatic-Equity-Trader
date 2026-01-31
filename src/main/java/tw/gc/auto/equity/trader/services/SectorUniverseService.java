package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.SectorData;
import tw.gc.auto.equity.trader.entities.StockUniverse;
import tw.gc.auto.equity.trader.repositories.SectorDataRepository;
import tw.gc.auto.equity.trader.repositories.StockUniverseRepository;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SectorUniverseService
 *
 * Maintains sector and industry mappings for the stock universe.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SectorUniverseService {

    private static final Path DEFAULT_SECTOR_PATH = Path.of("config", "sector-universe.json");

    private final SectorDataRepository sectorDataRepository;
    private final StockUniverseRepository stockUniverseRepository;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @Transactional
    public void loadDefaultSectorUniverse() {
        if (sectorDataRepository.count() > 0) {
            return;
        }

        List<SectorSeed> seeds = loadSeedsFromConfig().orElseGet(this::defaultSeeds);
        if (seeds.isEmpty()) {
            return;
        }

        for (SectorSeed seed : seeds) {
            upsertSector(seed.symbol(), seed.sector(), seed.industry(), seed.source());
        }

        log.info("✅ Seeded {} sector mappings", seeds.size());
    }

    public Optional<SectorData> findSectorData(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return sectorDataRepository.findBySymbol(normalized);
    }

    public Optional<String> findSector(String symbol) {
        String normalized = normalizeSymbol(symbol);
        Optional<SectorData> fromRepo = sectorDataRepository.findBySymbol(normalized);
        if (fromRepo.isPresent()) {
            return Optional.ofNullable(fromRepo.get().getSector());
        }

        Optional<StockUniverse> fallback = findStockUniverse(normalized);
        return fallback.map(StockUniverse::getSector);
    }

    @Transactional
    public SectorData upsertSector(String symbol, String sector, String industry, String source) {
        String normalized = normalizeSymbol(symbol);
        SectorData data = sectorDataRepository.findBySymbol(normalized)
            .orElseGet(() -> SectorData.builder()
                .symbol(normalized)
                .createdAt(LocalDateTime.now())
                .build());

        data.setSector(sector);
        data.setIndustry(industry);
        data.setSource(source);
        data.setEffectiveAt(LocalDateTime.now());

        return sectorDataRepository.save(data);
    }

    private Optional<List<SectorSeed>> loadSeedsFromConfig() {
        if (!Files.exists(DEFAULT_SECTOR_PATH)) {
            return Optional.empty();
        }

        try {
            SectorSeed[] seeds = objectMapper.readValue(DEFAULT_SECTOR_PATH.toFile(), SectorSeed[].class);
            return Optional.of(List.of(seeds));
        } catch (Exception e) {
            log.warn("Failed to read sector universe config: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<SectorSeed> defaultSeeds() {
        return List.of(
            new SectorSeed("2330", "Technology", "Semiconductors", "seed"),
            new SectorSeed("2454", "Technology", "Semiconductors", "seed"),
            new SectorSeed("2317", "Technology", "Electronics Manufacturing", "seed"),
            new SectorSeed("2303", "Technology", "Semiconductors", "seed"),
            new SectorSeed("3711", "Technology", "Semiconductor Packaging", "seed"),
            new SectorSeed("2412", "Communication Services", "Telecom", "seed"),
            new SectorSeed("2882", "Financials", "Banks", "seed"),
            new SectorSeed("2881", "Financials", "Banks", "seed"),
            new SectorSeed("2886", "Financials", "Banks", "seed"),
            new SectorSeed("2002", "Materials", "Steel", "seed")
        );
    }

    private Optional<StockUniverse> findStockUniverse(String normalized) {
        Optional<StockUniverse> exact = stockUniverseRepository.findBySymbol(normalized);
        if (exact.isPresent()) {
            return exact;
        }
        return stockUniverseRepository.findBySymbol(normalized + ".TW");
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

    private record SectorSeed(String symbol, String sector, String industry, String source) {
    }
}
