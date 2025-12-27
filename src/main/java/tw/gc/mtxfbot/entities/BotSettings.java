package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bot settings stored in database.
 * Allows dynamic configuration without application restart.
 */
@Entity
@Table(name = "bot_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "setting_key", nullable = false, unique = true)
    private String key;
    
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Common setting keys as constants
    public static final String TRADING_MODE = "trading_mode"; // "simulation" or "live"
    public static final String OLLAMA_MODEL = "ollama_model";
    public static final String DAILY_LOSS_LIMIT = "daily_loss_limit";
    public static final String WEEKLY_LOSS_LIMIT = "weekly_loss_limit";
    public static final String MONTHLY_LOSS_LIMIT = "monthly_loss_limit";
    public static final String WEEKLY_PROFIT_LIMIT = "weekly_profit_limit";
    public static final String MONTHLY_PROFIT_LIMIT = "monthly_profit_limit";
    public static final String TUTOR_QUESTIONS_PER_DAY = "tutor_questions_per_day";
    public static final String TUTOR_INSIGHTS_PER_DAY = "tutor_insights_per_day";
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
