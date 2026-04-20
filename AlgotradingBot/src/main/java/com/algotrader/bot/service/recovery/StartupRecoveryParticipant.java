package com.algotrader.bot.service.recovery;

public interface StartupRecoveryParticipant {

    String participantName();

    int recoverPendingWork();
}
