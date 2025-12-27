package tw.gc.mtxfbot.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.mtxfbot.entities.*;
import tw.gc.mtxfbot.repositories.EventRepository;
import tw.gc.mtxfbot.repositories.SignalRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

import java.time.LocalDateTime;

/**
 * DataLoggingService - Centralized service for logging all bot activities.
 * Handles trades, signals, events, and provides data for analysis and backtesting.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataLoggingService {

    private final TradeRepository tradeRepository;
    private final SignalRepository signalRepository;
    private final EventRepository eventRepository;

    /**
     * Log a trade execution
     */
    @Transactional
    public Trade logTrade(Trade trade) {
        log.info("📊 Logging trade: {} {} {} @ {}", trade.getAction(), trade.getQuantity(),
                trade.getSymbol(), trade.getEntryPrice());
        return tradeRepository.save(trade);
    }

    /**
     * Log a trading signal
     */
    @Transactional
    public Signal logSignal(Signal signal) {
        log.debug("📊 Logging signal: {} confidence={}", signal.getDirection(), signal.getConfidence());
        return signalRepository.save(signal);
    }

    /**
     * Log a bot event
     */
    @Transactional
    public Event logEvent(Event event) {
        log.debug("📊 Logging event: {} - {}", event.getType(), event.getMessage());
        return eventRepository.save(event);
    }

    /**
     * Log API call with timing
     */
    public Event logApiCall(String component, String endpoint, long responseTimeMs, boolean success, String errorDetails) {
        Event.EventType type = success ? Event.EventType.SUCCESS : Event.EventType.ERROR;
        Event.EventSeverity severity = success ? Event.EventSeverity.LOW : Event.EventSeverity.MEDIUM;

        return logEvent(Event.builder()
                .timestamp(LocalDateTime.now())
                .type(type)
                .severity(severity)
                .category("API")
                .message(success ? "API call successful" : "API call failed")
                .details(String.format("{\"endpoint\":\"%s\",\"responseTimeMs\":%d,\"error\":\"%s\"}",
                        endpoint, responseTimeMs, errorDetails != null ? errorDetails : ""))
                .component(component)
                .responseTimeMs(responseTimeMs)
                .errorCode(success ? null : "API_ERROR")
                .build());
    }

    /**
     * Log Telegram command
     */
    public Event logTelegramCommand(String userId, String command, String details) {
        return logEvent(Event.builder()
                .timestamp(LocalDateTime.now())
                .type(Event.EventType.COMMAND)
                .severity(Event.EventSeverity.LOW)
                .category("TELEGRAM")
                .message("Telegram command received")
                .details(String.format("{\"command\":\"%s\",\"details\":\"%s\"}", command, details))
                .component("TelegramService")
                .userId(userId)
                .build());
    }

    /**
     * Log news veto event
     */
    public Event logNewsVeto(String reason, double score) {
        return logEvent(Event.builder()
                .timestamp(LocalDateTime.now())
                .type(Event.EventType.VETO)
                .severity(Event.EventSeverity.MEDIUM)
                .category("NEWS")
                .message("News veto activated")
                .details(String.format("{\"reason\":\"%s\",\"score\":%.3f}", reason, score))
                .component("TradingEngine")
                .build());
    }

    /**
     * Log bot state change
     */
    public Event logBotStateChange(String state, String reason, Event.EventSeverity severity) {
        return logEvent(Event.builder()
                .timestamp(LocalDateTime.now())
                .type(Event.EventType.INFO)
                .severity(severity)
                .category("SYSTEM")
                .message("Bot state changed: " + state)
                .details(String.format("{\"reason\":\"%s\"}", reason))
                .component("TradingEngine")
                .build());
    }

    /**
     * Log risk management event
     */
    public Event logRiskEvent(String eventType, String details, Event.EventSeverity severity) {
        return logEvent(Event.builder()
                .timestamp(LocalDateTime.now())
                .type(Event.EventType.WARNING)
                .severity(severity)
                .category("RISK")
                .message("Risk management: " + eventType)
                .details(details)
                .component("RiskManagementService")
                .build());
    }
}