package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BridgeManagerServiceCoverageTest {

    private BridgeManagerService service;

    @BeforeEach
    void setUp() {
        service = new BridgeManagerService();
        ReflectionTestUtils.setField(service, "pythonExecutable", "echo");
        ReflectionTestUtils.setField(service, "scriptPath", "test");
        ReflectionTestUtils.setField(service, "workingDir", new File(".").getAbsolutePath());
    }

    @Test
    void startBridge_healthyLogsSuccess() {
        BridgeManagerService spy = spy(service);
        doReturn(true).when(spy).isBridgeHealthy();

        spy.startBridge("pw");
        assertTrue(spy.getBridgePid() >= 0);
    }

    @Test
    void stopBridge_interruptDuringWait_setsInterrupted() throws Exception {
        BridgeManagerService spy = spy(service);
        ReflectionTestUtils.setField(spy, "isRunning", true);
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), any())).thenThrow(new InterruptedException("boom"));
        when(process.pid()).thenReturn(123L);
        ReflectionTestUtils.setField(spy, "bridgeProcess", process);

        Thread.currentThread().interrupt();
        try {
            spy.stopBridge();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void stopBridge_forceKillsWhenTimeout() throws Exception {
        BridgeManagerService spy = spy(service);
        ReflectionTestUtils.setField(spy, "isRunning", true);
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), any())).thenReturn(false);
        when(process.pid()).thenReturn(123L);
        ReflectionTestUtils.setField(spy, "bridgeProcess", process);

        spy.stopBridge();
        verify(process).destroyForcibly();
    }

    @Test
    void startBridge_handlesIOException() throws Exception {
        try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class,
            (builder, ctx) -> {
                when(builder.command(anyString(), anyString())).thenReturn(builder);
                when(builder.directory(any(File.class))).thenReturn(builder);
                when(builder.environment()).thenReturn(new java.util.HashMap<>());
                when(builder.redirectOutput(any(ProcessBuilder.Redirect.class))).thenReturn(builder);
                when(builder.redirectError(any(ProcessBuilder.Redirect.class))).thenReturn(builder);
                when(builder.start()).thenThrow(new IOException("fail"));
            })) {
            service.startBridge("pw");
            assertEquals(-1, service.getBridgePid());
        }
    }
}
