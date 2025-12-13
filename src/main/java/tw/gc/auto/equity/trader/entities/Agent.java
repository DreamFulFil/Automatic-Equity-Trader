package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent entity representing modular, reusable AI agents.
 * Based on best practices from Anthropic, OpenAI, and agent frameworks like LangChain/CrewAI.
 */
@Entity
@Table(name = "agents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    /** JSON array of capabilities/tools this agent can use */
    @Column(columnDefinition = "TEXT")
    private String capabilities;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AgentStatus status = AgentStatus.ACTIVE;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /** Agent type for polymorphic behavior */
    @Column(name = "agent_type", nullable = false)
    private String agentType;
    
    /** Configuration JSON for agent-specific settings */
    @Column(columnDefinition = "TEXT")
    private String config;
    
    public enum AgentStatus {
        ACTIVE,
        INACTIVE,
        ERROR
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
