package com.algotrader.bot.service.recovery;

import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.repository.MarketDataImportJobRepository;
import com.algotrader.bot.service.OperatorAuditService;
import com.algotrader.bot.service.marketdata.MarketDataImportExecutionService;
import com.algotrader.bot.service.marketdata.MarketDataImportJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MarketDataImportStartupRecoveryParticipant implements StartupRecoveryParticipant {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataImportStartupRecoveryParticipant.class);

    private final MarketDataImportJobRepository marketDataImportJobRepository;
    private final MarketDataImportExecutionService marketDataImportExecutionService;
    private final OperatorAuditService operatorAuditService;

    public MarketDataImportStartupRecoveryParticipant(MarketDataImportJobRepository marketDataImportJobRepository,
                                                      MarketDataImportExecutionService marketDataImportExecutionService,
                                                      OperatorAuditService operatorAuditService) {
        this.marketDataImportJobRepository = marketDataImportJobRepository;
        this.marketDataImportExecutionService = marketDataImportExecutionService;
        this.operatorAuditService = operatorAuditService;
    }

    @Override
    public String participantName() {
        return "marketDataImports";
    }

    @Override
    public int recoverPendingWork() {
        List<MarketDataImportJob> recoverable = marketDataImportJobRepository.findByStatusInOrderByCreatedAtAsc(
            List.of(
                MarketDataImportJobStatus.QUEUED,
                MarketDataImportJobStatus.RUNNING,
                MarketDataImportJobStatus.WAITING_RETRY
            )
        );
        if (recoverable.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        recoverable.forEach(job -> {
            if (job.getStatus() == MarketDataImportJobStatus.WAITING_RETRY
                && job.getNextRetryAt() != null
                && job.getNextRetryAt().isAfter(now)) {
                return;
            }

            MarketDataImportJobStatus previousStatus = job.getStatus();
            job.setStatus(MarketDataImportJobStatus.QUEUED);
            job.setNextRetryAt(null);
            job.setCompletedAt(null);
            job.setStatusMessage(previousStatus == MarketDataImportJobStatus.RUNNING
                ? "Recovered on startup after interrupted import. Continuing from the last saved cursor."
                : "Recovered queued import on startup. Dispatching downloader worker."
            );
        });
        marketDataImportJobRepository.saveAll(recoverable);
        marketDataImportJobRepository.flush();

        int resumed = 0;
        for (MarketDataImportJob job : recoverable) {
            if (job.getStatus() != MarketDataImportJobStatus.QUEUED) {
                continue;
            }
            resumed++;
            logger.warn("Recovering market-data import job {} on startup from cursor symbolIndex={}, chunkStart={}",
                job.getId(),
                job.getCurrentSymbolIndex(),
                job.getCurrentChunkStart()
            );
            marketDataImportExecutionService.processJobAsync(job.getId());
            operatorAuditService.recordSuccess(
                "MARKET_DATA_IMPORT_RECOVERED_ON_STARTUP",
                "test",
                "MARKET_DATA_IMPORT_JOB",
                String.valueOf(job.getId()),
                "provider=" + job.getProviderId() + ", status=" + job.getStatusMessage()
            );
        }
        return resumed;
    }
}
