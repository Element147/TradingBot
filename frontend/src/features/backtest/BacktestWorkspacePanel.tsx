import {
  Alert,
  Button,
  Grid,
  MenuItem,
  Select,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import {
  useGetBacktestTelemetryQuery,
  useGetBacktestTradeSeriesQuery,
  type BacktestDetails,
} from './backtestApi';
import { getPreferredTelemetrySymbol } from './backtestTelemetry';
import {
  actionVisuals,
  buildOverlayLegend,
  buildWorkspaceMarkers,
  buildWorkspaceTrades,
  condenseMarkersForChart,
  deriveWorkspaceFocusTime,
  findMarkerById,
  findTradeByMarkerId,
  getDefaultVisibleOverlayKeys,
  summarizeMarkerFilter,
  type BacktestMarkerFilter,
} from './backtestWorkspace';
import { BacktestWorkspaceChart } from './BacktestWorkspaceChart';

import { KeyValueGrid } from '@/components/ui/KeyValueGrid';
import { EmptyState, LegendList, StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import { StickyInspectorPanel } from '@/components/workspace/StickyInspectorPanel';
import { formatCurrency, formatDateTime, formatNumber, formatPercentage } from '@/utils/formatters';

interface BacktestWorkspacePanelProps {
  details: BacktestDetails;
}

const workspaceMarkerFilters: BacktestMarkerFilter[] = ['ALL', 'LONG', 'SHORT', 'EXITS', 'FORCED'];
const workspacePaneKeys = ['exposure', 'oscillator'] as const;

const compareTimestampAsc = (left: string, right: string) =>
  new Date(left).getTime() - new Date(right).getTime();

const parseWorkspaceMarkerFilter = (value: string | null): BacktestMarkerFilter =>
  workspaceMarkerFilters.find((item) => item === value) ?? 'ALL';

const parseParamList = (
  value: string | null,
  allowed: readonly string[],
  fallback: string[]
) => {
  if (value === null) {
    return fallback;
  }

  if (value === 'none') {
    return [];
  }

  const parsed = value
    .split(',')
    .map((item) => item.trim())
    .filter((item): item is string => Boolean(item) && allowed.includes(item));

  return parsed.length > 0 ? parsed : fallback;
};

export default function BacktestWorkspacePanel({ details }: BacktestWorkspacePanelProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTelemetrySymbol = searchParams.get('symbol');
  const preferredTelemetrySymbol = getPreferredTelemetrySymbol(
    details.symbol,
    details.availableTelemetrySymbols
  );
  const telemetrySymbol = requestedTelemetrySymbol ?? preferredTelemetrySymbol ?? undefined;

  const {
    data: tradeSeries = [],
    isLoading: tradesLoading,
    isFetching: tradesFetching,
  } = useGetBacktestTradeSeriesQuery(details.id);
  const {
    data: telemetryResponse,
    isLoading: telemetryLoading,
    isFetching: telemetryFetching,
  } = useGetBacktestTelemetryQuery({
    id: details.id,
    symbol: telemetrySymbol,
  });

  const activeTelemetry = telemetryResponse?.telemetry ?? null;
  const availableTelemetrySymbols = useMemo(
    () =>
      Array.from(
        new Set(
          telemetryResponse?.resolvedSymbol
            ? [...details.availableTelemetrySymbols, telemetryResponse.resolvedSymbol]
            : details.availableTelemetrySymbols
        )
      ),
    [details.availableTelemetrySymbols, telemetryResponse]
  );
  const overlayLegend = useMemo(
    () => (activeTelemetry ? buildOverlayLegend(activeTelemetry) : []),
    [activeTelemetry]
  );
  const workspaceTrades = useMemo(
    () =>
      activeTelemetry
        ? buildWorkspaceTrades(tradeSeries, activeTelemetry.symbol, activeTelemetry.actions)
        : [],
    [activeTelemetry, tradeSeries]
  );
  const workspaceMarkers = useMemo(
    () =>
      buildWorkspaceMarkers(workspaceTrades).sort((left, right) =>
        compareTimestampAsc(left.timestamp, right.timestamp)
      ),
    [workspaceTrades]
  );
  const selectedTradeId = searchParams.get('trade');
  const selectedMarkerId = searchParams.get('marker');
  const markerFilter = parseWorkspaceMarkerFilter(searchParams.get('filter'));
  const overlayKeys = useMemo(() => overlayLegend.map((item) => item.key), [overlayLegend]);
  const resolvedOverlayKeys = useMemo(
    () =>
      activeTelemetry
        ? parseParamList(
            searchParams.get('overlays'),
            overlayKeys,
            getDefaultVisibleOverlayKeys(activeTelemetry)
          )
        : [],
    [activeTelemetry, overlayKeys, searchParams]
  );
  const visiblePanes = useMemo(
    () => parseParamList(searchParams.get('panes'), workspacePaneKeys, [...workspacePaneKeys]),
    [searchParams]
  );
  const explicitMarker = useMemo(
    () => findMarkerById(workspaceMarkers, selectedMarkerId),
    [selectedMarkerId, workspaceMarkers]
  );
  const tradeFromExplicitMarker = useMemo(
    () => findTradeByMarkerId(workspaceTrades, selectedMarkerId),
    [selectedMarkerId, workspaceTrades]
  );
  const selectedTrade = useMemo(
    () =>
      workspaceTrades.find((trade) => trade.id === selectedTradeId) ??
      tradeFromExplicitMarker ??
      workspaceTrades[0] ??
      null,
    [selectedTradeId, tradeFromExplicitMarker, workspaceTrades]
  );
  const inspectorMarker = useMemo(
    () =>
      explicitMarker ??
      (selectedTrade ? findMarkerById(workspaceMarkers, selectedTrade.entryMarkerId) : null),
    [explicitMarker, selectedTrade, workspaceMarkers]
  );
  const visibleMarkers = useMemo(
    () =>
      workspaceMarkers.filter((marker) => {
        switch (markerFilter) {
          case 'LONG':
            return marker.side === 'LONG' && marker.category === 'ENTRY';
          case 'SHORT':
            return marker.side === 'SHORT' && marker.category === 'ENTRY';
          case 'EXITS':
            return marker.category === 'EXIT' || marker.category === 'FORCED';
          case 'FORCED':
            return marker.category === 'FORCED' || marker.isForced;
          default:
            return true;
        }
      }),
    [markerFilter, workspaceMarkers]
  );
  const visibleSelectedMarker = useMemo(
    () =>
      visibleMarkers.find((marker) => marker.id === inspectorMarker?.id) ??
      visibleMarkers.find((marker) => marker.tradeId === selectedTrade?.id) ??
      visibleMarkers[0] ??
      null,
    [inspectorMarker, selectedTrade, visibleMarkers]
  );
  const chartMarkers = useMemo(
    () => condenseMarkersForChart(visibleMarkers, visibleSelectedMarker?.id ?? null),
    [visibleMarkers, visibleSelectedMarker]
  );
  const condensedMarkerCount = Math.max(visibleMarkers.length - chartMarkers.length, 0);
  const activeMarkerIndex = visibleSelectedMarker
    ? visibleMarkers.findIndex((marker) => marker.id === visibleSelectedMarker.id)
    : -1;
  const focusTimestamp = useMemo(
    () => deriveWorkspaceFocusTime(inspectorMarker ?? visibleSelectedMarker, selectedTrade),
    [inspectorMarker, selectedTrade, visibleSelectedMarker]
  );

  if (tradesLoading || telemetryLoading) {
    return (
      <Alert severity="info">
        Loading the chart workspace. Telemetry and symbol-linked trade context are requested only when
        this section is opened.
      </Alert>
    );
  }

  if (tradesFetching || telemetryFetching) {
    return <Alert severity="info">Refreshing the active telemetry symbol and linked trade evidence.</Alert>;
  }

  if (!activeTelemetry) {
    return (
      <EmptyState
        title="Telemetry was not persisted for this run"
        description="Price-action review is unavailable for this symbol, so keep the overview or analytics tabs open for the durable evidence."
        tone="info"
      />
    );
  }

  const updateWorkspaceParams = (update: (params: URLSearchParams) => void) => {
    const nextParams = new URLSearchParams(searchParams);
    update(nextParams);
    setSearchParams(nextParams, { replace: true });
  };

  const stepMarker = (offset: number) => {
    if (visibleMarkers.length === 0) {
      return;
    }

    const nextIndex =
      activeMarkerIndex === -1
        ? 0
        : Math.min(Math.max(activeMarkerIndex + offset, 0), visibleMarkers.length - 1);
    const nextMarker = visibleMarkers[nextIndex];
    if (!nextMarker) {
      return;
    }

    updateWorkspaceParams((params) => {
      params.set('marker', nextMarker.id);
      params.set('trade', nextMarker.tradeId);
    });
  };

  return (
    <Stack spacing={2.5}>
      <Grid container spacing={2.5} alignItems="flex-start">
        <Grid size={{ xs: 12, xl: 8.25 }}>
          <SurfacePanel
            title="Chart workspace"
            description={`${summarizeMarkerFilter(markerFilter, visibleMarkers.length)} on ${activeTelemetry.symbol}. Trade review now lives on its own tab, but marker selection still stays shareable through the URL.`}
            actions={
              availableTelemetrySymbols.length > 1 ? (
                <Select
                  size="small"
                  value={activeTelemetry.symbol}
                  onChange={(event) =>
                    updateWorkspaceParams((params) => {
                      params.set('symbol', String(event.target.value));
                      params.delete('trade');
                      params.delete('marker');
                    })
                  }
                >
                  {availableTelemetrySymbols.map((symbol) => (
                    <MenuItem key={symbol} value={symbol}>
                      {symbol}
                    </MenuItem>
                  ))}
                </Select>
              ) : undefined
            }
            contentSx={{ gap: 1.5 }}
          >
            <LegendList
              items={[
                {
                  color: actionVisuals.BUY.color,
                  label: 'BUY',
                  detail: 'long entry',
                  shape: actionVisuals.BUY.legendShape,
                },
                {
                  color: actionVisuals.SELL.color,
                  label: 'SELL',
                  detail: 'long exit',
                  shape: actionVisuals.SELL.legendShape,
                },
                {
                  color: actionVisuals.SHORT.color,
                  label: 'SHORT',
                  detail: 'short entry',
                  shape: actionVisuals.SHORT.legendShape,
                },
                {
                  color: actionVisuals.COVER.color,
                  label: 'COVER',
                  detail: 'short exit',
                  shape: actionVisuals.COVER.legendShape,
                },
              ]}
            />

            <Stack spacing={1.25}>
              {condensedMarkerCount > 0 ? (
                <Alert severity="info">
                  Dense marker windows are condensed for chart rendering to keep the workspace responsive.
                  {` ${condensedMarkerCount} lower-priority markers remain available through the inspector and filter controls.`}
                </Alert>
              ) : null}

              <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1} alignItems={{ xs: 'stretch', lg: 'center' }}>
                <ToggleButtonGroup
                  size="small"
                  exclusive
                  value={markerFilter}
                  onChange={(_, value: BacktestMarkerFilter | null) => {
                    if (!value) {
                      return;
                    }

                    updateWorkspaceParams((params) => {
                      params.set('filter', value);
                    });
                  }}
                >
                  <ToggleButton value="ALL">All</ToggleButton>
                  <ToggleButton value="LONG">Long</ToggleButton>
                  <ToggleButton value="SHORT">Short</ToggleButton>
                  <ToggleButton value="EXITS">Exits</ToggleButton>
                  <ToggleButton value="FORCED">Forced</ToggleButton>
                </ToggleButtonGroup>

                {overlayLegend.length > 0 ? (
                  <ToggleButtonGroup
                    size="small"
                    value={resolvedOverlayKeys}
                    onChange={(_, value: string[]) =>
                      updateWorkspaceParams((params) => {
                        params.set('overlays', value.length > 0 ? value.join(',') : 'none');
                      })
                    }
                  >
                    {overlayLegend.map((overlay) => (
                      <ToggleButton key={overlay.key} value={overlay.key}>
                        {overlay.label}
                      </ToggleButton>
                    ))}
                  </ToggleButtonGroup>
                ) : null}

                <ToggleButtonGroup
                  size="small"
                  value={visiblePanes}
                  onChange={(_, value: string[]) =>
                    updateWorkspaceParams((params) => {
                      params.set('panes', value.length > 0 ? value.join(',') : 'none');
                    })
                  }
                >
                  <ToggleButton value="exposure">Exposure pane</ToggleButton>
                  <ToggleButton value="oscillator">Indicator pane</ToggleButton>
                </ToggleButtonGroup>
              </Stack>

              <BacktestWorkspaceChart
                series={activeTelemetry}
                markers={chartMarkers}
                selectedMarkerId={visibleSelectedMarker?.id ?? null}
                visibleOverlayKeys={resolvedOverlayKeys}
                showExposurePane={visiblePanes.includes('exposure')}
                showOscillatorPane={visiblePanes.includes('oscillator')}
                focusTimestamp={focusTimestamp}
                onMarkerSelect={(marker) => {
                  updateWorkspaceParams((params) => {
                    params.set('marker', marker.id);
                    params.set('trade', marker.tradeId);
                  });
                }}
              />
            </Stack>
          </SurfacePanel>
        </Grid>

        <Grid size={{ xs: 12, xl: 3.75 }}>
          <StickyInspectorPanel
            title="Inspector"
            description="Selection stays linked to chart markers now that trade review is a separate panel."
            actions={
              selectedTrade ? (
                <StatusPill
                  label={`${selectedTrade.side} trade`}
                  tone={selectedTrade.side === 'LONG' ? 'success' : 'error'}
                  variant="filled"
                />
              ) : undefined
            }
            mobileBehavior="drawer"
            mobileOpenLabel={selectedTrade ? 'Open trade detail' : 'Open inspector'}
          >
            {selectedTrade ? (
              <>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <StatusPill label={`Entry: ${selectedTrade.entryAction}`} tone="success" />
                  <StatusPill label={`Exit: ${selectedTrade.exitAction}`} tone="info" />
                  <StatusPill
                    label={
                      inspectorMarker?.category === 'FORCED'
                        ? 'Forced or flagged event'
                        : inspectorMarker?.category ?? 'Trade selection'
                    }
                    tone={inspectorMarker?.category === 'FORCED' ? 'warning' : 'default'}
                  />
                </Stack>
                <KeyValueGrid
                  items={[
                    { label: 'Symbol', value: selectedTrade.symbol },
                    { label: 'Entry time', value: formatDateTime(selectedTrade.entryTime) },
                    { label: 'Exit time', value: formatDateTime(selectedTrade.exitTime) },
                    { label: 'Duration', value: `${formatNumber(selectedTrade.durationMinutes)} min` },
                    { label: 'Entry price', value: formatCurrency(selectedTrade.entryPrice, 4) },
                    { label: 'Exit price', value: formatCurrency(selectedTrade.exitPrice, 4) },
                    { label: 'Quantity', value: formatNumber(selectedTrade.quantity, 6) },
                    {
                      label: 'PnL',
                      value: formatCurrency(selectedTrade.pnlValue, 2),
                      tone: selectedTrade.pnlValue >= 0 ? 'success' : 'error',
                    },
                    {
                      label: 'Return',
                      value: formatPercentage(selectedTrade.returnPct, 2),
                      tone: selectedTrade.returnPct >= 0 ? 'success' : 'error',
                    },
                    { label: 'Entry label', value: selectedTrade.entryLabel },
                    { label: 'Exit label', value: selectedTrade.exitLabel },
                  ]}
                />
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    onClick={() => stepMarker(-1)}
                    disabled={visibleMarkers.length === 0 || activeMarkerIndex <= 0}
                  >
                    Previous event
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={() => stepMarker(1)}
                    disabled={
                      visibleMarkers.length === 0 ||
                      activeMarkerIndex === -1 ||
                      activeMarkerIndex >= visibleMarkers.length - 1
                    }
                  >
                    Next event
                  </Button>
                </Stack>
              </>
            ) : (
              <EmptyState
                title="Select a trade or marker"
                description="Click a chart marker or open the trade tab to push a selection into the shared inspector."
                tone="info"
              />
            )}
          </StickyInspectorPanel>
        </Grid>
      </Grid>
    </Stack>
  );
}
