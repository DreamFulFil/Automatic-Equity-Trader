package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.SystemStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock
    private SystemStatusService systemStatusService;

    private StatusController statusController;

    @BeforeEach
    void setUp() {
        statusController = new StatusController(systemStatusService);
    }

    @Test
    void getStatus_shouldReturnNoOperationRunning_whenNothingIsRunning() {
        // Given
        when(systemStatusService.isHistoryDownloadRunning()).thenReturn(false);
        when(systemStatusService.isBacktestRunning()).thenReturn(false);
        when(systemStatusService.getHistoryDownloadStartTime()).thenReturn(0L);
        when(systemStatusService.getBacktestStartTime()).thenReturn(0L);

        // When
        StatusController.OperationStatus status = statusController.getStatus();

        // Then
        assertThat(status.isOperationRunning()).isFalse();
        assertThat(status.isHistoryDownloadRunning()).isFalse();
        assertThat(status.isBacktestRunning()).isFalse();
        assertThat(status.getHistoryDownloadStartTime()).isEqualTo(0L);
        assertThat(status.getBacktestStartTime()).isEqualTo(0L);
    }

    @Test
    void getStatus_shouldReturnDownloadRunning_whenDownloadIsInProgress() {
        // Given
        long startTime = System.currentTimeMillis();
        when(systemStatusService.isHistoryDownloadRunning()).thenReturn(true);
        when(systemStatusService.isBacktestRunning()).thenReturn(false);
        when(systemStatusService.getHistoryDownloadStartTime()).thenReturn(startTime);
        when(systemStatusService.getBacktestStartTime()).thenReturn(0L);

        // When
        StatusController.OperationStatus status = statusController.getStatus();

        // Then
        assertThat(status.isOperationRunning()).isTrue();
        assertThat(status.isHistoryDownloadRunning()).isTrue();
        assertThat(status.isBacktestRunning()).isFalse();
        assertThat(status.getHistoryDownloadStartTime()).isEqualTo(startTime);
    }

    @Test
    void getStatus_shouldReturnBacktestRunning_whenBacktestIsInProgress() {
        // Given
        long startTime = System.currentTimeMillis();
        when(systemStatusService.isHistoryDownloadRunning()).thenReturn(false);
        when(systemStatusService.isBacktestRunning()).thenReturn(true);
        when(systemStatusService.getHistoryDownloadStartTime()).thenReturn(0L);
        when(systemStatusService.getBacktestStartTime()).thenReturn(startTime);

        // When
        StatusController.OperationStatus status = statusController.getStatus();

        // Then
        assertThat(status.isOperationRunning()).isTrue();
        assertThat(status.isHistoryDownloadRunning()).isFalse();
        assertThat(status.isBacktestRunning()).isTrue();
        assertThat(status.getBacktestStartTime()).isEqualTo(startTime);
    }

    @Test
    void getStatus_shouldReturnBothRunning_whenBothOperationsInProgress() {
        // Given
        long downloadStart = System.currentTimeMillis() - 10000;
        long backtestStart = System.currentTimeMillis() - 5000;
        when(systemStatusService.isHistoryDownloadRunning()).thenReturn(true);
        when(systemStatusService.isBacktestRunning()).thenReturn(true);
        when(systemStatusService.getHistoryDownloadStartTime()).thenReturn(downloadStart);
        when(systemStatusService.getBacktestStartTime()).thenReturn(backtestStart);

        // When
        StatusController.OperationStatus status = statusController.getStatus();

        // Then
        assertThat(status.isOperationRunning()).isTrue();
        assertThat(status.isHistoryDownloadRunning()).isTrue();
        assertThat(status.isBacktestRunning()).isTrue();
        assertThat(status.getHistoryDownloadStartTime()).isEqualTo(downloadStart);
        assertThat(status.getBacktestStartTime()).isEqualTo(backtestStart);
    }

    @Test
    void operationStatus_shouldHaveAllFields() {
        // Given/When
        StatusController.OperationStatus status = new StatusController.OperationStatus(
            true, true, false, 12345L, 0L
        );

        // Then
        assertThat(status.isOperationRunning()).isTrue();
        assertThat(status.isHistoryDownloadRunning()).isTrue();
        assertThat(status.isBacktestRunning()).isFalse();
        assertThat(status.getHistoryDownloadStartTime()).isEqualTo(12345L);
        assertThat(status.getBacktestStartTime()).isEqualTo(0L);
    }

    @Test
    void operationStatus_defaultConstructor_shouldWork() {
        // Given/When
        StatusController.OperationStatus status = new StatusController.OperationStatus();

        // Then
        assertThat(status.isOperationRunning()).isFalse();
        assertThat(status.isHistoryDownloadRunning()).isFalse();
        assertThat(status.isBacktestRunning()).isFalse();
        assertThat(status.getHistoryDownloadStartTime()).isEqualTo(0L);
        assertThat(status.getBacktestStartTime()).isEqualTo(0L);
    }
}
