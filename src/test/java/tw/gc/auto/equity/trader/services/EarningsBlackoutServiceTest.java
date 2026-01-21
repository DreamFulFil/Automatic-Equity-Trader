package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.EarningsProperties;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.repositories.EarningsBlackoutMetaRepository;
import tw.gc.auto.equity.trader.services.TelegramService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EarningsBlackoutServiceTest {

    private static final java.nio.file.Path LEGACY_PATH = java.nio.file.Paths.get("config/earnings-blackout-dates.json");

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void withLegacyFileContent(String content, ThrowingRunnable body) throws Exception {
        String original = null;
        if (java.nio.file.Files.exists(LEGACY_PATH)) {
            original = java.nio.file.Files.readString(LEGACY_PATH);
        }

        try {
            java.nio.file.Files.createDirectories(LEGACY_PATH.getParent());
            java.nio.file.Files.writeString(LEGACY_PATH, content);
            body.run();
        } finally {
            if (original != null) {
                java.nio.file.Files.writeString(LEGACY_PATH, original);
            } else {
                java.nio.file.Files.deleteIfExists(LEGACY_PATH);
            }
        }
    }

    private void withLegacyFileMissing(ThrowingRunnable body) throws Exception {
        String original = null;
        if (java.nio.file.Files.exists(LEGACY_PATH)) {
            original = java.nio.file.Files.readString(LEGACY_PATH);
        }

        try {
            java.nio.file.Files.deleteIfExists(LEGACY_PATH);
            body.run();
        } finally {
            if (original != null) {
                java.nio.file.Files.createDirectories(LEGACY_PATH.getParent());
                java.nio.file.Files.writeString(LEGACY_PATH, original);
            }
        }
    }

    @Mock
    private EarningsBlackoutMetaRepository metaRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private TelegramService telegramService;

    private ObjectMapper objectMapper;
    private EarningsProperties earningsProperties;
    private EarningsBlackoutService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        earningsProperties = new EarningsProperties();
        // Default disabled to prevent slow retry sleeps in unrelated unit tests.
        earningsProperties.getRefresh().setEnabled(false);

        service = new EarningsBlackoutService(
                metaRepository,
                restTemplate,
                objectMapper,
                telegramService,
                earningsProperties
        );

        lenient().when(metaRepository.save(any(EarningsBlackoutMeta.class))).thenAnswer(i -> i.getArgument(0));
        // Default non-null response to avoid slow retry sleeps if refresh is triggered unexpectedly.
        lenient().when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn("{\"dates\":[]}");
    }

    @Test
    void seedFromJson_parses_and_persists() throws Exception {
        String payload = "{" +
                "\"tickers_checked\":[\"2330.TW\"]," +
                "\"dates\":[\"2026-01-05\",\"2026-01-06\"]," +
                "\"last_updated\":\"2026-01-01T10:00:00+08:00\"," +
                "\"ttl_days\":3," +
                "\"source\":\"unit-test\"}";

        ObjectMapper om = new ObjectMapper();
        Optional<EarningsBlackoutMeta> opt = service.seedFromJson(om.readTree(payload));

        assertTrue(opt.isPresent());
        EarningsBlackoutMeta meta = opt.get();
        assertEquals(3, meta.getTtlDays());
        assertEquals("unit-test", meta.getSource());
        assertTrue(meta.getTickersChecked().contains("2330.TW"));
        assertEquals(2, meta.getDates().size());
    }

    @Test
    void getCurrentBlackoutDates_when_stale_sendsTelegramAndReturnsEmpty() {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")).minusDays(10))
                .ttlDays(1)
                .build();

        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(meta));

        Set<LocalDate> dates = service.getCurrentBlackoutDates();
        assertTrue(dates.isEmpty());
        verify(telegramService, times(1)).sendMessage(anyString());
    }

    @Test
    void fetchEarningsForTickers_shouldCallPythonBridge() throws Exception {
        String mockResponse = "{" +
                "\"last_updated\": \"2025-12-10T09:00:00\"," +
                "\"source\": \"Yahoo Finance (yfinance)\"," +
                "\"tickers_checked\": [\"TSM\", \"2454.TW\"]," +
                "\"dates\": [\"2025-12-15\", \"2025-12-20\"]" +
                "}";

        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn(mockResponse);

        @SuppressWarnings("unchecked")
        Map<String, List<LocalDate>> result = (Map<String, List<LocalDate>>) ReflectionTestUtils.invokeMethod(
                service, "fetchEarningsForTickers", Set.of("TSM", "2454.TW"));

        assertThat(result).isNotNull();
        assertThat(result).containsKey("aggregated");
        assertThat(result.get("aggregated")).hasSize(2);
        assertThat(result.get("aggregated").get(0)).isEqualTo(LocalDate.of(2025, 12, 15));
        assertThat(result.get("aggregated").get(1)).isEqualTo(LocalDate.of(2025, 12, 20));
    }

    @Test
    void fetchEarningsForTickers_shouldHandleEmptyResponse() throws Exception {
        String mockResponse = "{" +
                "\"last_updated\": \"2025-12-10T09:00:00\"," +
                "\"source\": \"Yahoo Finance (yfinance)\"," +
                "\"tickers_checked\": []," +
                "\"dates\": []" +
                "}";

        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn(mockResponse);

        @SuppressWarnings("unchecked")
        Map<String, List<LocalDate>> result = (Map<String, List<LocalDate>>) ReflectionTestUtils.invokeMethod(
                service, "fetchEarningsForTickers", Set.of("TSM"));

        assertThat(result).isNotNull();
        assertThat(result).containsKey("aggregated");
        assertThat(result.get("aggregated")).isEmpty();
    }

    @Test
    void seedFromJson_returnsEmpty_whenMissingOrNoDates() throws Exception {
        assertTrue(service.seedFromJson(null).isEmpty());

        String payloadNoDates = "{" +
                "\"tickers_checked\":[\"2330.TW\"]," +
                "\"dates\":[]," +
                "\"last_updated\":\"2026-01-01T10:00:00+08:00\"" +
                "}";

        Optional<EarningsBlackoutMeta> opt = service.seedFromJson(objectMapper.readTree(payloadNoDates));
        assertTrue(opt.isEmpty());
    }

    @Test
    void seedFromJson_handlesException_returnsEmpty() throws Exception {
        when(metaRepository.save(any(EarningsBlackoutMeta.class))).thenThrow(new RuntimeException("db down"));

        String payload = "{" +
                "\"tickers_checked\":[\"2330.TW\"]," +
                "\"dates\":[\"2026-01-05\"]," +
                "\"last_updated\":\"2026-01-01T10:00:00+08:00\"" +
                "}";

        Optional<EarningsBlackoutMeta> opt = service.seedFromJson(objectMapper.readTree(payload));
        assertTrue(opt.isEmpty());
    }

    @Test
    void seedFromLegacyFileIfPresent_readsRepoConfigFile() {
        String payload = "{" +
                "\"tickers_checked\":[\"TSM\"]," +
                "\"dates\":[\"2026-01-05\"]," +
                "\"last_updated\":\"2026-01-01T10:00:00+08:00\"" +
                "}";

        assertDoesNotThrow(() -> withLegacyFileContent(payload, () -> {
            Optional<EarningsBlackoutMeta> seeded = service.seedFromLegacyFileIfPresent();
            assertTrue(seeded.isPresent());
            assertFalse(seeded.get().getDates().isEmpty());
        }));
    }

    @Test
    void getCurrentBlackoutDates_bootstrapsFromLegacyFile_whenDbEmpty() throws Exception {
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.empty());

        java.nio.file.Path path = java.nio.file.Paths.get("config/earnings-blackout-dates.json");
        java.nio.file.Path backup = java.nio.file.Paths.get("config/earnings-blackout-dates.json.bak_for_test");
        String original = null;
        if (java.nio.file.Files.exists(path)) {
            original = java.nio.file.Files.readString(path);
            java.nio.file.Files.move(path, backup);
        }
        try {
            String payload = "{\"tickers_checked\":[\"2330.TW\"],\"dates\":[\"2026-01-05\"]}";
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, payload);

            Set<LocalDate> dates = service.getCurrentBlackoutDates();

            assertFalse(dates.isEmpty());
            assertTrue(service.isDateBlackout(dates.iterator().next()));
        } finally {
            if (java.nio.file.Files.exists(backup)) {
                java.nio.file.Files.move(backup, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void isLatestStale_trueWhenMissingOrStale() {
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.empty());
        assertTrue(service.isLatestStale());

        EarningsBlackoutMeta stale = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")).minusDays(10))
                .ttlDays(1)
                .build();
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(stale));
        assertTrue(service.isLatestStale());
    }

    @Test
    void scheduledRefresh_and_refreshOnStartup_respectEnabledFlag() {
        earningsProperties.getRefresh().setEnabled(false);

        service.scheduledRefresh();
        service.refreshOnStartup();

        verifyNoInteractions(restTemplate);
    }

    @Test
    void refreshOnStartup_whenEnabled_persistsSnapshot() {
        earningsProperties.getRefresh().setEnabled(true);

        String mockResponse = "{" +
                "\"dates\": [\"2026-01-05\"]" +
                "}";
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn(mockResponse);

        service.refreshOnStartup();

        verify(metaRepository, atLeastOnce()).save(any(EarningsBlackoutMeta.class));
    }

    @Test
    void tryFetchWithRetry_interruptedDuringBackoff_returnsNull() throws Exception {
        earningsProperties.getRefresh().setEnabled(true);
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenThrow(new RuntimeException("boom"));

        Thread.currentThread().interrupt();
        @SuppressWarnings("unchecked")
        Map<String, List<LocalDate>> result = (Map<String, List<LocalDate>>) ReflectionTestUtils.invokeMethod(
                service, "tryFetchWithRetry", Set.of("TSM"));
        assertNull(result);
        assertTrue(Thread.interrupted());
    }

    @Test
    void manualRefresh_returnsEmpty_whenLockHeldByOtherThread() throws Exception {
        earningsProperties.getRefresh().setEnabled(true);
        java.util.concurrent.locks.ReentrantLock lock = (java.util.concurrent.locks.ReentrantLock)
                ReflectionTestUtils.getField(service, "refreshLock");
        assertNotNull(lock);

        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        Thread t = new Thread(() -> {
            lock.lock();
            try {
                locked.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        t.start();

        assertTrue(locked.await(2, java.util.concurrent.TimeUnit.SECONDS));
        try {
            Optional<EarningsBlackoutMeta> res = service.manualRefresh();
            assertTrue(res.isEmpty());
        } finally {
            release.countDown();
            t.join(2000);
        }
    }

    @Test
    void warnStale_sendsTelegramOnlyOnce() {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")).minusDays(10))
                .ttlDays(1)
                .build();
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(meta));

        assertTrue(service.getCurrentBlackoutDates().isEmpty());
        assertTrue(service.getCurrentBlackoutDates().isEmpty());

        verify(telegramService, times(1)).sendMessage(anyString());
    }

    @Test
    void refreshFailure_sendsTelegramOnlyOnce() {
        earningsProperties.getRefresh().setEnabled(true);
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenThrow(new RuntimeException("network"));

        try {
            Thread.currentThread().interrupt();
            assertTrue(service.manualRefresh().isEmpty());
        } finally {
            Thread.interrupted();
        }

        try {
            Thread.currentThread().interrupt();
            assertTrue(service.manualRefresh().isEmpty());
        } finally {
            Thread.interrupted();
        }

        verify(telegramService, times(1)).sendMessage(contains("refresh failed"));
    }

    @Test
    void parseDateNode_coversRawTextNumericAndInvalid() throws Exception {
        ObjectMapper om = new ObjectMapper();

        assertTrue(((java.util.Optional<?>) ReflectionTestUtils.invokeMethod(service, "parseDateNode", (Object) null)).isEmpty());

        Object rawNode = om.readTree("{\"raw\":1700000000}");
        assertTrue(((java.util.Optional<?>) ReflectionTestUtils.invokeMethod(service, "parseDateNode", rawNode)).isPresent());

        Object textNode = om.readTree("\"2026-01-01\"");
        assertEquals(LocalDate.of(2026, 1, 1), ((java.util.Optional<?>) ReflectionTestUtils.invokeMethod(service, "parseDateNode", textNode)).get());

        Object numNode = om.readTree("1700000000000");
        assertTrue(((java.util.Optional<?>) ReflectionTestUtils.invokeMethod(service, "parseDateNode", numNode)).isPresent());

        Object badText = om.readTree("\"not-a-date\"");
        assertTrue(((java.util.Optional<?>) ReflectionTestUtils.invokeMethod(service, "parseDateNode", badText)).isEmpty());
    }

    @Test
    void parseDateNode_whenNoRecognizedShape_returnsEmptyWithoutException() throws Exception {
        ObjectMapper om = new ObjectMapper();
        Object objNode = om.readTree("{\"foo\":\"bar\"}");
        assertTrue(((java.util.Optional<?>) ReflectionTestUtils.invokeMethod(service, "parseDateNode", objNode)).isEmpty());
    }

    @Test
    void parseEarningsDates_filtersRange_and_handlesNonArray() throws Exception {
        ObjectMapper om = new ObjectMapper();
        LocalDate today = LocalDate.of(2026, 1, 1);
        LocalDate horizon = LocalDate.of(2026, 1, 10);

        Object missing = om.readTree("{}").path("missing");
        @SuppressWarnings("unchecked")
        List<LocalDate> empty = (List<LocalDate>) ReflectionTestUtils.invokeMethod(service, "parseEarningsDates", missing, today, horizon);
        assertTrue(empty.isEmpty());

        Object array = om.readTree("[\"2025-12-31\",\"2026-01-05\",\"2026-01-11\"]");
        @SuppressWarnings("unchecked")
        List<LocalDate> filtered = (List<LocalDate>) ReflectionTestUtils.invokeMethod(service, "parseEarningsDates", array, today, horizon);
        assertEquals(List.of(LocalDate.of(2026, 1, 5)), filtered);

        Object single = om.readTree("\"2026-01-02\"");
        @SuppressWarnings("unchecked")
        List<LocalDate> singleParsed = (List<LocalDate>) ReflectionTestUtils.invokeMethod(service, "parseEarningsDates", single, today, horizon);
        assertEquals(List.of(LocalDate.of(2026, 1, 2)), singleParsed);
    }

    @Test
    void fetchEarningsForTickers_ignoresInvalidDateEntries() throws Exception {
        String mockResponse = "{" +
                "\"dates\": [\"2025-12-15\", \"bad\", \"2025-12-20\"]" +
                "}";

        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn(mockResponse);

        @SuppressWarnings("unchecked")
        Map<String, List<LocalDate>> result = (Map<String, List<LocalDate>>) ReflectionTestUtils.invokeMethod(
                service, "fetchEarningsForTickers", Set.of("TSM"));

        assertThat(result.get("aggregated")).containsExactly(
                LocalDate.of(2025, 12, 15),
                LocalDate.of(2025, 12, 20)
        );
    }

    @Test
    void persistSnapshot_deduplicatesDatesAcrossTickers() {
        EarningsBlackoutMeta meta = service.persistSnapshot(
                Map.of(
                        "t1", List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)),
                        "t2", List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3))
                ),
                Set.of("A", "B"),
                "unit",
                OffsetDateTime.now(ZoneId.of("Asia/Taipei")),
                7
        );

        assertEquals(3, meta.getDates().size());
        verify(metaRepository, atLeastOnce()).save(any(EarningsBlackoutMeta.class));
    }

    @Test
    void scheduledRefresh_whenEnabled_triggersRefresh() {
        earningsProperties.getRefresh().setEnabled(true);

        service.scheduledRefresh();

        verify(metaRepository, atLeastOnce()).save(any(EarningsBlackoutMeta.class));
    }

    @Test
    void manualRefresh_whenDisabled_hitsRefreshDisabledBranch() {
        earningsProperties.getRefresh().setEnabled(false);

        assertTrue(service.manualRefresh().isEmpty());
    }

    @Test
    void tryFetchWithRetry_noDataPath_doesNotSleepWhenInterrupted() {
        earningsProperties.getRefresh().setEnabled(true);
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn("{}");

        try {
            Thread.currentThread().interrupt();
            assertTrue(service.manualRefresh().isEmpty());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void extractDates_ignoresBadEntries() throws Exception {
        Object node = objectMapper.readTree("{\"dates\":[\"bad\",\"2026-01-02\"]}");
        @SuppressWarnings("unchecked")
        List<LocalDate> dates = (List<LocalDate>) ReflectionTestUtils.invokeMethod(service, "extractDates", node);
        assertEquals(List.of(LocalDate.of(2026, 1, 2)), dates);
    }

    @Test
    void parseLastUpdated_fallsBackToNow_onInvalidValue() throws Exception {
        Object node = objectMapper.readTree("{\"last_updated\":\"not-a-timestamp\"}");
        OffsetDateTime odt = (OffsetDateTime) ReflectionTestUtils.invokeMethod(service, "parseLastUpdated", node);
        assertNotNull(odt);
    }

    @Test
    void seedFromLegacyFileIfPresent_missingFile_returnsEmpty() throws Exception {
        withLegacyFileMissing(() -> assertTrue(service.seedFromLegacyFileIfPresent().isEmpty()));
    }

    @Test
    void seedFromLegacyFileIfPresent_invalidJson_hitsCatch() throws Exception {
        withLegacyFileContent("not-json", () -> assertTrue(service.seedFromLegacyFileIfPresent().isEmpty()));
    }

    @Test
    void getCurrentBlackoutDates_returnsEmpty_whenNoMetaAndNoSeedAndRefreshDisabled() throws Exception {
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.empty());
        earningsProperties.getRefresh().setEnabled(false);

        withLegacyFileMissing(() -> assertTrue(service.getCurrentBlackoutDates().isEmpty()));
    }

    @Test
    void parseEarningsDates_withInvalidEntry_executesParseDateNodeCatch() throws Exception {
        LocalDate today = LocalDate.of(2026, 1, 1);
        LocalDate horizon = LocalDate.of(2026, 1, 10);
        Object badArray = objectMapper.readTree("[\"not-a-date\"]");

        @SuppressWarnings("unchecked")
        List<LocalDate> result = (List<LocalDate>) ReflectionTestUtils.invokeMethod(service, "parseEarningsDates", badArray, today, horizon);

        assertTrue(result.isEmpty());
    }

    @Test
    void ensureMetaAvailable_bootstrapPath_whenNoLegacyFileAndRefreshEnabled() throws Exception {
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.empty());
        earningsProperties.getRefresh().setEnabled(true);
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn("{\"dates\":[]}");

        withLegacyFileMissing(() -> {
            assertTrue(service.getCurrentBlackoutDates().isEmpty());
            verify(metaRepository, atLeastOnce()).save(any(EarningsBlackoutMeta.class));
        });
    }

    // ==================== Coverage tests for lines 232, 246, 250 ====================
    
    @Test
    void tryFetchWithRetry_retryLoopExecuted() {
        // Line 232: for (int attempt = 1; attempt <= BACKOFFS.length + 1; attempt++)
        earningsProperties.getRefresh().setEnabled(true);
        
        // Return empty response to trigger retry loop (line 238)
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenReturn("{}"); // Empty response - no dates
        
        // Test via manualRefresh which calls tryFetchWithRetry
        // Use Thread.currentThread().interrupt() to trigger the InterruptedException path (lines 246-250)
        Thread.currentThread().interrupt();
        try {
            assertTrue(service.manualRefresh().isEmpty());
        } finally {
            Thread.interrupted(); // Clear interrupt flag
        }
    }
    
    @Test
    void tryFetchWithRetry_interruptedDuringBackoff_shouldBreak() {
        // Lines 246-250: InterruptedException during Thread.sleep breaks out of loop
        earningsProperties.getRefresh().setEnabled(true);
        
        // First attempt fails with exception, then interrupted during backoff
        when(restTemplate.getForObject(eq("http://localhost:8888/earnings/scrape"), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));
        
        Thread.currentThread().interrupt();
        try {
            Optional<EarningsBlackoutMeta> result = service.manualRefresh();
            assertTrue(result.isEmpty());
        } finally {
            Thread.interrupted(); // Clear interrupt flag
        }
    }
}
