package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Events entity for logging all bot events.
 * Includes API calls, errors, news vetos, Telegram commands, bot state changes.
 */
@Entity
@Table(name = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSeverity severity;

    @Column(length = 100, nullable = false)
    private String category; // e.g., "API", "TRADING", "TELEGRAM", "RISK", "SYSTEM"

    @Column(length = 200, nullable = false)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON string with additional data

    @Column(length = 50)
    private String component; // e.g., "TradingEngine", "TelegramService", "BridgeManager"

    @Column(length = 50)
    private String userId; // For Telegram commands

    @Column(name = "error_code")
    private String errorCode; // For API errors

    @Column(name = "response_time_ms")
    private Long responseTimeMs; // For API calls

    public enum EventType {
        INFO, WARNING, ERROR, SUCCESS, COMMAND, API_CALL, SIGNAL, TRADE, VETO
    }

    public enum EventSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}