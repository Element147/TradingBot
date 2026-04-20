package com.algotrader.bot.controller;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.repository.MarketDataImportJobRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataImportJobStatus;
import com.algotrader.bot.service.marketdata.MarketDataProvider;
import com.algotrader.bot.service.marketdata.MarketDataProviderDefinition;
import com.algotrader.bot.service.marketdata.MarketDataProviderFetchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(MarketDataControllerIntegrationTest.TestConfig.class)
class MarketDataControllerIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        StubMarketDataProvider marketDataControllerStubProvider() {
            return new StubMarketDataProvider();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MarketDataImportJobRepository marketDataImportJobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
        marketDataImportJobRepository.deleteAll();
    }

    @Test
    void createJob_returnsAcceptedWithLocationHeader() throws Exception {
        MarketDataImportJobRequest request = new MarketDataImportJobRequest(
            "controller-stub",
            MarketDataAssetType.CRYPTO,
            List.of("BTC/USDT"),
            "1h",
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-01-02"),
            "Controller Stub BTC 1h",
            false,
            false
        );

        mockMvc.perform(post("/api/market-data/jobs")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", matchesPattern("/api/market-data/jobs/\\d+")))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.datasetName").value("Controller Stub BTC 1h"));
    }

    @Test
    void job_returnsSingleAcceptedJobState() throws Exception {
        Long jobId = marketDataImportJobRepository.save(buildJob("Single job read", MarketDataImportJobStatus.QUEUED)).getId();

        mockMvc.perform(get("/api/market-data/jobs/{jobId}", jobId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(jobId))
            .andExpect(jsonPath("$.providerId").value("controller-stub"))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.symbolsCsv").value("BTC/USDT"));
    }

    @Test
    void retryJob_returnsAcceptedWithLocationHeader() throws Exception {
        Long jobId = marketDataImportJobRepository.save(buildJob("Retryable controller job", MarketDataImportJobStatus.FAILED)).getId();

        mockMvc.perform(post("/api/market-data/jobs/{jobId}/retry", jobId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/market-data/jobs/" + jobId))
            .andExpect(jsonPath("$.id").value(jobId))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.statusMessage").value("Retry requested. Job restarted from the beginning."));
    }

    private MarketDataImportJob buildJob(String datasetName, MarketDataImportJobStatus status) {
        MarketDataImportJob job = new MarketDataImportJob();
        job.setProviderId("controller-stub");
        job.setAssetType(MarketDataAssetType.CRYPTO);
        job.setDatasetName(datasetName);
        job.setSymbolsCsv("BTC/USDT");
        job.setTimeframe("1h");
        job.setStartDate(LocalDate.parse("2025-01-01"));
        job.setEndDate(LocalDate.parse("2025-01-02"));
        job.setAdjusted(false);
        job.setRegularSessionOnly(false);
        job.setStatus(status);
        job.setStatusMessage(status == MarketDataImportJobStatus.FAILED
            ? "Provider timed out."
            : "Queued. Waiting for downloader worker.");
        job.setCurrentSymbolIndex(0);
        job.setCurrentChunkStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        job.setImportedRowCount(0);
        job.setAttemptCount(status == MarketDataImportJobStatus.FAILED ? 3 : 0);
        return job;
    }

    static class StubMarketDataProvider implements MarketDataProvider {

        @Override
        public MarketDataProviderDefinition definition() {
            return new MarketDataProviderDefinition(
                "controller-stub",
                "Controller Stub Provider",
                "Controller integration test provider.",
                Set.of(MarketDataAssetType.CRYPTO),
                List.of("1h"),
                false,
                null,
                false,
                false,
                List.of("BTC/USDT"),
                "https://example.com/docs",
                "https://example.com/signup",
                "No setup required in tests."
            );
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public List<OHLCVData> fetch(MarketDataProviderFetchRequest request) {
            return List.of(
                new OHLCVData(
                    request.start(),
                    "BTC/USDT",
                    new BigDecimal("100"),
                    new BigDecimal("101"),
                    new BigDecimal("99"),
                    new BigDecimal("100"),
                    new BigDecimal("1000")
                )
            );
        }
    }
}
