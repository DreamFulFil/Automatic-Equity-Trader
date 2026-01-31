package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Annual Report Metadata Entity
 * 
 * Phase 7: Annual Report RAG Integration
 * Stores metadata for downloaded annual reports and their summaries.
 */
@Entity
@Table(name = "annual_report_metadata", indexes = {
    @Index(name = "idx_annual_report_ticker", columnList = "ticker"),
    @Index(name = "idx_annual_report_year", columnList = "report_year"),
    @Index(name = "idx_annual_report_indexed", columnList = "indexed_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnualReportMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String ticker;
    
    @Column(name = "report_year")
    private Integer reportYear;
    
    @Column(name = "roc_year")
    private Integer rocYear;
    
    @Column(name = "report_type", length = 10)
    @Builder.Default
    private String reportType = "F04";
    
    @Column(name = "file_path", length = 500)
    private String filePath;
    
    @Column(name = "index_path", length = 500)
    private String indexPath;
    
    @Column(name = "chunk_count")
    private Integer chunkCount;
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    /**
     * RAG-based fundamental score (0-100)
     * Higher = more positive report content
     */
    @Column(name = "fundamental_score")
    private Double fundamentalScore;
    
    /**
     * P/E ratio from report (if available)
     */
    @Column(name = "pe_ratio")
    private Double peRatio;
    
    /**
     * Revenue growth rate from report (%)
     */
    @Column(name = "revenue_growth_pct")
    private Double revenueGrowthPct;
    
    /**
     * Debt-to-equity ratio from report
     */
    @Column(name = "debt_to_equity")
    private Double debtToEquity;
    
    @Column(name = "downloaded_at")
    private LocalDateTime downloadedAt;
    
    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;
    
    @Column(name = "summarized_at")
    private LocalDateTime summarizedAt;
}
