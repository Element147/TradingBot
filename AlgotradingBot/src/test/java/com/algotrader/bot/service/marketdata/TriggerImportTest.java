package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.repository.MarketDataImportJobRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("This is a manual utility to trigger retries in a live dev environment, not a regression test.")
public class TriggerImportTest {

    @Autowired
    private MarketDataImportService marketDataImportService;

    @Autowired
    private MarketDataImportJobRepository marketDataImportJobRepository;

    @Autowired
    private org.springframework.core.env.Environment env;

    @Test
    @Transactional
    @Commit
    public void triggerRetry() {
        System.out.println("--- TRIGGER RETRY START ---");
        System.out.println("Using DB URL: " + env.getProperty("spring.datasource.url"));
        
        List<MarketDataImportJob> allJobs = marketDataImportJobRepository.findAll();
        System.out.println("Total jobs found: " + allJobs.size());
        for (MarketDataImportJob job : allJobs) {
            System.out.println("Job #" + job.getId() + ": " + job.getDatasetName() + " (Status: " + job.getStatus() + ")");
            System.out.println("  Message: " + job.getStatusMessage());
        }

        System.out.println("Finding jobs to retry...");
        List<MarketDataImportJob> failedJobs = marketDataImportJobRepository.findAll().stream()
            .filter(job -> job.getStatus() == MarketDataImportJobStatus.FAILED)
            .filter(job -> job.getStatusMessage().contains("25MB"))
            .toList();

        if (failedJobs.isEmpty()) {
            System.out.println("No failed jobs found with the 25MB error.");
            // Try searching for job #3 specifically if it exists
            marketDataImportJobRepository.findById(3L).ifPresent(job -> {
                System.out.println("Found job #3: " + job.getDatasetName() + " (Status: " + job.getStatus() + ")");
                marketDataImportService.retryJob(3L);
                System.out.println("Triggered retry for job #3.");
            });
        } else {
            for (MarketDataImportJob job : failedJobs) {
                System.out.println("Retrying job #" + job.getId() + ": " + job.getDatasetName());
                marketDataImportService.retryJob(job.getId());
                System.out.println("Triggered retry for job #" + job.getId());
            }
        }
    }
}
