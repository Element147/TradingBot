package com.algotrader.bot.controller;

import com.algotrader.bot.service.BacktestDatasetCatalogService;
import com.algotrader.bot.service.BacktestManagementService;
import com.algotrader.bot.service.BacktestResultQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/backtests")
public class BacktestManagementController {

    private final BacktestManagementService backtestManagementService;
    private final BacktestDatasetCatalogService backtestDatasetCatalogService;
    private final BacktestResultQueryService backtestResultQueryService;

    public BacktestManagementController(BacktestManagementService backtestManagementService,
                                        BacktestDatasetCatalogService backtestDatasetCatalogService,
                                        BacktestResultQueryService backtestResultQueryService) {
        this.backtestManagementService = backtestManagementService;
        this.backtestDatasetCatalogService = backtestDatasetCatalogService;
        this.backtestResultQueryService = backtestResultQueryService;
    }

    @GetMapping("/algorithms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BacktestAlgorithmResponse>> algorithms() {
        return ResponseEntity.ok(backtestManagementService.getAlgorithms());
    }

    @GetMapping("/datasets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BacktestDatasetResponse>> datasets() {
        return ResponseEntity.ok(backtestDatasetCatalogService.listDatasets());
    }

    @GetMapping("/datasets/retention-report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestDatasetRetentionReportResponse> retentionReport() {
        return ResponseEntity.ok(backtestDatasetCatalogService.getRetentionReport());
    }

    @PostMapping(value = "/datasets/upload", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestDatasetResponse> uploadDataset(@RequestParam(required = false) String name,
                                                                 @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(backtestDatasetCatalogService.uploadDataset(name, file));
    }

    @PostMapping("/datasets/{datasetId}/archive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestDatasetResponse> archiveDataset(
        @PathVariable Long datasetId,
        @RequestBody(required = false) BacktestDatasetArchiveRequest request
    ) {
        return ResponseEntity.ok(backtestDatasetCatalogService.archiveDataset(datasetId, request));
    }

    @PostMapping("/datasets/{datasetId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestDatasetResponse> restoreDataset(@PathVariable Long datasetId) {
        return ResponseEntity.ok(backtestDatasetCatalogService.restoreDataset(datasetId));
    }

    @GetMapping("/datasets/{datasetId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadDataset(@PathVariable Long datasetId) {
        BacktestDatasetDownloadResponse dataset = backtestDatasetCatalogService.downloadDataset(datasetId);
        String safeFilename = dataset.originalFilename().replace("\"", "");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename + "\"")
            .header("X-Dataset-Checksum-Sha256", dataset.checksumSha256())
            .header("X-Dataset-Schema-Version", dataset.schemaVersion())
            .header("X-Dataset-Download-Source", dataset.exportSource())
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(dataset.csvData());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestHistoryPageResponse> history(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDirection,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String strategyId,
        @RequestParam(required = false) String datasetName,
        @RequestParam(required = false) String experimentName,
        @RequestParam(required = false) String market,
        @RequestParam(required = false) String executionStatus,
        @RequestParam(required = false) String validationStatus,
        @RequestParam(required = false) Integer feesBpsMin,
        @RequestParam(required = false) Integer feesBpsMax,
        @RequestParam(required = false) Integer slippageBpsMin,
        @RequestParam(required = false) Integer slippageBpsMax
    ) {
        return ResponseEntity.ok(backtestResultQueryService.getHistory(new BacktestHistoryQuery(
            page,
            pageSize,
            sortBy,
            sortDirection,
            search,
            strategyId,
            datasetName,
            experimentName,
            market,
            executionStatus,
            validationStatus,
            feesBpsMin,
            feesBpsMax,
            slippageBpsMin,
            slippageBpsMax
        )));
    }

    @GetMapping("/experiments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BacktestExperimentSummaryResponse>> experiments() {
        return ResponseEntity.ok(backtestResultQueryService.getExperimentSummaries());
    }

    @GetMapping("/{backtestId}/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestSummaryResponse> summary(@PathVariable Long backtestId) {
        return ResponseEntity.ok(backtestResultQueryService.getSummary(backtestId));
    }

    @GetMapping("/{backtestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestDetailsResponse> details(@PathVariable Long backtestId) {
        return ResponseEntity.ok(backtestResultQueryService.getDetails(backtestId));
    }

    @GetMapping("/{backtestId}/equity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BacktestEquityPointResponse>> equityCurve(@PathVariable Long backtestId) {
        return ResponseEntity.ok(backtestResultQueryService.getEquityCurve(backtestId));
    }

    @GetMapping("/{backtestId}/trades")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BacktestTradeSeriesItemResponse>> tradeSeries(@PathVariable Long backtestId) {
        return ResponseEntity.ok(backtestResultQueryService.getTradeSeries(backtestId));
    }

    @GetMapping("/{backtestId}/telemetry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestTelemetryQueryResponse> telemetry(@PathVariable Long backtestId,
                                                                    @RequestParam(required = false) String symbol) {
        return ResponseEntity.ok(backtestResultQueryService.getTelemetry(backtestId, symbol));
    }

    @DeleteMapping("/{backtestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long backtestId) {
        backtestManagementService.deleteBacktest(backtestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/run")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestRunResponse> run(@Valid @RequestBody RunBacktestRequest request) {
        BacktestRunResponse response = backtestManagementService.runBacktest(request);
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, "/api/backtests/" + response.id() + "/summary")
            .body(response);
    }

    @PostMapping("/{backtestId}/replay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestRunResponse> replay(@PathVariable Long backtestId) {
        BacktestRunResponse response = backtestManagementService.replayBacktest(backtestId);
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, "/api/backtests/" + response.id() + "/summary")
            .body(response);
    }

    @GetMapping("/compare")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BacktestComparisonResponse> compare(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(backtestResultQueryService.compareBacktests(ids));
    }
}
