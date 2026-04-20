export type ExecutionContext = 'research' | 'forward-test' | 'paper' | 'live';
export type ExecutionEnvironment = 'test' | 'live';

export type ExecutionContextMeta = {
  context: ExecutionContext;
  label: string;
  description: string;
  environment: ExecutionEnvironment;
};

export const executionContextMeta: Record<ExecutionContext, ExecutionContextMeta> = {
  research: {
    context: 'research',
    label: 'Research',
    description: 'Route-owned research workflows stay pinned to test-scoped data and never place orders.',
    environment: 'test',
  },
  'forward-test': {
    context: 'forward-test',
    label: 'Forward Test',
    description: 'Observation-only strategy monitoring stays fail-closed and test-scoped by default.',
    environment: 'test',
  },
  paper: {
    context: 'paper',
    label: 'Paper',
    description: 'Simulated execution workflows stay isolated from live routing.',
    environment: 'test',
  },
  live: {
    context: 'live',
    label: 'Live',
    description: 'Approved live routes use live-scoped reads and writes with explicit capability gates.',
    environment: 'live',
  },
};

export const resolveExecutionEnvironment = (
  context: ExecutionContext
): ExecutionEnvironment => executionContextMeta[context].environment;

export const resolveRouteExecutionContext = (pathname: string): ExecutionContextMeta | null => {
  if (pathname.startsWith('/backtest') || pathname.startsWith('/market-data')) {
    return executionContextMeta.research;
  }

  if (pathname.startsWith('/forward-testing')) {
    return executionContextMeta['forward-test'];
  }

  if (
    pathname.startsWith('/paper') ||
    pathname.startsWith('/strategies') ||
    pathname.startsWith('/trades')
  ) {
    return executionContextMeta.paper;
  }

  if (pathname.startsWith('/live')) {
    return executionContextMeta.live;
  }

  return null;
};
