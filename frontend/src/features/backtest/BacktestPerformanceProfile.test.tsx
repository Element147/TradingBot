import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Profiler, type ProfilerOnRenderCallback } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import {
  backtestPerformanceBudgets,
  evaluateBacktestPerformanceBudgets,
} from './backtestPerformanceBudget';
import { BacktestResults } from './BacktestResults';
import BacktestTradeReviewPanel from './BacktestTradeReviewPanel';
import {
  createDrawdownCurve,
  createEquityCurve,
  createMonthlyReturns,
  createTradeDistribution,
} from './backtestVisualization';
import { buildOverlayLegend, buildWorkspaceMarkers, buildWorkspaceTrades } from './backtestWorkspace';

type ProfileMetric = {
  label: string;
  durationMs: number;
};

type ChartProfile = {
  createChartCalls: number;
  chartSetupMs: number | null;
  markerCount: number;
  seriesDataCalls: Array<{ kind: string; points: number }>;
};

type ProfilerSample = {
  id: string;
  phase: 'mount' | 'update' | 'nested-update';
  actualDuration: number;
  baseDuration: number;
  startTime: number;
  commitTime: number;
};

let mockedEquityCurve: Array<{ timestamp: string; equity: number; drawdownPct: number }> = [];
let mockedTradeSeries: Array<{
  symbol: string;
  side: 'LONG' | 'SHORT';
  entryTime: string;
  exitTime: string;
  entryPrice: number;
  exitPrice: number;
  quantity: number;
  entryValue: number;
  exitValue: number;
  returnPct: number;
}> = [];
let mockedTelemetryResponse:
  | {
      requestedSymbol: string;
      resolvedSymbol: string;
      availableSymbols: string[];
      telemetry: {
        symbol: string;
        points: Array<{
          timestamp: string;
          open: number;
          high: number;
          low: number;
          close: number;
          volume: number;
          exposurePct: number;
          regime: 'WARMUP' | 'RANGE' | 'TREND_UP' | 'TREND_DOWN';
        }>;
        actions: Array<{
          timestamp: string;
          action: 'BUY' | 'SELL' | 'SHORT' | 'COVER';
          price: number;
          label: string;
        }>;
        indicators: Array<{
          key: string;
          label: string;
          pane: 'PRICE' | 'OSCILLATOR';
          points: Array<{ timestamp: string; value: number | null }>;
        }>;
      };
    }
  | undefined;

let chartProfile: ChartProfile;

const resetChartProfile = () => {
  chartProfile = {
    createChartCalls: 0,
    chartSetupMs: null,
    markerCount: 0,
    seriesDataCalls: [],
  };
};

const measure = <T,>(label: string, operation: () => T): ProfileMetric & { value: T } => {
  const startedAt = performance.now();
  const value = operation();
  return {
    label,
    durationMs: performance.now() - startedAt,
    value,
  };
};

const byteSize = (value: unknown) => new TextEncoder().encode(JSON.stringify(value)).length;

const formatMetricList = (metrics: ProfileMetric[]) =>
  metrics
    .sort((left, right) => right.durationMs - left.durationMs)
    .map((metric) => `- ${metric.label}: ${metric.durationMs.toFixed(2)} ms`)
    .join('\n');

const summarizeProfilerSamples = (samples: ProfilerSample[], phase: ProfilerSample['phase']) => {
  const matching = samples.filter((sample) => sample.phase === phase);
  if (matching.length === 0) {
    return null;
  }

  const actualDuration = matching.reduce((sum, sample) => sum + sample.actualDuration, 0);
  const baseDuration = Math.max(...matching.map((sample) => sample.baseDuration));

  return {
    count: matching.length,
    actualDuration,
    baseDuration,
    lastCommitTime: matching[matching.length - 1]?.commitTime ?? 0,
  };
};

const formatBudgetLine = (pass: boolean) => (pass ? 'PASS' : 'FAIL');

vi.mock('./backtestApi', () => ({
  useGetBacktestEquityCurveQuery: () => ({ data: mockedEquityCurve }),
  useGetBacktestTradeSeriesQuery: () => ({ data: mockedTradeSeries }),
  useGetBacktestTelemetryQuery: () => ({ data: mockedTelemetryResponse }),
}));

vi.mock('@/components/charts/EquityCurve', () => ({
  EquityCurve: () => <div>equity curve</div>,
}));

vi.mock('@/components/charts/DrawdownChart', () => ({
  DrawdownChart: () => <div>drawdown chart</div>,
}));

vi.mock('./MonthlyReturnsHeatmap', () => ({
  MonthlyReturnsHeatmap: () => <div>monthly returns</div>,
}));

vi.mock('./TradeDistributionHistogram', () => ({
  TradeDistributionHistogram: () => <div>trade distribution</div>,
}));

vi.mock('lightweight-charts', () => {
  const createMockSeries = (kind: string) => ({
    setData: (points: unknown[]) => {
      chartProfile.seriesDataCalls.push({ kind, points: points.length });
    },
  });

  return {
    CandlestickSeries: 'CandlestickSeries',
    HistogramSeries: 'HistogramSeries',
    LineSeries: 'LineSeries',
    ColorType: { Solid: 'Solid' },
    CrosshairMode: { Normal: 'Normal' },
    createChart: () => {
      chartProfile.createChartCalls += 1;
      const chartStartedAt = performance.now();
      const timeScaleApi = {
        fitContent: () => {
          if (chartProfile.chartSetupMs === null) {
            chartProfile.chartSetupMs = performance.now() - chartStartedAt;
          }
        },
        setVisibleLogicalRange: () => undefined,
      };

      return {
        addSeries: (kind: string) => createMockSeries(kind),
        applyOptions: () => undefined,
        panes: () => [
          { setHeight: () => undefined },
          { setHeight: () => undefined },
          { setHeight: () => undefined },
        ],
        remove: () => undefined,
        setCrosshairPosition: () => undefined,
        subscribeClick: () => undefined,
        timeScale: () => timeScaleApi,
      };
    },
    createSeriesMarkers: (_series: unknown, markers: Array<{ id: string }>) => {
      chartProfile.markerCount = markers.length;
      return {
        detach: () => undefined,
      };
    },
  };
});

const buildDetails = () => {
  const tradeSeries = Array.from({ length: 320 }, (_, index) => ({
    symbol: index % 2 === 0 ? 'BTC/USDT' : 'ETH/USDT',
    side: index % 3 === 0 ? ('SHORT' as const) : ('LONG' as const),
    entryTime: new Date(Date.UTC(2025, 0, 1, index * 6)).toISOString().slice(0, 19),
    exitTime: new Date(Date.UTC(2025, 0, 1, index * 6 + 4)).toISOString().slice(0, 19),
    entryPrice: 100 + index,
    exitPrice: 101 + index,
    quantity: 1.25,
    entryValue: 125 + index,
    exitValue: 126 + index,
    returnPct: Number(((index % 9) - 4) * 0.75),
  }));

  const telemetryPoints = Array.from({ length: 2400 }, (_, index) => ({
    timestamp: new Date(Date.UTC(2025, 0, 1, index)).toISOString().slice(0, 19),
    open: 100 + index,
    high: 101 + index,
    low: 99 + index,
    close: 100 + index,
    volume: 1000 + index,
    exposurePct: index % 2 === 0 ? 95 : 0,
    regime:
      index < 200 ? ('WARMUP' as const) : index % 3 === 0 ? ('TREND_UP' as const) : ('RANGE' as const),
  }));

  return {
    details: {
      id: 42,
      strategyId: 'TREND_FIRST_ADAPTIVE_ENSEMBLE',
      datasetId: 7,
      datasetName: 'Profiling Dataset',
      experimentName: 'Backtest Performance Profile',
      experimentKey: 'backtest-performance-profile',
      datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      datasetSchemaVersion: 'ohlcv-v1',
      datasetUploadedAt: '2026-03-25T10:00:00',
      datasetArchived: false,
      symbol: 'BTC/USDT',
      timeframe: '1h',
      executionStatus: 'COMPLETED' as const,
      validationStatus: 'PASSED' as const,
      feesBps: 10,
      slippageBps: 3,
      timestamp: '2026-03-25T10:05:00',
      initialBalance: 1000,
      finalBalance: 1240,
      sharpeRatio: 1.42,
      profitFactor: 1.55,
      winRate: 54,
      maxDrawdown: 12,
      totalTrades: tradeSeries.length,
      startDate: '2025-01-01T00:00:00',
      endDate: '2025-04-11T00:00:00',
      executionStage: 'COMPLETED' as const,
      progressPercent: 100,
      processedCandles: telemetryPoints.length * 2,
      totalCandles: telemetryPoints.length * 2,
      currentDataTimestamp: '2025-04-11T00:00:00',
      statusMessage: 'Backtest completed. Metrics and trade series are ready to review.',
      lastProgressAt: '2026-03-25T10:05:00',
      startedAt: '2026-03-25T10:00:00',
      completedAt: '2026-03-25T10:05:00',
      errorMessage: null,
      availableTelemetrySymbols: ['BTC/USDT', 'ETH/USDT'],
    },
    telemetryPoints,
    tradeSeries,
  };
};

describe('BacktestPerformanceProfile', () => {
  it(
    'writes a route profile report and enforces repeatable route budgets',
    { timeout: 60000 },
    async () => {
    resetChartProfile();

    const { details, telemetryPoints, tradeSeries } = buildDetails();
    mockedEquityCurve = Array.from({ length: 2400 }, (_, index) => ({
      timestamp: new Date(Date.UTC(2025, 0, 1, index)).toISOString().slice(0, 19),
      equity: 1000 + index * 2,
      drawdownPct: Number((index % 25) * 0.1),
    }));
    mockedTradeSeries = tradeSeries;
    mockedTelemetryResponse = {
      requestedSymbol: 'BTC/USDT',
      resolvedSymbol: 'BTC/USDT',
      availableSymbols: ['BTC/USDT', 'ETH/USDT'],
      telemetry: {
        symbol: 'BTC/USDT',
        points: telemetryPoints,
        actions: mockedTradeSeries
          .filter((trade) => trade.symbol === 'BTC/USDT')
          .flatMap((trade) => [
            {
              timestamp: trade.entryTime,
              action: trade.side === 'SHORT' ? 'SHORT' : 'BUY',
              price: trade.entryPrice,
              label: trade.side === 'SHORT' ? 'Short entry' : 'Long entry',
            },
            {
              timestamp: trade.exitTime,
              action: trade.side === 'SHORT' ? 'COVER' : 'SELL',
              price: trade.exitPrice,
              label: trade.side === 'SHORT' ? 'Cover short' : 'Exit long',
            },
          ]),
        indicators: [
          {
            key: 'ema_50_0',
            label: 'EMA 50',
            pane: 'PRICE',
            points: telemetryPoints.map((point, index) => ({
              timestamp: point.timestamp,
              value: index < 49 ? null : 100 + index * 0.98,
            })),
          },
          {
            key: 'adx_14_0',
            label: 'ADX 14',
            pane: 'OSCILLATOR',
            points: telemetryPoints.map((point, index) => ({
              timestamp: point.timestamp,
              value: index < 14 ? null : 20 + (index % 20),
            })),
          },
        ],
      },
    };

    const payloads = [
      { label: 'Backtest detail summary payload', bytes: byteSize(details) },
      { label: 'Equity curve payload', bytes: byteSize(mockedEquityCurve) },
      { label: 'Trade series payload', bytes: byteSize(mockedTradeSeries) },
      { label: 'Telemetry payload', bytes: byteSize(mockedTelemetryResponse) },
    ];
    const totalPayloadBytes = payloads.reduce((sum, payload) => sum + payload.bytes, 0);

    const equityMetric = measure('Equity curve normalization', () =>
      createEquityCurve(details, mockedEquityCurve)
    );
    const drawdownMetric = measure('Drawdown derivation', () =>
      createDrawdownCurve(equityMetric.value)
    );
    const monthlyReturnsMetric = measure('Monthly returns bucketing', () =>
      createMonthlyReturns(equityMetric.value)
    );
    const tradeDistributionMetric = measure('Trade distribution bucketing', () =>
      createTradeDistribution(mockedTradeSeries)
    );
    const overlayLegendMetric = measure('Overlay legend derivation', () =>
      buildOverlayLegend(mockedTelemetryResponse.telemetry)
    );
    const workspaceTradesMetric = measure('Workspace trade derivation', () =>
      buildWorkspaceTrades(
        mockedTradeSeries,
        mockedTelemetryResponse.telemetry.symbol,
        mockedTelemetryResponse.telemetry.actions
      )
    );
    const workspaceMarkersMetric = measure('Workspace marker derivation', () =>
      buildWorkspaceMarkers(workspaceTradesMetric.value)
    );

    const transformMetrics = [
      equityMetric,
      drawdownMetric,
      monthlyReturnsMetric,
      tradeDistributionMetric,
      overlayLegendMetric,
      workspaceTradesMetric,
      workspaceMarkersMetric,
    ].map(({ label, durationMs }) => ({ label, durationMs }));

    const profilerSamples: ProfilerSample[] = [];
    const onRender: ProfilerOnRenderCallback = (
      id,
      phase,
      actualDuration,
      baseDuration,
      startTime,
      commitTime
    ) => {
      profilerSamples.push({
        id,
        phase,
        actualDuration,
        baseDuration,
        startTime,
        commitTime,
      });
    };

    const user = userEvent.setup();
    const warmupView = render(
      <MemoryRouter initialEntries={['/backtest?section=workspace&symbol=BTC%2FUSDT']}>
        <BacktestResults details={details} />
      </MemoryRouter>
    );
    await screen.findByText('Run #42 research workspace');
    warmupView.unmount();
    resetChartProfile();

    const renderStartedAt = performance.now();
    const view = render(
      <MemoryRouter initialEntries={['/backtest?section=workspace&symbol=BTC%2FUSDT']}>
        <Profiler id="BacktestResults" onRender={onRender}>
          <BacktestResults details={details} />
        </Profiler>
      </MemoryRouter>
    );
    expect(screen.getByText('Run #42 research workspace')).toBeInTheDocument();
    const firstMeaningfulPaintMs = performance.now() - renderStartedAt;

    const initialMountSummary = summarizeProfilerSamples(profilerSamples, 'mount');

    await waitFor(() => expect(chartProfile.createChartCalls).toBeGreaterThan(0));
    view.unmount();
    profilerSamples.length = 0;

    render(
      <MemoryRouter initialEntries={['/backtest?trade=BTC%2FUSDT-2025-01-01T00%3A00%3A00-0']}>
        <Profiler id="BacktestTradeReviewPanel" onRender={onRender}>
          <BacktestTradeReviewPanel details={details} selectedSymbol="BTC/USDT" />
        </Profiler>
      </MemoryRouter>
    );

    const largeQueryRenderStartedAt = performance.now();
    const tradeTable = await screen.findByRole('table', { name: /Trade review table/i }, { timeout: 20000 });
    const largeQueryRenderMs = performance.now() - largeQueryRenderStartedAt;
    const dataRows = Array.from(
      tradeTable.querySelectorAll<HTMLElement>('[id^="backtest-trade-row-"]')
    );
    const interactionTargetRow = dataRows[Math.min(48, dataRows.length - 1)];
    expect(interactionTargetRow).toBeDefined();

    const tradeInteractionStartedAt = performance.now();
    await user.click(interactionTargetRow);
    await waitFor(() => expect(interactionTargetRow).toHaveAttribute('aria-selected', 'true'));
    const tradeInteractionMs = performance.now() - tradeInteractionStartedAt;

    const updateSummary =
      summarizeProfilerSamples(profilerSamples, 'update') ??
      summarizeProfilerSamples(profilerSamples, 'nested-update');

    const dominantPayload = [...payloads].sort((left, right) => right.bytes - left.bytes)[0];
    const dominantTransform = [...transformMetrics].sort(
      (left, right) => right.durationMs - left.durationMs
    )[0];
    const budgetResults = evaluateBacktestPerformanceBudgets([
      { key: 'routeLoadMs', durationMs: firstMeaningfulPaintMs },
      { key: 'chartMountMs', durationMs: chartProfile.chartSetupMs ?? Number.POSITIVE_INFINITY },
      { key: 'largeQueryRenderMs', durationMs: largeQueryRenderMs },
    ]);

    const report = `# Backtest Page Performance Profile

Generated by \`npm run profile:backtest\` for tasks \`2B.1\` and \`2B.5\`.

## Scenario

- Backtest ID: \`${details.id}\`
- Strategy: \`${details.strategyId}\`
- Timeframe: \`${details.timeframe}\`
- Equity points: ${mockedEquityCurve.length}
- Trades: ${mockedTradeSeries.length}
- Telemetry symbols: ${details.availableTelemetrySymbols.length}
- Active telemetry points: ${mockedTelemetryResponse?.telemetry.points.length ?? 0}
- Active telemetry actions: ${mockedTelemetryResponse?.telemetry.actions.length ?? 0}
- Active overlays: ${mockedTelemetryResponse?.telemetry.indicators.length ?? 0}

## User-flow timings

- First meaningful paint proxy: ${firstMeaningfulPaintMs.toFixed(2)} ms
- React mount commit duration: ${initialMountSummary?.actualDuration.toFixed(2) ?? 'n/a'} ms
- React mount base duration: ${initialMountSummary?.baseDuration.toFixed(2) ?? 'n/a'} ms
- Chart setup duration: ${chartProfile.chartSetupMs?.toFixed(2) ?? 'n/a'} ms
- Large-query trade panel render: ${largeQueryRenderMs.toFixed(2)} ms
- Trade-table row selection: ${tradeInteractionMs.toFixed(2)} ms
- React update duration after row selection: ${updateSummary?.actualDuration.toFixed(2) ?? 'n/a'} ms

## Performance budgets

${budgetResults
  .map(
    (budget) =>
      `- ${budget.label}: ${budget.durationMs.toFixed(2)} ms / ${budget.limitMs.toFixed(2)} ms (${formatBudgetLine(
        budget.pass
      )})`
  )
  .join('\n')}

## Payload analysis

${payloads
  .sort((left, right) => right.bytes - left.bytes)
  .map(
    (payload) =>
      `- ${payload.label}: ${(payload.bytes / 1024).toFixed(2)} KB (${(
        (payload.bytes / totalPayloadBytes) *
        100
      ).toFixed(1)}%)`
  )
  .join('\n')}

- Combined route payload: ${(totalPayloadBytes / 1024).toFixed(2)} KB
- Dominant payload: ${dominantPayload.label}

## Client-side transformation timings

${formatMetricList(transformMetrics)}

- Dominant transformation: ${dominantTransform.label}

## Chart setup details

- Chart create calls: ${chartProfile.createChartCalls}
- Workspace markers pushed to chart: ${chartProfile.markerCount}
- Series data calls:
${chartProfile.seriesDataCalls.map((call) => `  - ${call.kind}: ${call.points} points`).join('\n')}

## React profiler samples

- Mount commits captured: ${initialMountSummary?.count ?? 0}
- Update commits captured: ${updateSummary?.count ?? 0}
- Last update commit timestamp: ${updateSummary?.lastCommitTime.toFixed(2) ?? 'n/a'} ms

## Diagnosis

- The largest transferred payload in the current split route is ${dominantPayload.label.toLowerCase()}, which means the review workspace is now dominated by the symbol-specific telemetry request rather than the slim run-summary request.
- The heaviest measured client-side derivation in this jsdom trace is ${dominantTransform.label.toLowerCase()}, so subsequent optimization work should focus on reducing repeated workspace transformation work before touching lighter summary widgets.
- Chart setup completed in ${chartProfile.chartSetupMs?.toFixed(2) ?? 'n/a'} ms with mocked \`lightweight-charts\`, which suggests the current hotspot is more likely data preparation or rerender churn than wrapper-side chart bootstrapping alone. Real browser canvas cost still needs to be interpreted separately from this repeatable harness.
- The representative large-query render completed in ${largeQueryRenderMs.toFixed(2)} ms, which gives us a repeatable guardrail for dense trade-review queries in CI alongside the route-load and chart-mount budgets.
- Trade-table interaction remained measurable at ${tradeInteractionMs.toFixed(2)} ms, which stays in the report as a secondary signal even though the enforced budget now focuses on the larger render path instead of row-selection micro-interactions.

## Notes

- This report uses React Profiler plus a mocked \`lightweight-charts\` adapter in jsdom, so it measures route orchestration, payload shaping, and chart setup glue rather than full browser canvas paint or GPU work.
- Budget definitions live in \`frontend/src/features/backtest/backtestPerformanceBudget.ts\` and are enforced by this profile test so regressions fail fast before the report is published.
- Use this together with \`AlgotradingBot/build/reports/backend-workflow-profile/report.md\` when comparing backend delivery cost versus frontend render and interaction cost.
`;

    const output = resolve(process.cwd(), 'build', 'reports', 'backtest-page-profile', 'report.md');
    mkdirSync(dirname(output), { recursive: true });
    writeFileSync(output, report, 'utf8');

    expect(firstMeaningfulPaintMs).toBeGreaterThanOrEqual(0);
    expect(chartProfile.createChartCalls).toBeGreaterThan(0);
    expect(chartProfile.markerCount).toBeGreaterThan(0);
    expect(budgetResults).toHaveLength(backtestPerformanceBudgets.length);
    budgetResults.forEach((budget) => {
      expect(
        budget.pass,
        `${budget.label} exceeded its budget of ${budget.limitMs} ms with ${budget.durationMs.toFixed(2)} ms`
      ).toBe(true);
    });
    }
  );
});
