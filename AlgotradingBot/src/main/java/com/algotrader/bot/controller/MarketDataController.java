package com.algotrader.bot.controller;

import com.algotrader.bot.service.marketdata.MarketDataImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataImportService marketDataImportService;

    public MarketDataController(MarketDataImportService marketDataImportService) {
        this.marketDataImportService = marketDataImportService;
    }

    @GetMapping("/providers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MarketDataProviderResponse>> providers() {
        return ResponseEntity.ok(marketDataImportService.listProviders());
    }

    @GetMapping("/provider-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MarketDataProviderCredentialResponse>> providerCredentials() {
        return ResponseEntity.ok(marketDataImportService.listProviderCredentials());
    }

    @GetMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MarketDataImportJobResponse>> jobs() {
        return ResponseEntity.ok(marketDataImportService.listJobs());
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MarketDataImportJobResponse> job(@PathVariable Long jobId) {
        return ResponseEntity.ok(marketDataImportService.getJobResponse(jobId));
    }

    @PostMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MarketDataImportJobResponse> createJob(@Valid @RequestBody MarketDataImportJobRequest request) {
        MarketDataImportJobResponse created = marketDataImportService.createJob(request);
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, "/api/market-data/jobs/" + created.id())
            .body(created);
    }

    @PostMapping("/provider-credentials/{providerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MarketDataProviderCredentialResponse> saveProviderCredential(
        @PathVariable String providerId,
        @Valid @RequestBody MarketDataProviderCredentialRequest request
    ) {
        return ResponseEntity.ok(marketDataImportService.saveProviderCredential(providerId, request));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MarketDataImportJobResponse> retryJob(@PathVariable Long jobId) {
        MarketDataImportJobResponse retried = marketDataImportService.retryJob(jobId);
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, "/api/market-data/jobs/" + retried.id())
            .body(retried);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MarketDataImportJobResponse> cancelJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(marketDataImportService.cancelJob(jobId));
    }

    @DeleteMapping("/provider-credentials/{providerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProviderCredential(@PathVariable String providerId) {
        marketDataImportService.deleteProviderCredential(providerId);
        return ResponseEntity.noContent().build();
    }
}
