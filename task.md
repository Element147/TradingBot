# Normalize Market Data Storage — Tasks

## Phase 1: Liquibase Migration
- `[ ]` Create `0019-remove-legacy-csv-columns.yaml` — drop `csv_data` from `backtest_datasets`, drop `staged_csv_data` from `market_data_import_jobs`

## Phase 2: Entities
- `[ ]` `BacktestDataset.java` — remove `csvData` field and its getter/setter; remove `@JdbcTypeCode` import if unused
- `[ ]` `MarketDataImportJob.java` — remove `stagedCsvData` field and its getter/setter

## Phase 3: Import Pipeline (no more staged CSV bytes)
- `[ ]` `MarketDataImportExecutionService.java` — replace CSV staging with an in-memory `List<OHLCVData>` accumulator; update `completeJob` to call the new `storeImportedDataset(name, filename, List<OHLCVData>, source)`

## Phase 4: Storage Service (no more raw bytes)
- `[ ]` `BacktestDatasetStorageService.java`:
  - Remove `storeImportedDataset(String, String, byte[])` overloads, replace with `(String, String, List<OHLCVData>, String)`
  - Update `saveDataset` to take `List<OHLCVData>` directly, compute checksum from serialized bytes for audit, do not store in DB
  - Update `downloadDataset` — remove fallback to `dataset.getCsvData()`; throw if normalized CSV cannot be built
  - Remove 2-arg constructor (no longer needed)

## Phase 5: Catalog Service (signature propagation)
- `[ ]` `BacktestDatasetCatalogService.java` — update `importDataset` to take `List<OHLCVData>` instead of `byte[]`

## Phase 6: Migration Service (parse from candles, not CSV)
- `[x]` `LegacyMarketDataMigrationService.java` — update `buildSymbolPlans` and `ingestDataset` to take `List<OHLCVData>` instead of reading `dataset.getCsvData()`

## Phase 7: Delete legacy cache
- `[x]` Delete `BacktestDatasetCandleCache.java`

## Phase 8: Query Service (no legacy fallback)
- `[x]` `MarketDataQueryService.java`:
  - Remove `loadLegacyCandles()` and references to it
  - Remove `BacktestDatasetCandleCache` and `BacktestDatasetStorageService` dependencies
  - If no relational data found, return empty result
- `[x]` Finalize `LegacyMarketDataMigrationService` decommissioning
- `[x]` Refactor `BacktestManagementControllerIntegrationTest` for normalized data
- `[x]` Update `MarketDataQueryServiceIntegrationTest` for robust gauge verification
- `[x]` Resolve `BacktestDatasetStorageServiceIntegrationTest` compilation issues
- `[x]` Resolve integration test database persistence conflicts (`IllegalStateException`)
- `[x]` Verify complete test suite stability (`gradlew test`)
- `[x]` Verify OpenAPI contract integrity (`npm run contract:check`)
- `[x]` Commit and push all finalized changes to `main`

## Phase 9: Metrics Binder (remove legacy cache metrics)
- `[x]` `OperationalWorkloadMetricsBinder.java` — remove `BacktestDatasetCandleCache` dependency and its 4 metric gauges

## Phase 10: Audit Dataset Seeder
- `[x]` Create `AuditDatasetSeeder.java` — a Spring `ApplicationRunner` that, when `AUDIT_DATASET_FILE` env var is set, uploads the CSV file to the normalized store at startup (for test/CI environments)
- `[x]` Create a sample CSV seed file or document the seeding procedure

## Phase 11: Cleanup audit runner tests
- `[ ]` `LegacyMarketDataFlowAuditRunner.java` — refactor to not use `BacktestDatasetCandleCache`, work directly with parsed `OHLCVData`
- `[ ]` Verify `StrategyCatalogAuditRunner.java` still works (it queries via `MarketDataQueryService`, should be fine)

## Phase 12: Docs
- `[ ]` Update `PROJECT_STATUS.md`

## Phase 13: Verification
- `[ ]` `gradlew test`
- `[ ]` Commit + push
