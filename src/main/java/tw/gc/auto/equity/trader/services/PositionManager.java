package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class PositionManager {

    // Position tracking per symbol
    private final Map<String, AtomicInteger> positions = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> entryPrices = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<LocalDateTime>> positionEntryTimes = new ConcurrentHashMap<>();

    public AtomicInteger positionFor(String symbol) {
        return positions.computeIfAbsent(symbol, k -> new AtomicInteger(0));
    }

    public AtomicReference<Double> entryPriceFor(String symbol) {
        return entryPrices.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0));
    }

    public AtomicReference<LocalDateTime> entryTimeFor(String symbol) {
        return positionEntryTimes.computeIfAbsent(symbol, k -> new AtomicReference<>(null));
    }

    public int getPosition(String symbol) {
        return positionFor(symbol).get();
    }

    public double getEntryPrice(String symbol) {
        return entryPriceFor(symbol).get();
    }

    public LocalDateTime getEntryTime(String symbol) {
        return entryTimeFor(symbol).get();
    }

    public void updatePosition(String symbol, int quantityChange) {
        positionFor(symbol).addAndGet(quantityChange);
    }
    
    public void setPosition(String symbol, int quantity) {
        positionFor(symbol).set(quantity);
    }

    public void updateEntry(String symbol, double price, LocalDateTime time) {
        entryPriceFor(symbol).set(price);
        entryTimeFor(symbol).set(time);
    }

    public void clearEntry(String symbol) {
        entryTimeFor(symbol).set(null);
    }
}
