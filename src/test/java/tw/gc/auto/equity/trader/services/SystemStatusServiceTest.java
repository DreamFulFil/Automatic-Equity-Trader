package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStatusServiceTest {

    private SystemStatusService systemStatusService;

    @BeforeEach
    void setUp() {
        systemStatusService = new SystemStatusService();
    }

    @Test
    void initialState_shouldHaveNoOperationsRunning() {
        assertThat(systemStatusService.isOperationRunning()).isFalse();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isFalse();
        assertThat(systemStatusService.isBacktestRunning()).isFalse();
        assertThat(systemStatusService.getHistoryDownloadStartTime()).isEqualTo(0);
        assertThat(systemStatusService.getBacktestStartTime()).isEqualTo(0);
    }

    @Test
    void startHistoryDownload_shouldSetRunningFlag() {
        // When
        boolean started = systemStatusService.startHistoryDownload();

        // Then
        assertThat(started).isTrue();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isTrue();
        assertThat(systemStatusService.isOperationRunning()).isTrue();
        assertThat(systemStatusService.getHistoryDownloadStartTime()).isGreaterThan(0);
    }

    @Test
    void startHistoryDownload_shouldReturnFalseIfAlreadyRunning() {
        // Given
        systemStatusService.startHistoryDownload();

        // When
        boolean started = systemStatusService.startHistoryDownload();

        // Then
        assertThat(started).isFalse();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isTrue();
    }

    @Test
    void completeHistoryDownload_shouldClearRunningFlag() {
        // Given
        systemStatusService.startHistoryDownload();

        // When
        systemStatusService.completeHistoryDownload();

        // Then
        assertThat(systemStatusService.isHistoryDownloadRunning()).isFalse();
        assertThat(systemStatusService.getHistoryDownloadStartTime()).isEqualTo(0);
    }

    @Test
    void startBacktest_shouldSetRunningFlag() {
        // When
        boolean started = systemStatusService.startBacktest();

        // Then
        assertThat(started).isTrue();
        assertThat(systemStatusService.isBacktestRunning()).isTrue();
        assertThat(systemStatusService.isOperationRunning()).isTrue();
        assertThat(systemStatusService.getBacktestStartTime()).isGreaterThan(0);
    }

    @Test
    void startBacktest_shouldReturnFalseIfAlreadyRunning() {
        // Given
        systemStatusService.startBacktest();

        // When
        boolean started = systemStatusService.startBacktest();

        // Then
        assertThat(started).isFalse();
        assertThat(systemStatusService.isBacktestRunning()).isTrue();
    }

    @Test
    void completeBacktest_shouldClearRunningFlag() {
        // Given
        systemStatusService.startBacktest();

        // When
        systemStatusService.completeBacktest();

        // Then
        assertThat(systemStatusService.isBacktestRunning()).isFalse();
        assertThat(systemStatusService.getBacktestStartTime()).isEqualTo(0);
    }

    @Test
    void isOperationRunning_shouldReturnTrueIfDownloadRunning() {
        // Given
        systemStatusService.startHistoryDownload();

        // Then
        assertThat(systemStatusService.isOperationRunning()).isTrue();
    }

    @Test
    void isOperationRunning_shouldReturnTrueIfBacktestRunning() {
        // Given
        systemStatusService.startBacktest();

        // Then
        assertThat(systemStatusService.isOperationRunning()).isTrue();
    }

    @Test
    void isOperationRunning_shouldReturnTrueIfBothRunning() {
        // Given
        systemStatusService.startHistoryDownload();
        systemStatusService.startBacktest();

        // Then
        assertThat(systemStatusService.isOperationRunning()).isTrue();
    }

    @Test
    void isOperationRunning_shouldReturnFalseWhenAllComplete() {
        // Given
        systemStatusService.startHistoryDownload();
        systemStatusService.startBacktest();
        systemStatusService.completeHistoryDownload();
        systemStatusService.completeBacktest();

        // Then
        assertThat(systemStatusService.isOperationRunning()).isFalse();
    }

    @Test
    void operationsCanRunIndependently() {
        // Start download
        systemStatusService.startHistoryDownload();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isTrue();
        assertThat(systemStatusService.isBacktestRunning()).isFalse();

        // Start backtest
        systemStatusService.startBacktest();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isTrue();
        assertThat(systemStatusService.isBacktestRunning()).isTrue();

        // Complete download
        systemStatusService.completeHistoryDownload();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isFalse();
        assertThat(systemStatusService.isBacktestRunning()).isTrue();

        // Complete backtest
        systemStatusService.completeBacktest();
        assertThat(systemStatusService.isHistoryDownloadRunning()).isFalse();
        assertThat(systemStatusService.isBacktestRunning()).isFalse();
    }
}
