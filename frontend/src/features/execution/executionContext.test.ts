import { describe, expect, it } from 'vitest';

import {
  executionContextMeta,
  resolveExecutionEnvironment,
  resolveRouteExecutionContext,
} from './executionContext';

describe('executionContext', () => {
  it('maps execution contexts to backend environments', () => {
    expect(resolveExecutionEnvironment('research')).toBe('test');
    expect(resolveExecutionEnvironment('forward-test')).toBe('test');
    expect(resolveExecutionEnvironment('paper')).toBe('test');
    expect(resolveExecutionEnvironment('live')).toBe('live');
  });

  it('pins execution routes to explicit contexts', () => {
    expect(resolveRouteExecutionContext('/backtest')).toEqual(executionContextMeta.research);
    expect(resolveRouteExecutionContext('/market-data/jobs')).toEqual(executionContextMeta.research);
    expect(resolveRouteExecutionContext('/forward-testing')).toEqual(
      executionContextMeta['forward-test']
    );
    expect(resolveRouteExecutionContext('/paper')).toEqual(executionContextMeta.paper);
    expect(resolveRouteExecutionContext('/strategies')).toEqual(executionContextMeta.paper);
    expect(resolveRouteExecutionContext('/trades/export')).toEqual(executionContextMeta.paper);
    expect(resolveRouteExecutionContext('/live')).toEqual(executionContextMeta.live);
    expect(resolveRouteExecutionContext('/settings')).toBeNull();
    expect(resolveRouteExecutionContext('/dashboard')).toBeNull();
  });
});
