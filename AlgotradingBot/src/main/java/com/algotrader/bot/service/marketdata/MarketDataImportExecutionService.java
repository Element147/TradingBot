package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.controller.BacktestDatasetResponse;
import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.repository.MarketDataImportJobRepository;
import com.algotrader.bot.service.BacktestDatasetCatalogService;
import com.algotrader.bot.service.BacktestDatasetStorageService;
import com.algotrader.bot.service.OperatorAuditService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataImportExecutionService {

    private static final Set<MarketDataImportJobStatus> READY_STATUSES = Set.of(
        MarketDataImportJobStatus.QUEUED,
        MarketDataImportJobStatus.WAITING_RETRY
    );
    private static final long WORK_SLICE_SECONDS = 20;
    private static final long ACTIVE_TIMEOUT_SECONDS = 900;

    private final MarketDataImportJobRepository marketDataImportJobRepository;
    private final MarketDataProviderRegistry marketDataProviderRegistry;
    private final MarketDataCsvSupport marketDataCsvSupport;
    private final MarketDataSessionFilter marketDataSessionFilter;
    private final BacktestDatasetCatalogService backtestDatasetCatalogService;
    private final BacktestDatasetStorageService backtestDatasetStorageService;
    private final OperatorAuditService operatorAuditService;
    private final MarketDataImportProgressService marketDataImportProgressService;
    private final Set<Long> locallyDispatchedJobIds = ConcurrentHashMap.newKeySet();

    public MarketDataImportExecutionService(MarketDataImportJobRepository marketDataImportJobRepository,
                                            MarketDataProviderRegistry marketDataProviderRegistry,
                                            MarketDataCsvSupport marketDataCsvSupport,
                                            MarketDataSessionFilter marketDataSessionFilter,
                                            BacktestDatasetCatalogService backtestDatasetCatalogService,
                                            BacktestDatasetStorageService backtestDatasetStorageService,
                                            OperatorAuditService operatorAuditService,
                                            MarketDataImportProgressService marketDataImportProgressService) {
        this.marketDataImportJobRepository = marketDataImportJobRepository;
        this.marketDataProviderRegistry = marketDataProviderRegistry;
        this.marketDataCsvSupport = marketDataCsvSupport;
        this.marketDataSessionFilter = marketDataSessionFilter;
        this.backtestDatasetCatalogService = backtestDatasetCatalogService;
        this.backtestDatasetStorageService = backtestDatasetStorageService;
        this.operatorAuditService = operatorAuditService;
        this.marketDataImportProgressService = marketDataImportProgressService;
    }

    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<Void> processJobAsync(Long jobId) {
        if (!locallyDispatchedJobIds.add(jobId)) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            processJob(jobId);
        } finally {
            locallyDispatchedJobIds.remove(jobId);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void processJob(Long jobId) {
        MarketDataImportJob job = markRunning(jobId);
        if (job == null) {
            return;
        }

        try {
            runWorkSlice(job);
        } catch (MarketDataRetryableException exception) {
            updateRetry(jobId, exception.getRetryAt(), exception.getMessage());
        } catch (Exception exception) {
            markFailed(jobId, exception.getMessage());
        }
    }

    public int getLocallyDispatchedJobCount() {
        return locallyDispatchedJobIds.size();
    }

    private void runWorkSlice(MarketDataImportJob initialJob) {
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(WORK_SLICE_SECONDS);
        MarketDataImportJob job = initialJob;
        MarketDataProvider provider = marketDataProviderRegistry.get(job.getProviderId());
        List<String> symbols = parseSymbols(job.getSymbolsCsv());
        MarketDataTimeframe timeframe = MarketDataTimeframe.from(job.getTimeframe());
        LocalDateTime absoluteEnd = job.getEndDate().atTime(23, 59, 59);

        while (LocalDateTime.now().isBefore(deadline)) {
            if (job.getStatus() == MarketDataImportJobStatus.CANCELLED) {
                return;
            }

            if (job.getCurrentSymbolIndex() >= symbols.size()) {
                completeJob(job, provider);
                return;
            }

            String currentSymbol = symbols.get(job.getCurrentSymbolIndex());
            LocalDateTime chunkStart = job.getCurrentChunkStart() == null
                ? job.getStartDate().atStartOfDay()
                : job.getCurrentChunkStart();
            if (chunkStart.isAfter(absoluteEnd)) {
                advanceCursor(job, symbols, absoluteEnd, timeframe, 0);
                job = marketDataImportJobRepository.save(job);
                marketDataImportProgressService.publish(job);
                continue;
            }

            LocalDateTime proposedChunkEnd = min(chunkStart.plus(timeframe.chunkWindow()), absoluteEnd);
            MarketDataProviderFetchRequest providerRequest = new MarketDataProviderFetchRequest(
                job.getAssetType(),
                currentSymbol,
                timeframe,
                chunkStart,
                proposedChunkEnd,
                Boolean.TRUE.equals(job.getAdjusted()),
                Boolean.TRUE.equals(job.getRegularSessionOnly())
            );
            LocalDateTime actualChunkEnd = provider.resolveChunkEnd(providerRequest);
            providerRequest = new MarketDataProviderFetchRequest(
                providerRequest.assetType(),
                providerRequest.requestedSymbol(),
                providerRequest.timeframe(),
                providerRequest.start(),
                actualChunkEnd,
                providerRequest.adjusted(),
                providerRequest.regularSessionOnly()
            );

            job.setAttemptCount(job.getAttemptCount() + 1);
            List<OHLCVData> fetchedBars = provider.fetch(providerRequest).stream()
                .sorted(Comparator.comparing(OHLCVData::getTimestamp))
                .toList();

            List<OHLCVData> filteredBars = job.getAssetType() == MarketDataAssetType.STOCK
                && Boolean.TRUE.equals(job.getRegularSessionOnly())
                && timeframe.isIntraday()
                ? marketDataSessionFilter.regularSessionOnly(fetchedBars)
                : fetchedBars;

            byte[] updatedCsv = marketDataCsvSupport.appendRows(job.getStagedCsvData(), filteredBars);
            backtestDatasetStorageService.validateDatasetSize(updatedCsv.length);
            job.setStagedCsvData(updatedCsv);
            job.setImportedRowCount(job.getImportedRowCount() + filteredBars.size());
            job.setStatusMessage(
                "Fetched " + filteredBars.size() + " bars for " + currentSymbol
                    + " (" + chunkStart + " to " + actualChunkEnd + ")."
            );
            advanceCursor(job, symbols, actualChunkEnd, timeframe, filteredBars.size());
            job = marketDataImportJobRepository.save(job);
            marketDataImportProgressService.publish(job);
        }

        queueForContinuation(job.getId(), "Work slice finished. Continuing automatically.");
    }

    private void advanceCursor(MarketDataImportJob job,
                               List<String> symbols,
                               LocalDateTime processedChunkEnd,
                               MarketDataTimeframe timeframe,
                               int importedRowsThisStep) {
        LocalDateTime absoluteEnd = job.getEndDate().atTime(23, 59, 59);
        if (!processedChunkEnd.isBefore(absoluteEnd)) {
            if (job.getCurrentSymbolIndex() >= symbols.size() - 1) {
                job.setCurrentSymbolIndex(symbols.size());
                job.setCurrentChunkStart(null);
                if (importedRowsThisStep == 0) {
                    job.setStatusMessage("No more bars returned. Finalizing dataset.");
                }
                return;
            }
            job.setCurrentSymbolIndex(job.getCurrentSymbolIndex() + 1);
            job.setCurrentChunkStart(job.getStartDate().atStartOfDay());
            return;
        }

        job.setCurrentChunkStart(processedChunkEnd.plus(timeframe.step()));
    }

    private MarketDataImportJob markRunning(Long jobId) {
        MarketDataImportJob job = getJob(jobId);
        if (!READY_STATUSES.contains(job.getStatus())) {
            return null;
        }
        job.setStatus(MarketDataImportJobStatus.RUNNING);
        job.setStatusMessage("Running import step.");
        job.setNextRetryAt(null);
        if (job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
        return saved;
    }

    private void updateRetry(Long jobId, LocalDateTime retryAt, String message) {
        MarketDataImportJob job = getJob(jobId);
        int nextRetryCount = job.getRetryCount() + 1;
        job.setRetryCount(nextRetryCount);
        if (job.getMaxRetryCount() != null && nextRetryCount > job.getMaxRetryCount()) {
            markFailed(
                jobId,
                "Automatic retry limit reached after " + job.getRetryCount()
                    + " wait windows. Review provider limits or credentials, then retry manually."
            );
            return;
        }
        job.setStatus(MarketDataImportJobStatus.WAITING_RETRY);
        job.setStatusMessage(
            (message == null || message.isBlank() ? "Provider asked the downloader to wait." : message)
                + " Automatic retry " + nextRetryCount + " of " + job.getMaxRetryCount() + "."
        );
        job.setNextRetryAt(retryAt);
        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
    }

    private void markFailed(Long jobId, String message) {
        MarketDataImportJob job = getJob(jobId);
        job.setStatus(MarketDataImportJobStatus.FAILED);
        job.setStatusMessage(message == null || message.isBlank() ? "Import failed." : message);
        job.setNextRetryAt(null);
        job.setCompletedAt(LocalDateTime.now());
        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
        operatorAuditService.recordFailure(
            "MARKET_DATA_IMPORT_FAILED",
            "test",
            "MARKET_DATA_IMPORT_JOB",
            String.valueOf(job.getId()),
            "provider=" + job.getProviderId() + ", error=" + job.getStatusMessage()
        );
    }

    private void queueForContinuation(Long jobId, String message) {
        MarketDataImportJob job = getJob(jobId);
        if (job.getStatus() == MarketDataImportJobStatus.CANCELLED) {
            return;
        }
        job.setStatus(MarketDataImportJobStatus.QUEUED);
        job.setStatusMessage(message);
        job.setNextRetryAt(LocalDateTime.now().plusSeconds(1));
        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
    }

    private void completeJob(MarketDataImportJob job, MarketDataProvider provider) {
        String filename = provider.definition().id()
            + "-" + job.getAssetType().name().toLowerCase(Locale.ROOT)
            + "-" + job.getTimeframe()
            + "-" + job.getStartDate()
            + "-" + job.getEndDate()
            + ".csv";
        BacktestDatasetResponse dataset = backtestDatasetCatalogService.importDataset(
            job.getDatasetName(),
            filename,
            job.getStagedCsvData(),
            provider.definition().label()
        );

        MarketDataImportJob managed = getJob(job.getId());
        managed.setDatasetId(dataset.id());
        managed.setStatus(MarketDataImportJobStatus.COMPLETED);
        managed.setStatusMessage(
            "Completed. Imported " + managed.getImportedRowCount() + " rows into dataset #" + dataset.id() + "."
        );
        managed.setStagedCsvData(null);
        managed.setNextRetryAt(null);
        managed.setCompletedAt(LocalDateTime.now());
        MarketDataImportJob saved = marketDataImportJobRepository.save(managed);
        marketDataImportProgressService.publish(saved);
        operatorAuditService.recordSuccess(
            "MARKET_DATA_IMPORT_COMPLETED",
            "test",
            "MARKET_DATA_IMPORT_JOB",
            String.valueOf(managed.getId()),
            "provider=" + managed.getProviderId() + ", datasetId=" + dataset.id()
        );
    }

    private List<String> parseSymbols(String symbolsCsv) {
        return List.of(symbolsCsv.split(",")).stream()
            .map(String::trim)
            .filter(symbol -> !symbol.isBlank())
            .toList();
    }

    private MarketDataImportJob getJob(Long jobId) {
        return marketDataImportJobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Market data import job not found: " + jobId));
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }
}
