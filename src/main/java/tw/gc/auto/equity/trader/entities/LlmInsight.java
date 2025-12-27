package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM-Generated Intelligence Entity (First-Class Citizen)
 * 
 * Persists ALL structured LLM outputs for querying and historical analysis.
 * LLM insights are never treated as ephemeral - they are permanent analytical artifacts.
 * 
 * This entity stores outputs from Ollama Llama 3.1 8B-Instruct for:
 * - News impact scoring and sentiment analysis
 * - Statistical pattern interpretation
 * - Veto rationale augmentation
 * - Risk assessment and market context
 */
@Entity
@Table(name = "llm_insight", indexes = {
    @Index(name = "idx_llm_timestamp", columnList = "timestamp"),
    @Index(name = "idx_llm_type", columnList = "insight_type"),
    @Index(name = "idx_llm_symbol", columnList = "symbol"),
    @Index(name = "idx_llm_source", columnList = "source")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmInsight {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Timestamp when LLM insight was generated
     */
    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Type of insight generated
     */
    @Column(name = "insight_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private InsightType insightType;
    
    /**
     * Source system/component that requested the LLM analysis
     */
    @Column(name = "source", nullable = false, length = 100)
    private String source;
    
    /**
     * Symbol/instrument this insight relates to (nullable for market-wide insights)
     */
    @Column(name = "symbol", length = 20)
    private String symbol;
    
    /**
     * Raw prompt sent to LLM (for audit and debugging)
     */
    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;
    
    /**
     * Structured JSON response from LLM
     * Must conform to predefined schema for the insight type
     */
    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;
    
    /**
     * Parsed confidence score from LLM (0.0 to 1.0)
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    /**
     * Primary recommendation/action from LLM
     */
    @Column(name = "recommendation", length = 50)
    private String recommendation;
    
    /**
     * Human-readable explanation from LLM
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;
    
    /**
     * Model name and version used
     */
    @Column(name = "model_name", length = 100)
    @Builder.Default
    private String modelName = "llama3.1:8b-instruct-q5_K_M";
    
    /**
     * Processing time in milliseconds
     */
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;
    
    /**
     * Whether the LLM call succeeded
     */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private boolean success = true;
    
    /**
     * Error message if LLM call failed
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    /**
     * Associated trade ID if this insight relates to a specific trade
     */
    @Column(name = "trade_id")
    private Long tradeId;
    
    /**
     * Associated signal ID if this insight relates to a specific signal
     */
    @Column(name = "signal_id")
    private Long signalId;
    
    /**
     * Associated event ID if this insight relates to a specific event
     */
    @Column(name = "event_id")
    private Long eventId;
    
    /**
     * Metadata JSON for extensibility (e.g., affected sectors, impact scores)
     */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    
    /**
     * Types of LLM insights that can be generated
     */
    public enum InsightType {
        /** News sentiment and impact scoring */
        NEWS_IMPACT_SCORING,
        
        /** Veto rationale generation */
        VETO_RATIONALE,
        
        /** Statistical pattern interpretation */
        PATTERN_INTERPRETATION,
        
        /** Risk assessment */
        RISK_ASSESSMENT,
        
        /** Market context analysis */
        MARKET_CONTEXT,
        
        /** Trade performance analysis */
        TRADE_ANALYSIS,
        
        /** Strategy recommendation */
        STRATEGY_RECOMMENDATION,
        
        /** Earnings impact analysis */
        EARNINGS_IMPACT,
        
        /** Sector/symbol correlation analysis */
        CORRELATION_ANALYSIS,
        
        /** General market commentary */
        MARKET_COMMENTARY,
        
        /** AI-assisted signal generation */
        SIGNAL_GENERATION,
        
        /** Startup market analysis */
        MARKET_ANALYSIS
    }
}
