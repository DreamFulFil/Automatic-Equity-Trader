package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Shioaji API settings stored in database.
 * Allows dynamic configuration of Shioaji connection parameters.
 */
@Entity
@Table(name = "shioaji_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShioajiSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Whether to use simulation/paper trading mode */
    @Column(name = "simulation", nullable = false)
    @Builder.Default
    private boolean simulation = true; // Default to simulation for safety

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;
}