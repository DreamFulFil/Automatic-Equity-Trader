package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.Event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CorporateActionService - Adjusts positions and logs corporate actions.
 *
 * Supports:
 * - Cash dividends
 * - Stock splits
 * - Rights issues (placeholder for future expansion)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CorporateActionService {

    private final PositionManager positionManager;
    private final DataLoggingService dataLoggingService;

    public record CashDividendResult(
            String symbol,
            int position,
            double dividendPerShare,
            double totalDividend,
            LocalDate exDate
    ) {}

    public record SplitAdjustmentResult(
            String symbol,
            int oldQuantity,
            int newQuantity,
            double oldEntryPrice,
            double newEntryPrice,
            double splitRatio
    ) {}

    public record RightsIssueResult(
            String symbol,
            int position,
            double subscriptionPrice,
            double ratio,
            LocalDate exDate
    ) {}

    /**
     * Apply a cash dividend to current position.
     */
    public CashDividendResult applyCashDividend(String symbol, double dividendPerShare, LocalDate exDate) {
        Objects.requireNonNull(symbol, "symbol");
        if (dividendPerShare <= 0.0) {
            throw new IllegalArgumentException("Dividend per share must be positive");
        }

        int position = positionManager.getPosition(symbol);
        double totalDividend = position * dividendPerShare;

        if (position == 0) {
            log.info("No position for {} on dividend ex-date {}", symbol, exDate);
        }

        logEvent("DIVIDEND", symbol, String.format(
                "{\"dividendPerShare\":%.4f,\"position\":%d,\"totalDividend\":%.2f,\"exDate\":\"%s\"}",
                dividendPerShare, position, totalDividend, exDate));

        return new CashDividendResult(symbol, position, dividendPerShare, totalDividend, exDate);
    }

    /**
     * Apply a stock split by adjusting position size and entry price.
     */
    public SplitAdjustmentResult applyStockSplit(String symbol, int numerator, int denominator) {
        Objects.requireNonNull(symbol, "symbol");
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException("Split ratio must be positive");
        }

        int oldQuantity = positionManager.getPosition(symbol);
        double oldEntryPrice = positionManager.getEntryPrice(symbol);
        double ratio = (double) numerator / denominator;

        if (oldQuantity == 0) {
            log.info("No position for {} - split applied for record only", symbol);
            return new SplitAdjustmentResult(symbol, 0, 0, oldEntryPrice, oldEntryPrice, ratio);
        }

        int newQuantity = (int) Math.round(oldQuantity * ratio);
        double newEntryPrice = oldEntryPrice > 0.0 ? oldEntryPrice / ratio : oldEntryPrice;

        positionManager.setPosition(symbol, newQuantity);
        if (oldEntryPrice > 0.0) {
            positionManager.updateEntry(symbol, newEntryPrice, positionManager.getEntryTime(symbol));
        }

        logEvent("SPLIT", symbol, String.format(
                "{\"ratio\":%.4f,\"oldQuantity\":%d,\"newQuantity\":%d,\"oldEntryPrice\":%.4f,\"newEntryPrice\":%.4f}",
                ratio, oldQuantity, newQuantity, oldEntryPrice, newEntryPrice));

        return new SplitAdjustmentResult(symbol, oldQuantity, newQuantity, oldEntryPrice, newEntryPrice, ratio);
    }

    /**
     * Record a rights issue event (placeholder until subscription flow is automated).
     */
    public RightsIssueResult recordRightsIssue(String symbol, double subscriptionPrice, double ratio, LocalDate exDate) {
        Objects.requireNonNull(symbol, "symbol");
        if (subscriptionPrice <= 0.0 || ratio <= 0.0) {
            throw new IllegalArgumentException("Rights issue price and ratio must be positive");
        }

        int position = positionManager.getPosition(symbol);

        logEvent("RIGHTS", symbol, String.format(
                "{\"subscriptionPrice\":%.2f,\"ratio\":%.4f,\"position\":%d,\"exDate\":\"%s\"}",
                subscriptionPrice, ratio, position, exDate));

        return new RightsIssueResult(symbol, position, subscriptionPrice, ratio, exDate);
    }

    private void logEvent(String type, String symbol, String details) {
        dataLoggingService.logEvent(Event.builder()
                .timestamp(LocalDateTime.now())
                .type(Event.EventType.INFO)
                .severity(Event.EventSeverity.LOW)
                .category("CORPORATE_ACTION")
                .message(type + " action processed for " + symbol)
                .details(details)
                .component("CorporateActionService")
                .build());
    }
}