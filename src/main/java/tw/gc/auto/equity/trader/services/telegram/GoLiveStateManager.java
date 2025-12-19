package tw.gc.auto.equity.trader.services.telegram;

import lombok.Getter;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * Manages go-live confirmation state with expiration.
 * 
 * <p><b>Why separate state manager:</b> Extracts temporal state management from TelegramService,
 * adhering to Single Responsibility Principle. This component is responsible solely for tracking
 * pending go-live confirmations and their expiration.</p>
 * 
 * <p><b>Design Decision:</b> Thread-safe using synchronized methods since state is mutable.
 * Alternative would be AtomicReference, but simplicity wins for this use case.</p>
 */
@Component
public class GoLiveStateManager {
    
    @Getter
    private boolean goLivePending = false;
    
    private LocalDateTime goLiveConfirmationExpiresAt;
    
    /**
     * Mark go-live as pending with 10-minute expiration.
     * 
     * <p><b>Why 10 minutes:</b> Balances user convenience with security.
     * Forces deliberate action while preventing stale authorizations.</p>
     */
    public synchronized void markPending() {
        this.goLivePending = true;
        this.goLiveConfirmationExpiresAt = LocalDateTime.now().plusMinutes(10);
    }
    
    /**
     * Clear go-live pending state.
     */
    public synchronized void clearPending() {
        this.goLivePending = false;
        this.goLiveConfirmationExpiresAt = null;
    }
    
    /**
     * Check if confirmation is still valid (not expired).
     * 
     * @return true if pending and not expired
     */
    public synchronized boolean isValid() {
        return goLivePending 
            && goLiveConfirmationExpiresAt != null 
            && goLiveConfirmationExpiresAt.isAfter(LocalDateTime.now());
    }
}
