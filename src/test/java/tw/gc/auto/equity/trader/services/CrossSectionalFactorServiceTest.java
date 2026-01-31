package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.StockUniverse;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StockUniverseRepository;
import tw.gc.auto.equity.trader.strategy.CrossSectionalFactorScore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossSectionalFactorServiceTest {

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private StockUniverseRepository stockUniverseRepository;

    @Mock
    private SectorUniverseService sectorUniverseService;

    @InjectMocks
    private CrossSectionalFactorService factorService;

    @Test
    void refreshMomentumRanks_shouldRankByMomentum() {
        StockUniverse stockA = StockUniverse.builder().symbol("AAA").build();
        StockUniverse stockB = StockUniverse.builder().symbol("BBB").build();

        when(stockUniverseRepository.findByEnabledTrueOrderBySelectionScoreDesc())
            .thenReturn(List.of(stockA, stockB));

        when(sectorUniverseService.findSector("AAA")).thenReturn(Optional.of("Tech"));
        when(sectorUniverseService.findSector("BBB")).thenReturn(Optional.of("Finance"));

        when(marketDataRepository.findRecentBySymbolAndTimeframe("AAA", MarketData.Timeframe.DAY_1, 3))
            .thenReturn(List.of(
                MarketData.builder().timestamp(LocalDateTime.now().minusDays(2)).close(100).build(),
                MarketData.builder().timestamp(LocalDateTime.now()).close(110).build()
            ));

        when(marketDataRepository.findRecentBySymbolAndTimeframe("BBB", MarketData.Timeframe.DAY_1, 3))
            .thenReturn(List.of(
                MarketData.builder().timestamp(LocalDateTime.now().minusDays(2)).close(100).build(),
                MarketData.builder().timestamp(LocalDateTime.now()).close(90).build()
            ));

        factorService.refreshMomentumRanks(2);

        CrossSectionalFactorScore scoreA = factorService.getMomentumScore("AAA").orElseThrow();
        CrossSectionalFactorScore scoreB = factorService.getMomentumScore("BBB").orElseThrow();

        assertThat(scoreA.rank()).isEqualTo(1);
        assertThat(scoreB.rank()).isEqualTo(2);
        assertThat(scoreA.percentile()).isGreaterThan(scoreB.percentile());
        assertThat(scoreA.sector()).isEqualTo("Tech");
    }
}
