package com.algotrader.bot.service;

import com.algotrader.bot.controller.BacktestDatasetArchiveRequest;
import com.algotrader.bot.controller.BacktestDatasetDownloadResponse;
import com.algotrader.bot.controller.BacktestDatasetResponse;
import com.algotrader.bot.controller.BacktestDatasetRetentionReportResponse;
import com.algotrader.bot.entity.BacktestDataset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class BacktestDatasetCatalogService {

    private final BacktestDatasetStorageService backtestDatasetStorageService;
    private final BacktestDatasetLifecycleService backtestDatasetLifecycleService;
    private final OperatorAuditService operatorAuditService;

    public BacktestDatasetCatalogService(BacktestDatasetStorageService backtestDatasetStorageService,
                                         BacktestDatasetLifecycleService backtestDatasetLifecycleService,
                                         OperatorAuditService operatorAuditService) {
        this.backtestDatasetStorageService = backtestDatasetStorageService;
        this.backtestDatasetLifecycleService = backtestDatasetLifecycleService;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional
    public BacktestDatasetResponse uploadDataset(String requestedName, MultipartFile file) {
        BacktestDataset saved = backtestDatasetStorageService.storeUploadedDataset(requestedName, file);
        operatorAuditService.recordSuccess(
            "BACKTEST_DATASET_UPLOADED",
            "test",
            "BACKTEST_DATASET",
            String.valueOf(saved.getId()),
            "name=" + saved.getName() + ", rows=" + saved.getRowCount()
        );
        return backtestDatasetLifecycleService.describeDataset(saved);
    }

    @Transactional
    public BacktestDatasetResponse importDataset(String requestedName, String filename, byte[] csvData, String sourceDetails) {
        BacktestDataset saved = backtestDatasetStorageService.storeImportedDataset(requestedName, filename, csvData, sourceDetails);
        operatorAuditService.recordSuccess(
            "BACKTEST_DATASET_IMPORTED",
            "test",
            "BACKTEST_DATASET",
            String.valueOf(saved.getId()),
            "name=" + saved.getName() + ", rows=" + saved.getRowCount() + ", source=" + sourceDetails
        );
        return backtestDatasetLifecycleService.describeDataset(saved);
    }

    @Transactional(readOnly = true)
    public List<BacktestDatasetResponse> listDatasets() {
        return backtestDatasetLifecycleService.listDatasets();
    }

    @Transactional(readOnly = true)
    public BacktestDatasetRetentionReportResponse getRetentionReport() {
        return backtestDatasetLifecycleService.getRetentionReport();
    }

    @Transactional(readOnly = true)
    public BacktestDatasetDownloadResponse downloadDataset(Long datasetId) {
        return backtestDatasetStorageService.downloadDataset(datasetId);
    }

    @Transactional
    public BacktestDatasetResponse archiveDataset(Long datasetId, BacktestDatasetArchiveRequest request) {
        return backtestDatasetLifecycleService.archiveDataset(datasetId, request);
    }

    @Transactional
    public BacktestDatasetResponse restoreDataset(Long datasetId) {
        return backtestDatasetLifecycleService.restoreDataset(datasetId);
    }
}
