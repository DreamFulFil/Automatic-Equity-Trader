package tw.gc.auto.equity.trader.compliance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Taiwan Stock Exchange Compliance Service
 * 
 * Enforces Taiwan-specific trading rules:
 * 1. Odd-lot day trading NOT allowed (capital < 2M TWD)
 * 2. Round lots only: 1 round lot = 1000 shares
 * 3. Capital verification via Shioaji API
 * 
 * CRITICAL: Violations can result in broker restrictions or penalties.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaiwanStockComplianceService {
    
    private static final int ROUND_LOT_SIZE = 1000;
    private static final double MIN_CAPITAL_FOR_ODD_LOT_DAY_TRADING_TWD = 2_000_000.0;
    
    private final RestTemplate restTemplate;
    
    /**
     * Check if a trade is compliant with Taiwan regulations.
     * 
     * @param shares Number of shares
     * @param isDayTrade Is this an intraday trade?
     * @param currentCapitalTwd Account balance in TWD
     * @return Compliance result with veto reason if blocked
     */
    public ComplianceResult checkTradeCompliance(int shares, boolean isDayTrade, double currentCapitalTwd) {
        boolean isRoundLot = (shares % ROUND_LOT_SIZE == 0);
        boolean isOddLot = !isRoundLot;
        boolean hasMinimumCapital = currentCapitalTwd >= MIN_CAPITAL_FOR_ODD_LOT_DAY_TRADING_TWD;
        
        if (isDayTrade && isOddLot && !hasMinimumCapital) {
            String reason = String.format(
                "Taiwan compliance: Odd-lot day trading requires >= 2,000,000 TWD capital (current: %.0f TWD, shares: %d)",
                currentCapitalTwd, shares
            );
            log.warn("COMPLIANCE VETO: {}", reason);
            return ComplianceResult.blocked(reason);
        }
        
        if (isDayTrade && isOddLot && hasMinimumCapital) {
            log.info("Odd-lot day trading approved (capital >= 2M TWD)");
        }
        
        if (isRoundLot) {
            log.debug("Round lot trade: {} shares", shares);
        }
        
        return ComplianceResult.approved();
    }
    
    public double fetchCurrentCapital() {
        try {
            String url = "http://localhost:8888/account";
            var response = restTemplate.getForObject(url, AccountResponse.class);
            
            if (response != null && response.equity > 0) {
                log.debug("Current capital: {} TWD", response.equity);
                return response.equity;
            }
            
            log.warn("Failed to fetch capital from Shioaji API, defaulting to 80,000 TWD");
            return 80_000.0;
            
        } catch (Exception e) {
            log.error("Error fetching capital from Python bridge: {}", e.getMessage());
            return 80_000.0;
        }
    }
    
    public boolean isIntradayStrategy(String strategyName) {
        if (strategyName == null) return false;
        
        String lower = strategyName.toLowerCase();
        return lower.contains("intraday") ||
               lower.contains("day trading") ||
               lower.contains("scalping") ||
               lower.contains("high frequency") ||
               lower.contains("pivot points") ||
               lower.contains("vwap") ||
               lower.contains("twap") ||
               lower.contains("tick") ||
               lower.contains("minute");
    }
    
    public static class ComplianceResult {
        public final boolean approved;
        public final String vetoReason;
        
        private ComplianceResult(boolean approved, String vetoReason) {
            this.approved = approved;
            this.vetoReason = vetoReason;
        }
        
        public static ComplianceResult approved() {
            return new ComplianceResult(true, null);
        }
        
        public static ComplianceResult blocked(String reason) {
            return new ComplianceResult(false, reason);
        }
        
        public boolean isApproved() {
            return approved;
        }
        
        public String getVetoReason() {
            return vetoReason;
        }
    }
    
    private static class AccountResponse {
        public double equity;
        public double available_margin;
        public String status;
    }
}
