package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.EconomicNews;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.OrderBookData;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeContextService {

    private static final int REGIME_LOOKBACK_LIMIT = 240;
    private static final int NEWS_LOOKBACK_HOURS = 24;
    private static final int NEWS_HEADLINE_LIMIT = 5;

    private final MarketRegimeService marketRegimeService;
    private final MarketDataRepository marketDataRepository;
    private final OrderBookService orderBookService;
    private final NewsService newsService;
    private final ObjectMapper objectMapper;

    public record TradeContextSnapshot(
            Map<String, Object> alternativeSignals,
            Double expectedPnl,
            Double executedSlippage,
            Map<String, Object> newsContext
    ) {
        public static TradeContextSnapshot empty() {
            return new TradeContextSnapshot(null, null, null, null);
        }
    }

    public Trade enrichTradeContext(Trade trade) {
        return enrichTradeContext(trade, TradeContextSnapshot.empty());
    }

    public Trade enrichTradeContext(Trade trade, TradeContextSnapshot snapshot) {
        if (trade == null || trade.getSymbol() == null || trade.getSymbol().isBlank()) {
            return trade;
        }

        if (snapshot != null) {
            if (snapshot.expectedPnl() != null && trade.getExpectedPnl() == null) {
                trade.setExpectedPnl(snapshot.expectedPnl());
            }
            if (snapshot.executedSlippage() != null && trade.getExecutedSlippage() == null) {
                trade.setExecutedSlippage(snapshot.executedSlippage());
            }
            if (snapshot.alternativeSignals() != null && trade.getAlternativeSignalsJson() == null) {
                trade.setAlternativeSignalsJson(serializeJson(snapshot.alternativeSignals()));
            }
            if (snapshot.newsContext() != null && trade.getNewsContextJson() == null) {
                trade.setNewsContextJson(serializeJson(snapshot.newsContext()));
            }
        }

        enrichMarketContext(trade);
        enrichOrderBookContext(trade);
        enrichNewsContext(trade);

        return trade;
    }

    private void enrichMarketContext(Trade trade) {
        if (trade.getMarketRegimeAtEntry() != null && trade.getVolatilityAtEntry() != null) {
            return;
        }

        List<MarketData> history = loadMarketHistory(trade.getSymbol());
        if (history.isEmpty()) {
            return;
        }

        MarketRegimeService.RegimeAnalysis analysis = marketRegimeService.analyzeRegime(trade.getSymbol(), history);
        if (trade.getMarketRegimeAtEntry() == null && analysis != null && analysis.regime() != null) {
            trade.setMarketRegimeAtEntry(analysis.regime().name());
        }
        if (trade.getVolatilityAtEntry() == null && analysis != null) {
            trade.setVolatilityAtEntry(analysis.volatility());
        }
    }

    private void enrichOrderBookContext(Trade trade) {
        if (trade.getOrderBookSpreadAtEntry() != null) {
            return;
        }

        Optional<OrderBookData> orderBook = orderBookService.getLatest(trade.getSymbol());
        orderBook.map(OrderBookData::getSpreadBps)
                .ifPresent(trade::setOrderBookSpreadAtEntry);
    }

    private void enrichNewsContext(Trade trade) {
        if (trade.getNewsContextJson() != null) {
            return;
        }

        try {
            List<EconomicNews> recentNews = newsService.getRecentNews(trade.getSymbol(), NEWS_LOOKBACK_HOURS);
            if (recentNews == null) {
                recentNews = List.of();
            }
            Double aggregateSentiment = newsService.getAggregateSentiment(trade.getSymbol());

            List<Map<String, Object>> headlines = recentNews.stream()
                    .sorted((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()))
                    .limit(NEWS_HEADLINE_LIMIT)
                    .map(this::toHeadlinePayload)
                    .toList();

            Map<String, Object> payload = new HashMap<>();
            payload.put("symbol", trade.getSymbol());
            payload.put("aggregateSentiment", aggregateSentiment);
            payload.put("newsCount", recentNews.size());
            payload.put("lookbackHours", NEWS_LOOKBACK_HOURS);
            payload.put("headlines", headlines);

            trade.setNewsContextJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Unable to enrich news context for {}: {}", trade.getSymbol(), e.getMessage());
        }
    }

    private Map<String, Object> toHeadlinePayload(EconomicNews news) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("headline", news.getHeadline());
        payload.put("source", news.getSource());
        payload.put("sentimentScore", news.getSentimentScore());
        payload.put("impactLevel", news.getImpactLevel());
        payload.put("isBreaking", news.getIsBreaking());
        OffsetDateTime publishedAt = news.getPublishedAt();
        payload.put("publishedAt", publishedAt != null ? publishedAt.toString() : null);
        return payload;
    }

    private List<MarketData> loadMarketHistory(String symbol) {
        List<MarketData> history = marketDataRepository.findRecentBySymbolAndTimeframe(
                symbol, MarketData.Timeframe.MIN_5, REGIME_LOOKBACK_LIMIT);
        if (history == null || history.isEmpty()) {
            history = marketDataRepository.findRecentBySymbolAndTimeframe(
                    symbol, MarketData.Timeframe.DAY_1, REGIME_LOOKBACK_LIMIT);
        }
        if (history == null) {
            return List.of();
        }
        List<MarketData> ordered = new java.util.ArrayList<>(history);
        Collections.reverse(ordered);
        return ordered;
    }

    private String serializeJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize trade context payload: {}", e.getMessage());
            return null;
        }
    }
}
