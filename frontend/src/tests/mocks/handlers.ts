import { http, HttpResponse } from 'msw';

const API_BASE_URL = 'http://localhost:8080';

/**
 * MSW request handlers for mocking backend API responses
 *
 * These handlers are used in integration tests to simulate
 * backend responses without making actual network requests.
 */
export const handlers = [
  // Login endpoint - success
  http.post(`${API_BASE_URL}/api/auth/login`, async ({ request }) => {
    const body = (await request.json()) as {
      username: string;
      password: string;
      rememberMe?: boolean;
    };

    if (body.username === 'testuser' && body.password === 'password123') {
      return HttpResponse.json({
        token: 'mock-jwt-token-12345',
        refreshToken: body.rememberMe ? 'mock-refresh-token-67890' : undefined,
        user: {
          id: 'user-123',
          username: 'testuser',
          role: 'trader',
        },
        expiresIn: 3600,
      });
    }

    return HttpResponse.json(
      {
        message: 'Invalid username or password',
        error: 'Unauthorized',
      },
      { status: 401 }
    );
  }),

  // Logout endpoint
  http.post(`${API_BASE_URL}/api/auth/logout`, () =>
    HttpResponse.json({ message: 'Logged out successfully' })
  ),

  // Token refresh endpoint - success
  http.post(`${API_BASE_URL}/api/auth/refresh`, async ({ request }) => {
    const body = (await request.json()) as { refreshToken?: string };

    if (body.refreshToken === 'mock-refresh-token-67890') {
      return HttpResponse.json({
        token: 'mock-jwt-token-refreshed',
        expiresIn: 3600,
      });
    }

    return HttpResponse.json(
      {
        message: 'Invalid refresh token',
        error: 'Unauthorized',
      },
      { status: 401 }
    );
  }),

  // Get current user endpoint
  http.get(`${API_BASE_URL}/api/auth/me`, ({ request }) => {
    const authHeader = request.headers.get('Authorization');

    if (
      authHeader === 'Bearer mock-jwt-token-12345' ||
      authHeader === 'Bearer mock-jwt-token-refreshed'
    ) {
      return HttpResponse.json({
        id: 'user-123',
        username: 'testuser',
        role: 'trader',
      });
    }

    return HttpResponse.json(
      {
        message: 'Unauthorized',
        error: 'Unauthorized',
      },
      { status: 401 }
    );
  }),

  // Protected endpoint that returns 401 to test token refresh
  http.get(`${API_BASE_URL}/api/protected`, ({ request }) => {
    const authHeader = request.headers.get('Authorization');

    if (authHeader === 'Bearer mock-jwt-token-expired') {
      return HttpResponse.json(
        {
          message: 'Token expired',
          error: 'Unauthorized',
        },
        { status: 401 }
      );
    }

    if (
      authHeader === 'Bearer mock-jwt-token-12345' ||
      authHeader === 'Bearer mock-jwt-token-refreshed'
    ) {
      return HttpResponse.json({
        message: 'Access granted',
        data: 'Protected data',
      });
    }

    return HttpResponse.json(
      {
        message: 'Unauthorized',
        error: 'Unauthorized',
      },
      { status: 401 }
    );
  }),

  // Account balance endpoint
  http.get(`${API_BASE_URL}/api/account/balance`, () =>
    HttpResponse.json({
      total: '10000.00',
      available: '8500.00',
      locked: '1500.00',
      assets: [
        {
          symbol: 'USD',
          amount: '8500.00',
          valueUSD: '8500.00',
        },
        {
          symbol: 'BTC',
          amount: '0.025',
          valueUSD: '1500.00',
        },
      ],
      lastSync: '2026-03-09T12:00:00Z',
    })
  ),

  http.get(`${API_BASE_URL}/api/exchange/connections`, () =>
    HttpResponse.json({
      activeConnectionId: 'binance-paper',
      connections: [
        {
          id: 'binance-paper',
          name: 'Binance Paper',
          exchange: 'binance',
          apiKey: '',
          apiSecret: '',
          testnet: true,
          active: true,
          updatedAt: '2026-03-12T12:00:00',
        },
      ],
    })
  ),
  http.post(`${API_BASE_URL}/api/exchange/connections`, async ({ request }) =>
    {
      const body = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json({
        id: 'saved-connection',
        active: false,
        updatedAt: '2026-03-12T12:00:00',
        ...body,
      });
    }
  ),
  http.put(`${API_BASE_URL}/api/exchange/connections/:connectionId`, async ({ request, params }) =>
    {
      const body = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json({
        id: String(params.connectionId),
        active: false,
        updatedAt: '2026-03-12T12:00:00',
        ...body,
      });
    }
  ),
  http.post(`${API_BASE_URL}/api/exchange/connections/:connectionId/activate`, ({ params }) =>
    HttpResponse.json({
      id: String(params.connectionId),
      name: 'Activated Connection',
      exchange: 'binance',
      apiKey: '',
      apiSecret: '',
      testnet: true,
      active: true,
      updatedAt: '2026-03-12T12:00:00',
    })
  ),
  http.delete(`${API_BASE_URL}/api/exchange/connections/:connectionId`, () =>
    new HttpResponse(null, { status: 204 })
  ),

  // Account performance endpoint
  http.get(`${API_BASE_URL}/api/account/performance`, ({ request }) => {
    const url = new URL(request.url);
    const timeframe = url.searchParams.get('timeframe') ?? 'today';

    return HttpResponse.json({
      totalProfitLoss: timeframe === 'all' ? '1250.00' : '125.00',
      profitLossPercentage: timeframe === 'all' ? '12.50' : '1.25',
      winRate: '58.3',
      tradeCount: timeframe === 'all' ? 48 : 6,
      cashRatio: '85.0',
    });
  }),
  http.get(`${API_BASE_URL}/api/positions/open`, () =>
    HttpResponse.json([
      {
        id: '1',
        strategyId: 'strat-1',
        strategyName: 'Bollinger Bands',
        symbol: 'BTC/USDT',
        side: 'LONG',
        entryPrice: '50000.00',
        currentPrice: '51000.00',
        quantity: '0.01',
        entryTime: '2026-03-09T10:00:00Z',
        unrealizedPnL: '10.00',
        unrealizedPnLPercentage: '2.00',
        status: 'OPEN',
      },
      {
        id: '2',
        strategyId: 'strat-1',
        strategyName: 'Bollinger Bands',
        symbol: 'ETH/USDT',
        side: 'SHORT',
        entryPrice: '3000.00',
        currentPrice: '3050.00',
        quantity: '0.1',
        entryTime: '2026-03-09T11:00:00Z',
        unrealizedPnL: '-5.00',
        unrealizedPnLPercentage: '-1.67',
        status: 'OPEN',
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/trades/recent`, ({ request }) => {
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get('limit') ?? '10');

    return HttpResponse.json(
      [
        {
          id: '1',
          strategyId: 'strat-1',
          strategyName: 'Bollinger Bands',
          symbol: 'BTC/USDT',
          side: 'LONG',
          entryPrice: '50000.00',
          exitPrice: '51000.00',
          quantity: '0.01',
          entryTime: '2026-03-09T10:00:00Z',
          exitTime: '2026-03-09T12:00:00Z',
          duration: '2h',
          profitLoss: '10.00',
          profitLossPercentage: '2.00',
          fees: '0.50',
          slippage: '0.15',
          status: 'CLOSED',
        },
        {
          id: '2',
          strategyId: 'strat-1',
          strategyName: 'Bollinger Bands',
          symbol: 'ETH/USDT',
          side: 'SHORT',
          entryPrice: '3000.00',
          exitPrice: '2950.00',
          quantity: '0.1',
          entryTime: '2026-03-09T11:00:00Z',
          exitTime: '2026-03-09T13:00:00Z',
          duration: '2h',
          profitLoss: '-5.00',
          profitLossPercentage: '-1.67',
          fees: '0.30',
          slippage: '0.09',
          status: 'CLOSED',
        },
      ].slice(0, limit)
    );
  }),

  // Strategy management endpoints
  http.get(`${API_BASE_URL}/api/strategies`, () =>
    HttpResponse.json([
      {
        id: 1,
        name: 'Bollinger BTC Mean Reversion',
        type: 'bollinger-bands',
        status: 'STOPPED',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 10,
        maxPositionSize: 100,
        profitLoss: 0,
        tradeCount: 0,
        currentDrawdown: 0,
        paperMode: true,
        shortSellingEnabled: true,
        configVersion: 1,
        lastConfigChangedAt: '2026-03-10T10:00:00',
      },
    ])
  ),
  http.post(`${API_BASE_URL}/api/strategies/:strategyId/start`, ({ params }) =>
    HttpResponse.json({
      strategyId: Number(params.strategyId),
      status: 'RUNNING',
      message: 'Strategy started in paper mode',
    })
  ),
  http.post(`${API_BASE_URL}/api/strategies/:strategyId/stop`, ({ params }) =>
    HttpResponse.json({
      strategyId: Number(params.strategyId),
      status: 'STOPPED',
      message: 'Strategy stopped',
    })
  ),
  http.put(`${API_BASE_URL}/api/strategies/:strategyId/config`, async ({ request, params }) => {
    const body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json({
      id: Number(params.strategyId),
      name: 'Bollinger BTC Mean Reversion',
      type: 'bollinger-bands',
      status: 'STOPPED',
      ...body,
      profitLoss: 0,
      tradeCount: 0,
      currentDrawdown: 0,
      paperMode: true,
      shortSellingEnabled: true,
      configVersion: 2,
      lastConfigChangedAt: '2026-03-10T10:05:00',
    });
  }),
  http.get(`${API_BASE_URL}/api/strategies/:strategyId/config-history`, ({ params }) =>
    HttpResponse.json([
      {
        id: 20,
        versionNumber: 2,
        changeReason: 'Updated symbol, timeframe',
        symbol: 'ETH/USDT',
        timeframe: '4h',
        riskPerTrade: 0.03,
        minPositionSize: 20,
        maxPositionSize: 120,
        status: 'STOPPED',
        paperMode: true,
        shortSellingEnabled: true,
        changedAt: '2026-03-10T10:05:00',
      },
      {
        id: 10,
        versionNumber: 1,
        changeReason: `Seeded configuration for strategy ${String(params.strategyId ?? 'unknown')}`,
        symbol: 'BTC/USDT',
        timeframe: '1h',
        riskPerTrade: 0.02,
        minPositionSize: 10,
        maxPositionSize: 100,
        status: 'STOPPED',
        paperMode: true,
        shortSellingEnabled: true,
        changedAt: '2026-03-10T10:00:00',
      },
    ])
  ),

  // Backtest endpoints
  http.get(`${API_BASE_URL}/api/backtests/algorithms`, () =>
    HttpResponse.json([
      {
        id: 'BOLLINGER_BANDS',
        label: 'Bollinger Bands',
        description: 'Mean-reversion bands strategy',
        selectionMode: 'SINGLE_SYMBOL',
      },
      {
        id: 'SMA_CROSSOVER',
        label: 'SMA Crossover',
        description: 'Fast/slow moving average crossover',
        selectionMode: 'SINGLE_SYMBOL',
      },
      {
        id: 'BUY_AND_HOLD',
        label: 'Buy and Hold',
        description: 'Baseline hold from first to last candle',
        selectionMode: 'SINGLE_SYMBOL',
      },
      {
        id: 'ICHIMOKU_TREND',
        label: 'Ichimoku Trend',
        description: 'Long/cash Ichimoku cloud trend filter',
        selectionMode: 'SINGLE_SYMBOL',
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/backtests/datasets`, () =>
    HttpResponse.json([
      {
        id: 7,
        name: 'BTC 1h 2025',
        originalFilename: 'btc_2025.csv',
        rowCount: 1000,
        symbolsCsv: 'BTC/USDT,ETH/USDT',
        dataStart: '2025-01-01T00:00:00',
        dataEnd: '2025-12-31T23:00:00',
        uploadedAt: '2026-03-10T10:00:00',
        checksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        schemaVersion: 'ohlcv-v1',
        archived: false,
        archivedAt: null,
        archiveReason: null,
        usageCount: 1,
        lastUsedAt: '2026-03-10T10:00:00',
        usedByBacktests: true,
        duplicateCount: 1,
        retentionStatus: 'ACTIVE',
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/backtests/datasets/retention-report`, () =>
    HttpResponse.json({
      totalDatasets: 1,
      activeDatasets: 1,
      archivedDatasets: 0,
      archiveCandidateDatasets: 0,
      duplicateDatasetCount: 0,
      referencedDatasetCount: 1,
      oldestActiveUploadedAt: '2026-03-10T10:00:00',
      newestUploadedAt: '2026-03-10T10:00:00',
    })
  ),
  http.post(`${API_BASE_URL}/api/backtests/datasets/upload`, () =>
    HttpResponse.json({
      id: 8,
      name: 'Uploaded dataset',
      originalFilename: 'upload.csv',
      rowCount: 240,
      symbolsCsv: 'BTC/USDT',
      dataStart: '2025-01-01T00:00:00',
      dataEnd: '2025-01-10T23:00:00',
      uploadedAt: '2026-03-10T10:01:00',
      checksumSha256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
      schemaVersion: 'ohlcv-v1',
      archived: false,
      archivedAt: null,
      archiveReason: null,
      usageCount: 0,
      lastUsedAt: null,
      usedByBacktests: false,
      duplicateCount: 1,
      retentionStatus: 'ACTIVE',
    })
  ),
  http.post(`${API_BASE_URL}/api/backtests/datasets/:datasetId/archive`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.datasetId),
      name: 'BTC 1h 2025',
      originalFilename: 'btc_2025.csv',
      rowCount: 1000,
      symbolsCsv: 'BTC/USDT,ETH/USDT',
      dataStart: '2025-01-01T00:00:00',
      dataEnd: '2025-12-31T23:00:00',
      uploadedAt: '2026-03-10T10:00:00',
      checksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      schemaVersion: 'ohlcv-v1',
      archived: true,
      archivedAt: '2026-03-12T10:00:00',
      archiveReason: 'Archived from active inventory after lifecycle review.',
      usageCount: 1,
      lastUsedAt: '2026-03-10T10:00:00',
      usedByBacktests: true,
      duplicateCount: 1,
      retentionStatus: 'ARCHIVED',
    })
  ),
  http.post(`${API_BASE_URL}/api/backtests/datasets/:datasetId/restore`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.datasetId),
      name: 'BTC 1h 2025',
      originalFilename: 'btc_2025.csv',
      rowCount: 1000,
      symbolsCsv: 'BTC/USDT,ETH/USDT',
      dataStart: '2025-01-01T00:00:00',
      dataEnd: '2025-12-31T23:00:00',
      uploadedAt: '2026-03-10T10:00:00',
      checksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      schemaVersion: 'ohlcv-v1',
      archived: false,
      archivedAt: null,
      archiveReason: null,
      usageCount: 1,
      lastUsedAt: '2026-03-10T10:00:00',
      usedByBacktests: true,
      duplicateCount: 1,
      retentionStatus: 'ACTIVE',
    })
  ),
  http.get(`${API_BASE_URL}/api/backtests`, () =>
    HttpResponse.json([
      {
        id: 42,
        strategyId: 'BOLLINGER_BANDS',
        datasetName: 'BTC 1h 2025',
        experimentName: 'BTC Mean Reversion Retest',
        symbol: 'BTC/USDT',
        timeframe: '1h',
        executionStatus: 'COMPLETED',
        validationStatus: 'PASSED',
        feesBps: 10,
        slippageBps: 3,
        timestamp: '2026-03-10T10:00:00',
        initialBalance: 1000,
        finalBalance: 1080,
        executionStage: 'COMPLETED',
        progressPercent: 100,
        processedCandles: 8760,
        totalCandles: 8760,
        currentDataTimestamp: '2025-12-31T00:00:00',
        statusMessage: 'Backtest completed. Metrics and trade series are ready to review.',
        lastProgressAt: '2026-03-10T10:00:00',
        startedAt: '2026-03-10T09:58:00',
        completedAt: '2026-03-10T10:00:00',
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/backtests/:id`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.id),
      strategyId: 'BOLLINGER_BANDS',
      datasetId: 7,
      datasetName: 'BTC 1h 2025',
      experimentName: 'BTC Mean Reversion Retest',
      experimentKey: 'btc-mean-reversion-retest',
      datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      datasetSchemaVersion: 'ohlcv-v1',
      datasetUploadedAt: '2026-03-10T10:00:00',
      datasetArchived: false,
      symbol: 'BTC/USDT',
      timeframe: '1h',
      executionStatus: 'COMPLETED',
      validationStatus: 'PASSED',
      feesBps: 10,
      slippageBps: 3,
      timestamp: '2026-03-10T10:00:00',
      initialBalance: 1000,
      finalBalance: 1080,
      sharpeRatio: 1.2,
      profitFactor: 1.6,
      winRate: 52.0,
      maxDrawdown: 18.0,
      totalTrades: 80,
      startDate: '2025-01-01T00:00:00',
      endDate: '2025-12-31T00:00:00',
      executionStage: 'COMPLETED',
      progressPercent: 100,
      processedCandles: 8760,
      totalCandles: 8760,
      currentDataTimestamp: '2025-12-31T00:00:00',
      statusMessage: 'Backtest completed. Metrics and trade series are ready to review.',
      lastProgressAt: '2026-03-10T10:00:00',
      startedAt: '2026-03-10T09:58:00',
      completedAt: '2026-03-10T10:00:00',
      errorMessage: null,
      availableTelemetrySymbols: ['BTC/USDT', 'ETH/USDT'],
    })
  ),
  http.get(`${API_BASE_URL}/api/backtests/:id/equity`, () =>
    HttpResponse.json([
      { timestamp: '2025-01-01T00:00:00', equity: 1000, drawdownPct: 0 },
      { timestamp: '2025-01-02T00:00:00', equity: 1080, drawdownPct: 0 },
    ])
  ),
  http.get(`${API_BASE_URL}/api/backtests/:id/trades`, () =>
    HttpResponse.json([
      {
        symbol: 'BTC/USDT',
        side: 'LONG',
        entryTime: '2025-01-01T00:00:00',
        exitTime: '2025-01-02T00:00:00',
        entryPrice: 100,
        exitPrice: 108,
        quantity: 9.5,
        entryValue: 950,
        exitValue: 1026,
        returnPct: 8,
      },
      {
        symbol: 'ETH/USDT',
        side: 'LONG',
        entryTime: '2025-01-03T00:00:00',
        exitTime: '2025-01-04T00:00:00',
        entryPrice: 200,
        exitPrice: 214,
        quantity: 4.5,
        entryValue: 900,
        exitValue: 963,
        returnPct: 7,
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/backtests/:id/telemetry`, ({ request }) => {
    const url = new URL(request.url);
    const symbol = url.searchParams.get('symbol') ?? 'BTC/USDT';
    return HttpResponse.json({
      requestedSymbol: url.searchParams.get('symbol'),
      resolvedSymbol: symbol,
      availableSymbols: ['BTC/USDT', 'ETH/USDT'],
      telemetry: {
        symbol,
        points: [
          {
            timestamp: '2025-01-01T00:00:00',
            open: 100,
            high: 101,
            low: 99,
            close: 100,
            volume: 1000,
            exposurePct: 0,
            regime: 'WARMUP',
          },
          {
            timestamp: '2025-01-02T00:00:00',
            open: 108,
            high: 109,
            low: 107,
            close: 108,
            volume: 1200,
            exposurePct: 95,
            regime: 'TREND_UP',
          },
        ],
        actions: [
          {
            timestamp: '2025-01-01T00:00:00',
            action: 'BUY',
            price: 100,
            label: 'Long entry',
          },
        ],
        indicators: [
          {
            key: 'bb_middle_20',
            label: 'Bollinger Mid (20)',
            pane: 'PRICE',
            points: [
              { timestamp: '2025-01-01T00:00:00', value: null },
              { timestamp: '2025-01-02T00:00:00', value: 104 },
            ],
          },
        ],
      },
    });
  }),
  http.get(`${API_BASE_URL}/api/backtests/:id/summary`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.id),
      strategyId: 'BOLLINGER_BANDS',
      datasetId: 7,
      datasetName: 'BTC 1h 2025',
      experimentName: 'BTC Mean Reversion Retest',
      experimentKey: 'btc-mean-reversion-retest',
      datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      datasetSchemaVersion: 'ohlcv-v1',
      datasetUploadedAt: '2026-03-10T10:00:00',
      datasetArchived: false,
      symbol: 'BTC/USDT',
      timeframe: '1h',
      executionStatus: 'RUNNING',
      validationStatus: 'PENDING',
      feesBps: 10,
      slippageBps: 3,
      timestamp: '2026-03-10T10:00:00',
      initialBalance: 1000,
      finalBalance: 1012,
      sharpeRatio: 0.4,
      profitFactor: 1.05,
      winRate: 50.0,
      maxDrawdown: 4.2,
      totalTrades: 2,
      startDate: '2025-01-01T00:00:00',
      endDate: '2025-12-31T00:00:00',
      executionStage: 'SIMULATING',
      progressPercent: 54,
      processedCandles: 4728,
      totalCandles: 8760,
      currentDataTimestamp: '2025-07-15T00:00:00',
      statusMessage: 'Historical candles are being replayed.',
      lastProgressAt: '2026-03-10T10:00:30',
      startedAt: '2026-03-10T09:58:00',
      completedAt: null,
      errorMessage: null,
    })
  ),
  http.delete(`${API_BASE_URL}/api/backtests/:id`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${API_BASE_URL}/api/backtests/compare`, ({ request }) => {
    const url = new URL(request.url);
    const ids = url.searchParams.getAll('ids').map((value) => Number(value));

    return HttpResponse.json({
      baselineBacktestId: ids[0] ?? 42,
      items: [
        {
          id: ids[0] ?? 42,
          strategyId: 'BOLLINGER_BANDS',
          datasetName: 'BTC 1h 2025',
          datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          datasetSchemaVersion: 'ohlcv-v1',
          datasetUploadedAt: '2026-03-10T10:00:00',
          datasetArchived: false,
          symbol: 'BTC/USDT',
          timeframe: '1h',
          executionStatus: 'COMPLETED',
          validationStatus: 'PASSED',
          feesBps: 10,
          slippageBps: 3,
          timestamp: '2026-03-10T10:00:00',
          initialBalance: 1000,
          finalBalance: 1080,
          totalReturnPercent: 8,
          sharpeRatio: 1.2,
          profitFactor: 1.6,
          winRate: 52,
          maxDrawdown: 18,
          totalTrades: 80,
          finalBalanceDelta: 0,
          totalReturnDeltaPercent: 0,
        },
        {
          id: ids[1] ?? 43,
          strategyId: 'SMA_CROSSOVER',
          datasetName: 'BTC 1h 2025',
          datasetChecksumSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          datasetSchemaVersion: 'ohlcv-v1',
          datasetUploadedAt: '2026-03-10T10:00:00',
          datasetArchived: false,
          symbol: 'BTC/USDT',
          timeframe: '1h',
          executionStatus: 'COMPLETED',
          validationStatus: 'FAILED',
          feesBps: 10,
          slippageBps: 3,
          timestamp: '2026-03-11T10:00:00',
          initialBalance: 1000,
          finalBalance: 950,
          totalReturnPercent: -5,
          sharpeRatio: 0.8,
          profitFactor: 1.1,
          winRate: 45,
          maxDrawdown: 22,
          totalTrades: 48,
          finalBalanceDelta: -130,
          totalReturnDeltaPercent: -13,
        },
      ],
    });
  }),
  http.post(`${API_BASE_URL}/api/backtests/run`, () =>
    HttpResponse.json({
      id: 43,
      status: 'PENDING',
      submittedAt: '2026-03-10T10:01:00',
    }, { status: 202 })
  ),

  http.get(`${API_BASE_URL}/api/market-data/providers`, () =>
    HttpResponse.json([
      {
        id: 'binance',
        label: 'Binance',
        description: 'Public spot klines for major crypto pairs with no API key required.',
        supportedAssetTypes: ['CRYPTO'],
        supportedTimeframes: ['1m', '5m', '15m', '30m', '1h', '4h', '1d'],
        apiKeyRequired: false,
        apiKeyEnvironmentVariable: null,
        apiKeyConfigured: true,
        apiKeyConfiguredSource: 'NOT_REQUIRED',
        supportsAdjusted: false,
        supportsRegularSessionOnly: false,
        symbolExamples: ['BTC/USDT', 'ETH/USDT', 'SOL/USDT'],
        docsUrl: 'https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints',
        signupUrl: 'https://www.binance.com/',
        accountNotes: 'No API key is needed for historical spot klines.',
      },
      {
        id: 'twelvedata',
        label: 'Twelve Data',
        description: 'Unified time series API for stocks and crypto with free-key access.',
        supportedAssetTypes: ['STOCK', 'CRYPTO'],
        supportedTimeframes: ['1m', '5m', '15m', '30m', '1h', '4h', '1d'],
        apiKeyRequired: true,
        apiKeyEnvironmentVariable: 'ALGOTRADING_MARKET_DATA_TWELVEDATA_API_KEY',
        apiKeyConfigured: false,
        apiKeyConfiguredSource: 'NONE',
        supportsAdjusted: false,
        supportsRegularSessionOnly: true,
        symbolExamples: ['AAPL', 'MSFT', 'BTC/USD'],
        docsUrl: 'https://twelvedata.com/docs#time-series',
        signupUrl: 'https://twelvedata.com/pricing',
        accountNotes: 'Free API key required.',
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/market-data/provider-credentials`, () =>
    HttpResponse.json([
      {
        providerId: 'twelvedata',
        providerLabel: 'Twelve Data',
        apiKeyEnvironmentVariable: 'ALGOTRADING_MARKET_DATA_TWELVEDATA_API_KEY',
        apiKeyRequired: true,
        hasStoredCredential: false,
        hasEnvironmentCredential: false,
        effectiveCredentialConfigured: false,
        credentialSource: 'NONE',
        storageEncryptionConfigured: true,
        note: null,
        updatedAt: null,
        docsUrl: 'https://twelvedata.com/docs#time-series',
        signupUrl: 'https://twelvedata.com/pricing',
        accountNotes: 'Free API key required.',
      },
      {
        providerId: 'finnhub',
        providerLabel: 'Finnhub',
        apiKeyEnvironmentVariable: 'ALGOTRADING_MARKET_DATA_FINNHUB_API_KEY',
        apiKeyRequired: true,
        hasStoredCredential: true,
        hasEnvironmentCredential: false,
        effectiveCredentialConfigured: true,
        credentialSource: 'DATABASE',
        storageEncryptionConfigured: true,
        note: 'Free-tier candle key',
        updatedAt: '2026-03-12T09:45:00',
        docsUrl: 'https://finnhub.io/docs/api/stock-candles',
        signupUrl: 'https://finnhub.io/register',
        accountNotes: 'Free API key required.',
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/market-data/jobs`, () =>
    HttpResponse.json([
      {
        id: 12,
        providerId: 'binance',
        providerLabel: 'Binance',
        assetType: 'CRYPTO',
        datasetName: 'BTC majors 1h',
        symbolsCsv: 'BTC/USDT,ETH/USDT',
        timeframe: '1h',
        startDate: '2024-03-12',
        endDate: '2026-03-12',
        adjusted: false,
        regularSessionOnly: false,
        status: 'WAITING_RETRY',
        statusMessage: 'Provider asked the downloader to wait.',
        nextRetryAt: '2026-03-12T10:00:00',
        currentSymbolIndex: 0,
        totalSymbols: 2,
        currentSymbol: 'BTC/USDT',
        importedRowCount: 4000,
        datasetId: null,
        datasetReady: false,
        currentChunkStart: '2025-01-01T00:00:00',
        attemptCount: 3,
        createdAt: '2026-03-12T09:00:00',
        updatedAt: '2026-03-12T09:05:00',
        startedAt: '2026-03-12T09:00:30',
        completedAt: null,
      },
    ])
  ),
  http.get(`${API_BASE_URL}/api/market-data/jobs/:jobId`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.jobId),
      providerId: 'binance',
      providerLabel: 'Binance',
      assetType: 'CRYPTO',
      datasetName: 'BTC majors 1h',
      symbolsCsv: 'BTC/USDT,ETH/USDT',
      timeframe: '1h',
      startDate: '2024-03-12',
      endDate: '2026-03-12',
      adjusted: false,
      regularSessionOnly: false,
      status: 'QUEUED',
      statusMessage: 'Queued. Waiting for downloader worker.',
      nextRetryAt: null,
      currentSymbolIndex: 0,
      totalSymbols: 2,
      currentSymbol: 'BTC/USDT',
      importedRowCount: 0,
      datasetId: null,
      datasetReady: false,
      currentChunkStart: '2024-03-12T00:00:00',
      attemptCount: 0,
      createdAt: '2026-03-12T09:10:00',
      updatedAt: '2026-03-12T09:10:00',
      startedAt: null,
      completedAt: null,
    })
  ),
  http.post(`${API_BASE_URL}/api/market-data/jobs`, async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json({
      id: 13,
      providerId: body.providerId,
      providerLabel: body.providerId === 'twelvedata' ? 'Twelve Data' : 'Binance',
      assetType: body.assetType,
      datasetName: body.datasetName ?? 'Generated dataset',
      symbolsCsv: Array.isArray(body.symbols) ? body.symbols.join(',') : 'BTC/USDT',
      timeframe: body.timeframe,
      startDate: body.startDate,
      endDate: body.endDate,
      adjusted: Boolean(body.adjusted),
      regularSessionOnly: Boolean(body.regularSessionOnly),
      status: 'QUEUED',
      statusMessage: 'Queued. Waiting for downloader worker.',
      nextRetryAt: null,
      currentSymbolIndex: 0,
      totalSymbols: Array.isArray(body.symbols) ? body.symbols.length : 1,
      currentSymbol: Array.isArray(body.symbols) ? String(body.symbols[0]) : 'BTC/USDT',
      importedRowCount: 0,
      datasetId: null,
      datasetReady: false,
      currentChunkStart: `${String(body.startDate)}T00:00:00`,
      attemptCount: 0,
      createdAt: '2026-03-12T09:10:00',
      updatedAt: '2026-03-12T09:10:00',
      startedAt: null,
      completedAt: null,
    }, { status: 202 });
  }),
  http.post(`${API_BASE_URL}/api/market-data/provider-credentials/:providerId`, async ({ request, params }) => {
    const body = (await request.json()) as Record<string, unknown>;
    const providerId = String(params.providerId);
    const providerLabel = providerId === 'finnhub' ? 'Finnhub' : 'Twelve Data';
    const envVar =
      providerId === 'finnhub'
        ? 'ALGOTRADING_MARKET_DATA_FINNHUB_API_KEY'
        : 'ALGOTRADING_MARKET_DATA_TWELVEDATA_API_KEY';

    return HttpResponse.json({
      providerId,
      providerLabel,
      apiKeyEnvironmentVariable: envVar,
      apiKeyRequired: true,
      hasStoredCredential: true,
      hasEnvironmentCredential: false,
      effectiveCredentialConfigured: true,
      credentialSource: 'DATABASE',
      storageEncryptionConfigured: true,
      note: typeof body.note === 'string' ? body.note : null,
      updatedAt: '2026-03-12T09:50:00',
      docsUrl: providerId === 'finnhub' ? 'https://finnhub.io/docs/api/stock-candles' : 'https://twelvedata.com/docs#time-series',
      signupUrl: providerId === 'finnhub' ? 'https://finnhub.io/register' : 'https://twelvedata.com/pricing',
      accountNotes: 'Free API key required.',
    });
  }),
  http.delete(`${API_BASE_URL}/api/market-data/provider-credentials/:providerId`, () =>
    new HttpResponse(null, { status: 204 })
  ),
  http.post(`${API_BASE_URL}/api/market-data/jobs/:jobId/retry`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.jobId),
      providerId: 'binance',
      providerLabel: 'Binance',
      assetType: 'CRYPTO',
      datasetName: 'BTC majors 1h',
      symbolsCsv: 'BTC/USDT,ETH/USDT',
      timeframe: '1h',
      startDate: '2024-03-12',
      endDate: '2026-03-12',
      adjusted: false,
      regularSessionOnly: false,
      status: 'QUEUED',
      statusMessage: 'Retry requested. Job restarted from the beginning.',
      nextRetryAt: null,
      currentSymbolIndex: 0,
      totalSymbols: 2,
      currentSymbol: 'BTC/USDT',
      importedRowCount: 0,
      datasetId: null,
      datasetReady: false,
      currentChunkStart: '2024-03-12T00:00:00',
      attemptCount: 0,
      createdAt: '2026-03-12T09:00:00',
      updatedAt: '2026-03-12T09:10:00',
      startedAt: null,
      completedAt: null,
    }, { status: 202 })
  ),
  http.post(`${API_BASE_URL}/api/market-data/jobs/:jobId/cancel`, ({ params }) =>
    HttpResponse.json({
      id: Number(params.jobId),
      providerId: 'binance',
      providerLabel: 'Binance',
      assetType: 'CRYPTO',
      datasetName: 'BTC majors 1h',
      symbolsCsv: 'BTC/USDT,ETH/USDT',
      timeframe: '1h',
      startDate: '2024-03-12',
      endDate: '2026-03-12',
      adjusted: false,
      regularSessionOnly: false,
      status: 'CANCELLED',
      statusMessage: 'Cancelled by operator.',
      nextRetryAt: null,
      currentSymbolIndex: 0,
      totalSymbols: 2,
      currentSymbol: 'BTC/USDT',
      importedRowCount: 4000,
      datasetId: null,
      datasetReady: false,
      currentChunkStart: '2025-01-01T00:00:00',
      attemptCount: 3,
      createdAt: '2026-03-12T09:00:00',
      updatedAt: '2026-03-12T09:12:00',
      startedAt: '2026-03-12T09:00:30',
      completedAt: '2026-03-12T09:12:00',
    })
  ),

  // Risk endpoints
  http.get(`${API_BASE_URL}/api/risk/status`, () =>
    HttpResponse.json({
      currentDrawdown: 10,
      maxDrawdownLimit: 25,
      dailyLoss: 2,
      dailyLossLimit: 5,
      openRiskExposure: 20,
      positionCorrelation: 35,
      circuitBreakerActive: false,
      circuitBreakerReason: '',
    })
  ),
  http.get(`${API_BASE_URL}/api/risk/config`, () =>
    HttpResponse.json({
      maxRiskPerTrade: 0.02,
      maxDailyLossLimit: 0.05,
      maxDrawdownLimit: 0.25,
      maxOpenPositions: 5,
      correlationLimit: 0.75,
      circuitBreakerActive: false,
      circuitBreakerReason: '',
    })
  ),
  http.put(`${API_BASE_URL}/api/risk/config`, async ({ request }) =>
    HttpResponse.json(await request.json())
  ),
  http.post(`${API_BASE_URL}/api/risk/circuit-breaker/override`, async ({ request }) =>
    HttpResponse.json(await request.json())
  ),
  http.get(`${API_BASE_URL}/api/risk/alerts`, () =>
    HttpResponse.json([])
  ),

  // Paper state endpoint
  http.get(`${API_BASE_URL}/api/paper/state`, () =>
    HttpResponse.json({
      paperMode: true,
      cashBalance: 10000,
      positionCount: 2,
      totalOrders: 12,
      openOrders: 1,
      filledOrders: 10,
      cancelledOrders: 1,
      lastOrderAt: '2026-03-10T10:00:00',
      lastPositionUpdateAt: '2026-03-10T10:05:00',
      staleOpenOrderCount: 0,
      stalePositionCount: 0,
      recoveryStatus: 'HEALTHY',
      recoveryMessage: 'No stale paper-trading state detected after the latest activity.',
    })
  ),
];
