package com.algotrader.bot.controller;

import com.algotrader.bot.service.EnvironmentRequestResolver;
import com.algotrader.bot.service.RiskManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskManagementService riskManagementService;
    private final EnvironmentRequestResolver environmentRequestResolver;

    public RiskController(
        RiskManagementService riskManagementService,
        EnvironmentRequestResolver environmentRequestResolver
    ) {
        this.riskManagementService = riskManagementService;
        this.environmentRequestResolver = environmentRequestResolver;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RiskStatusResponse> status() {
        return ResponseEntity.ok(riskManagementService.getStatus());
    }

    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RiskConfigResponse> config() {
        return ResponseEntity.ok(riskManagementService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RiskConfigResponse> updateConfig(@Valid @RequestBody UpdateRiskConfigRequest request) {
        return ResponseEntity.ok(riskManagementService.updateConfig(request));
    }

    @GetMapping("/circuit-breakers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RiskConfigResponse>> circuitBreakers() {
        return ResponseEntity.ok(riskManagementService.getCircuitBreakers());
    }

    @PostMapping("/circuit-breaker/override")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RiskConfigResponse> overrideCircuitBreaker(
        @RequestParam(required = false) String env,
        @RequestHeader(name = "X-Environment", required = false) String headerEnvironment,
        @RequestParam(required = false) String context,
        @RequestHeader(name = "X-Execution-Context", required = false) String headerExecutionContext,
        @Valid @RequestBody CircuitBreakerOverrideRequest request
    ) {
        String environment = environmentRequestResolver.resolve(
            env,
            headerEnvironment,
            context,
            headerExecutionContext
        );
        return ResponseEntity.ok(riskManagementService.overrideCircuitBreaker(environment, request));
    }

    @GetMapping("/alerts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RiskAlertResponse>> alerts() {
        return ResponseEntity.ok(riskManagementService.getAlerts());
    }
}
