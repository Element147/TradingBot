package com.algotrader.bot.controller;

import com.algotrader.bot.service.ExchangeIntegrationService;
import com.algotrader.bot.service.OperatorAuditService;
import com.algotrader.bot.service.SystemOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System information and exchange connectivity checks")
@SecurityRequirement(name = "bearer-jwt")
public class SystemController {

    private final SystemOperationsService systemOperationsService;
    private final ExchangeIntegrationService exchangeIntegrationService;
    private final OperatorAuditService operatorAuditService;

    public SystemController(
        SystemOperationsService systemOperationsService,
        ExchangeIntegrationService exchangeIntegrationService,
        OperatorAuditService operatorAuditService
    ) {
        this.systemOperationsService = systemOperationsService;
        this.exchangeIntegrationService = exchangeIntegrationService;
        this.operatorAuditService = operatorAuditService;
    }

    @GetMapping("/info")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get system info")
    public ResponseEntity<SystemInfoResponse> getSystemInfo() {
        return ResponseEntity.ok(systemOperationsService.getSystemInfo());
    }

    @PostMapping("/test-connection")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Test exchange API connection")
    public ResponseEntity<ExchangeConnectionStatusResponse> testExchangeConnection(
        @RequestBody(required = false) ExchangeConnectionTestRequest request
    ) {
        return ResponseEntity.ok(exchangeIntegrationService.testConnection(request));
    }

    @PostMapping("/backup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trigger a database backup artifact")
    public ResponseEntity<BackupResponse> triggerBackup() {
        return ResponseEntity.ok(systemOperationsService.triggerBackup());
    }

    @GetMapping("/audit-events")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List recent operator audit events")
    public ResponseEntity<OperatorAuditEventListResponse> listAuditEvents(
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int limit,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String environment,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String outcome,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String targetType,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(operatorAuditService.listEvents(limit, environment, outcome, targetType, search));
    }
}
