package com.algotrader.bot.entity;

import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataImportJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_data_import_jobs", indexes = {
    @Index(name = "idx_market_data_import_ready_scan", columnList = "status,next_retry_at,created_at")
})
public class MarketDataImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 2, max = 40)
    @Column(name = "provider_id", nullable = false, length = 40)
    private String providerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private MarketDataAssetType assetType;

    @NotNull
    @Size(min = 3, max = 100)
    @Column(name = "dataset_name", nullable = false, length = 100)
    private String datasetName;

    @NotNull
    @Size(min = 1, max = 1000)
    @Column(name = "symbols_csv", nullable = false, length = 1000)
    private String symbolsCsv;

    @NotNull
    @Size(min = 2, max = 10)
    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Column(name = "adjusted", nullable = false)
    private Boolean adjusted;

    @NotNull
    @Column(name = "regular_session_only", nullable = false)
    private Boolean regularSessionOnly;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MarketDataImportJobStatus status;

    @Size(max = 2000)
    @Column(name = "status_message", length = 2000)
    private String statusMessage;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @NotNull
    @Column(name = "current_symbol_index", nullable = false)
    private Integer currentSymbolIndex;

    @Column(name = "current_chunk_start")
    private LocalDateTime currentChunkStart;

    @NotNull
    @Column(name = "imported_row_count", nullable = false)
    private Integer importedRowCount;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "staged_csv_data")
    private byte[] stagedCsvData;

    @Column(name = "dataset_id")
    private Long datasetId;

    @NotNull
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @NotNull
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @NotNull
    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = MarketDataImportJobStatus.QUEUED;
        }
        if (adjusted == null) {
            adjusted = Boolean.FALSE;
        }
        if (regularSessionOnly == null) {
            regularSessionOnly = Boolean.FALSE;
        }
        if (currentSymbolIndex == null) {
            currentSymbolIndex = 0;
        }
        if (importedRowCount == null) {
            importedRowCount = 0;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetryCount == null) {
            maxRetryCount = 4;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public MarketDataAssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(MarketDataAssetType assetType) {
        this.assetType = assetType;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getSymbolsCsv() {
        return symbolsCsv;
    }

    public void setSymbolsCsv(String symbolsCsv) {
        this.symbolsCsv = symbolsCsv;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getAdjusted() {
        return adjusted;
    }

    public void setAdjusted(Boolean adjusted) {
        this.adjusted = adjusted;
    }

    public Boolean getRegularSessionOnly() {
        return regularSessionOnly;
    }

    public void setRegularSessionOnly(Boolean regularSessionOnly) {
        this.regularSessionOnly = regularSessionOnly;
    }

    public MarketDataImportJobStatus getStatus() {
        return status;
    }

    public void setStatus(MarketDataImportJobStatus status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Integer getCurrentSymbolIndex() {
        return currentSymbolIndex;
    }

    public void setCurrentSymbolIndex(Integer currentSymbolIndex) {
        this.currentSymbolIndex = currentSymbolIndex;
    }

    public LocalDateTime getCurrentChunkStart() {
        return currentChunkStart;
    }

    public void setCurrentChunkStart(LocalDateTime currentChunkStart) {
        this.currentChunkStart = currentChunkStart;
    }

    public Integer getImportedRowCount() {
        return importedRowCount;
    }

    public void setImportedRowCount(Integer importedRowCount) {
        this.importedRowCount = importedRowCount;
    }

    public byte[] getStagedCsvData() {
        return stagedCsvData;
    }

    public void setStagedCsvData(byte[] stagedCsvData) {
        this.stagedCsvData = stagedCsvData;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
