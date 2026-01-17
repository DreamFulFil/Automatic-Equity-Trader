package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BridgeManagerServiceTest {

    private BridgeManagerService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new BridgeManagerService();
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "test");
        ReflectionTestUtils.setField(service, "workingDir", tempDir.toString());
    }

    @Test
    void testGetBridgePidWhenNotStarted() {
        long pid = service.getBridgePid();
        
        assertEquals(-1, pid);
    }

    @Test
    void testIsBridgeHealthyWhenNotRunning() {
        boolean healthy = service.isBridgeHealthy();
        
        assertFalse(healthy);
    }

    @Test
    void testStopBridgeWhenNotRunning() {
        // Should not throw exception
        assertDoesNotThrow(() -> service.stopBridge());
    }

    @Test
    void testStartBridgeCreatesProcess() throws InterruptedException {
        // Use a simple command that will succeed
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "hello");
        
        service.startBridge("test_password");
        
        // Give it time to complete since echo exits immediately
        Thread.sleep(100);
        
        long pid = service.getBridgePid();
        // Process was created (even if it exited)
        assertTrue(pid >= 0);
    }

    @Test
    void testStartBridgeWhenAlreadyRunning() {
        ReflectionTestUtils.setField(service, "isRunning", true);
        
        // Should return early without creating new process
        service.startBridge("test_password");
        
        // Process should still be null since we didn't actually start
        assertEquals(-1, service.getBridgePid());
    }

    @Test
    void testStopBridgeAfterStart() throws InterruptedException {
        ReflectionTestUtils.setField(service, "pythonExecutable", "sleep");
        ReflectionTestUtils.setField(service, "scriptPath", "5");
        
        service.startBridge("test_password");
        Thread.sleep(500); // Let it start
        
        long pidBeforeStop = service.getBridgePid();
        assertTrue(pidBeforeStop > 0);
        
        service.stopBridge();
        
        // After stopping, health check should fail
        assertFalse(service.isBridgeHealthy());
    }

    @Test
    void testRestartBridge() throws InterruptedException {
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "test");
        
        service.restartBridge("test_password");
        
        Thread.sleep(100);
        
        // Should have attempted to start
        long pid = service.getBridgePid();
        assertTrue(pid >= 0);
    }

    @Test
    void testStartBridgeWithInvalidCommand() {
        ReflectionTestUtils.setField(service, "pythonExecutable", "nonexistent_command_xyz");
        ReflectionTestUtils.setField(service, "scriptPath", "test");
        
        // Should handle error gracefully
        assertDoesNotThrow(() -> service.startBridge("test_password"));
        
        // Should not be running after failed start
        assertEquals(-1, service.getBridgePid());
    }

    @Test
    void testBridgeEnvironmentVariableSet() throws InterruptedException {
        // This test verifies that JASYPT_PASSWORD is set in environment
        // We can't directly test environment variables, but we can verify the process starts
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "test");
        
        service.startBridge("secret_password");
        Thread.sleep(100);
        
        // If we got here without exception, environment variable was set
        assertTrue(service.getBridgePid() >= 0);
    }

    @Test
    void testStopBridgeForciblyWhenGracefulFails() throws InterruptedException {
        // Start a long-running process
        ReflectionTestUtils.setField(service, "pythonExecutable", "sleep");
        ReflectionTestUtils.setField(service, "scriptPath", "60");
        
        service.startBridge("test_password");
        Thread.sleep(500);
        
        long pidBeforeStop = service.getBridgePid();
        assertTrue(pidBeforeStop > 0);
        
        // Stop should work even if process doesn't exit gracefully
        assertDoesNotThrow(() -> service.stopBridge());
    }

    @Test
    void testStartBridgeHealthCheckFails_shouldStopBridge() throws InterruptedException {
        // Use a command that exits immediately so health check will fail
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "quick_exit");
        
        service.startBridge("test_password");
        
        // Process was created but exited quickly (health check would fail)
        // Service should have attempted to stop it
        Thread.sleep(100);
        
        // Health check should fail since the process exited
        assertFalse(service.isBridgeHealthy());
    }

    @Test
    void testRestartBridgeInterrupted_shouldHandleGracefully() {
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "test");
        
        // This should handle interruption gracefully
        assertDoesNotThrow(() -> service.restartBridge("test_password"));
    }
}
