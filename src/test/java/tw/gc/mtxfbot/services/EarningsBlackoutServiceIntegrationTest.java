package tw.gc.mtxfbot.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import tw.gc.mtxfbot.RiskManagementService;
import tw.gc.mtxfbot.entities.EarningsBlackoutMeta;
import tw.gc.mtxfbot.repositories.EarningsBlackoutMetaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class EarningsBlackoutServiceIntegrationTest {

    @SuppressWarnings("removal")
    @SpyBean
    private EarningsBlackoutService earningsBlackoutService;

    @Autowired
    private EarningsBlackoutMetaRepository metaRepository;

    @Autowired
    private RiskManagementService riskManagementService;

    @BeforeEach
    void clean() {
        metaRepository.deleteAll();
        Mockito.clearInvocations(earningsBlackoutService);
    }

    @Test
    void manualRefresh_persistsSnapshot_andIsFresh() throws Exception {
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Taipei")).plusDays(1);
        doReturn(Map.of("TSM", List.of(futureDate))).when(earningsBlackoutService).fetchEarningsForTickers(anySet());

        Optional<EarningsBlackoutMeta> meta = earningsBlackoutService.manualRefresh();

        assertTrue(meta.isPresent());
        assertEquals(1, metaRepository.count());
        assertEquals(1, meta.get().getDates().size());
        assertFalse(earningsBlackoutService.isDataStale(meta.get()));
    }

    @Test
    void refreshFailure_keepsLastGoodData() throws Exception {
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Taipei")).plusDays(2);
        doReturn(Map.of("TSM", List.of(futureDate))).when(earningsBlackoutService).fetchEarningsForTickers(anySet());
        EarningsBlackoutMeta first = earningsBlackoutService.manualRefresh().orElseThrow();

        doThrow(new RuntimeException("fetch failed")).when(earningsBlackoutService).fetchEarningsForTickers(anySet());
        Optional<EarningsBlackoutMeta> second = earningsBlackoutService.manualRefresh();

        assertTrue(second.isEmpty());
        assertEquals(1, metaRepository.count());
        EarningsBlackoutMeta latest = metaRepository.findFirstByOrderByLastUpdatedDesc().orElseThrow();
        assertEquals(first.getId(), latest.getId());
    }

    @Test
    void retryBackoff_retriesUntilSuccess() throws Exception {
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Taipei")).plusDays(3);
        doThrow(new RuntimeException("first"))
                .doReturn(Map.of("TSM", List.of(futureDate)))
                .when(earningsBlackoutService).fetchEarningsForTickers(anySet());

        Optional<EarningsBlackoutMeta> meta = earningsBlackoutService.manualRefresh();

        assertTrue(meta.isPresent());
        verify(earningsBlackoutService, times(2)).fetchEarningsForTickers(anySet());
    }

    @Test
    void staleData_disablesBlackoutEnforcement() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        doReturn(Map.of("TSM", List.of(today))).when(earningsBlackoutService).fetchEarningsForTickers(anySet());
        EarningsBlackoutMeta meta = earningsBlackoutService.manualRefresh().orElseThrow();

        meta.setLastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")).minusDays(meta.getTtlDays() + 1));
        metaRepository.save(meta);

        assertFalse(riskManagementService.isEarningsBlackout());
        assertTrue(riskManagementService.isEarningsBlackoutDataStale());
    }
}
