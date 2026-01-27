package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * FundamentalData Entity - Financial metrics for fundamental analysis strategies.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Value Strategies</b>: P/E ratio, Book-to-Market, Earnings Yield</li>
 *   <li><b>Quality Strategies</b>: ROE, ROA, Gross Margin, Profitability</li>
 *   <li><b>Distress Strategies</b>: Debt ratios, Altman Z-score components</li>
 *   <li><b>Dividend Strategies</b>: Dividend yield, payout ratio</li>
 *   <li><b>Growth Strategies</b>: Revenue growth, asset growth</li>
 * </ul>
 * 
 * <h3>Data Source:</h3>
 * Fetched from Yahoo Finance (yfinance) via Python bridge, refreshed daily after market close.
 * 
 * @see FundamentalDataService for data retrieval and refresh
 * @since 2026-01-26 - Phase 1 Data Improvement Plan
 */
@Entity
@Table(name = "fundamental_data", indexes = {
    @Index(name = "idx_fundamental_symbol", columnList = "symbol"),
    @Index(name = "idx_fundamental_symbol_date", columnList = "symbol, report_date"),
    @Index(name = "idx_fundamental_fetched_at", columnList = "fetched_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stock symbol (e.g., "2330.TW", "TSM")
     */
    @Column(length = 20, nullable = false)
    private String symbol;

    /**
     * Stock name (e.g., "Taiwan Semiconductor Manufacturing")
     */
    @Column(length = 200)
    private String name;

    /**
     * Date this fundamental data is reported for (fiscal period end)
     */
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    /**
     * When this data was fetched from the data source
     */
    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    // ========== Valuation Metrics ==========

    /**
     * Earnings Per Share (TTM)
     */
    @Column
    private Double eps;

    /**
     * Price-to-Earnings ratio
     */
    @Column(name = "pe_ratio")
    private Double peRatio;

    /**
     * Forward P/E ratio (using analyst estimates)
     */
    @Column(name = "forward_pe")
    private Double forwardPe;

    /**
     * Price-to-Book ratio
     */
    @Column(name = "pb_ratio")
    private Double pbRatio;

    /**
     * Price-to-Sales ratio
     */
    @Column(name = "ps_ratio")
    private Double psRatio;

    /**
     * Book value per share
     */
    @Column(name = "book_value")
    private Double bookValue;

    /**
     * Market capitalization in TWD/USD
     */
    @Column(name = "market_cap")
    private Double marketCap;

    /**
     * Enterprise Value
     */
    @Column(name = "enterprise_value")
    private Double enterpriseValue;

    /**
     * EV/EBITDA ratio
     */
    @Column(name = "ev_to_ebitda")
    private Double evToEbitda;

    // ========== Profitability Metrics ==========

    /**
     * Return on Equity (ROE)
     */
    @Column
    private Double roe;

    /**
     * Return on Assets (ROA)
     */
    @Column
    private Double roa;

    /**
     * Return on Invested Capital (ROIC)
     */
    @Column
    private Double roic;

    /**
     * Gross profit margin
     */
    @Column(name = "gross_margin")
    private Double grossMargin;

    /**
     * Operating profit margin
     */
    @Column(name = "operating_margin")
    private Double operatingMargin;

    /**
     * Net profit margin
     */
    @Column(name = "net_margin")
    private Double netMargin;

    // ========== Financial Health Metrics ==========

    /**
     * Debt-to-Equity ratio
     */
    @Column(name = "debt_to_equity")
    private Double debtToEquity;

    /**
     * Current ratio (current assets / current liabilities)
     */
    @Column(name = "current_ratio")
    private Double currentRatio;

    /**
     * Quick ratio (acid-test ratio)
     */
    @Column(name = "quick_ratio")
    private Double quickRatio;

    /**
     * Total debt
     */
    @Column(name = "total_debt")
    private Double totalDebt;

    /**
     * Total cash and cash equivalents
     */
    @Column(name = "total_cash")
    private Double totalCash;

    // ========== Dividend Metrics ==========

    /**
     * Annual dividend yield (%)
     */
    @Column(name = "dividend_yield")
    private Double dividendYield;

    /**
     * Dividend payout ratio (%)
     */
    @Column(name = "payout_ratio")
    private Double payoutRatio;

    /**
     * Trailing annual dividend per share
     */
    @Column(name = "dividend_rate")
    private Double dividendRate;

    /**
     * Number of consecutive years with dividend increases
     */
    @Column(name = "dividend_years")
    private Integer dividendYears;

    // ========== Cash Flow Metrics ==========

    /**
     * Operating cash flow
     */
    @Column(name = "operating_cash_flow")
    private Double operatingCashFlow;

    /**
     * Free cash flow
     */
    @Column(name = "free_cash_flow")
    private Double freeCashFlow;

    /**
     * Free cash flow per share
     */
    @Column(name = "fcf_per_share")
    private Double fcfPerShare;

    // ========== Growth Metrics ==========

    /**
     * Revenue (TTM)
     */
    @Column
    private Double revenue;

    /**
     * Revenue growth rate (YoY %)
     */
    @Column(name = "revenue_growth")
    private Double revenueGrowth;

    /**
     * Earnings growth rate (YoY %)
     */
    @Column(name = "earnings_growth")
    private Double earningsGrowth;

    /**
     * Total assets
     */
    @Column(name = "total_assets")
    private Double totalAssets;

    /**
     * Asset growth rate (YoY %)
     */
    @Column(name = "asset_growth")
    private Double assetGrowth;

    // ========== Quality / Accrual Metrics ==========

    /**
     * Accruals ratio (earnings quality indicator)
     * Lower is better - cash-based earnings are more reliable
     */
    @Column(name = "accruals_ratio")
    private Double accrualsRatio;

    /**
     * Shares outstanding
     */
    @Column(name = "shares_outstanding")
    private Long sharesOutstanding;

    /**
     * Net stock issuance (negative = buybacks)
     */
    @Column(name = "net_stock_issuance")
    private Double netStockIssuance;

    // ========== Analyst Data ==========

    /**
     * Number of analyst recommendations
     */
    @Column(name = "analyst_count")
    private Integer analystCount;

    /**
     * Average analyst target price
     */
    @Column(name = "target_price")
    private Double targetPrice;

    /**
     * Analyst recommendation (1=Strong Buy, 5=Strong Sell)
     */
    @Column(name = "recommendation_mean")
    private Double recommendationMean;

    // ========== Beta ==========

    /**
     * Stock beta vs market (volatility measure)
     */
    @Column
    private Double beta;

    /**
     * 52-week high price
     */
    @Column(name = "fifty_two_week_high")
    private Double fiftyTwoWeekHigh;

    /**
     * 52-week low price
     */
    @Column(name = "fifty_two_week_low")
    private Double fiftyTwoWeekLow;

    // ========== Data Source Metadata ==========

    /**
     * Data source identifier (e.g., "yfinance", "twse")
     */
    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "yfinance";

    /**
     * Currency of monetary values (TWD, USD)
     */
    @Column(length = 10)
    @Builder.Default
    private String currency = "TWD";

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = OffsetDateTime.now();
        }
    }

    // ========== Derived Calculations ==========

    /**
     * Calculate earnings yield (inverse of P/E)
     * @return earnings yield as decimal (e.g., 0.05 for 5%)
     */
    public Double getEarningsYield() {
        if (peRatio == null || peRatio == 0) {
            return null;
        }
        return 1.0 / peRatio;
    }

    /**
     * Calculate book-to-market ratio (inverse of P/B)
     * @return book-to-market ratio
     */
    public Double getBookToMarket() {
        if (pbRatio == null || pbRatio == 0) {
            return null;
        }
        return 1.0 / pbRatio;
    }

    /**
     * Calculate simplified Altman Z-score proxy for distress detection
     * Uses available data to approximate financial distress risk
     * @return Z-score proxy (higher = healthier)
     */
    public Double getAltmanZScoreProxy() {
        if (totalAssets == null || totalAssets == 0) {
            return null;
        }
        double score = 0.0;
        int factors = 0;

        // Working capital / Total Assets proxy (using current ratio)
        if (currentRatio != null) {
            score += (currentRatio - 1) * 1.2;
            factors++;
        }

        // Retained Earnings / Total Assets proxy (using ROE)
        if (roe != null) {
            score += roe * 1.4;
            factors++;
        }

        // EBIT / Total Assets proxy (using operating margin)
        if (operatingMargin != null && revenue != null && totalAssets > 0) {
            score += (operatingMargin * revenue / totalAssets) * 3.3;
            factors++;
        }

        // Market Cap / Total Liabilities proxy
        if (marketCap != null && totalDebt != null && totalDebt > 0) {
            score += (marketCap / totalDebt) * 0.6;
            factors++;
        }

        // Sales / Total Assets proxy
        if (revenue != null && totalAssets > 0) {
            score += (revenue / totalAssets);
            factors++;
        }

        return factors > 0 ? score / factors * 5 : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FundamentalData other)) return false;
        if (id != null && other.id != null) {
            return id.equals(other.id);
        }
        return symbol != null && symbol.equals(other.symbol)
                && reportDate != null && reportDate.equals(other.reportDate);
    }

    @Override
    public int hashCode() {
        if (id != null) return id.hashCode();
        int result = symbol != null ? symbol.hashCode() : 0;
        result = 31 * result + (reportDate != null ? reportDate.hashCode() : 0);
        return result;
    }
}
