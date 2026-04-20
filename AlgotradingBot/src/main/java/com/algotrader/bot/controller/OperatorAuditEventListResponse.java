package com.algotrader.bot.controller;

import java.util.List;

public record OperatorAuditEventListResponse(
    OperatorAuditSummaryResponse summary,
    List<OperatorAuditEventResponse> events
) {}
