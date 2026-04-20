package com.algotrader.bot.controller;

import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.BacktestEquityPoint;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.BacktestTradeSeriesItem;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import com.algotrader.bot.service.recovery.BacktestStartupRecoveryParticipant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BacktestManagementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BacktestResultRepository backtestResultRepository;

    @Autowired
    private BacktestDatasetRepository backtestDatasetRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private BacktestStartupRecoveryParticipant backtestStartupRecoveryParticipant;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String authToken;
    private Long backtestId;
    private Long comparisonBacktestId;
    private Long failedBacktestId;
    private Long datasetId;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
        backtestResultRepository.deleteAll();
        backtestDatasetRepository.deleteAll();

        BacktestDataset dataset = new BacktestDataset();
        dataset.setName("sample-btc");
        dataset.setOriginalFilename("sample-btc.csv");
        dataset.setCsvData((
            "timestamp,symbol,open,high,low,close,volume\n" +
            "2025-01-01T00:00:00,BTC/USDT,100,101,99,100,1\n" +
            "2025-01-01T01:00:00,BTC/USDT,100,102,99,101,1\n" +
            "2025-01-01T02:00:00,BTC/USDT,101,103,100,102,1\n" +
            "2025-01-01T03:00:00,BTC/USDT,102,104,101,103,1\n" +
            "2025-01-01T04:00:00,BTC/USDT,103,105,102,104,1\n" +
            "2025-01-01T05:00:00,BTC/USDT,104,106,103,105,1\n" +
            "2025-01-01T06:00:00,BTC/USDT,105,107,104,106,1\n" +
            "2025-01-01T07:00:00,BTC/USDT,106,108,105,107,1\n" +
            "2025-01-01T08:00:00,BTC/USDT,107,109,106,108,1\n" +
            "2025-01-01T09:00:00,BTC/USDT,108,110,107,109,1\n" +
            "2025-01-01T10:00:00,BTC/USDT,109,111,108,110,1\n" +
            "2025-01-01T11:00:00,BTC/USDT,110,112,109,111,1\n" +
            "2025-01-01T12:00:00,BTC/USDT,111,113,110,112,1\n" +
            "2025-01-01T13:00:00,BTC/USDT,112,114,111,113,1\n" +
            "2025-01-01T14:00:00,BTC/USDT,113,115,112,114,1\n" +
            "2025-01-01T15:00:00,BTC/USDT,114,116,113,115,1\n" +
            "2025-01-01T16:00:00,BTC/USDT,115,117,114,116,1\n" +
            "2025-01-01T17:00:00,BTC/USDT,116,118,115,117,1\n" +
            "2025-01-01T18:00:00,BTC/USDT,117,119,116,118,1\n" +
            "2025-01-01T19:00:00,BTC/USDT,118,120,117,119,1\n" +
            "2025-01-01T20:00:00,BTC/USDT,119,121,118,120,1\n" +
            "2025-01-01T21:00:00,BTC/USDT,120,122,119,121,1\n" +
            "2025-01-01T22:00:00,BTC/USDT,121,123,120,122,1\n" +
            "2025-01-01T23:00:00,BTC/USDT,122,124,121,123,1\n"
        ).getBytes());
        dataset.setRowCount(24);
        dataset.setSymbolsCsv("BTC/USDT");
        dataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        dataset.setDataEnd(LocalDateTime.parse("2025-01-01T23:00:00"));
        dataset.setChecksumSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        dataset.setSchemaVersion("ohlcv-v1");
        datasetId = backtestDatasetRepository.save(dataset).getId();

        BacktestDataset universeDataset = new BacktestDataset();
        universeDataset.setName("multi-asset-universe");
        universeDataset.setOriginalFilename("multi-asset.csv");
        universeDataset.setCsvData((
            "timestamp,symbol,open,high,low,close,volume\n" +
            "2025-01-01T00:00:00,BTC/USDT,100,101,99,100,1\n" +
            "2025-01-01T01:00:00,BTC/USDT,101,102,100,101,1\n" +
            "2025-01-01T02:00:00,BTC/USDT,102,103,101,102,1\n" +
            "2025-01-01T03:00:00,BTC/USDT,103,104,102,103,1\n" +
            "2025-01-01T04:00:00,BTC/USDT,104,105,103,104,1\n" +
            "2025-01-01T05:00:00,BTC/USDT,105,106,104,105,1\n" +
            "2025-01-01T06:00:00,BTC/USDT,106,107,105,106,1\n" +
            "2025-01-01T07:00:00,BTC/USDT,107,108,106,107,1\n" +
            "2025-01-01T08:00:00,BTC/USDT,108,109,107,108,1\n" +
            "2025-01-01T09:00:00,BTC/USDT,109,110,108,109,1\n" +
            "2025-01-01T10:00:00,BTC/USDT,110,111,109,110,1\n" +
            "2025-01-01T11:00:00,BTC/USDT,111,112,110,111,1\n" +
            "2025-01-01T12:00:00,ETH/USDT,200,201,199,200,1\n" +
            "2025-01-01T13:00:00,ETH/USDT,201,202,200,201,1\n" +
            "2025-01-01T14:00:00,ETH/USDT,202,203,201,202,1\n" +
            "2025-01-01T15:00:00,ETH/USDT,203,204,202,203,1\n" +
            "2025-01-01T16:00:00,ETH/USDT,204,205,203,204,1\n" +
            "2025-01-01T17:00:00,ETH/USDT,205,206,204,205,1\n" +
            "2025-01-01T18:00:00,ETH/USDT,206,207,205,206,1\n" +
            "2025-01-01T19:00:00,ETH/USDT,207,208,206,207,1\n" +
            "2025-01-01T20:00:00,ETH/USDT,208,209,207,208,1\n" +
            "2025-01-01T21:00:00,ETH/USDT,209,210,208,209,1\n" +
            "2025-01-01T22:00:00,ETH/USDT,210,211,209,210,1\n" +
            "2025-01-01T23:00:00,ETH/USDT,211,212,210,211,1\n"
        ).getBytes());
        universeDataset.setRowCount(24);
        universeDataset.setSymbolsCsv("BTC/USDT,ETH/USDT");
        universeDataset.setDataStart(LocalDateTime.parse("2025-01-01T00:00:00"));
        universeDataset.setDataEnd(LocalDateTime.parse("2025-01-01T23:00:00"));
        universeDataset.setChecksumSha256("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        universeDataset.setSchemaVersion("ohlcv-v1");
        backtestDatasetRepository.save(universeDataset);

        BacktestResult result = new BacktestResult(
            "BOLLINGER_BANDS",
            "BTC/USDT",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T23:00:00"),
            new BigDecimal("1000"),
            new BigDecimal("1100"),
            new BigDecimal("1.2"),
            new BigDecimal("1.6"),
            new BigDecimal("52.0"),
            new BigDecimal("18.0"),
            60,
            BacktestResult.ValidationStatus.PASSED
        );
        result.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        result.setTimeframe("1h");
        result.setDatasetId(datasetId);
        result.setDatasetName("sample-btc");
        result.setExperimentName("BTC Mean Reversion Retest");
        result.setExperimentKey("btc-mean-reversion-retest");
        result.setTimestamp(LocalDateTime.parse("2026-03-01T10:00:00"));
        BacktestEquityPoint equityPoint = new BacktestEquityPoint();
        equityPoint.setPointTimestamp(LocalDateTime.parse("2025-01-01T12:00:00"));
        equityPoint.setEquity(new BigDecimal("1000"));
        equityPoint.setDrawdownPct(BigDecimal.ZERO);
        result.addEquityPoint(equityPoint);
        BacktestTradeSeriesItem tradeSeriesItem = new BacktestTradeSeriesItem();
        tradeSeriesItem.setSymbol("BTC/USDT");
        tradeSeriesItem.setEntryTime(LocalDateTime.parse("2025-01-01T10:00:00"));
        tradeSeriesItem.setExitTime(LocalDateTime.parse("2025-01-01T12:00:00"));
        tradeSeriesItem.setEntryPrice(new BigDecimal("100"));
        tradeSeriesItem.setExitPrice(new BigDecimal("110"));
        tradeSeriesItem.setQuantity(new BigDecimal("5"));
        tradeSeriesItem.setEntryValue(new BigDecimal("500"));
        tradeSeriesItem.setExitValue(new BigDecimal("550"));
        tradeSeriesItem.setReturnPct(new BigDecimal("10.0000"));
        result.addTradeSeriesItem(tradeSeriesItem);
        backtestId = backtestResultRepository.save(result).getId();

        BacktestResult comparison = new BacktestResult(
            "SMA_CROSSOVER",
            "BTC/USDT",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T23:00:00"),
            new BigDecimal("1000"),
            new BigDecimal("950"),
            new BigDecimal("0.7"),
            new BigDecimal("1.2"),
            new BigDecimal("45.0"),
            new BigDecimal("22.0"),
            48,
            BacktestResult.ValidationStatus.FAILED
        );
        comparison.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        comparison.setTimeframe("1h");
        comparison.setDatasetId(datasetId);
        comparison.setDatasetName("sample-btc");
        comparison.setExperimentName("BTC Mean Reversion Retest");
        comparison.setExperimentKey("btc-mean-reversion-retest");
        comparison.setFeesBps(12);
        comparison.setSlippageBps(5);
        comparison.setTimestamp(LocalDateTime.parse("2026-02-28T10:00:00"));
        comparisonBacktestId = backtestResultRepository.save(comparison).getId();

        BacktestResult failed = new BacktestResult(
            "DUAL_MOMENTUM_ROTATION",
            "ETH/USDT",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T23:00:00"),
            new BigDecimal("1500"),
            new BigDecimal("1400"),
            new BigDecimal("0.5"),
            new BigDecimal("0.9"),
            new BigDecimal("40.0"),
            new BigDecimal("26.0"),
            21,
            BacktestResult.ValidationStatus.PENDING
        );
        failed.setExecutionStatus(BacktestResult.ExecutionStatus.FAILED);
        failed.setExecutionStage(BacktestResult.ExecutionStage.FAILED);
        failed.setTimeframe("4h");
        failed.setDatasetName("multi-asset-universe");
        failed.setExperimentName("Universe rotation sweep");
        failed.setExperimentKey("universe-rotation-sweep");
        failed.setFeesBps(18);
        failed.setSlippageBps(7);
        failed.setStatusMessage("Run failed while simulating portfolio rotation.");
        failed.setTimestamp(LocalDateTime.parse("2026-03-02T10:00:00"));
        failedBacktestId = backtestResultRepository.save(failed).getId();
    }

    @Test
    void history_returnsItems() throws Exception {
        mockMvc.perform(get("/api/backtests")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(25))
            .andExpect(jsonPath("$.items[0].id").value(failedBacktestId))
            .andExpect(jsonPath("$.items[0].datasetName").value("multi-asset-universe"))
            .andExpect(jsonPath("$.items[0].experimentName").value("Universe rotation sweep"))
            .andExpect(jsonPath("$.items[0].feesBps").value(18))
            .andExpect(jsonPath("$.items[0].slippageBps").value(7))
            .andExpect(jsonPath("$.items[0].executionStage").value("FAILED"))
            .andExpect(jsonPath("$.items[0].progressPercent").value(100))
            .andExpect(jsonPath("$.items[0].statusMessage").value("Run failed while simulating portfolio rotation."));
    }

    @Test
    void history_appliesPagingSortingAndFilters() throws Exception {
        mockMvc.perform(get("/api/backtests")
                .param("page", "1")
                .param("pageSize", "1")
                .param("sortBy", "feesBps")
                .param("sortDirection", "asc")
                .param("search", "rotation")
                .param("strategyId", "dual")
                .param("datasetName", "multi")
                .param("experimentName", "sweep")
                .param("market", "eth")
                .param("executionStatus", "FAILED")
                .param("validationStatus", "PENDING")
                .param("feesBpsMin", "15")
                .param("feesBpsMax", "20")
                .param("slippageBpsMin", "7")
                .param("slippageBpsMax", "7")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(failedBacktestId))
            .andExpect(jsonPath("$.items[0].strategyId").value("DUAL_MOMENTUM_ROTATION"))
            .andExpect(jsonPath("$.items[0].executionStatus").value("FAILED"))
            .andExpect(jsonPath("$.items[0].validationStatus").value("PENDING"));
    }

    @Test
    void history_sanitizesInvalidPagingAndSortDefaults() throws Exception {
        mockMvc.perform(get("/api/backtests")
                .param("page", "0")
                .param("pageSize", "999")
                .param("sortBy", "bogus")
                .param("sortDirection", "sideways")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(100))
            .andExpect(jsonPath("$.items[0].id").value(failedBacktestId))
            .andExpect(jsonPath("$.items[1].id").value(backtestId))
            .andExpect(jsonPath("$.items[2].id").value(comparisonBacktestId));
    }

    @Test
    void details_returnsSingleBacktest() throws Exception {
        mockMvc.perform(get("/api/backtests/{backtestId}", backtestId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(backtestId))
            .andExpect(jsonPath("$.strategyId").value("BOLLINGER_BANDS"))
            .andExpect(jsonPath("$.datasetName").value("sample-btc"))
            .andExpect(jsonPath("$.experimentName").value("BTC Mean Reversion Retest"))
            .andExpect(jsonPath("$.experimentKey").value("btc-mean-reversion-retest"))
            .andExpect(jsonPath("$.datasetChecksumSha256").value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .andExpect(jsonPath("$.datasetSchemaVersion").value("ohlcv-v1"))
            .andExpect(jsonPath("$.datasetArchived").value(false))
            .andExpect(jsonPath("$.executionStage").value("COMPLETED"))
            .andExpect(jsonPath("$.progressPercent").value(100))
            .andExpect(jsonPath("$.availableTelemetrySymbols", hasItem("BTC/USDT")))
            .andExpect(jsonPath("$.equityCurve").doesNotExist())
            .andExpect(jsonPath("$.tradeSeries").doesNotExist())
            .andExpect(jsonPath("$.telemetry").doesNotExist());
    }

    @Test
    void summary_returnsLightweightBacktestMetadataWithoutHeavySeries() throws Exception {
        mockMvc.perform(get("/api/backtests/{backtestId}/summary", backtestId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(backtestId))
            .andExpect(jsonPath("$.strategyId").value("BOLLINGER_BANDS"))
            .andExpect(jsonPath("$.datasetName").value("sample-btc"))
            .andExpect(jsonPath("$.experimentKey").value("btc-mean-reversion-retest"))
            .andExpect(jsonPath("$.executionStage").value("COMPLETED"))
            .andExpect(jsonPath("$.progressPercent").value(100))
            .andExpect(jsonPath("$.datasetChecksumSha256").value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .andExpect(jsonPath("$.equityCurve").doesNotExist())
            .andExpect(jsonPath("$.tradeSeries").doesNotExist())
            .andExpect(jsonPath("$.telemetry").doesNotExist());
    }

    @Test
    void equityCurve_returnsSeriesIndependently() throws Exception {
        mockMvc.perform(get("/api/backtests/{backtestId}/equity", backtestId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].equity").value(1000))
            .andExpect(jsonPath("$[0].drawdownPct").value(0));
    }

    @Test
    void tradeSeries_returnsSeriesIndependently() throws Exception {
        mockMvc.perform(get("/api/backtests/{backtestId}/trades", backtestId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].symbol").value("BTC/USDT"))
            .andExpect(jsonPath("$[0].returnPct").value(10.0000));
    }

    @Test
    void telemetry_returnsRequestedSymbolIndependently() throws Exception {
        mockMvc.perform(get("/api/backtests/{backtestId}/telemetry", backtestId)
                .param("symbol", "BTC/USDT")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedSymbol").value("BTC/USDT"))
            .andExpect(jsonPath("$.resolvedSymbol").value("BTC/USDT"))
            .andExpect(jsonPath("$.availableSymbols", hasItem("BTC/USDT")))
            .andExpect(jsonPath("$.telemetry.symbol").value("BTC/USDT"))
            .andExpect(jsonPath("$.telemetry.points", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void deleteBacktest_removesCompletedResult() throws Exception {
        mockMvc.perform(delete("/api/backtests/{backtestId}", backtestId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertFalse(backtestResultRepository.findById(backtestId).isPresent());
    }

    @Test
    void startupRecovery_requeuesInterruptedBacktestAndCompletesIt() throws Exception {
        BacktestResult interrupted = new BacktestResult(
            "BUY_AND_HOLD",
            "BTC/USDT",
            LocalDateTime.parse("2025-01-01T00:00:00"),
            LocalDateTime.parse("2025-01-01T23:00:00"),
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            BacktestResult.ValidationStatus.PENDING
        );
        interrupted.setExecutionStatus(BacktestResult.ExecutionStatus.RUNNING);
        interrupted.setExecutionStage(BacktestResult.ExecutionStage.SIMULATING);
        interrupted.setProgressPercent(47);
        interrupted.setProcessedCandles(11);
        interrupted.setTotalCandles(24);
        interrupted.setCurrentDataTimestamp(LocalDateTime.parse("2025-01-01T11:00:00"));
        interrupted.setStatusMessage("Historical candles are being replayed.");
        interrupted.setDatasetId(datasetId);
        interrupted.setDatasetName("sample-btc");
        interrupted.setExperimentName("Interrupted recovery test");
        interrupted.setExperimentKey("interrupted-recovery-test");
        interrupted.setTimeframe("1h");
        Long interruptedId = backtestResultRepository.save(interrupted).getId();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        org.junit.jupiter.api.Assertions.assertEquals(1, backtestStartupRecoveryParticipant.recoverPendingWork());

        BacktestResult recovered = waitForBacktest(interruptedId);
        org.junit.jupiter.api.Assertions.assertEquals(BacktestResult.ExecutionStatus.COMPLETED, recovered.getExecutionStatus());
        org.junit.jupiter.api.Assertions.assertEquals(BacktestResult.ExecutionStage.COMPLETED, recovered.getExecutionStage());
        org.junit.jupiter.api.Assertions.assertEquals(100, recovered.getProgressPercent());
    }

    @Test
    void runBacktest_createsPendingRecord() throws Exception {
        RunBacktestRequest request = new RunBacktestRequest(
            "SMA_CROSSOVER",
            datasetId,
            "BTC/USDT",
            "1h",
            java.time.LocalDate.parse("2025-01-01"),
            java.time.LocalDate.parse("2025-01-02"),
            new BigDecimal("2000"),
            10,
            3,
            null
        );

        mockMvc.perform(post("/api/backtests/run")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", matchesPattern("/api/backtests/\\d+/summary")))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void runBacktest_datasetUniverseAcceptsMissingSymbolAndStoresSafeMarketLabel() throws Exception {
        Long universeDatasetId = backtestDatasetRepository.findAll().stream()
            .filter(dataset -> "multi-asset-universe".equals(dataset.getName()))
            .findFirst()
            .orElseThrow()
            .getId();

        RunBacktestRequest request = new RunBacktestRequest(
            "DUAL_MOMENTUM_ROTATION",
            universeDatasetId,
            null,
            "1h",
            java.time.LocalDate.parse("2025-01-01"),
            java.time.LocalDate.parse("2025-01-02"),
            new BigDecimal("2000"),
            10,
            3,
            "Universe rotation smoke test"
        );

        mockMvc.perform(post("/api/backtests/run")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", matchesPattern("/api/backtests/\\d+/summary")))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("PENDING"));

        BacktestResult saved = backtestResultRepository.findAllByOrderByTimestampDesc().stream()
            .findFirst()
            .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("DATASET_UNIVERSE", saved.getSymbol());
    }

    @Test
    void runBacktest_singleSymbolRejectsMissingSymbol() throws Exception {
        RunBacktestRequest request = new RunBacktestRequest(
            "SMA_CROSSOVER",
            datasetId,
            null,
            "1h",
            java.time.LocalDate.parse("2025-01-01"),
            java.time.LocalDate.parse("2025-01-02"),
            new BigDecimal("2000"),
            10,
            3,
            null
        );

        mockMvc.perform(post("/api/backtests/run")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.message").value("Selected symbol is required for single-symbol strategies"));
    }

    @Test
    void runBacktest_rejectsInvalidDateRange() throws Exception {
        RunBacktestRequest request = new RunBacktestRequest(
            "BOLLINGER_BANDS",
            datasetId,
            "BTC/USDT",
            "1h",
            java.time.LocalDate.parse("2025-01-02"),
            java.time.LocalDate.parse("2025-01-01"),
            new BigDecimal("2000"),
            10,
            3,
            null
        );

        mockMvc.perform(post("/api/backtests/run")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void listAlgorithms_andDatasets() throws Exception {
        mockMvc.perform(get("/api/backtests/algorithms")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("BUY_AND_HOLD"))
            .andExpect(jsonPath("$[1].id").value("DUAL_MOMENTUM_ROTATION"))
            .andExpect(jsonPath("$[1].selectionMode").value("DATASET_UNIVERSE"));

        mockMvc.perform(get("/api/backtests/datasets")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'sample-btc')]").isArray())
            .andExpect(jsonPath("$[?(@.name == 'sample-btc')].checksumSha256").isNotEmpty())
            .andExpect(jsonPath("$[?(@.name == 'sample-btc')].schemaVersion").value(org.hamcrest.Matchers.hasItem("ohlcv-v1")))
            .andExpect(jsonPath("$[?(@.name == 'sample-btc')].archived").value(org.hamcrest.Matchers.hasItem(false)))
            .andExpect(jsonPath("$[?(@.name == 'sample-btc')].usageCount").isNotEmpty())
            .andExpect(jsonPath("$[?(@.name == 'sample-btc')].retentionStatus").value(org.hamcrest.Matchers.hasItem("ACTIVE")));

        mockMvc.perform(get("/api/backtests/datasets/retention-report")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDatasets").value(2))
            .andExpect(jsonPath("$.activeDatasets").value(2))
            .andExpect(jsonPath("$.referencedDatasetCount").value(1));
    }

    @Test
    void replayBacktest_createsPendingRecord() throws Exception {
        mockMvc.perform(post("/api/backtests/{backtestId}/replay", backtestId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", matchesPattern("/api/backtests/\\d+/summary")))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void experiments_returnsGroupedSummaries() throws Exception {
        mockMvc.perform(get("/api/backtests/experiments")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.experimentName == 'BTC Mean Reversion Retest')].runCount")
                .value(org.hamcrest.Matchers.hasItem(2)))
            .andExpect(jsonPath("$[?(@.experimentName == 'Universe rotation sweep')].latestBacktestId")
                .value(org.hamcrest.Matchers.hasItem(failedBacktestId.intValue())));
    }

    @Test
    void experiments_groupsLegacyRunsWithoutExperimentKey() throws Exception {
        BacktestResult legacyFirst = new BacktestResult(
            "SMA_CROSSOVER",
            "BTC/USDT",
            LocalDateTime.now().minusDays(10),
            LocalDateTime.now().minusDays(9),
            new BigDecimal("1000"),
            new BigDecimal("1015"),
            new BigDecimal("1.0"),
            new BigDecimal("1.3"),
            new BigDecimal("51.0"),
            new BigDecimal("12.0"),
            18,
            BacktestResult.ValidationStatus.PASSED
        );
        legacyFirst.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        legacyFirst.setTimeframe("1h");
        legacyFirst.setDatasetId(datasetId);
        legacyFirst.setDatasetName("sample-btc");
        legacyFirst.setExperimentName("Legacy Momentum Review");
        legacyFirst.setExperimentKey(null);
        legacyFirst.setTimestamp(LocalDateTime.now().minusHours(2));
        backtestResultRepository.save(legacyFirst);

        BacktestResult legacySecond = new BacktestResult(
            "SMA_CROSSOVER",
            "BTC/USDT",
            LocalDateTime.now().minusDays(8),
            LocalDateTime.now().minusDays(7),
            new BigDecimal("1000"),
            new BigDecimal("990"),
            new BigDecimal("0.8"),
            new BigDecimal("1.1"),
            new BigDecimal("47.0"),
            new BigDecimal("14.0"),
            16,
            BacktestResult.ValidationStatus.FAILED
        );
        legacySecond.setExecutionStatus(BacktestResult.ExecutionStatus.COMPLETED);
        legacySecond.setTimeframe("1h");
        legacySecond.setDatasetId(datasetId);
        legacySecond.setDatasetName("sample-btc");
        legacySecond.setExperimentName("Legacy Momentum Review");
        legacySecond.setExperimentKey(null);
        legacySecond.setTimestamp(LocalDateTime.now().minusHours(1));
        backtestResultRepository.save(legacySecond);

        mockMvc.perform(get("/api/backtests/experiments")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.experimentKey == 'legacy-momentum-review')].runCount").value(hasItem(2)))
            .andExpect(jsonPath("$[?(@.experimentKey == 'legacy-momentum-review')].experimentName").value(hasItem("Legacy Momentum Review")));
    }

    @Test
    void compareBacktests_returnsComparisonItems() throws Exception {
        mockMvc.perform(get("/api/backtests/compare")
                .param("ids", String.valueOf(backtestId))
                .param("ids", String.valueOf(comparisonBacktestId))
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baselineBacktestId").value(backtestId))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].id").value(backtestId))
            .andExpect(jsonPath("$.items[1].id").value(comparisonBacktestId))
            .andExpect(jsonPath("$.items[0].datasetChecksumSha256").value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .andExpect(jsonPath("$.items[0].datasetSchemaVersion").value("ohlcv-v1"))
            .andExpect(jsonPath("$.items[1].totalReturnDeltaPercent").exists());
    }

    @Test
    void downloadDataset_returnsCsvWithMetadataHeaders() throws Exception {
        mockMvc.perform(get("/api/backtests/datasets/{datasetId}/download", datasetId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Dataset-Checksum-Sha256"))
            .andExpect(header().string("X-Dataset-Schema-Version", "ohlcv-v1"))
            .andExpect(header().string("X-Dataset-Download-Source", "LEGACY_CSV_COMPATIBILITY"))
            .andExpect(content().string(containsString("timestamp,symbol,open,high,low,close,volume")));
    }

    @Test
    void archiveAndRestoreDataset_updatesLifecycleMetadata() throws Exception {
        mockMvc.perform(post("/api/backtests/datasets/{datasetId}/archive", datasetId)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Superseded by cleaned upload\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(true))
            .andExpect(jsonPath("$.archiveReason").value("Superseded by cleaned upload"))
            .andExpect(jsonPath("$.archivedAt", notNullValue()))
            .andExpect(jsonPath("$.retentionStatus").value("ARCHIVED"));

        mockMvc.perform(post("/api/backtests/datasets/{datasetId}/restore", datasetId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(false))
            .andExpect(jsonPath("$.archiveReason").isEmpty())
            .andExpect(jsonPath("$.retentionStatus").value("ACTIVE"));
    }

    @Test
    void runBacktest_rejectsArchivedDataset() throws Exception {
        BacktestDataset dataset = backtestDatasetRepository.findById(datasetId).orElseThrow();
        dataset.setArchived(Boolean.TRUE);
        dataset.setArchivedAt(LocalDateTime.now());
        dataset.setArchiveReason("Retired from active catalog");
        backtestDatasetRepository.save(dataset);

        RunBacktestRequest request = new RunBacktestRequest(
            "SMA_CROSSOVER",
            datasetId,
            "BTC/USDT",
            "1h",
            java.time.LocalDate.parse("2025-01-01"),
            java.time.LocalDate.parse("2025-01-02"),
            new BigDecimal("2000"),
            10,
            3,
            "Momentum Retest"
        );

        mockMvc.perform(post("/api/backtests/run")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.message").value(containsString("Archived datasets cannot be used for new backtests")));
    }

    private BacktestResult waitForBacktest(Long id) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            BacktestResult result = backtestResultRepository.findById(id).orElseThrow();
            if (result.getExecutionStatus() == BacktestResult.ExecutionStatus.COMPLETED
                || result.getExecutionStatus() == BacktestResult.ExecutionStatus.FAILED) {
                return result;
            }
            Thread.sleep(100);
        }
        return backtestResultRepository.findById(id).orElseThrow();
    }
}
