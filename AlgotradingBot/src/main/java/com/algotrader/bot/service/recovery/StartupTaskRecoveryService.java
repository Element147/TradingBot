package com.algotrader.bot.service.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StartupTaskRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(StartupTaskRecoveryService.class);

    private final List<StartupRecoveryParticipant> participants;

    public StartupTaskRecoveryService(List<StartupRecoveryParticipant> participants) {
        this.participants = participants;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingTasksOnStartup() {
        Map<String, Integer> recovered = recoverPendingTasks();
        if (recovered.isEmpty()) {
            logger.info("Startup recovery scan found no registered long-running task participants.");
            return;
        }

        int totalRecovered = recovered.values().stream().mapToInt(Integer::intValue).sum();
        logger.info("Startup recovery scan finished. Re-dispatched {} unfinished tasks across {} participants: {}",
            totalRecovered,
            recovered.size(),
            recovered
        );
    }

    public Map<String, Integer> recoverPendingTasks() {
        Map<String, Integer> recovered = new LinkedHashMap<>();
        for (StartupRecoveryParticipant participant : participants) {
            int count = participant.recoverPendingWork();
            recovered.put(participant.participantName(), count);
        }
        return recovered;
    }
}
