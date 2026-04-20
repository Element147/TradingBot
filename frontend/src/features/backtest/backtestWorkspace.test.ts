import { describe, expect, it } from 'vitest';

import { condenseMarkersForChart, type WorkspaceMarker } from './backtestWorkspace';

const buildMarker = (index: number, overrides: Partial<WorkspaceMarker> = {}): WorkspaceMarker => ({
  id: `marker-${index}`,
  tradeId: `trade-${index}`,
  timestamp: `2025-01-${String((index % 28) + 1).padStart(2, '0')}T00:00:00`,
  action: index % 2 === 0 ? 'BUY' : 'SELL',
  price: 100 + index,
  label: `Marker ${index}`,
  side: index % 2 === 0 ? 'LONG' : 'SHORT',
  category: index % 2 === 0 ? 'ENTRY' : 'EXIT',
  isForced: false,
  ...overrides,
});

describe('condenseMarkersForChart', () => {
  it('returns the original marker set when it is already under the limit', () => {
    const markers = Array.from({ length: 6 }, (_, index) => buildMarker(index));

    expect(condenseMarkersForChart(markers, null, 10)).toEqual(markers);
  });

  it('keeps selected and forced markers while reducing dense marker windows', () => {
    const markers = Array.from({ length: 1200 }, (_, index) =>
      buildMarker(index, {
        isForced: index === 175 || index === 910,
        category: index === 175 || index === 910 ? 'FORCED' : index % 2 === 0 ? 'ENTRY' : 'EXIT',
      })
    );

    const condensed = condenseMarkersForChart(markers, 'marker-640', 180);

    expect(condensed).toHaveLength(180);
    expect(condensed[0]?.id).toBe('marker-0');
    expect(condensed.at(-1)?.id).toBe('marker-1199');
    expect(condensed.some((marker) => marker.id === 'marker-640')).toBe(true);
    expect(condensed.some((marker) => marker.id === 'marker-175')).toBe(true);
    expect(condensed.some((marker) => marker.id === 'marker-910')).toBe(true);
  });
});
