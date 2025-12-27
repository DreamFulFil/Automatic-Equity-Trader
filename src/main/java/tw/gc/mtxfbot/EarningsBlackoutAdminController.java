package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tw.gc.mtxfbot.entities.EarningsBlackoutMeta;
import tw.gc.mtxfbot.services.EarningsBlackoutService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/earnings-blackout")
@RequiredArgsConstructor
public class EarningsBlackoutAdminController {

    private final EarningsBlackoutService earningsBlackoutService;
    private final RiskManagementService riskManagementService;

    @PostMapping("/seed")
    public Map<String, Object> seedFromJson(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                            @RequestBody JsonNode payload) {
        requireAdminToken(token);
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
    public Map<String, Object> refresh(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAdminToken(token);
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
    public Map<String, Object> status(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAdminToken(token);
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

    private void requireAdminToken(String providedToken) {
        String expected = resolveAdminToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin token not configured");
        }
        if (!expected.equals(providedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
    }

    private String resolveAdminToken() {
        String fromEnv = System.getenv("ADMIN_TOKEN");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return System.getProperty("admin.token");
    }
}
