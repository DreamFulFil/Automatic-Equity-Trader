package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.OrderBookData;
import tw.gc.auto.equity.trader.repositories.OrderBookDataRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fetching and managing order book (Level 2) data.
 * 
 * <h3>Data Sources:</h3>
 * <ul>
 *   <li>Primary: Shioaji L2 BidAsk subscription via Python bridge</li>
 *   <li>Fallback: Synthetic spread calculation from tick data</li>
 * </ul>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Real-time order book snapshots</li>
 *   <li>In-memory caching with configurable TTL</li>
 *   <li>Spread and imbalance calculations</li>
 *   <li>Historical persistence for analysis</li>
 * </ul>
 * 
 * @since 2026-01-27 - Phase 5 Data Improvement Plan
 */
@Slf4j
@Service
public class OrderBookService {

    private final OrderBookDataRepository repository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${python.bridge.url:http://localhost:8888}")
    private String bridgeUrl;
    
    @Value("${orderbook.cache.ttl.seconds:5}")
    private int cacheTtlSeconds;
    
    @Value("${orderbook.persistence.enabled:false}")
    private boolean persistenceEnabled;
    
    // In-memory cache for real-time access
    private final Map<String, CachedOrderBook> cache = new ConcurrentHashMap<>();
    
    @Autowired
    public OrderBookService(OrderBookDataRepository repository) {
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    // Constructor for testing
    OrderBookService(OrderBookDataRepository repository, String bridgeUrl) {
        this.repository = repository;
        this.bridgeUrl = bridgeUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.cacheTtlSeconds = 5;
        this.persistenceEnabled = false;
    }

    /**
     * Get the latest order book for a symbol.
     * 
     * @param symbol Stock/futures symbol (e.g., "2454.TW")
     * @return Optional containing order book data
     */
    public Optional<OrderBookData> getLatest(String symbol) {
        // Check cache first
        CachedOrderBook cached = cache.get(symbol);
        if (cached != null && !cached.isExpired(cacheTtlSeconds)) {
            return Optional.of(cached.data);
        }
        
        // Fetch from bridge
        try {
            OrderBookData data = fetchFromBridge(symbol);
            if (data != null) {
                cache.put(symbol, new CachedOrderBook(data));
                if (persistenceEnabled) {
                    repository.save(data);
                }
                return Optional.of(data);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch order book for {}: {}", symbol, e.getMessage());
        }
        
        // Fallback to database
        return repository.findFirstBySymbolOrderByTimestampDesc(symbol);
    }

    /**
     * Get order book history for a symbol.
     * 
     * @param symbol Stock/futures symbol
     * @param minutes Number of minutes of history
     * @return List of historical snapshots
     */
    public List<OrderBookData> getHistory(String symbol, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return repository.findRecentBySymbol(symbol, since);
    }

    /**
     * Get snapshots with buy pressure.
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Minimum imbalance ratio
     * @param minutes Number of minutes to look back
     * @return List of snapshots
     */
    public List<OrderBookData> getWithBuyPressure(String symbol, double threshold, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return repository.findWithBuyPressure(symbol, threshold, since);
    }

    /**
     * Get snapshots with sell pressure.
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Maximum imbalance ratio (will be negated)
     * @param minutes Number of minutes to look back
     * @return List of snapshots
     */
    public List<OrderBookData> getWithSellPressure(String symbol, double threshold, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return repository.findWithSellPressure(symbol, -Math.abs(threshold), since);
    }

    /**
     * Calculate average spread over a time period.
     * 
     * @param symbol Stock/futures symbol
     * @param minutes Number of minutes
     * @return Average spread in basis points, or null
     */
    public Double getAverageSpread(String symbol, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return repository.calculateAverageSpread(symbol, since);
    }

    /**
     * Calculate average imbalance over a time period.
     * 
     * @param symbol Stock/futures symbol
     * @param minutes Number of minutes
     * @return Average imbalance ratio, or null
     */
    public Double getAverageImbalance(String symbol, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return repository.calculateAverageImbalance(symbol, since);
    }

    /**
     * Check if order book data is available for a symbol.
     * 
     * @param symbol Stock/futures symbol
     * @return true if data is available
     */
    public boolean hasData(String symbol) {
        return cache.containsKey(symbol) || repository.existsBySymbol(symbol);
    }

    /**
     * Update order book data (called from real-time feed).
     * 
     * @param symbol Symbol
     * @param data Order book data
     */
    public void updateOrderBook(String symbol, OrderBookData data) {
        data.setTimestamp(LocalDateTime.now());
        data.setSymbol(symbol);
        
        // Update cache
        cache.put(symbol, new CachedOrderBook(data));
        
        // Persist if enabled
        if (persistenceEnabled) {
            repository.save(data);
        }
        
        log.debug("Updated order book for {}: spread={}bps, imbalance={}", 
                symbol, data.getSpreadBps(), data.getImbalance());
    }

    /**
     * Clear cache for a symbol.
     * 
     * @param symbol Symbol to clear, or null for all
     */
    public void clearCache(String symbol) {
        if (symbol == null) {
            cache.clear();
        } else {
            cache.remove(symbol);
        }
    }

    /**
     * Fetch order book from Python bridge.
     */
    private OrderBookData fetchFromBridge(String symbol) {
        try {
            String url = bridgeUrl + "/orderbook/" + symbol;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseOrderBookResponse(response.body(), symbol);
            } else {
                log.debug("Bridge returned {} for order book {}", response.statusCode(), symbol);
            }
        } catch (Exception e) {
            log.debug("Failed to fetch order book from bridge: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Parse order book JSON response from Python bridge.
     */
    private OrderBookData parseOrderBookResponse(String json, String symbol) {
        try {
            JsonNode root = objectMapper.readTree(json);
            
            OrderBookData.OrderBookDataBuilder builder = OrderBookData.builder()
                    .symbol(symbol)
                    .timestamp(LocalDateTime.now());
            
            // Parse bids
            JsonNode bids = root.path("bids");
            if (bids.isArray()) {
                for (int i = 0; i < Math.min(bids.size(), 5); i++) {
                    JsonNode bid = bids.get(i);
                    double price = bid.path("price").asDouble();
                    long volume = bid.path("volume").asLong();
                    setBidLevel(builder, i + 1, price, volume);
                }
            }
            
            // Parse asks
            JsonNode asks = root.path("asks");
            if (asks.isArray()) {
                for (int i = 0; i < Math.min(asks.size(), 5); i++) {
                    JsonNode ask = asks.get(i);
                    double price = ask.path("price").asDouble();
                    long volume = ask.path("volume").asLong();
                    setAskLevel(builder, i + 1, price, volume);
                }
            }
            
            OrderBookData data = builder.build();
            
            // Pre-calculate metrics
            data.setSpread(data.getSpread());
            data.setSpreadBps(data.getSpreadBps());
            data.setMidPrice(data.getMidPrice());
            data.setTotalBidVolume(data.getTotalBidVolume());
            data.setTotalAskVolume(data.getTotalAskVolume());
            data.setImbalance(data.getImbalance());
            
            return data;
        } catch (Exception e) {
            log.error("Failed to parse order book response: {}", e.getMessage());
            return null;
        }
    }

    private void setBidLevel(OrderBookData.OrderBookDataBuilder builder, int level, 
                             double price, long volume) {
        switch (level) {
            case 1 -> builder.bidPrice1(price).bidVolume1(volume);
            case 2 -> builder.bidPrice2(price).bidVolume2(volume);
            case 3 -> builder.bidPrice3(price).bidVolume3(volume);
            case 4 -> builder.bidPrice4(price).bidVolume4(volume);
            case 5 -> builder.bidPrice5(price).bidVolume5(volume);
        }
    }

    private void setAskLevel(OrderBookData.OrderBookDataBuilder builder, int level, 
                             double price, long volume) {
        switch (level) {
            case 1 -> builder.askPrice1(price).askVolume1(volume);
            case 2 -> builder.askPrice2(price).askVolume2(volume);
            case 3 -> builder.askPrice3(price).askVolume3(volume);
            case 4 -> builder.askPrice4(price).askVolume4(volume);
            case 5 -> builder.askPrice5(price).askVolume5(volume);
        }
    }

    /**
     * Cleanup old order book data (run daily at 3 AM).
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldData() {
        if (!persistenceEnabled) {
            return;
        }
        
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            long deleted = repository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old order book records", deleted);
        } catch (Exception e) {
            log.error("Failed to cleanup old order book data", e);
        }
    }

    /**
     * Simple cache entry with timestamp.
     */
    private static class CachedOrderBook {
        final OrderBookData data;
        final LocalDateTime timestamp;
        
        CachedOrderBook(OrderBookData data) {
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }
        
        boolean isExpired(int ttlSeconds) {
            return LocalDateTime.now().isAfter(timestamp.plusSeconds(ttlSeconds));
        }
    }
}
