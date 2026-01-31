package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FillAnalyticsService - Phase 8
 */
@ExtendWith(MockitoExtension.class)
class FillAnalyticsServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private FillAnalyticsService fillAnalytics;

    @BeforeEach
    void setUp() {
        reset(tradeRepository);
        fillAnalytics.clearStatistics();
    }

    @Test
    void shouldRecordExecution() {
        // Given
        String symbol = "2330";
        String orderType = "MARKET";

        // When
        fillAnalytics.recordExecution(symbol, orderType, 100.0, 100.5, 100, 100, LocalDateTime.now());

        // Then
        FillAnalyticsService.ExecutionStats stats = fillAnalytics.getExecutionStats(symbol);
        assertThat(stats).isNotNull();
        assertThat(stats.getSymbol()).isEqualTo(symbol);
        assertThat(stats.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void shouldCalculateAverageSlippage() {
        // Given: Multiple executions with varying slippage
        String symbol = "2330";
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.1, 100, 100, LocalDateTime.now()); // 0.1% slippage
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.3, 100, 100, LocalDateTime.now()); // 0.3% slippage
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.2, 100, 100, LocalDateTime.now()); // 0.2% slippage

        // When
        FillAnalyticsService.ExecutionStats stats = fillAnalytics.getExecutionStats(symbol);

        // Then: Average should be 0.2%
        assertThat(stats.getAverageSlippage()).isCloseTo(0.002, within(0.0001));
        assertThat(stats.getExecutionCount()).isEqualTo(3);
    }

    @Test
    void shouldCalculateAverageFillRate() {
        // Given: Multiple executions with varying fill rates
        String symbol = "2330";
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.0, 100, 100, LocalDateTime.now()); // 100% fill
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.0, 100, 80, LocalDateTime.now());  // 80% fill
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.0, 100, 90, LocalDateTime.now());  // 90% fill

        // When
        FillAnalyticsService.ExecutionStats stats = fillAnalytics.getExecutionStats(symbol);

        // Then: Average should be 90%
        assertThat(stats.getAverageFillRate()).isCloseTo(0.90, within(0.01));
    }

    @Test
    void shouldRecommendMarketOrderWhenInsufficientData() {
        // Given: No execution history
        String symbol = "9999";

        // When
        FillAnalyticsService.OrderTypeRecommendation rec = 
            fillAnalytics.getOrderTypeRecommendation(symbol, 0.02);

        // Then: Should default to MARKET
        assertThat(rec.getRecommendedType()).isEqualTo("MARKET");
        assertThat(rec.getConfidence()).isLessThanOrEqualTo(60);
    }

    @Test
    void shouldRecommendMarketOrderForBetterFillRate() {
        // Given: Market orders have better fill rate
        String symbol = "2330";
        
        // Market orders: 100% fill rate, 0.2% slippage
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.2, 100, 100, LocalDateTime.now());
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.2, 100, 100, LocalDateTime.now());
        
        // Limit orders: 80% fill rate, 0.1% slippage
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.1, 100, 80, LocalDateTime.now());
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.1, 100, 80, LocalDateTime.now());

        // When
        FillAnalyticsService.OrderTypeRecommendation rec = 
            fillAnalytics.getOrderTypeRecommendation(symbol, 0.02);

        // Then: Should prefer market orders (better fill rate outweighs higher slippage)
        assertThat(rec.getRecommendedType()).isEqualTo("MARKET");
    }

    @Test
    void shouldRecommendLimitOrderForLowerSlippage() {
        // Given: Limit orders have much lower slippage
        String symbol = "2330";
        
        // Market orders: 100% fill rate, 1.0% slippage
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 101.0, 100, 100, LocalDateTime.now());
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 101.0, 100, 100, LocalDateTime.now());
        
        // Limit orders: 95% fill rate, 0.1% slippage
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.1, 100, 95, LocalDateTime.now());
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.1, 100, 95, LocalDateTime.now());

        // When
        FillAnalyticsService.OrderTypeRecommendation rec = 
            fillAnalytics.getOrderTypeRecommendation(symbol, 0.02);

        // Then: Should prefer limit orders (much lower slippage)
        assertThat(rec.getRecommendedType()).isEqualTo("LIMIT");
    }

    @Test
    void shouldAdjustRecommendationForHighVolatility() {
        // Given: Equal performance, but high volatility
        String symbol = "2330";
        
        fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.2, 100, 100, LocalDateTime.now());
        fillAnalytics.recordExecution(symbol, "LIMIT", 100.0, 100.2, 100, 100, LocalDateTime.now());

        // When: High volatility (> 0.03)
        FillAnalyticsService.OrderTypeRecommendation rec = 
            fillAnalytics.getOrderTypeRecommendation(symbol, 0.05);

        // Then: Should prefer market orders in high volatility
        assertThat(rec.getRecommendedType()).isEqualTo("MARKET");
    }

    @Test
    void shouldAnalyzeExecutionQualityFromTrades() {
        // Given: Recent trades
        List<Trade> trades = createMockTrades(10);
        when(tradeRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(trades);

        // When
        FillAnalyticsService.ExecutionQualityReport report = fillAnalytics.analyzeExecutionQuality(7);

        // Then
        assertThat(report.getTotalTrades()).isEqualTo(10);
        assertThat(report.getAverageSlippage()).isGreaterThan(0);
        assertThat(report.getAverageFillRate()).isGreaterThan(0);
    }

    @Test
    void shouldReturnEmptyReportWhenNoTrades() {
        // Given: No recent trades
        when(tradeRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(new ArrayList<>());

        // When
        FillAnalyticsService.ExecutionQualityReport report = fillAnalytics.analyzeExecutionQuality(7);

        // Then
        assertThat(report.getTotalTrades()).isEqualTo(0);
        assertThat(report.getAverageSlippage()).isEqualTo(0.0);
    }

    @Test
    void shouldAssignQualityGrade() {
        // Given: Reports with different slippage levels
        FillAnalyticsService.ExecutionQualityReport excellentReport = 
            FillAnalyticsService.ExecutionQualityReport.builder()
                .periodDays(7)
                .totalTrades(100)
                .averageSlippage(0.0005) // 0.05%
                .medianSlippage(0.0005)
                .worstSlippage(0.001)
                .averageFillRate(1.0)
                .build();
        
        FillAnalyticsService.ExecutionQualityReport poorReport = 
            FillAnalyticsService.ExecutionQualityReport.builder()
                .periodDays(7)
                .totalTrades(100)
                .averageSlippage(0.015) // 1.5%
                .medianSlippage(0.015)
                .worstSlippage(0.03)
                .averageFillRate(0.9)
                .build();

        // When/Then
        assertThat(excellentReport.getQualityGrade()).isEqualTo("A+");
        assertThat(poorReport.getQualityGrade()).isEqualTo("D");
    }

    @Test
    void shouldLimitExecutionHistorySize() {
        // Given: Record more than 100 executions
        String symbol = "2330";
        
        for (int i = 0; i < 150; i++) {
            fillAnalytics.recordExecution(symbol, "MARKET", 100.0, 100.1, 100, 100, LocalDateTime.now());
        }

        // When
        FillAnalyticsService.ExecutionStats stats = fillAnalytics.getExecutionStats(symbol);

        // Then: Should only keep last 100
        assertThat(stats.getExecutionCount()).isEqualTo(100);
    }

    @Test
    void shouldClearStatistics() {
        // Given: Some recorded executions
        fillAnalytics.recordExecution("2330", "MARKET", 100.0, 100.1, 100, 100, LocalDateTime.now());
        
        assertThat(fillAnalytics.getAllExecutionStats()).isNotEmpty();

        // When
        fillAnalytics.clearStatistics();

        // Then
        assertThat(fillAnalytics.getAllExecutionStats()).isEmpty();
    }

    @Test
    void shouldGetAllExecutionStats() {
        // Given: Multiple symbols
        fillAnalytics.recordExecution("2330", "MARKET", 100.0, 100.1, 100, 100, LocalDateTime.now());
        fillAnalytics.recordExecution("2317", "LIMIT", 50.0, 50.05, 100, 100, LocalDateTime.now());

        // When
        Map<String, FillAnalyticsService.ExecutionStats> allStats = fillAnalytics.getAllExecutionStats();

        // Then
        assertThat(allStats).hasSize(2);
        assertThat(allStats).containsKeys("2330_MARKET", "2317_LIMIT");
    }

    /**
     * Helper method to create mock trades
     */
    private List<Trade> createMockTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < count; i++) {
            Trade trade = new Trade();
            trade.setId((long) i);
            trade.setSymbol("2330");
            trade.setStrategyName("TestStrategy");
            trade.setAction(Trade.TradeAction.BUY);
            trade.setQuantity(100);
            trade.setEntryPrice(100.0);
            trade.setExitPrice(101.0);
            trade.setRealizedPnL(100.0);
            trade.setTimestamp(now.minusDays(i));
            trades.add(trade);
        }
        
        return trades;
    }

    /**
     * Helper method for double comparison tolerance
     */
    private org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
