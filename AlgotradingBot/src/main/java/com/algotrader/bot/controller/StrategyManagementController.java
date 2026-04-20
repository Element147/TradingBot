package com.algotrader.bot.controller;

import com.algotrader.bot.service.StrategyManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/strategies")
@Tag(name = "Strategy Management", description = "Strategy list/start/stop/config endpoints")
public class StrategyManagementController {

    private final StrategyManagementService strategyManagementService;

    public StrategyManagementController(StrategyManagementService strategyManagementService) {
        this.strategyManagementService = strategyManagementService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List strategies")
    public ResponseEntity<List<StrategyDetailsResponse>> listStrategies() {
        return ResponseEntity.ok(strategyManagementService.listStrategies());
    }

    @PostMapping("/{strategyId}/start")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Start strategy in paper mode")
    public ResponseEntity<StrategyActionResponse> startStrategy(@PathVariable Long strategyId) {
        return ResponseEntity.ok(strategyManagementService.startStrategy(strategyId));
    }

    @PostMapping("/{strategyId}/stop")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Stop strategy")
    public ResponseEntity<StrategyActionResponse> stopStrategy(@PathVariable Long strategyId) {
        return ResponseEntity.ok(strategyManagementService.stopStrategy(strategyId));
    }

    @PutMapping("/{strategyId}/config")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update strategy config")
    public ResponseEntity<StrategyDetailsResponse> updateConfig(
        @PathVariable Long strategyId,
        @Valid @RequestBody UpdateStrategyConfigRequest request
    ) {
        return ResponseEntity.ok(strategyManagementService.updateStrategyConfig(strategyId, request));
    }

    @GetMapping("/{strategyId}/config-history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List strategy config history")
    public ResponseEntity<List<StrategyConfigHistoryResponse>> configHistory(@PathVariable Long strategyId) {
        return ResponseEntity.ok(strategyManagementService.getStrategyConfigHistory(strategyId));
    }
}
