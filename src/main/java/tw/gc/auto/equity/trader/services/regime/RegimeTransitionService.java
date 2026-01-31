package tw.gc.auto.equity.trader.services.regime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.MarketRegime;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.RegimeAnalysis;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for detecting and managing market regime transitions.
 * 
 * <p>Regime transitions are critical moments that require special handling:
 * <ul>
 *   <li>False transitions (whipsaw) cause unnecessary position changes</li>
 *   <li>True transitions require gradual position scaling</li>
 *   <li>Confirmation period prevents over-trading on noise</li>
 * </ul>
 * 
 * <h3>Transition Detection Rules:</h3>
 * <ul>
 *   <li><b>Confirmation Period:</b> 3 days of consistent regime signals</li>
 *   <li><b>Gradual Scaling:</b> Position sizes adjust over 5 days during transition</li>
 *   <li><b>Whipsaw Protection:</b> Ignore transitions shorter than confirmation</li>
 *   <li><b>Crisis Override:</b> Crisis regime triggers immediately (no confirmation)</li>
 * </ul>
 * 
 * @see MarketRegimeService for regime classification
 * @see RegimeStrategyMapper for strategy selection
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegimeTransitionService {

    /** Days required to confirm a regime change */
    private static final int CONFIRMATION_DAYS = 3;
    
    /** Days over which to scale positions during transition */
    private static final int TRANSITION_SCALING_DAYS = 5;
    
    /** Maximum history entries to keep per symbol */
    private static final int MAX_HISTORY_SIZE = 30;
    
    /** Minimum confidence to trigger transition alert */
    private static final double MIN_TRANSITION_CONFIDENCE = 0.6;

    private final MarketRegimeService marketRegimeService;
    
    // Track regime history per symbol
    private final Map<String, Deque<RegimeSnapshot>> regimeHistory = new ConcurrentHashMap<>();
    
    // Track active transitions
    private final Map<String, TransitionState> activeTransitions = new ConcurrentHashMap<>();

    /**
     * Snapshot of regime state at a point in time.
     */
    public record RegimeSnapshot(
            MarketRegime regime,
            double confidence,
            LocalDateTime timestamp
    ) {}

    /**
     * Current transition state for a symbol.
     */
    public record TransitionState(
            String symbol,
            MarketRegime fromRegime,
            MarketRegime toRegime,
            int confirmationCount,
            LocalDateTime transitionStart,
            double scalingProgress,
            boolean confirmed
    ) {
        /**
         * Get remaining confirmation days needed.
         */
        public int remainingConfirmationDays() {
            return Math.max(0, CONFIRMATION_DAYS - confirmationCount);
        }

        /**
         * Check if transition is complete (fully scaled).
         */
        public boolean isComplete() {
            return scalingProgress >= 1.0;
        }
    }

    /**
     * Transition event for notification.
     */
    public record TransitionEvent(
            String symbol,
            MarketRegime fromRegime,
            MarketRegime toRegime,
            TransitionEventType eventType,
            double confidence,
            String description,
            LocalDateTime timestamp
    ) {}

    /**
     * Type of transition event.
     */
    public enum TransitionEventType {
        /** Initial signal of potential regime change */
        SIGNAL_DETECTED,
        /** Regime change confirmed after confirmation period */
        CONFIRMED,
        /** False signal - regime reverted before confirmation */
        CANCELLED,
        /** Transition scaling complete */
        COMPLETED,
        /** Immediate crisis transition (no confirmation) */
        CRISIS_TRIGGERED
    }

    /**
     * Update regime analysis and detect transitions.
     * 
     * @param symbol the stock symbol
     * @param marketData recent market data
     * @return transition event if one occurred, null otherwise
     */
    public TransitionEvent updateAndDetectTransition(String symbol, List<MarketData> marketData) {
        RegimeAnalysis currentAnalysis = marketRegimeService.analyzeRegime(symbol, marketData);
        return processRegimeUpdate(symbol, currentAnalysis);
    }

    /**
     * Process a new regime analysis and detect transitions.
     * 
     * @param symbol the stock symbol
     * @param analysis the current regime analysis
     * @return transition event if one occurred, null otherwise
     */
    public TransitionEvent processRegimeUpdate(String symbol, RegimeAnalysis analysis) {
        Deque<RegimeSnapshot> history = regimeHistory.computeIfAbsent(symbol, 
                k -> new ArrayDeque<>());
        
        RegimeSnapshot currentSnapshot = new RegimeSnapshot(
                analysis.regime(), analysis.confidence(), LocalDateTime.now());
        
        // Add to history
        history.addLast(currentSnapshot);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
        
        // Get previous stable regime
        MarketRegime previousRegime = getStableRegime(history, currentSnapshot);
        
        // Check for crisis - immediate trigger
        if (analysis.regime() == MarketRegime.CRISIS) {
            return handleCrisisTrigger(symbol, previousRegime, analysis);
        }
        
        // Check for regime change
        if (previousRegime != null && previousRegime != analysis.regime()) {
            return handlePotentialTransition(symbol, previousRegime, analysis);
        }
        
        // Check existing transition state
        TransitionState existingTransition = activeTransitions.get(symbol);
        if (existingTransition != null) {
            return updateExistingTransition(symbol, existingTransition, analysis);
        }
        
        return null;
    }

    /**
     * Get the current transition state for a symbol.
     * 
     * @param symbol the stock symbol
     * @return current transition state or null if no active transition
     */
    public TransitionState getTransitionState(String symbol) {
        return activeTransitions.get(symbol);
    }

    /**
     * Check if a symbol is currently in regime transition.
     * 
     * @param symbol the stock symbol
     * @return true if transitioning
     */
    public boolean isInTransition(String symbol) {
        TransitionState state = activeTransitions.get(symbol);
        return state != null && !state.isComplete();
    }

    /**
     * Get the position scaling factor during transition.
     * 
     * <p>During transition, position sizes are gradually scaled from old regime
     * recommendations to new regime recommendations.
     * 
     * @param symbol the stock symbol
     * @return scaling progress (0.0 = old regime, 1.0 = new regime)
     */
    public double getTransitionScalingFactor(String symbol) {
        TransitionState state = activeTransitions.get(symbol);
        if (state == null || !state.confirmed()) {
            return 1.0; // No transition, use current regime fully
        }
        return state.scalingProgress();
    }

    /**
     * Calculate blended position size during transition.
     * 
     * @param symbol the stock symbol
     * @param oldRegimeSize position size for old regime
     * @param newRegimeSize position size for new regime
     * @return blended position size
     */
    public double blendPositionSize(String symbol, double oldRegimeSize, double newRegimeSize) {
        double factor = getTransitionScalingFactor(symbol);
        return oldRegimeSize * (1 - factor) + newRegimeSize * factor;
    }

    /**
     * Get regime history for a symbol.
     * 
     * @param symbol the stock symbol
     * @param days number of days to retrieve
     * @return list of regime snapshots
     */
    public List<RegimeSnapshot> getRegimeHistory(String symbol, int days) {
        Deque<RegimeSnapshot> history = regimeHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<RegimeSnapshot> result = new ArrayList<>();
        Iterator<RegimeSnapshot> iter = history.descendingIterator();
        int count = 0;
        while (iter.hasNext() && count < days) {
            result.add(iter.next());
            count++;
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Get the dominant regime over a period.
     * 
     * @param symbol the stock symbol
     * @param days lookback period
     * @return most frequent regime or null
     */
    public MarketRegime getDominantRegime(String symbol, int days) {
        List<RegimeSnapshot> history = getRegimeHistory(symbol, days);
        if (history.isEmpty()) {
            return null;
        }
        
        Map<MarketRegime, Long> counts = new HashMap<>();
        for (RegimeSnapshot snapshot : history) {
            counts.merge(snapshot.regime(), 1L, Long::sum);
        }
        
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Clear all transition data for a symbol.
     * 
     * @param symbol the stock symbol
     */
    public void clearTransitionData(String symbol) {
        regimeHistory.remove(symbol);
        activeTransitions.remove(symbol);
        log.debug("Cleared transition data for {}", symbol);
    }

    /**
     * Clear all transition data.
     */
    public void clearAllTransitionData() {
        regimeHistory.clear();
        activeTransitions.clear();
        log.debug("Cleared all transition data");
    }

    /**
     * Advance transition scaling progress (call daily).
     * 
     * @param symbol the stock symbol
     * @return updated transition state or null
     */
    public TransitionState advanceTransitionScaling(String symbol) {
        TransitionState current = activeTransitions.get(symbol);
        if (current == null || !current.confirmed() || current.isComplete()) {
            return current;
        }
        
        // Advance scaling by 1/TRANSITION_SCALING_DAYS
        double newProgress = Math.min(1.0, 
                current.scalingProgress() + (1.0 / TRANSITION_SCALING_DAYS));
        
        TransitionState updated = new TransitionState(
                current.symbol(),
                current.fromRegime(),
                current.toRegime(),
                current.confirmationCount(),
                current.transitionStart(),
                newProgress,
                true
        );
        
        activeTransitions.put(symbol, updated);
        
        log.debug("Advanced transition scaling for {}: {:.1f}%", symbol, newProgress * 100);
        
        return updated;
    }

    // ===== PRIVATE METHODS =====

    private MarketRegime getStableRegime(Deque<RegimeSnapshot> history, 
                                          RegimeSnapshot current) {
        if (history.size() < CONFIRMATION_DAYS + 1) {
            return null;
        }
        
        // Look at history before current, excluding recent changes
        Iterator<RegimeSnapshot> iter = history.descendingIterator();
        iter.next(); // Skip current
        
        MarketRegime candidate = null;
        int count = 0;
        
        while (iter.hasNext() && count < CONFIRMATION_DAYS * 2) {
            RegimeSnapshot snapshot = iter.next();
            if (candidate == null) {
                candidate = snapshot.regime();
            } else if (snapshot.regime() == candidate) {
                count++;
                if (count >= CONFIRMATION_DAYS) {
                    return candidate;
                }
            } else {
                // Reset if different
                candidate = snapshot.regime();
                count = 1;
            }
            count++;
        }
        
        return candidate;
    }

    private TransitionEvent handleCrisisTrigger(String symbol, MarketRegime fromRegime,
                                                 RegimeAnalysis analysis) {
        // Crisis triggers immediately - no confirmation needed
        TransitionState state = new TransitionState(
                symbol,
                fromRegime != null ? fromRegime : MarketRegime.RANGING,
                MarketRegime.CRISIS,
                CONFIRMATION_DAYS, // Immediately confirmed
                LocalDateTime.now(),
                1.0, // Full scaling immediately
                true
        );
        
        activeTransitions.put(symbol, state);
        
        log.warn("🚨 CRISIS TRIGGERED for {}: {} → CRISIS (immediate)", 
                symbol, fromRegime);
        
        return new TransitionEvent(
                symbol,
                state.fromRegime(),
                MarketRegime.CRISIS,
                TransitionEventType.CRISIS_TRIGGERED,
                analysis.confidence(),
                String.format("Crisis triggered! Vol=%.1f%%, Drawdown=%.1f%%", 
                        analysis.volatility(), analysis.recentDrawdown() * 100),
                LocalDateTime.now()
        );
    }

    private TransitionEvent handlePotentialTransition(String symbol, MarketRegime fromRegime,
                                                       RegimeAnalysis analysis) {
        TransitionState existing = activeTransitions.get(symbol);
        
        if (existing != null && existing.toRegime() == analysis.regime()) {
            // Continue existing transition
            return updateExistingTransition(symbol, existing, analysis);
        }
        
        // New potential transition
        if (analysis.confidence() < MIN_TRANSITION_CONFIDENCE) {
            log.debug("Low confidence regime signal for {}: {} → {} (conf: {:.1f}%)", 
                    symbol, fromRegime, analysis.regime(), analysis.confidence() * 100);
            return null;
        }
        
        // Create new transition state (unconfirmed)
        TransitionState newState = new TransitionState(
                symbol,
                fromRegime,
                analysis.regime(),
                1, // First confirmation day
                LocalDateTime.now(),
                0.0, // No scaling yet
                false
        );
        
        activeTransitions.put(symbol, newState);
        
        log.info("📊 Potential regime transition for {}: {} → {} (day 1/{})", 
                symbol, fromRegime, analysis.regime(), CONFIRMATION_DAYS);
        
        return new TransitionEvent(
                symbol,
                fromRegime,
                analysis.regime(),
                TransitionEventType.SIGNAL_DETECTED,
                analysis.confidence(),
                String.format("Potential regime change detected. %d days to confirm.", 
                        CONFIRMATION_DAYS - 1),
                LocalDateTime.now()
        );
    }

    private TransitionEvent updateExistingTransition(String symbol, TransitionState existing,
                                                      RegimeAnalysis analysis) {
        // Check if regime reverted (false signal)
        if (analysis.regime() == existing.fromRegime()) {
            activeTransitions.remove(symbol);
            
            log.info("❌ Regime transition cancelled for {}: {} → {} reverted", 
                    symbol, existing.fromRegime(), existing.toRegime());
            
            return new TransitionEvent(
                    symbol,
                    existing.fromRegime(),
                    existing.toRegime(),
                    TransitionEventType.CANCELLED,
                    analysis.confidence(),
                    "Regime reverted to previous state - false signal",
                    LocalDateTime.now()
            );
        }
        
        // Continue same direction
        if (analysis.regime() == existing.toRegime()) {
            int newCount = existing.confirmationCount() + 1;
            
            if (!existing.confirmed() && newCount >= CONFIRMATION_DAYS) {
                // Transition confirmed!
                TransitionState confirmed = new TransitionState(
                        symbol,
                        existing.fromRegime(),
                        existing.toRegime(),
                        newCount,
                        existing.transitionStart(),
                        1.0 / TRANSITION_SCALING_DAYS, // Start scaling
                        true
                );
                
                activeTransitions.put(symbol, confirmed);
                
                log.info("✅ Regime transition CONFIRMED for {}: {} → {}", 
                        symbol, existing.fromRegime(), existing.toRegime());
                
                return new TransitionEvent(
                        symbol,
                        existing.fromRegime(),
                        existing.toRegime(),
                        TransitionEventType.CONFIRMED,
                        analysis.confidence(),
                        String.format("Regime change confirmed after %d days. Scaling positions.", 
                                CONFIRMATION_DAYS),
                        LocalDateTime.now()
                );
            } else {
                // Update confirmation count
                TransitionState updated = new TransitionState(
                        symbol,
                        existing.fromRegime(),
                        existing.toRegime(),
                        newCount,
                        existing.transitionStart(),
                        existing.scalingProgress(),
                        existing.confirmed()
                );
                
                activeTransitions.put(symbol, updated);
                
                log.debug("Transition progress for {}: day {}/{}", 
                        symbol, newCount, CONFIRMATION_DAYS);
            }
        }
        
        return null;
    }
}
