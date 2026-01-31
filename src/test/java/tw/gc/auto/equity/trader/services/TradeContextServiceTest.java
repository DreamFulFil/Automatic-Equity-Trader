package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.EconomicNews;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.OrderBookData;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeContextServiceTest {

    @Mock
    private MarketRegimeService marketRegimeService;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private OrderBookService orderBookService;

    @Mock
    private NewsService newsService;

    private TradeContextService tradeContextService;

    @BeforeEach
    void setUp() {
        tradeContextService = new TradeContextService(
                marketRegimeService,
                marketDataRepository,
                orderBookService,
                newsService,
                new ObjectMapper()
        );
    }

    @Test
    void enrichTradeContext_populatesMarketOrderBookAndNews() {
        Trade trade = Trade.builder()
                .symbol("2454.TW")
                .entryPrice(1000.0)
                .build();

        List<MarketData> history = List.of(MarketData.builder()
                .symbol("2454.TW")
                .timestamp(LocalDateTime.now().minusMinutes(5))
                .timeframe(MarketData.Timeframe.MIN_5)
                .open(990.0)
                .high(1010.0)
                .low(980.0)
                .close(1000.0)
                .volume(1000L)
                .build());

        when(marketDataRepository.findRecentBySymbolAndTimeframe("2454.TW", MarketData.Timeframe.MIN_5, 240))
                .thenReturn(history);
        when(marketRegimeService.analyzeRegime(eq("2454.TW"), anyList()))
                .thenReturn(new MarketRegimeService.RegimeAnalysis(
                        "2454.TW",
                        MarketRegimeService.MarketRegime.TRENDING_UP,
                        0.75,
                        30.0,
                        25.0,
                        10.0,
                        28.5,
                        1000.0,
                        900.0,
                        0.05,
                        LocalDateTime.now(),
                        "trend"
                ));

        OrderBookData orderBookData = OrderBookData.builder()
                .symbol("2454.TW")
                .spreadBps(12.5)
                .build();
        when(orderBookService.getLatest("2454.TW")).thenReturn(Optional.of(orderBookData));

        EconomicNews news = EconomicNews.builder()
                .headline("Earnings beat expectations")
                .source("Yahoo Finance")
                .sentimentScore(0.4)
                .impactLevel("high")
                .isBreaking(false)
                .publishedAt(OffsetDateTime.now().minusHours(2))
                .build();
        when(newsService.getRecentNews("2454.TW", 24)).thenReturn(List.of(news));
        when(newsService.getAggregateSentiment("2454.TW")).thenReturn(0.3);

        Trade enriched = tradeContextService.enrichTradeContext(trade);

        assertEquals("TRENDING_UP", enriched.getMarketRegimeAtEntry());
        assertEquals(28.5, enriched.getVolatilityAtEntry());
        assertEquals(12.5, enriched.getOrderBookSpreadAtEntry());
        assertNotNull(enriched.getNewsContextJson());
    }

    @Test
    void enrichTradeContext_withoutSymbol_returnsUnchanged() {
        Trade trade = Trade.builder().build();

        Trade enriched = tradeContextService.enrichTradeContext(trade);

        assertNull(enriched.getMarketRegimeAtEntry());
        assertNull(enriched.getNewsContextJson());
    }
}
