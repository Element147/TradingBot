package com.algotrader.bot.service.recovery;

import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.repository.BacktestResultRepository;
import com.algotrader.bot.service.BacktestExecutionService;
import com.algotrader.bot.service.OperatorAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BacktestStartupRecoveryParticipant implements StartupRecoveryParticipant {

    private static final Logger logger = LoggerFactory.getLogger(BacktestStartupRecoveryParticipant.class);

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestExecutionService backtestExecutionService;
    private final OperatorAuditService operatorAuditService;

    public BacktestStartupRecoveryParticipant(BacktestResultRepository backtestResultRepository,
                                             BacktestExecutionService backtestExecutionService,
                                             OperatorAuditService operatorAuditService) {
        this.backtestResultRepository = backtestResultRepository;
        this.backtestExecutionService = backtestExecutionService;
        this.operatorAuditService = operatorAuditService;
    }

    @Override
    public String participantName() {
        return "backtests";
    }

    @Override
    public int recoverPendingWork() {
        List<BacktestResult> recoverable = backtestResultRepository.findByExecutionStatusInOrderByTimestampAsc(
            List.of(BacktestResult.ExecutionStatus.PENDING, BacktestResult.ExecutionStatus.RUNNING)
        );
        if (recoverable.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        recoverable.forEach(result -> {
            BacktestResult.ExecutionStatus previousStatus = result.getExecutionStatus();
            result.setExecutionStatus(BacktestResult.ExecutionStatus.PENDING);
            result.setExecutionStage(BacktestResult.ExecutionStage.QUEUED);
            result.setProgressPercent(0);
            result.setProcessedCandles(0);
            result.setTotalCandles(0);
            result.setCurrentDataTimestamp(null);
            result.setCompletedAt(null);
            result.setLastProgressAt(now);
            result.setStatusMessage(previousStatus == BacktestResult.ExecutionStatus.RUNNING
                ? "Recovered on startup after interrupted execution. Restarting the run from the beginning."
                : "Recovered queued run on startup. Dispatching execution worker."
            );
            result.setErrorMessage(null);
        });
        backtestResultRepository.saveAll(recoverable);
        backtestResultRepository.flush();

        recoverable.forEach(result -> {
            logger.warn("Recovering backtest {} from startup scan with message: {}", result.getId(), result.getStatusMessage());
            backtestExecutionService.executeAsync(result.getId());
            operatorAuditService.recordSuccess(
                "BACKTEST_RECOVERED_ON_STARTUP",
                "test",
                "BACKTEST",
                String.valueOf(result.getId()),
                "status=" + result.getStatusMessage()
            );
        });
        return recoverable.size();
    }
}
