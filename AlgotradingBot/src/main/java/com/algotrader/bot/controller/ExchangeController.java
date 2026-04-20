package com.algotrader.bot.controller;

import com.algotrader.bot.service.ExchangeConnectionProfileService;
import com.algotrader.bot.service.ExchangeIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exchange")
@Tag(name = "Exchange", description = "Exchange integration and status")
@SecurityRequirement(name = "bearer-jwt")
public class ExchangeController {

    private final ExchangeIntegrationService exchangeIntegrationService;
    private final ExchangeConnectionProfileService exchangeConnectionProfileService;

    public ExchangeController(
        ExchangeIntegrationService exchangeIntegrationService,
        ExchangeConnectionProfileService exchangeConnectionProfileService
    ) {
        this.exchangeIntegrationService = exchangeIntegrationService;
        this.exchangeConnectionProfileService = exchangeConnectionProfileService;
    }

    @GetMapping("/connection-status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get exchange connection status")
    public ResponseEntity<ExchangeConnectionStatusResponse> getConnectionStatus() {
        return ResponseEntity.ok(exchangeIntegrationService.getConnectionStatus());
    }

    @GetMapping("/connections")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List saved exchange connections for the current user")
    public ResponseEntity<ExchangeConnectionProfilesResponse> listConnections() {
        return ResponseEntity.ok(exchangeConnectionProfileService.listConnectionsForCurrentUser());
    }

    @PostMapping("/connections")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a saved exchange connection for the current user")
    public ResponseEntity<ExchangeConnectionProfileResponse> createConnection(
        @Valid @RequestBody ExchangeConnectionProfileRequest request
    ) {
        return ResponseEntity.ok(exchangeConnectionProfileService.createConnectionForCurrentUser(request));
    }

    @PutMapping("/connections/{connectionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a saved exchange connection for the current user")
    public ResponseEntity<ExchangeConnectionProfileResponse> updateConnection(
        @PathVariable String connectionId,
        @Valid @RequestBody ExchangeConnectionProfileRequest request
    ) {
        return ResponseEntity.ok(
            exchangeConnectionProfileService.updateConnectionForCurrentUser(connectionId, request)
        );
    }

    @PostMapping("/connections/{connectionId}/activate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Activate a saved exchange connection for the current user")
    public ResponseEntity<ExchangeConnectionProfileResponse> activateConnection(@PathVariable String connectionId) {
        return ResponseEntity.ok(
            exchangeConnectionProfileService.activateConnectionForCurrentUser(connectionId)
        );
    }

    @DeleteMapping("/connections/{connectionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a saved exchange connection for the current user")
    public ResponseEntity<Void> deleteConnection(@PathVariable String connectionId) {
        exchangeConnectionProfileService.deleteConnectionForCurrentUser(connectionId);
        return ResponseEntity.noContent().build();
    }
}
