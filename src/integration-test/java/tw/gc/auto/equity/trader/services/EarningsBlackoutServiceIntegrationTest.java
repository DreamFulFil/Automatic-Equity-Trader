package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.config.EarningsProperties;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutDate;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.repositories.EarningsBlackoutMetaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EarningsBlackoutServiceIntegrationTest {

    @Mock
    private EarningsBlackoutMetaRepository metaRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TelegramService telegramService;

    @Mock
    private EarningsProperties earningsProperties;

    private EarningsBlackoutService earningsBlackoutService;

    @BeforeEach
    void setUp() {
        earningsBlackoutService = spy(new EarningsBlackoutService(metaRepository, restTemplate, objectMapper, telegramService, earningsProperties));
        // Mock earnings properties to disable refresh by default
        when(earningsProperties.getRefresh()).thenReturn(new EarningsProperties.Refresh());
        // Mock RestTemplate to return empty earnings data
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"quoteSummary\":{\"result\":[{\"calendarEvents\":{\"earnings\":{\"earningsDate\":[]}}}]}}");
    }

    @Test
    void manualRefresh_persistsSnapshot_andIsFresh() throws Exception {
        // Enable refresh for this test
        EarningsProperties.Refresh refresh = new EarningsProperties.Refresh();
        refresh.setEnabled(true);
        when(earningsProperties.getRefresh()).thenReturn(refresh);

        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Taipei")).plusDays(1);
        doReturn(Map.of("TSM", List.of(futureDate))).when(earningsBlackoutService).fetchEarningsForTickers(anySet());

        EarningsBlackoutDate dateEntity = EarningsBlackoutDate.builder().blackoutDate(futureDate).build();
        EarningsBlackoutMeta mockMeta = EarningsBlackoutMeta.builder()
                .id(1L)
                .dates(Set.of(dateEntity))
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")))
                .build();
        when(metaRepository.save(any())).thenReturn(mockMeta);
        when(metaRepository.count()).thenReturn(1L);

        Optional<EarningsBlackoutMeta> meta = earningsBlackoutService.manualRefresh();

        assertTrue(meta.isPresent());
        assertEquals(1, meta.get().getDates().size());
        assertFalse(earningsBlackoutService.isDataStale(meta.get()));
        verify(metaRepository).save(any());
    }

    @Test
    void refreshFailure_keepsLastGoodData() throws Exception {
        // Enable refresh for this test
        EarningsProperties.Refresh refresh = new EarningsProperties.Refresh();
        refresh.setEnabled(true);
        when(earningsProperties.getRefresh()).thenReturn(refresh);

        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Taipei")).plusDays(2);
        EarningsBlackoutDate dateEntity = EarningsBlackoutDate.builder().blackoutDate(futureDate).build();
        EarningsBlackoutMeta first = EarningsBlackoutMeta.builder()
                .id(1L)
                .dates(Set.of(dateEntity))
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")))
                .build();
        when(metaRepository.save(any())).thenReturn(first);
        when(metaRepository.count()).thenReturn(1L);
        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(first));

        doReturn(Map.of("TSM", List.of(futureDate))).when(earningsBlackoutService).fetchEarningsForTickers(anySet());
        EarningsBlackoutMeta savedFirst = earningsBlackoutService.manualRefresh().orElseThrow();

        doThrow(new RuntimeException("fetch failed")).when(earningsBlackoutService).fetchEarningsForTickers(anySet());
        Optional<EarningsBlackoutMeta> second = earningsBlackoutService.manualRefresh();

        assertTrue(second.isEmpty());
        assertEquals(savedFirst.getId(), first.getId());
    }

    @Test
    void retryBackoff_retriesUntilSuccess() throws Exception {
        // Enable refresh for this test
        EarningsProperties.Refresh refresh = new EarningsProperties.Refresh();
        refresh.setEnabled(true);
        when(earningsProperties.getRefresh()).thenReturn(refresh);

        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Taipei")).plusDays(3);
        EarningsBlackoutDate dateEntity = EarningsBlackoutDate.builder().blackoutDate(futureDate).build();
        EarningsBlackoutMeta mockMeta = EarningsBlackoutMeta.builder()
                .dates(Set.of(dateEntity))
                .build();
        when(metaRepository.save(any())).thenReturn(mockMeta);

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
        EarningsBlackoutDate dateEntity = EarningsBlackoutDate.builder().blackoutDate(today).build();
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .dates(Set.of(dateEntity))
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")).minusDays(31)) // Make it stale
                .ttlDays(30)
                .build();

        when(metaRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(meta));

        assertTrue(earningsBlackoutService.isDataStale(meta));
    }
}