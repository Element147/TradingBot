package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_data_candle_segments", indexes = {
    @Index(name = "idx_market_segments_coverage", columnList = "series_id,timeframe,coverage_start,coverage_end"),
    @Index(name = "idx_market_segments_resolution", columnList = "series_id,timeframe,resolution_tier,source_priority,coverage_start"),
    @Index(name = "idx_market_segments_dataset", columnList = "dataset_id"),
    @Index(name = "idx_market_segments_import_job", columnList = "import_job_id"),
    @Index(name = "idx_market_segments_status_archived", columnList = "segment_status,archived")
})
public class MarketDataCandleSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private BacktestDataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id")
    private MarketDataImportJob importJob;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private MarketDataSeries series;

    @NotNull
    @Size(min = 2, max = 10)
    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @NotNull
    @Size(min = 2, max = 30)
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @NotNull
    @Column(name = "coverage_start", nullable = false)
    private LocalDateTime coverageStart;

    @NotNull
    @Column(name = "coverage_end", nullable = false)
    private LocalDateTime coverageEnd;

    @NotNull
    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @NotNull
    @Size(min = 64, max = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String checksumSha256;

    @NotNull
    @Size(min = 1, max = 32)
    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @NotNull
    @Size(min = 2, max = 20)
    @Column(name = "resolution_tier", nullable = false, length = 20)
    private String resolutionTier;

    @NotNull
    @Column(name = "source_priority", nullable = false)
    private Short sourcePriority;

    @NotNull
    @Size(min = 2, max = 20)
    @Column(name = "segment_status", nullable = false, length = 20)
    private String segmentStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_segment_id")
    private MarketDataCandleSegment supersedesSegment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_segment_id")
    private MarketDataCandleSegment duplicateOfSegment;

    @NotNull
    @Size(min = 2, max = 20)
    @Column(name = "storage_encoding", nullable = false, length = 20)
    private String storageEncoding;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "lineage_json")
    private String lineageJson;

    @Size(max = 255)
    @Column(name = "compressed_artifact_uri", length = 255)
    private String compressedArtifactUri;

    @NotNull
    @Column(nullable = false)
    private Boolean archived;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Size(max = 255)
    @Column(name = "archive_reason", length = 255)
    private String archiveReason;

    @Size(max = 120)
    @Column(name = "provider_batch_reference", length = 120)
    private String providerBatchReference;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = "ohlcv-v1";
        }
        if (resolutionTier == null || resolutionTier.isBlank()) {
            resolutionTier = "EXACT_RAW";
        }
        if (segmentStatus == null || segmentStatus.isBlank()) {
            segmentStatus = "ACTIVE";
        }
        if (storageEncoding == null || storageEncoding.isBlank()) {
            storageEncoding = "ROW_STORE";
        }
        if (sourcePriority == null) {
            sourcePriority = (short) 100;
        }
        if (archived == null) {
            archived = Boolean.FALSE;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public BacktestDataset getDataset() {
        return dataset;
    }

    public void setDataset(BacktestDataset dataset) {
        this.dataset = dataset;
    }

    public MarketDataImportJob getImportJob() {
        return importJob;
    }

    public void setImportJob(MarketDataImportJob importJob) {
        this.importJob = importJob;
    }

    public MarketDataSeries getSeries() {
        return series;
    }

    public void setSeries(MarketDataSeries series) {
        this.series = series;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getCoverageStart() {
        return coverageStart;
    }

    public void setCoverageStart(LocalDateTime coverageStart) {
        this.coverageStart = coverageStart;
    }

    public LocalDateTime getCoverageEnd() {
        return coverageEnd;
    }

    public void setCoverageEnd(LocalDateTime coverageEnd) {
        this.coverageEnd = coverageEnd;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getResolutionTier() {
        return resolutionTier;
    }

    public void setResolutionTier(String resolutionTier) {
        this.resolutionTier = resolutionTier;
    }

    public Short getSourcePriority() {
        return sourcePriority;
    }

    public void setSourcePriority(Short sourcePriority) {
        this.sourcePriority = sourcePriority;
    }

    public String getSegmentStatus() {
        return segmentStatus;
    }

    public void setSegmentStatus(String segmentStatus) {
        this.segmentStatus = segmentStatus;
    }

    public MarketDataCandleSegment getSupersedesSegment() {
        return supersedesSegment;
    }

    public void setSupersedesSegment(MarketDataCandleSegment supersedesSegment) {
        this.supersedesSegment = supersedesSegment;
    }

    public MarketDataCandleSegment getDuplicateOfSegment() {
        return duplicateOfSegment;
    }

    public void setDuplicateOfSegment(MarketDataCandleSegment duplicateOfSegment) {
        this.duplicateOfSegment = duplicateOfSegment;
    }

    public String getStorageEncoding() {
        return storageEncoding;
    }

    public void setStorageEncoding(String storageEncoding) {
        this.storageEncoding = storageEncoding;
    }

    public String getLineageJson() {
        return lineageJson;
    }

    public void setLineageJson(String lineageJson) {
        this.lineageJson = lineageJson;
    }

    public String getCompressedArtifactUri() {
        return compressedArtifactUri;
    }

    public void setCompressedArtifactUri(String compressedArtifactUri) {
        this.compressedArtifactUri = compressedArtifactUri;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public String getArchiveReason() {
        return archiveReason;
    }

    public void setArchiveReason(String archiveReason) {
        this.archiveReason = archiveReason;
    }

    public String getProviderBatchReference() {
        return providerBatchReference;
    }

    public void setProviderBatchReference(String providerBatchReference) {
        this.providerBatchReference = providerBatchReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
