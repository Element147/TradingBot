package com.algotrader.bot.service;

import com.algotrader.bot.controller.BacktestDatasetArchiveRequest;
import com.algotrader.bot.controller.BacktestDatasetResponse;
import com.algotrader.bot.controller.BacktestDatasetRetentionReportResponse;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.BacktestDatasetUsageSummary;
import com.algotrader.bot.repository.BacktestResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BacktestDatasetLifecycleService {

    private static final int UNUSED_ARCHIVE_CANDIDATE_DAYS = 14;
    private static final int STALE_REFERENCE_DAYS = 60;

    private final BacktestDatasetRepository backtestDatasetRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final OperatorAuditService operatorAuditService;
    private final BackendOperationMetrics backendOperationMetrics;

    public BacktestDatasetLifecycleService(BacktestDatasetRepository backtestDatasetRepository,
                                           BacktestResultRepository backtestResultRepository,
                                           OperatorAuditService operatorAuditService,
                                           BackendOperationMetrics backendOperationMetrics) {
        this.backtestDatasetRepository = backtestDatasetRepository;
        this.backtestResultRepository = backtestResultRepository;
        this.operatorAuditService = operatorAuditService;
        this.backendOperationMetrics = backendOperationMetrics;
    }

    public List<BacktestDatasetResponse> listDatasets() {
        long startedAt = System.nanoTime();
        List<BacktestDataset> datasets = backtestDatasetRepository.findAllByOrderByUploadedAtDesc();
        Map<Long, DatasetUsageStats> usageStatsByDatasetId = getUsageStatsByDatasetId();
        Map<String, Integer> duplicateCountByChecksum = getDuplicateCountByChecksum(datasets);

        List<BacktestDatasetResponse> responses = datasets.stream()
            .map(dataset -> toResponse(dataset, usageStatsByDatasetId, duplicateCountByChecksum))
            .toList();
        backendOperationMetrics.record("read", "dataset_listing", "inventory", System.nanoTime() - startedAt, responses.size(), 0L);
        return responses;
    }

    public BacktestDatasetRetentionReportResponse getRetentionReport() {
        List<BacktestDataset> datasets = backtestDatasetRepository.findAllByOrderByUploadedAtDesc();
        Map<Long, DatasetUsageStats> usageStatsByDatasetId = getUsageStatsByDatasetId();
        Map<String, Integer> duplicateCountByChecksum = getDuplicateCountByChecksum(datasets);

        long archivedDatasets = datasets.stream()
            .filter(dataset -> Boolean.TRUE.equals(dataset.getArchived()))
            .count();
        long activeDatasets = datasets.size() - archivedDatasets;
        long archiveCandidateDatasets = datasets.stream()
            .filter(dataset -> isArchiveCandidate(dataset, usageStatsByDatasetId.get(dataset.getId()), duplicateCountByChecksum))
            .count();
        long duplicateDatasetCount = duplicateCountByChecksum.values().stream()
            .filter(count -> count > 1)
            .mapToLong(Long::valueOf)
            .sum();
        long referencedDatasetCount = usageStatsByDatasetId.values().stream()
            .filter(stats -> stats.usageCount() > 0)
            .count();
        LocalDateTime oldestActiveUploadedAt = datasets.stream()
            .filter(dataset -> !Boolean.TRUE.equals(dataset.getArchived()))
            .map(BacktestDataset::getUploadedAt)
            .min(Comparator.naturalOrder())
            .orElse(null);
        LocalDateTime newestUploadedAt = datasets.stream()
            .map(BacktestDataset::getUploadedAt)
            .max(Comparator.naturalOrder())
            .orElse(null);

        return new BacktestDatasetRetentionReportResponse(
            datasets.size(),
            activeDatasets,
            archivedDatasets,
            archiveCandidateDatasets,
            duplicateDatasetCount,
            referencedDatasetCount,
            oldestActiveUploadedAt,
            newestUploadedAt
        );
    }

    public BacktestDatasetResponse describeDataset(BacktestDataset dataset) {
        List<BacktestDataset> datasets = backtestDatasetRepository.findAllByOrderByUploadedAtDesc();
        return toResponse(dataset, getUsageStatsByDatasetId(), getDuplicateCountByChecksum(datasets));
    }

    public BacktestDatasetResponse archiveDataset(Long datasetId, BacktestDatasetArchiveRequest request) {
        BacktestDataset dataset = getDataset(datasetId);
        if (Boolean.TRUE.equals(dataset.getArchived())) {
            return describeDataset(dataset);
        }

        String reason = request == null || request.reason() == null || request.reason().isBlank()
            ? "Archived from dataset inventory."
            : request.reason().trim();
        dataset.setArchived(Boolean.TRUE);
        dataset.setArchivedAt(LocalDateTime.now());
        dataset.setArchiveReason(reason);

        BacktestDataset saved = backtestDatasetRepository.save(dataset);
        operatorAuditService.recordSuccess(
            "BACKTEST_DATASET_ARCHIVED",
            "test",
            "BACKTEST_DATASET",
            String.valueOf(saved.getId()),
            "name=" + saved.getName() + ", reason=" + reason
        );
        return describeDataset(saved);
    }

    public BacktestDatasetResponse restoreDataset(Long datasetId) {
        BacktestDataset dataset = getDataset(datasetId);
        dataset.setArchived(Boolean.FALSE);
        dataset.setArchivedAt(null);
        dataset.setArchiveReason(null);

        BacktestDataset saved = backtestDatasetRepository.save(dataset);
        operatorAuditService.recordSuccess(
            "BACKTEST_DATASET_RESTORED",
            "test",
            "BACKTEST_DATASET",
            String.valueOf(saved.getId()),
            "name=" + saved.getName()
        );
        return describeDataset(saved);
    }

    public void validateDatasetAvailableForNewRuns(Long datasetId) {
        BacktestDataset dataset = getDataset(datasetId);
        if (Boolean.TRUE.equals(dataset.getArchived())) {
            throw new IllegalArgumentException(
                "Archived datasets cannot be used for new backtests. Restore the dataset or upload a new active version."
            );
        }
    }

    private BacktestDataset getDataset(Long datasetId) {
        return backtestDatasetRepository.findById(datasetId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest dataset not found: " + datasetId));
    }

    private BacktestDatasetResponse toResponse(BacktestDataset dataset,
                                               Map<Long, DatasetUsageStats> usageStatsByDatasetId,
                                               Map<String, Integer> duplicateCountByChecksum) {
        DatasetUsageStats usageStats = usageStatsByDatasetId.getOrDefault(dataset.getId(), DatasetUsageStats.empty());
        int duplicateCount = duplicateCountByChecksum.getOrDefault(dataset.getChecksumSha256(), 1);

        return new BacktestDatasetResponse(
            dataset.getId(),
            dataset.getName(),
            dataset.getOriginalFilename(),
            dataset.getRowCount(),
            dataset.getSymbolsCsv(),
            dataset.getDataStart(),
            dataset.getDataEnd(),
            dataset.getUploadedAt(),
            dataset.getChecksumSha256(),
            dataset.getSchemaVersion(),
            dataset.getArchived(),
            dataset.getArchivedAt(),
            dataset.getArchiveReason(),
            usageStats.usageCount(),
            usageStats.lastUsedAt(),
            usageStats.usageCount() > 0,
            duplicateCount,
            resolveRetentionStatus(dataset, usageStats, duplicateCount)
        );
    }

    private Map<Long, DatasetUsageStats> getUsageStatsByDatasetId() {
        Map<Long, DatasetUsageStats> usageStatsByDatasetId = new HashMap<>();
        for (BacktestDatasetUsageSummary summary : backtestResultRepository.summarizeDatasetUsage()) {
            usageStatsByDatasetId.put(
                summary.getDatasetId(),
                new DatasetUsageStats(summary.getUsageCount(), summary.getLastUsedAt())
            );
        }
        return usageStatsByDatasetId;
    }

    private Map<String, Integer> getDuplicateCountByChecksum(List<BacktestDataset> datasets) {
        Map<String, Integer> duplicateCountByChecksum = new HashMap<>();
        for (BacktestDataset dataset : datasets) {
            duplicateCountByChecksum.merge(dataset.getChecksumSha256(), 1, Integer::sum);
        }
        return duplicateCountByChecksum;
    }

    private boolean isArchiveCandidate(BacktestDataset dataset,
                                       DatasetUsageStats usageStats,
                                       Map<String, Integer> duplicateCountByChecksum) {
        if (Boolean.TRUE.equals(dataset.getArchived())) {
            return false;
        }

        int duplicateCount = duplicateCountByChecksum.getOrDefault(dataset.getChecksumSha256(), 1);
        if (duplicateCount > 1 && (usageStats == null || usageStats.usageCount() == 0)) {
            return true;
        }

        if (usageStats == null || usageStats.usageCount() == 0) {
            return dataset.getUploadedAt().isBefore(LocalDateTime.now().minusDays(UNUSED_ARCHIVE_CANDIDATE_DAYS));
        }

        return false;
    }

    private String resolveRetentionStatus(BacktestDataset dataset,
                                          DatasetUsageStats usageStats,
                                          int duplicateCount) {
        if (Boolean.TRUE.equals(dataset.getArchived())) {
            return "ARCHIVED";
        }
        if (duplicateCount > 1 && usageStats.usageCount() == 0) {
            return "ARCHIVE_CANDIDATE_DUPLICATE";
        }
        if (usageStats.usageCount() == 0
            && dataset.getUploadedAt().isBefore(LocalDateTime.now().minusDays(UNUSED_ARCHIVE_CANDIDATE_DAYS))) {
            return "ARCHIVE_CANDIDATE_UNUSED";
        }
        if (duplicateCount > 1) {
            return "ACTIVE_DUPLICATE_RETAINED";
        }
        if (usageStats.lastUsedAt() != null
            && usageStats.lastUsedAt().isBefore(LocalDateTime.now().minusDays(STALE_REFERENCE_DAYS))) {
            return "ACTIVE_STALE_RETAINED";
        }
        return "ACTIVE";
    }

    private record DatasetUsageStats(long usageCount, LocalDateTime lastUsedAt) {
        private static DatasetUsageStats empty() {
            return new DatasetUsageStats(0L, null);
        }
    }
}
