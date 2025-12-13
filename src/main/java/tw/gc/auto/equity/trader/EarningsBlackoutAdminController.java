package tw.gc.auto.equity.trader;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.services.EarningsBlackoutService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/earnings-blackout")
@RequiredArgsConstructor
public class EarningsBlackoutAdminController {

    @NonNull
    private final EarningsBlackoutService earningsBlackoutService;
    @NonNull
    private final RiskManagementService riskManagementService;

    @PostMapping("/seed")
    public Map<String, Object> seedFromJson(@RequestBody JsonNode payload) {
        Optional<EarningsBlackoutMeta> meta = earningsBlackoutService.seedFromJson(payload);
        // Refresh in-memory state for immediate use
        riskManagementService.isEarningsBlackout();

        Map<String, Object> response = new HashMap<>();
        response.put("status", meta.isPresent() ? "seeded" : "ignored");
        response.put("datesCount", meta.map(m -> m.getDates().size()).orElse(0));
        response.put("stale", meta.map(earningsBlackoutService::isDataStale).orElse(true));
        return response;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        Optional<EarningsBlackoutMeta> meta = earningsBlackoutService.manualRefresh();
        riskManagementService.isEarningsBlackout();

        Map<String, Object> response = new HashMap<>();
        response.put("status", meta.isPresent() ? "refreshed" : "failed");
        response.put("lastUpdated", meta.map(EarningsBlackoutMeta::getLastUpdated).orElse(null));
        response.put("datesCount", meta.map(m -> m.getDates().size()).orElse(0));
        response.put("stale", meta.map(earningsBlackoutService::isDataStale).orElse(true));
        return response;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Optional<EarningsBlackoutMeta> meta = earningsBlackoutService.getLatestMeta();
        Map<String, Object> response = new HashMap<>();

        response.put("present", meta.isPresent());
        response.put("stale", meta.map(earningsBlackoutService::isDataStale).orElse(true));
        response.put("lastUpdated", meta.map(EarningsBlackoutMeta::getLastUpdated).orElse(null));
        response.put("ttlDays", meta.map(EarningsBlackoutMeta::getTtlDays).orElse(null));
        response.put("tickersChecked", meta.map(EarningsBlackoutMeta::getTickersChecked).orElse(null));
        response.put("datesCount", meta.map(m -> m.getDates().size()).orElse(0));
        return response;
    }
}
