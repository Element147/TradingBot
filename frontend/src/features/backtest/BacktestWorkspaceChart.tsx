import { Box } from '@mui/material';
import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  HistogramSeries,
  LineSeries,
  createChart,
  createSeriesMarkers,
  type ISeriesApi,
  type SeriesMarker,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts';
import { useEffect, useMemo, useRef } from 'react';

import type {
  BacktestIndicatorSeries,
  BacktestSymbolTelemetry,
  BacktestTelemetryPoint,
} from './backtestTypes';
import {
  actionVisuals,
  buildOverlayColorLookup,
  type WorkspaceMarker,
} from './backtestWorkspace';

interface BacktestWorkspaceChartProps {
  series: BacktestSymbolTelemetry;
  markers: WorkspaceMarker[];
  selectedMarkerId: string | null;
  visibleOverlayKeys: string[];
  showExposurePane: boolean;
  showOscillatorPane: boolean;
  focusTimestamp: string | null;
  onMarkerSelect: (marker: WorkspaceMarker) => void;
}

const toChartTime = (timestamp: string): UTCTimestamp =>
  Math.floor(new Date(timestamp).getTime() / 1000) as UTCTimestamp;

const toChartTimeNumber = (timestamp: string): number | null => {
  const timestampMs = new Date(timestamp).getTime();
  if (!Number.isFinite(timestampMs)) {
    return null;
  }

  return Math.floor(timestampMs / 1000);
};

const compareTimestampAsc = (left: string, right: string) =>
  new Date(left).getTime() - new Date(right).getTime();

const normalizeTelemetryPoints = (points: BacktestTelemetryPoint[]) => {
  const lookup = new Map<number, BacktestTelemetryPoint>();

  points
    .slice()
    .sort((left, right) => compareTimestampAsc(left.timestamp, right.timestamp))
    .forEach((point) => {
      const chartTime = toChartTimeNumber(point.timestamp);
      if (chartTime === null) {
        return;
      }

      lookup.set(chartTime, point);
    });

  return Array.from(lookup.entries())
    .sort(([leftTime], [rightTime]) => leftTime - rightTime)
    .map(([, point]) => point);
};

const normalizeIndicatorPoints = (points: BacktestIndicatorSeries['points']) => {
  const lookup = new Map<number, (typeof points)[number]>();

  points
    .slice()
    .sort((left, right) => compareTimestampAsc(left.timestamp, right.timestamp))
    .forEach((point) => {
      const chartTime = toChartTimeNumber(point.timestamp);
      if (chartTime === null) {
        return;
      }

      lookup.set(chartTime, point);
    });

  return Array.from(lookup.entries())
    .sort(([leftTime], [rightTime]) => leftTime - rightTime)
    .map(([, point]) => point);
};

const markerShapeForAction = (action: WorkspaceMarker['action']) => {
  switch (action) {
    case 'BUY':
      return 'arrowUp' as const;
    case 'SHORT':
      return 'arrowDown' as const;
    case 'SELL':
      return 'square' as const;
    case 'COVER':
      return 'circle' as const;
    default:
      return 'circle' as const;
  }
};

const markerPositionForAction = (action: WorkspaceMarker['action']) => {
  switch (action) {
    case 'BUY':
      return 'belowBar' as const;
    case 'SHORT':
      return 'aboveBar' as const;
    case 'SELL':
      return 'aboveBar' as const;
    case 'COVER':
      return 'belowBar' as const;
    default:
      return 'aboveBar' as const;
  }
};

export function BacktestWorkspaceChart({
  series,
  markers,
  selectedMarkerId,
  visibleOverlayKeys,
  showExposurePane,
  showOscillatorPane,
  focusTimestamp,
  onMarkerSelect,
}: BacktestWorkspaceChartProps) {
  const normalizedPoints = useMemo(() => normalizeTelemetryPoints(series.points), [series.points]);
  const visibleIndicators = useMemo(
    () => series.indicators.filter((indicator) => visibleOverlayKeys.includes(indicator.key)),
    [series.indicators, visibleOverlayKeys]
  );
  const normalizedIndicators = useMemo(
    () =>
      visibleIndicators.map((indicator) => ({
        ...indicator,
        points: normalizeIndicatorPoints(indicator.points),
      })),
    [visibleIndicators]
  );
  const normalizedMarkers = useMemo(
    () => [...markers].sort((left, right) => compareTimestampAsc(left.timestamp, right.timestamp)),
    [markers]
  );
  const overlayColorLookup = useMemo(() => buildOverlayColorLookup(series), [series]);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<ReturnType<typeof createChart> | null>(null);
  const priceSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const logicalIndexLookup = useMemo(
    () =>
      new Map(
        normalizedPoints
          .map<[number, number] | null>((point, index) => {
            const chartTime = toChartTimeNumber(point.timestamp);
            return chartTime === null ? null : [chartTime, index];
          })
          .filter((entry): entry is [number, number] => entry !== null)
      ),
    [normalizedPoints]
  );
  const focusPointLookup = useMemo(
    () =>
      new Map(
        normalizedPoints
          .map<[number, BacktestTelemetryPoint] | null>((point) => {
            const chartTime = toChartTimeNumber(point.timestamp);
            return chartTime === null ? null : [chartTime, point];
          })
          .filter((entry): entry is [number, BacktestTelemetryPoint] => entry !== null)
      ),
    [normalizedPoints]
  );
  const filteredMarkers = useMemo(() => normalizedMarkers, [normalizedMarkers]);
  const visiblePriceIndicators = useMemo(
    () => normalizedIndicators.filter((indicator) => indicator.pane === 'PRICE'),
    [normalizedIndicators]
  );
  const oscillatorIndicators = useMemo(
    () => normalizedIndicators.filter((indicator) => indicator.pane === 'OSCILLATOR'),
    [normalizedIndicators]
  );

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const chart = createChart(container, {
      width: container.clientWidth || 980,
      height: showExposurePane || showOscillatorPane ? 620 : 480,
      layout: {
        background: {
          type: ColorType.Solid,
          color: 'transparent',
        },
        textColor: '#60707a',
        panes: {
          separatorColor: 'rgba(21, 34, 41, 0.10)',
          enableResize: true,
        },
      },
      grid: {
        vertLines: {
          color: 'rgba(96, 112, 122, 0.08)',
        },
        horzLines: {
          color: 'rgba(96, 112, 122, 0.08)',
        },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
      },
      rightPriceScale: {
        borderColor: 'rgba(96, 112, 122, 0.18)',
      },
      timeScale: {
        borderColor: 'rgba(96, 112, 122, 0.18)',
        timeVisible: true,
        secondsVisible: false,
      },
    });
    chartRef.current = chart;

    const priceSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#1f8a5a',
      downColor: '#c24a3e',
      wickUpColor: '#1f8a5a',
      wickDownColor: '#c24a3e',
      borderVisible: false,
      priceLineVisible: true,
    });
    priceSeriesRef.current = priceSeries;

    priceSeries.setData(
      normalizedPoints.map((point) => ({
        time: toChartTime(point.timestamp),
        open: point.open,
        high: point.high,
        low: point.low,
        close: point.close,
      }))
    );

    visiblePriceIndicators.forEach((indicator) => {
      const overlaySeries = chart.addSeries(LineSeries, {
        color: overlayColorLookup.get(indicator.key) ?? '#1f8a5a',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: true,
      });
      overlaySeries.setData(
        indicator.points
          .filter((point) => point.value !== null)
          .map((point) => ({
            time: toChartTime(point.timestamp),
            value: point.value as number,
          }))
      );
    });

    let paneIndex = 1;
    if (showExposurePane) {
      const exposureSeries = chart.addSeries(
        HistogramSeries,
        {
          priceLineVisible: false,
          lastValueVisible: false,
          priceFormat: {
            type: 'price',
            precision: 0,
            minMove: 1,
          },
        },
        paneIndex
      );
      exposureSeries.setData(
        normalizedPoints.map((point) => ({
          time: toChartTime(point.timestamp),
          value: point.exposurePct,
          color:
            point.exposurePct >= 0
              ? 'rgba(31, 138, 90, 0.72)'
              : 'rgba(194, 74, 62, 0.72)',
        }))
      );
      paneIndex += 1;
    }

    const shouldRenderOscillatorPane =
      showOscillatorPane && oscillatorIndicators.length > 0;

    if (shouldRenderOscillatorPane) {
      oscillatorIndicators.forEach((indicator) => {
        const oscillatorSeries = chart.addSeries(
          LineSeries,
          {
            color: overlayColorLookup.get(indicator.key) ?? '#1f8a5a',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
          },
          paneIndex
        );
        oscillatorSeries.setData(
          indicator.points
            .filter((point) => point.value !== null)
            .map((point) => ({
              time: toChartTime(point.timestamp),
              value: point.value as number,
            }))
        );
      });
    }

    const markerPlugin = createSeriesMarkers(
      priceSeries,
      filteredMarkers.map<SeriesMarker<Time>>((marker) => {
        const visual = actionVisuals[marker.action];
        const isSelected = marker.id === selectedMarkerId;

        return {
          id: marker.id,
          time: toChartTime(marker.timestamp),
          position: markerPositionForAction(marker.action),
          shape: markerShapeForAction(marker.action),
          color: isSelected ? '#111827' : visual.color,
          text: isSelected ? visual.label : undefined,
        };
      }),
      {
        autoScale: true,
        zOrder: 'aboveSeries',
      }
    );

    chart.subscribeClick((param) => {
      if (!param.hoveredObjectId) {
        return;
      }

      const hoveredMarker = filteredMarkers.find(
        (marker) => marker.id === String(param.hoveredObjectId)
      );

      if (hoveredMarker) {
        onMarkerSelect(hoveredMarker);
      }
    });

    chart.timeScale().fitContent();

    const panes = chart.panes();
    panes[0]?.setHeight(showExposurePane || shouldRenderOscillatorPane ? 380 : 440);
    if (showExposurePane) {
      panes[1]?.setHeight(115);
    }
    if (shouldRenderOscillatorPane) {
      panes[showExposurePane ? 2 : 1]?.setHeight(125);
    }

    const resize = () => {
      chart.applyOptions({
        width: container.clientWidth || 980,
      });
    };

    resize();
    const resizeObserver =
      typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver(() => resize())
        : null;
    resizeObserver?.observe(container);

    return () => {
      resizeObserver?.disconnect();
      markerPlugin.detach();
      chart.remove();
      chartRef.current = null;
      priceSeriesRef.current = null;
    };
  }, [
    filteredMarkers,
    normalizedPoints,
    oscillatorIndicators,
    overlayColorLookup,
    onMarkerSelect,
    selectedMarkerId,
    showExposurePane,
    showOscillatorPane,
    visiblePriceIndicators,
  ]);

  useEffect(() => {
    const chart = chartRef.current;
    const priceSeries = priceSeriesRef.current;
    if (!chart || !priceSeries || !focusTimestamp) {
      return;
    }

    const focusChartTime = toChartTimeNumber(focusTimestamp);
    if (focusChartTime === null) {
      return;
    }

    const logicalIndex = logicalIndexLookup.get(focusChartTime);
    if (logicalIndex !== undefined) {
      chart.timeScale().setVisibleLogicalRange({
        from: Math.max(logicalIndex - 45, 0),
        to: logicalIndex + 18,
      });
    }

    const focusPoint = focusPointLookup.get(focusChartTime);
    if (focusPoint) {
      chart.setCrosshairPosition(
        focusPoint.close,
        focusChartTime as UTCTimestamp,
        priceSeries
      );
    }
  }, [focusPointLookup, focusTimestamp, logicalIndexLookup]);

  return (
    <Box
      ref={containerRef}
      sx={{
        width: '100%',
        height: showExposurePane || showOscillatorPane ? 620 : 480,
      }}
    />
  );
}
