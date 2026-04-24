package com.algotrader.bot.service;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.controller.BacktestDatasetDownloadResponse;
import com.algotrader.bot.entity.BacktestDataset;
import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleSegment;
import com.algotrader.bot.repository.BacktestDatasetRepository;
import com.algotrader.bot.repository.MarketDataCandleRepository;
import com.algotrader.bot.repository.MarketDataCandleSegmentRepository;
import com.algotrader.bot.service.marketdata.LegacyMarketDataMigrationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class BacktestDatasetStorageService {

    private static final long MAX_UPLOAD_BYTES = 250L * 1024L * 1024L;
    private static final DateTimeFormatter CSV_TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String CSV_HEADER = "timestamp,symbol,open,high,low,close,volume";

    private final BacktestDatasetRepository backtestDatasetRepository;
    private final HistoricalDataCsvParser historicalDataCsvParser;
    private final LegacyMarketDataMigrationService legacyMarketDataMigrationService;
    private final MarketDataCandleSegmentRepository marketDataCandleSegmentRepository;
    private final MarketDataCandleRepository marketDataCandleRepository;

    @Autowired
    public BacktestDatasetStorageService(BacktestDatasetRepository backtestDatasetRepository,
                                         HistoricalDataCsvParser historicalDataCsvParser,
                                         LegacyMarketDataMigrationService legacyMarketDataMigrationService,
                                         MarketDataCandleSegmentRepository marketDataCandleSegmentRepository,
                                         MarketDataCandleRepository marketDataCandleRepository) {
        this.backtestDatasetRepository = backtestDatasetRepository;
        this.historicalDataCsvParser = historicalDataCsvParser;
        this.legacyMarketDataMigrationService = legacyMarketDataMigrationService;
        this.marketDataCandleSegmentRepository = marketDataCandleSegmentRepository;
        this.marketDataCandleRepository = marketDataCandleRepository;
    }

    public BacktestDataset storeUploadedDataset(String requestedName, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        String filename = file.getOriginalFilename() == null ? "dataset.csv" : file.getOriginalFilename();
        byte[] csvBytes;
        try {
            csvBytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded file");
        }

        validateDatasetSize(csvBytes.length);
        List<OHLCVData> candles = historicalDataCsvParser.parse(csvBytes);
        String checksum = sha256Hex(csvBytes);
        return saveDataset(requestedName, filename, candles, checksum, "UPLOAD", "Persisted from uploaded dataset CSV.");
    }

    /**
     * Store an imported dataset from already-parsed OHLCV rows.
     * The caller (e.g. MarketDataImportExecutionService) owns the parsed candles and
     * must provide a deterministic checksum computed from the canonical serialization of those rows.
     */
    public BacktestDataset storeImportedDataset(String requestedName,
                                                String filename,
                                                List<OHLCVData> candles,
                                                String checksumSha256,
                                                String sourceDetails) {
        String detail = sourceDetails == null || sourceDetails.isBlank()
            ? "Imported dataset."
            : "Imported dataset from " + sourceDetails + ".";
        return saveDataset(requestedName, filename, candles, checksumSha256, "IMPORT_JOB", detail);
    }

    public BacktestDataset getDataset(Long datasetId) {
        return backtestDatasetRepository.findById(datasetId)
            .orElseThrow(() -> new EntityNotFoundException("Backtest dataset not found: " + datasetId));
    }

    public BacktestDatasetDownloadResponse downloadDataset(Long datasetId) {
        BacktestDataset dataset = getDataset(datasetId);
        byte[] normalizedCsv = buildNormalizedCsv(dataset);
        if (normalizedCsv == null) {
            throw new IllegalStateException(
                "Dataset " + datasetId + " has no normalized candle data in the relational store. "
                    + "Re-upload the dataset to populate the normalized store."
            );
        }
        return new BacktestDatasetDownloadResponse(
            dataset.getOriginalFilename(),
            dataset.getChecksumSha256(),
            dataset.getSchemaVersion(),
            normalizedCsv,
            "NORMALIZED_EXPORT"
        );
    }

    public void validateDatasetSize(long payloadSizeBytes) {
        if (payloadSizeBytes > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("CSV file is too large. Max size is 250MB");
        }
    }

    /**
     * Compute a stable SHA-256 checksum from the canonical CSV serialization of the given candles.
     * Use this when you have already-parsed rows and need to produce the checksum that would have
     * been computed from the original bytes (e.g. for import jobs that accumulate candles in memory).
     */
    public static String checksumFromCandles(List<OHLCVData> candles) {
        byte[] canonicalBytes = toCsvBytes(candles);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private BacktestDataset saveDataset(String requestedName,
                                        String filename,
                                        List<OHLCVData> candles,
                                        String checksumSha256,
                                        String normalizedSourceType,
                                        String normalizedNotes) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("CSV dataset does not contain any rows");
        }

        String name = (requestedName == null || requestedName.isBlank()) ? filename : requestedName.trim();
        if (name.length() < 3 || name.length() > 100) {
            throw new IllegalArgumentException("Dataset name must be between 3 and 100 characters");
        }

        Set<String> symbols = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        LocalDateTime start = null;
        LocalDateTime end = null;

        for (OHLCVData candle : candles) {
            symbols.add(candle.getSymbol());
            LocalDateTime timestamp = candle.getTimestamp();
            if (start == null || timestamp.isBefore(start)) {
                start = timestamp;
            }
            if (end == null || timestamp.isAfter(end)) {
                end = timestamp;
            }
        }

        BacktestDataset dataset = new BacktestDataset();
        dataset.setName(name);
        dataset.setOriginalFilename(filename);
        dataset.setRowCount(candles.size());
        dataset.setSymbolsCsv(String.join(",", symbols));
        dataset.setDataStart(start);
        dataset.setDataEnd(end);
        dataset.setChecksumSha256(checksumSha256);
        dataset.setSchemaVersion("ohlcv-v1");
        dataset.setArchived(Boolean.FALSE);
        BacktestDataset saved = backtestDatasetRepository.save(dataset);

        legacyMarketDataMigrationService.ingestDataset(
            saved,
            candles,
            normalizedSourceType,
            normalizedNotes,
            checksumSha256
        );
        return saved;
    }

    private String sha256Hex(byte[] payload) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private byte[] buildNormalizedCsv(BacktestDataset dataset) {
        if (marketDataCandleSegmentRepository == null || marketDataCandleRepository == null) {
            return null;
        }

        List<MarketDataCandleSegment> segments = marketDataCandleSegmentRepository.findByDatasetIdWithSeries(dataset.getId());
        if (segments.isEmpty()) {
            return null;
        }

        Set<String> timeframes = segments.stream()
            .map(MarketDataCandleSegment::getTimeframe)
            .collect(Collectors.toSet());
        if (timeframes.size() != 1) {
            return null;
        }
        boolean exactRawOnly = segments.stream()
            .allMatch(segment -> Objects.equals("EXACT_RAW", segment.getResolutionTier()));
        if (!exactRawOnly) {
            return null;
        }

        Map<SeriesTimeframeKey, List<MarketDataCandleSegment>> segmentsBySeries = segments.stream()
            .collect(Collectors.groupingBy(
                segment -> new SeriesTimeframeKey(segment.getSeries().getId(), segment.getTimeframe()),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<MarketDataCandle> candles = segmentsBySeries.values().stream()
            .flatMap(groupedSegments -> {
                MarketDataCandleSegment first = groupedSegments.getFirst();
                LocalDateTime coverageStart = groupedSegments.stream()
                    .map(MarketDataCandleSegment::getCoverageStart)
                    .min(LocalDateTime::compareTo)
                    .orElseThrow();
                LocalDateTime coverageEnd = groupedSegments.stream()
                    .map(MarketDataCandleSegment::getCoverageEnd)
                    .max(LocalDateTime::compareTo)
                    .orElseThrow();
                return marketDataCandleRepository.findDatasetSeriesCandlesInRange(
                    dataset.getId(),
                    first.getSeries().getId(),
                    first.getTimeframe(),
                    coverageStart,
                    coverageEnd
                ).stream();
            })
            .sorted((left, right) -> {
                int timestampComparison = left.getId().getBucketStart().compareTo(right.getId().getBucketStart());
                if (timestampComparison != 0) {
                    return timestampComparison;
                }
                return left.getSeries().getSymbolDisplay().compareToIgnoreCase(right.getSeries().getSymbolDisplay());
            })
            .toList();
        if (candles.isEmpty()) {
            return null;
        }

        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (MarketDataCandle candle : candles) {
            csv.append('\n')
                .append(CSV_TIMESTAMP_FORMATTER.format(candle.getId().getBucketStart()))
                .append(',')
                .append(candle.getSeries().getSymbolDisplay())
                .append(',')
                .append(candle.getOpenPrice().toPlainString())
                .append(',')
                .append(candle.getHighPrice().toPlainString())
                .append(',')
                .append(candle.getLowPrice().toPlainString())
                .append(',')
                .append(candle.getClosePrice().toPlainString())
                .append(',')
                .append(candle.getVolume().toPlainString());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Produce canonical CSV bytes from OHLCV rows — used to compute stable checksums for
     * import jobs that accumulate candles in memory rather than buffering raw CSV bytes.
     */
    private static byte[] toCsvBytes(List<OHLCVData> candles) {
        StringBuilder sb = new StringBuilder(CSV_HEADER);
        for (OHLCVData candle : candles) {
            sb.append('\n')
                .append(CSV_TIMESTAMP_FORMATTER.format(candle.getTimestamp()))
                .append(',')
                .append(candle.getSymbol())
                .append(',')
                .append(candle.getOpen().toPlainString())
                .append(',')
                .append(candle.getHigh().toPlainString())
                .append(',')
                .append(candle.getLow().toPlainString())
                .append(',')
                .append(candle.getClose().toPlainString())
                .append(',')
                .append(candle.getVolume().toPlainString());
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private record SeriesTimeframeKey(Long seriesId, String timeframe) {
    }
}
