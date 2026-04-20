package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.controller.MarketDataImportJobRequest;
import com.algotrader.bot.controller.MarketDataImportJobResponse;
import com.algotrader.bot.controller.MarketDataProviderCredentialRequest;
import com.algotrader.bot.controller.MarketDataProviderCredentialResponse;
import com.algotrader.bot.controller.MarketDataProviderResponse;
import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.repository.MarketDataImportJobRepository;
import com.algotrader.bot.service.BackendOperationMetrics;
import com.algotrader.bot.service.OperatorAuditService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class MarketDataImportService {

    private static final Set<MarketDataImportJobStatus> READY_STATUSES = Set.of(
        MarketDataImportJobStatus.QUEUED,
        MarketDataImportJobStatus.WAITING_RETRY
    );
    private static final int READY_JOB_BATCH_SIZE = 2;
    private static final int DEFAULT_MAX_RETRY_COUNT = 4;

    private final MarketDataImportJobRepository marketDataImportJobRepository;
    private final MarketDataProviderRegistry marketDataProviderRegistry;
    private final MarketDataProviderCredentialService marketDataProviderCredentialService;
    private final OperatorAuditService operatorAuditService;
    private final MarketDataImportJobResponseMapper marketDataImportJobResponseMapper;
    private final MarketDataImportProgressService marketDataImportProgressService;
    private final MarketDataImportExecutionService marketDataImportExecutionService;
    private final BackendOperationMetrics backendOperationMetrics;

    public MarketDataImportService(MarketDataImportJobRepository marketDataImportJobRepository,
                                   MarketDataProviderRegistry marketDataProviderRegistry,
                                   MarketDataProviderCredentialService marketDataProviderCredentialService,
                                   OperatorAuditService operatorAuditService,
                                   MarketDataImportJobResponseMapper marketDataImportJobResponseMapper,
                                   MarketDataImportProgressService marketDataImportProgressService,
                                   MarketDataImportExecutionService marketDataImportExecutionService,
                                   BackendOperationMetrics backendOperationMetrics) {
        this.marketDataImportJobRepository = marketDataImportJobRepository;
        this.marketDataProviderRegistry = marketDataProviderRegistry;
        this.marketDataProviderCredentialService = marketDataProviderCredentialService;
        this.operatorAuditService = operatorAuditService;
        this.marketDataImportJobResponseMapper = marketDataImportJobResponseMapper;
        this.marketDataImportProgressService = marketDataImportProgressService;
        this.marketDataImportExecutionService = marketDataImportExecutionService;
        this.backendOperationMetrics = backendOperationMetrics;
    }

    @Transactional(readOnly = true)
    public List<MarketDataProviderResponse> listProviders() {
        return marketDataProviderRegistry.all().stream()
            .map(provider -> new MarketDataProviderResponse(
                provider.definition().id(),
                provider.definition().label(),
                provider.definition().description(),
                provider.definition().supportedAssetTypes().stream().map(Enum::name).sorted().toList(),
                provider.definition().supportedTimeframes(),
                provider.definition().apiKeyRequired(),
                provider.definition().apiKeyEnvironmentVariable(),
                provider.isConfigured(),
                marketDataProviderCredentialService.resolveCredentialSource(provider.definition()).name(),
                provider.definition().supportsAdjusted(),
                provider.definition().supportsRegularSessionOnly(),
                provider.definition().symbolExamples(),
                provider.definition().docsUrl(),
                provider.definition().signupUrl(),
                provider.definition().accountNotes()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketDataProviderCredentialResponse> listProviderCredentials() {
        return marketDataProviderCredentialService.listCredentialSettings(
            marketDataProviderRegistry.all().stream()
                .map(MarketDataProvider::definition)
                .toList()
        );
    }

    @Transactional
    public MarketDataProviderCredentialResponse saveProviderCredential(
        String providerId,
        MarketDataProviderCredentialRequest request
    ) {
        MarketDataProvider provider = marketDataProviderRegistry.get(providerId);
        return marketDataProviderCredentialService.saveCredential(provider.definition(), request);
    }

    @Transactional
    public void deleteProviderCredential(String providerId) {
        MarketDataProvider provider = marketDataProviderRegistry.get(providerId);
        marketDataProviderCredentialService.deleteCredential(provider.definition());
    }

    @Transactional(readOnly = true)
    public List<MarketDataImportJobResponse> listJobs() {
        long startedAt = System.nanoTime();
        List<MarketDataImportJobResponse> jobs = marketDataImportJobRepository.findTop25ByOrderByCreatedAtDesc().stream()
            .map(marketDataImportJobResponseMapper::toResponse)
            .toList();
        backendOperationMetrics.record("read", "market_data_import_jobs", "list", System.nanoTime() - startedAt, jobs.size(), 0L);
        return jobs;
    }

    @Transactional(readOnly = true)
    public MarketDataImportJobResponse getJobResponse(Long jobId) {
        long startedAt = System.nanoTime();
        MarketDataImportJobResponse response = marketDataImportJobResponseMapper.toResponse(getJob(jobId));
        backendOperationMetrics.record("read", "market_data_import_jobs", "single", System.nanoTime() - startedAt, 1, 0L);
        return response;
    }

    @Transactional
    public MarketDataImportJobResponse createJob(MarketDataImportJobRequest request) {
        MarketDataProvider provider = marketDataProviderRegistry.get(request.providerId());
        MarketDataTimeframe timeframe = MarketDataTimeframe.from(request.timeframe());
        validateCreateRequest(request, provider, timeframe);

        List<String> symbols = normalizeSymbols(request.symbols(), request.assetType());
        provider.validateImportRequest(new MarketDataProviderFetchRequest(
            request.assetType(),
            symbols.get(0),
            timeframe,
            request.startDate().atStartOfDay(),
            request.endDate().atTime(23, 59, 59),
            Boolean.TRUE.equals(request.adjusted()),
            request.assetType() == MarketDataAssetType.STOCK && Boolean.TRUE.equals(request.regularSessionOnly())
        ));

        MarketDataImportJob job = new MarketDataImportJob();
        job.setProviderId(provider.definition().id());
        job.setAssetType(request.assetType());
        job.setDatasetName(resolveDatasetName(request, provider, timeframe, symbols));
        job.setSymbolsCsv(String.join(",", symbols));
        job.setTimeframe(timeframe.id());
        job.setStartDate(request.startDate());
        job.setEndDate(request.endDate());
        job.setAdjusted(Boolean.TRUE.equals(request.adjusted()));
        job.setRegularSessionOnly(request.assetType() == MarketDataAssetType.STOCK && Boolean.TRUE.equals(request.regularSessionOnly()));
        job.setStatus(MarketDataImportJobStatus.QUEUED);
        job.setStatusMessage("Queued. Waiting for downloader worker.");
        job.setCurrentSymbolIndex(0);
        job.setCurrentChunkStart(request.startDate().atStartOfDay());
        job.setImportedRowCount(0);
        job.setAttemptCount(0);
        job.setRetryCount(0);
        job.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);

        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
        operatorAuditService.recordSuccess(
            "MARKET_DATA_IMPORT_CREATED",
            "test",
            "MARKET_DATA_IMPORT_JOB",
            String.valueOf(saved.getId()),
            "provider=" + saved.getProviderId() + ", dataset=" + saved.getDatasetName()
        );
        return marketDataImportJobResponseMapper.toResponse(saved);
    }

    @Transactional
    public MarketDataImportJobResponse retryJob(Long jobId) {
        MarketDataImportJob job = getJob(jobId);
        if (job.getStatus() == MarketDataImportJobStatus.COMPLETED) {
            throw new IllegalArgumentException("Completed import jobs cannot be retried.");
        }

        job.setStatus(MarketDataImportJobStatus.QUEUED);
        job.setStatusMessage("Retry requested. Job restarted from the beginning.");
        job.setNextRetryAt(null);
        job.setCurrentSymbolIndex(0);
        job.setCurrentChunkStart(job.getStartDate().atStartOfDay());
        job.setImportedRowCount(0);
        job.setAttemptCount(0);
        job.setRetryCount(0);
        job.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        job.setStagedCsvData(null);
        job.setDatasetId(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);

        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
        operatorAuditService.recordSuccess(
            "MARKET_DATA_IMPORT_RETRIED",
            "test",
            "MARKET_DATA_IMPORT_JOB",
            String.valueOf(saved.getId()),
            "provider=" + saved.getProviderId() + ", dataset=" + saved.getDatasetName()
        );
        return marketDataImportJobResponseMapper.toResponse(saved);
    }

    @Transactional
    public MarketDataImportJobResponse cancelJob(Long jobId) {
        MarketDataImportJob job = getJob(jobId);
        if (job.getStatus() == MarketDataImportJobStatus.COMPLETED
            || job.getStatus() == MarketDataImportJobStatus.CANCELLED) {
            return marketDataImportJobResponseMapper.toResponse(job);
        }

        job.setStatus(MarketDataImportJobStatus.CANCELLED);
        job.setStatusMessage("Cancelled by operator.");
        job.setNextRetryAt(null);
        job.setCompletedAt(LocalDateTime.now());
        MarketDataImportJob saved = marketDataImportJobRepository.save(job);
        marketDataImportProgressService.publish(saved);
        operatorAuditService.recordSuccess(
            "MARKET_DATA_IMPORT_CANCELLED",
            "test",
            "MARKET_DATA_IMPORT_JOB",
            String.valueOf(saved.getId()),
            "provider=" + saved.getProviderId() + ", dataset=" + saved.getDatasetName()
        );
        return marketDataImportJobResponseMapper.toResponse(saved);
    }

    @Scheduled(fixedDelayString = "${algotrading.market-data.import-poll-ms:5000}")
    public void processReadyJobs() {
        List<MarketDataImportJob> readyJobs = marketDataImportJobRepository.findReadyJobs(
            READY_STATUSES,
            LocalDateTime.now(),
            PageRequest.of(0, READY_JOB_BATCH_SIZE)
        );
        if (readyJobs.isEmpty()) {
            return;
        }

        readyJobs.stream()
            .map(MarketDataImportJob::getId)
            .forEach(marketDataImportExecutionService::processJobAsync);
    }

    public CompletableFuture<Void> processJobAsync(Long jobId) {
        return marketDataImportExecutionService.processJobAsync(jobId);
    }

    public void processJob(Long jobId) {
        marketDataImportExecutionService.processJob(jobId);
    }

    public int getLocallyDispatchedJobCount() {
        return marketDataImportExecutionService.getLocallyDispatchedJobCount();
    }

    private void validateCreateRequest(MarketDataImportJobRequest request,
                                       MarketDataProvider provider,
                                       MarketDataTimeframe timeframe) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        if (!provider.definition().supportedAssetTypes().contains(request.assetType())) {
            throw new IllegalArgumentException(
                provider.definition().label() + " does not support " + request.assetType().name().toLowerCase(Locale.ROOT) + " imports."
            );
        }
        if (!provider.definition().supportedTimeframes().contains(timeframe.id())) {
            throw new IllegalArgumentException(provider.definition().label() + " does not support timeframe " + timeframe.id() + ".");
        }
        if (provider.definition().apiKeyRequired() && !provider.isConfigured()) {
            throw new IllegalArgumentException(
                marketDataProviderCredentialService.missingCredentialMessage(provider.definition())
            );
        }
        if (Boolean.TRUE.equals(request.adjusted()) && !provider.definition().supportsAdjusted()) {
            throw new IllegalArgumentException(provider.definition().label() + " does not support adjusted historical bars.");
        }
        if (request.assetType() == MarketDataAssetType.CRYPTO && Boolean.TRUE.equals(request.regularSessionOnly())) {
            throw new IllegalArgumentException("Regular-session filtering is only valid for stock datasets.");
        }
    }

    private String resolveDatasetName(MarketDataImportJobRequest request,
                                      MarketDataProvider provider,
                                      MarketDataTimeframe timeframe,
                                      List<String> symbols) {
        if (request.datasetName() != null && !request.datasetName().isBlank()) {
            return request.datasetName().trim();
        }
        String symbolLabel = symbols.size() == 1 ? symbols.get(0) : symbols.get(0) + " +" + (symbols.size() - 1);
        return provider.definition().label() + " " + symbolLabel + " " + timeframe.id() + " " + request.startDate() + " to " + request.endDate();
    }

    private List<String> normalizeSymbols(List<String> rawSymbols, MarketDataAssetType assetType) {
        List<String> normalized = rawSymbols.stream()
            .map(symbol -> symbol == null ? "" : symbol.trim())
            .filter(symbol -> !symbol.isBlank())
            .map(symbol -> assetType == MarketDataAssetType.STOCK
                ? symbol.toUpperCase(Locale.ROOT)
                : symbol.toUpperCase(Locale.ROOT).replace("-", "/").replace("_", "/"))
            .distinct()
            .toList();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one valid symbol is required.");
        }
        return normalized;
    }

    private MarketDataImportJob getJob(Long jobId) {
        return marketDataImportJobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Market data import job not found: " + jobId));
    }
}
