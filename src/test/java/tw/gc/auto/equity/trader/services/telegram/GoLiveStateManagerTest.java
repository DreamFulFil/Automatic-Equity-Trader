package tw.gc.auto.equity.trader.services.telegram;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GoLiveStateManagerTest {

    @Test
    void markPending_shouldSetPending_andBeValidInitially() {
        GoLiveStateManager mgr = new GoLiveStateManager();

        assertFalse(mgr.isGoLivePending());
        assertFalse(mgr.isValid());

        mgr.markPending();

        assertTrue(mgr.isGoLivePending());
        assertTrue(mgr.isValid());
    }

    @Test
    void clearPending_shouldUnsetPending_andInvalidate() {
        GoLiveStateManager mgr = new GoLiveStateManager();
        mgr.markPending();

        mgr.clearPending();

        assertFalse(mgr.isGoLivePending());
        assertFalse(mgr.isValid());
    }

    @Test
    void isValid_shouldReturnFalseWhenExpired() throws Exception {
        GoLiveStateManager mgr = new GoLiveStateManager();
        mgr.markPending();

        Field f = GoLiveStateManager.class.getDeclaredField("goLiveConfirmationExpiresAt");
        f.setAccessible(true);
        f.set(mgr, LocalDateTime.now().minusMinutes(1));

        assertTrue(mgr.isGoLivePending());
        assertFalse(mgr.isValid());
    }
}
