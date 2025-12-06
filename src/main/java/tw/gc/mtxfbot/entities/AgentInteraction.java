package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Logs all agent interactions for rate limiting and context tracking.
 * Used by TutorBot for question limits and context awareness.
 */
@Entity
@Table(name = "agent_interactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInteraction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_name", nullable = false)
    private String agentName;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    private InteractionType type;
    
    @Column(columnDefinition = "TEXT")
    private String input;
    
    @Column(columnDefinition = "TEXT")
    private String output;
    
    @Column(name = "user_id")
    private String userId; // Telegram chat ID
    
    @Column(name = "tokens_used")
    private Integer tokensUsed;
    
    @Column(name = "response_time_ms")
    private Long responseTimeMs;
    
    public enum InteractionType {
        QUESTION,      // TutorBot question
        INSIGHT,       // TutorBot insight/tutorial
        NEWS_ANALYSIS, // NewsAnalyzer veto check
        SIGNAL,        // SignalGenerator signal
        RISK_CHECK,    // RiskManager limit check
        COMMAND        // Telegram command
    }
}
