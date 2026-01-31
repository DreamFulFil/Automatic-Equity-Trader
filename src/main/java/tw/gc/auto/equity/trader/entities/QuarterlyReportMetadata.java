package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Quarterly Report Metadata Entity
 *
 * Stores metadata for downloaded quarterly reports and their summaries.
 */
@Entity
@Table(name = "quarterly_report_metadata", indexes = {
    @Index(name = "idx_quarterly_report_ticker", columnList = "ticker"),
    @Index(name = "idx_quarterly_report_year_quarter", columnList = "report_year, report_quarter"),
    @Index(name = "idx_quarterly_report_summarized", columnList = "summarized_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarterlyReportMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "report_year", nullable = false)
    private Integer reportYear;

    @Column(name = "report_quarter", nullable = false)
    private Integer reportQuarter;

    @Column(name = "roc_year")
    private Integer rocYear;

    @Column(name = "report_type", length = 10)
    @Builder.Default
    private String reportType = "F01";

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "downloaded_at")
    private LocalDateTime downloadedAt;

    @Column(name = "summarized_at")
    private LocalDateTime summarizedAt;
}
