package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.controller.MarketDataImportJobResponse;
import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.service.BackendOperationMetrics;
import com.algotrader.bot.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MarketDataImportProgressService {

    private final WebSocketEventPublisher webSocketEventPublisher;
    private final MarketDataImportJobResponseMapper marketDataImportJobResponseMapper;
    private final BackendOperationMetrics backendOperationMetrics;

    public MarketDataImportProgressService(WebSocketEventPublisher webSocketEventPublisher,
                                           MarketDataImportJobResponseMapper marketDataImportJobResponseMapper,
                                           BackendOperationMetrics backendOperationMetrics) {
        this.webSocketEventPublisher = webSocketEventPublisher;
        this.marketDataImportJobResponseMapper = marketDataImportJobResponseMapper;
        this.backendOperationMetrics = backendOperationMetrics;
    }

    public void publish(MarketDataImportJob job) {
        long startedAt = System.nanoTime();
        MarketDataImportJobResponse response = marketDataImportJobResponseMapper.toResponse(job);
        webSocketEventPublisher.publishMarketDataImportProgress(
            "test",
            response.id(),
            response.providerId(),
            response.providerLabel(),
            response.assetType(),
            response.datasetName(),
            response.symbolsCsv(),
            response.timeframe(),
            response.startDate().toString(),
            response.endDate().toString(),
            response.adjusted(),
            response.regularSessionOnly(),
            response.status(),
            response.statusMessage(),
            response.nextRetryAt(),
            response.currentSymbolIndex(),
            response.totalSymbols(),
            response.currentSymbol(),
            response.importedRowCount(),
            response.datasetId(),
            response.datasetReady(),
            response.currentChunkStart(),
            response.attemptCount(),
            response.createdAt(),
            response.updatedAt(),
            response.startedAt(),
            response.completedAt()
        );
        backendOperationMetrics.record("publish", "market_data_import_progress", "websocket_payload", System.nanoTime() - startedAt, 1, 0L);
    }
}
