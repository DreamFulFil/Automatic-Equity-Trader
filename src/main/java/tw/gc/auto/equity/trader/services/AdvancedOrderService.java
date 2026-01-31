package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AdvancedOrderService - OCO, trailing stop, and bracket order support.
 *
 * Maintains in-memory order state and evaluates triggers on price updates.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedOrderService {

    private final OrderExecutionService orderExecutionService;
    private final TradingStateService tradingStateService;

    private final Map<String, ManagedOrder> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public enum PositionSide {
        LONG,
        SHORT
    }

    public record OcoRequest(
            String symbol,
            PositionSide side,
            double takeProfitPrice,
            double stopLossPrice
    ) {}

    public record TrailingStopRequest(
            String symbol,
            PositionSide side,
            double trailPercent,
            double referencePrice
    ) {}

    public record BracketOrderRequest(
            String symbol,
            PositionSide side,
            double entryPrice,
            double takeProfitPrice,
            double stopLossPrice
    ) {}

    public String placeOcoOrder(OcoRequest request) {
        validateRequest(request.symbol(), request.side());
        if (request.takeProfitPrice() <= 0.0 || request.stopLossPrice() <= 0.0) {
            throw new IllegalArgumentException("OCO prices must be positive");
        }

        String id = nextId("OCO");
        orders.put(id, new OcoOrder(
                id,
                request.symbol(),
                request.side(),
                request.takeProfitPrice(),
                request.stopLossPrice(),
                LocalDateTime.now()
        ));
        log.info("📌 OCO order registered: {} {}", request.symbol(), id);
        return id;
    }

    public String placeTrailingStopOrder(TrailingStopRequest request) {
        validateRequest(request.symbol(), request.side());
        if (request.trailPercent() <= 0.0 || request.trailPercent() >= 1.0) {
            throw new IllegalArgumentException("Trail percent must be between 0 and 1");
        }
        if (request.referencePrice() <= 0.0) {
            throw new IllegalArgumentException("Reference price must be positive");
        }

        String id = nextId("TRAIL");
        orders.put(id, new TrailingStopOrder(
                id,
                request.symbol(),
                request.side(),
                request.trailPercent(),
                request.referencePrice(),
                request.referencePrice(),
                LocalDateTime.now()
        ));
        log.info("📌 Trailing stop registered: {} {}", request.symbol(), id);
        return id;
    }

    public String placeBracketOrder(BracketOrderRequest request) {
        validateRequest(request.symbol(), request.side());
        if (request.entryPrice() <= 0.0 || request.takeProfitPrice() <= 0.0 || request.stopLossPrice() <= 0.0) {
            throw new IllegalArgumentException("Bracket prices must be positive");
        }

        String id = nextId("BRACKET");
        orders.put(id, new BracketOrder(
                id,
                request.symbol(),
                request.side(),
                request.entryPrice(),
                request.takeProfitPrice(),
                request.stopLossPrice(),
                LocalDateTime.now()
        ));
        log.info("📌 Bracket order registered: {} {}", request.symbol(), id);
        return id;
    }

    public boolean cancelOrder(String orderId) {
        return orders.remove(orderId) != null;
    }

    public int cancelAllForSymbol(String symbol) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ManagedOrder> entry : orders.entrySet()) {
            if (entry.getValue().symbol().equals(symbol)) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(orders::remove);
        return toRemove.size();
    }

    /**
     * Evaluate all advanced orders against the latest price.
     */
    public void evaluateOrders(String symbol, double price) {
        if (symbol == null || price <= 0.0) {
            return;
        }

        List<String> triggered = new ArrayList<>();
        for (ManagedOrder order : orders.values()) {
            if (!order.symbol().equals(symbol)) {
                continue;
            }

            if (order instanceof OcoOrder oco) {
                if (shouldTriggerTakeProfit(oco.side(), price, oco.takeProfitPrice())) {
                    executeExit(symbol, "OCO take profit", triggered, oco.id());
                } else if (shouldTriggerStopLoss(oco.side(), price, oco.stopLossPrice())) {
                    executeExit(symbol, "OCO stop loss", triggered, oco.id());
                }
            } else if (order instanceof TrailingStopOrder trailing) {
                TrailingStopOrder updated = updateTrailingStop(trailing, price);
                orders.put(updated.id(), updated);
                if (shouldTriggerStopLoss(updated.side(), price, updated.stopPrice())) {
                    executeExit(symbol, "Trailing stop", triggered, updated.id());
                }
            } else if (order instanceof BracketOrder bracket) {
                if (shouldTriggerTakeProfit(bracket.side(), price, bracket.takeProfitPrice())) {
                    executeExit(symbol, "Bracket take profit", triggered, bracket.id());
                } else if (shouldTriggerStopLoss(bracket.side(), price, bracket.stopLossPrice())) {
                    executeExit(symbol, "Bracket stop loss", triggered, bracket.id());
                }
            }
        }

        triggered.forEach(orders::remove);
    }

    private void executeExit(String symbol, String reason, List<String> triggered, String orderId) {
        orderExecutionService.flattenPosition(reason, symbol, tradingStateService.getTradingMode(), tradingStateService.isEmergencyShutdown());
        triggered.add(orderId);
    }

    private TrailingStopOrder updateTrailingStop(TrailingStopOrder order, double price) {
        double peak = order.peakPrice();
        if (order.side() == PositionSide.LONG) {
            peak = Math.max(order.peakPrice(), price);
        } else {
            peak = Math.min(order.peakPrice(), price);
        }

        double stopPrice = order.side() == PositionSide.LONG
                ? peak * (1.0 - order.trailPercent())
                : peak * (1.0 + order.trailPercent());

        return order.withUpdatedPeak(peak, stopPrice);
    }

    private boolean shouldTriggerTakeProfit(PositionSide side, double price, double target) {
        return side == PositionSide.LONG ? price >= target : price <= target;
    }

    private boolean shouldTriggerStopLoss(PositionSide side, double price, double stop) {
        return side == PositionSide.LONG ? price <= stop : price >= stop;
    }

    private void validateRequest(String symbol, PositionSide side) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must be provided");
        }
    }

    private String nextId(String prefix) {
        return prefix + "-" + idGenerator.getAndIncrement();
    }

    private sealed interface ManagedOrder permits OcoOrder, TrailingStopOrder, BracketOrder {
        String id();
        String symbol();
    }

    private record OcoOrder(
            String id,
            String symbol,
            PositionSide side,
            double takeProfitPrice,
            double stopLossPrice,
            LocalDateTime createdAt
    ) implements ManagedOrder {}

    private record TrailingStopOrder(
            String id,
            String symbol,
            PositionSide side,
            double trailPercent,
            double referencePrice,
            double peakPrice,
            LocalDateTime createdAt
    ) implements ManagedOrder {
        double stopPrice() {
            return side == PositionSide.LONG
                    ? peakPrice * (1.0 - trailPercent)
                    : peakPrice * (1.0 + trailPercent);
        }

        TrailingStopOrder withUpdatedPeak(double newPeak, double stopPrice) {
            return new TrailingStopOrder(id, symbol, side, trailPercent, referencePrice, newPeak, createdAt);
        }
    }

    private record BracketOrder(
            String id,
            String symbol,
            PositionSide side,
            double entryPrice,
            double takeProfitPrice,
            double stopLossPrice,
            LocalDateTime createdAt
    ) implements ManagedOrder {}
}