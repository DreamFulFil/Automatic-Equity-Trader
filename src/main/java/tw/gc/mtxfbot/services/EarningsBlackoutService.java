package tw.gc.mtxfbot.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.TelegramService;
import tw.gc.mtxfbot.entities.EarningsBlackoutDate;
import tw.gc.mtxfbot.entities.EarningsBlackoutMeta;
import tw.gc.mtxfbot.repositories.EarningsBlackoutMetaRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * EarningsBlackoutService
 *
 * Refreshes earnings blackout dates from Yahoo Finance, persists snapshots in the database,
 * and exposes helpers for the trading engine and admin endpoints.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EarningsBlackoutService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final int DEFAULT_TTL_DAYS = 7;
    private static final String LEGACY_JSON_PATH = "config/earnings-blackout-dates.json";
    private static final Duration[] BACKOFFS = new Duration[]{
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8)
    };
    private static final List<String> DEFAULT_TICKERS = List.of(
            "TSM",
            "2454.TW",
            "2317.TW",
            "UMC",
            "2303.TW",
            "ASX",
            "3711.TW",
            "2412.TW",
            "2882.TW",
            "2881.TW",
            "1301.TW",
            "2002.TW"
    );

    private final EarningsBlackoutMetaRepository metaRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;

    private final AtomicBoolean staleAlertSent = new AtomicBoolean(false);
    private final AtomicBoolean refreshFailureAlertSent = new AtomicBoolean(false);
    private final ReentrantLock refreshLock = new ReentrantLock();

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Taipei")
    public void scheduledRefresh() {
        refreshAndPersist("scheduled-cron");
    }

    public Optional<EarningsBlackoutMeta> manualRefresh() {
        return refreshAndPersist("manual-refresh");
    }

    public Optional<EarningsBlackoutMeta> seedFromJson(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isMissingNode()) {
            return Optional.empty();
        }
        try {
            Set<String> tickers = extractTickers(jsonNode);
            List<LocalDate> dates = extractDates(jsonNode);
            if (dates.isEmpty()) {
                log.warn("Skipping seed from JSON because no dates were provided");
                return Optional.empty();
            }
            OffsetDateTime lastUpdated = parseLastUpdated(jsonNode);
            int ttlDays = jsonNode.path("ttl_days").asInt(DEFAULT_TTL_DAYS);
            EarningsBlackoutMeta meta = persistSnapshot(
                    Collections.singletonMap("legacy", dates),
                    tickers.isEmpty() ? new LinkedHashSet<>(DEFAULT_TICKERS) : tickers,
                    jsonNode.path("source").asText("legacy-json"),
                    lastUpdated,
                    ttlDays > 0 ? ttlDays : DEFAULT_TTL_DAYS
            );
            log.info("✅ Seeded earnings blackout data from JSON payload ({} dates)", dates.size());
            return Optional.of(meta);
        } catch (Exception e) {
            log.error("Failed to seed blackout data from JSON: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<EarningsBlackoutMeta> seedFromLegacyFileIfPresent() {
        Path path = Paths.get(LEGACY_JSON_PATH);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(path.toFile());
            return seedFromJson(jsonNode);
        } catch (Exception e) {
            log.error("Failed to seed legacy blackout JSON: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<EarningsBlackoutMeta> getLatestMeta() {
        return metaRepository.findFirstByOrderByLastUpdatedDesc();
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> getCurrentBlackoutDates() {
        Optional<EarningsBlackoutMeta> latest = ensureMetaAvailable();
        if (latest.isEmpty()) {
            return Collections.emptySet();
        }
        EarningsBlackoutMeta meta = latest.get();
        if (isDataStale(meta)) {
            warnStale(meta);
            return Collections.emptySet();
        }
        return meta.getDates().stream()
                .map(EarningsBlackoutDate::getBlackoutDate)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isDateBlackout(LocalDate date) {
        return getCurrentBlackoutDates().contains(date);
    }

    public boolean isLatestStale() {
        return getLatestMeta().map(this::isDataStale).orElse(true);
    }

    public boolean isDataStale(EarningsBlackoutMeta meta) {
        OffsetDateTime cutoff = meta.getLastUpdated().plusDays(meta.getTtlDays());
        return OffsetDateTime.now(TAIPEI_ZONE).isAfter(cutoff);
    }

    private Optional<EarningsBlackoutMeta> refreshAndPersist(String source) {
        if (!refreshLock.tryLock()) {
            log.warn("Earnings blackout refresh already in progress, skipping {}", source);
            return Optional.empty();
        }
        try {
            Set<String> tickers = new LinkedHashSet<>(DEFAULT_TICKERS);
            Map<String, List<LocalDate>> fetched = tryFetchWithRetry(tickers);
            if (fetched == null || fetched.isEmpty()) {
                handleRefreshFailure();
                return Optional.empty();
            }
            EarningsBlackoutMeta meta = persistSnapshot(
                    fetched,
                    tickers,
                    source,
                    OffsetDateTime.now(TAIPEI_ZONE),
                    DEFAULT_TTL_DAYS
            );
            refreshFailureAlertSent.set(false);
            staleAlertSent.set(false);
            log.info("✅ Earnings blackout refresh successful ({} tickers, {} dates, lastUpdated={})",
                    meta.getTickersChecked().size(),
                    meta.getDates().size(),
                    meta.getLastUpdated());
            return Optional.of(meta);
        } finally {
            refreshLock.unlock();
        }
    }

    private Map<String, List<LocalDate>> tryFetchWithRetry(Set<String> tickers) {
        for (int attempt = 1; attempt <= BACKOFFS.length + 1; attempt++) {
            try {
                Map<String, List<LocalDate>> results = fetchEarningsForTickers(tickers);
                if (results != null && !results.isEmpty()) {
                    return results;
                }
                log.warn("Earnings fetch attempt {} returned no data", attempt);
            } catch (Exception e) {
                log.warn("Earnings fetch attempt {} failed: {}", attempt, e.getMessage());
            }
            if (attempt <= BACKOFFS.length) {
                Duration backoff = BACKOFFS[attempt - 1];
                log.warn("Backing off for {}s before retry {}", backoff.getSeconds(), attempt + 1);
                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    @Transactional
    protected EarningsBlackoutMeta persistSnapshot(Map<String, List<LocalDate>> tickerDates,
                                                   Set<String> tickers,
                                                   String source,
                                                   OffsetDateTime lastUpdated,
                                                   int ttlDays) {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(lastUpdated)
                .tickersChecked(new LinkedHashSet<>(tickers))
                .source(source)
                .ttlDays(ttlDays)
                .build();

        Set<LocalDate> uniqueDates = new TreeSet<>();
        tickerDates.values().forEach(uniqueDates::addAll);

        uniqueDates.forEach(date -> meta.addDate(EarningsBlackoutDate.builder()
                .blackoutDate(date)
                .build()));

        return metaRepository.save(meta);
    }

    protected Map<String, List<LocalDate>> fetchEarningsForTickers(Set<String> tickers) throws Exception {
        Map<String, List<LocalDate>> tickerToDates = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        LocalDate horizon = today.plusDays(365);

        for (String ticker : new LinkedHashSet<>(tickers)) {
            String url = String.format(
                    "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=calendarEvents",
                    ticker);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode earningsDates = root.path("quoteSummary").path("result").path(0)
                    .path("calendarEvents").path("earnings").path("earningsDate");

            List<LocalDate> parsedDates = parseEarningsDates(earningsDates, today, horizon);
            if (!parsedDates.isEmpty()) {
                tickerToDates.put(ticker, parsedDates);
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return tickerToDates;
    }

    private List<LocalDate> parseEarningsDates(JsonNode earningsDates, LocalDate today, LocalDate horizon) {
        List<LocalDate> parsed = new ArrayList<>();
        if (earningsDates == null || earningsDates.isMissingNode()) {
            return parsed;
        }
        if (earningsDates.isArray()) {
            earningsDates.forEach(node -> parseDateNode(node).ifPresent(date -> {
                if (!date.isBefore(today) && !date.isAfter(horizon)) {
                    parsed.add(date);
                }
            }));
        } else {
            parseDateNode(earningsDates).ifPresent(parsed::add);
        }
        return parsed;
    }

    private Optional<LocalDate> parseDateNode(JsonNode node) {
        if (node == null) {
            return Optional.empty();
        }
        try {
            if (node.has("raw")) {
                long epochSeconds = node.path("raw").asLong();
                return Optional.of(Instant.ofEpochSecond(epochSeconds).atZone(TAIPEI_ZONE).toLocalDate());
            }
            if (node.isTextual()) {
                return Optional.of(LocalDate.parse(node.asText()));
            }
            if (node.isNumber()) {
                return Optional.of(Instant.ofEpochMilli(node.asLong()).atZone(TAIPEI_ZONE).toLocalDate());
            }
        } catch (Exception e) {
            log.debug("Could not parse earnings date node: {}", node);
        }
        return Optional.empty();
    }

    private Set<String> extractTickers(JsonNode jsonNode) {
        Set<String> tickers = new LinkedHashSet<>();
        JsonNode tickersNode = jsonNode.path("tickers_checked");
        if (tickersNode.isArray()) {
            tickersNode.forEach(node -> {
                if (node.isTextual()) {
                    tickers.add(node.asText());
                }
            });
        }
        return tickers;
    }

    private List<LocalDate> extractDates(JsonNode jsonNode) {
        List<LocalDate> dates = new ArrayList<>();
        JsonNode datesNode = jsonNode.path("dates");
        if (datesNode.isArray()) {
            datesNode.forEach(node -> {
                try {
                    dates.add(LocalDate.parse(node.asText()));
                } catch (Exception ignored) {
                    // ignore bad entries
                }
            });
        }
        return dates;
    }

    private OffsetDateTime parseLastUpdated(JsonNode jsonNode) {
        String text = jsonNode.path("last_updated").asText(null);
        if (text != null && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text);
            } catch (Exception ignored) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(text);
                    return ldt.atZone(TAIPEI_ZONE).toOffsetDateTime();
                } catch (Exception ignoredAgain) {
                    // fall through
                }
            }
        }
        return OffsetDateTime.now(TAIPEI_ZONE);
    }

    private void handleRefreshFailure() {
        log.error("❌ Earnings blackout refresh failed after retries; keeping last-good data");
        if (refreshFailureAlertSent.compareAndSet(false, true)) {
            telegramService.sendMessage(
                    "❌ Earnings blackout refresh failed after retries. Last-good data kept; blackout enforcement paused until refresh succeeds.");
        }
    }

    private void warnStale(EarningsBlackoutMeta meta) {
        log.warn("⚠️ Earnings blackout data stale (lastUpdated={}, ttl={}d)", meta.getLastUpdated(), meta.getTtlDays());
        if (staleAlertSent.compareAndSet(false, true)) {
            telegramService.sendMessage(String.format(
                    "⚠️ Earnings blackout data stale\nLast updated: %s\nTTL: %d days\nBlackout enforcement paused until refresh succeeds",
                    meta.getLastUpdated(), meta.getTtlDays()));
        }
    }

    private Optional<EarningsBlackoutMeta> ensureMetaAvailable() {
        Optional<EarningsBlackoutMeta> latest = getLatestMeta();
        if (latest.isPresent()) {
            return latest;
        }
        Optional<EarningsBlackoutMeta> seeded = seedFromLegacyFileIfPresent();
        if (seeded.isPresent()) {
            return seeded;
        }
        return refreshAndPersist("bootstrap");
    }
}
