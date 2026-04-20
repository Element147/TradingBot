export interface StrategyProfile {
  key: string;
  title: string;
  shortDescription: string;
  entryRule: string;
  exitRule: string;
  standAsideRule?: string;
  bestFor: string;
  riskNotes: string;
  timeframeGuidance?: string;
  entryReasons?: string[];
  exitReasons?: string[];
  standAsideReasons?: string[];
  indicatorChecklist?: string[];
  operatorNotes?: string[];
  auditDisposition: 'BASELINE_ONLY' | 'RESEARCH_ONLY' | 'ARCHIVE_CANDIDATE' | 'PAPER_MONITOR_CANDIDATE';
  auditLabel: string;
  auditTone: 'success' | 'info' | 'warning' | 'error';
  auditSummary: string;
  operatorAction: string;
  timeframeOptions: string[];
  configPreset: {
    timeframe: string;
    riskPerTrade: number;
    minPositionSize: number;
    maxPositionSize: number;
  };
}

const PROFILES: StrategyProfile[] = [
  {
    key: 'BUY_AND_HOLD',
    title: 'Buy and Hold Baseline',
    shortDescription:
      'Reference benchmark that enters once and stays invested through the selected test window.',
    entryRule: 'Single entry after the warm-up window begins.',
    exitRule: 'Single exit at the end of the selected period.',
    bestFor: 'Baseline comparison against active strategy logic.',
    riskNotes: 'No active reaction to volatility, drawdown, or changing market regime.',
    auditDisposition: 'BASELINE_ONLY',
    auditLabel: 'Baseline only',
    auditTone: 'info',
    auditSummary:
      'Keep as the passive benchmark only. One-trade evidence is too sparse to treat it as an active candidate.',
    operatorAction: 'Use for comparison and sanity checks, not as a promoted paper-monitor strategy.',
    timeframeOptions: ['1h', '4h', '1d'],
    configPreset: { timeframe: '1d', riskPerTrade: 0.02, minPositionSize: 10, maxPositionSize: 100 },
  },
  {
    key: 'DUAL_MOMENTUM_ROTATION',
    title: 'Dual Momentum Rotation',
    shortDescription:
      'Ranks the strongest assets in the dataset and rotates into the leader only when absolute momentum stays positive.',
    entryRule: 'Enter the top-ranked asset when it leads on 63/126-bar momentum and passes the absolute filter.',
    exitRule: 'Rotate when leadership changes, or move to cash when absolute momentum turns negative.',
    bestFor: 'Small-account, lower-turnover trend allocation across a multi-symbol dataset.',
    riskNotes: 'Can suffer during sharp momentum crashes and fast trend reversals.',
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'Full-sample return was strong, but the frozen holdout produced no out-of-sample trades, so the evidence is still too sparse for promotion.',
    operatorAction: 'Keep in the catalog for later dataset expansion and robustness work before any shadow paper monitoring.',
    timeframeOptions: ['4h', '1d'],
    configPreset: { timeframe: '1d', riskPerTrade: 0.02, minPositionSize: 25, maxPositionSize: 150 },
  },
  {
    key: 'VOLATILITY_MANAGED_DONCHIAN_BREAKOUT',
    title: 'Volatility-Managed Donchian Breakout',
    shortDescription:
      'Follows strong breakouts above a rolling high while reducing size when realized volatility rises.',
    entryRule: 'Enter after a 55-bar breakout above the long-term trend filter.',
    exitRule: 'Exit on 20-bar breakdown, ATR stop failure, or regime deterioration.',
    bestFor: 'Directional trend phases with persistent continuation.',
    riskNotes: 'Can take repeated small losses in sideways markets.',
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'It was the strongest full-sample path, but zero holdout trades means the current rerun does not justify moving it beyond research.',
    operatorAction: 'Retain as a hardened research candidate and rerun it on broader datasets before any paper shadowing.',
    timeframeOptions: ['1h', '4h', '1d'],
    configPreset: { timeframe: '4h', riskPerTrade: 0.02, minPositionSize: 20, maxPositionSize: 120 },
  },
  {
    key: 'TREND_PULLBACK_CONTINUATION',
    title: 'Trend Pullback Continuation',
    shortDescription:
      'Waits for a pullback inside an uptrend and enters once price resumes the higher-timeframe move.',
    entryRule: 'Enter after a bullish pullback with oversold RSI or EMA touch that reclaims the fast EMA.',
    exitRule: 'Exit when the continuation fails, the trend filter breaks, or ATR stop is breached.',
    bestFor: 'Trending markets where cleaner entries matter more than maximum trade count.',
    riskNotes: 'Repeated failed pullbacks can cluster near trend exhaustion.',
    auditDisposition: 'ARCHIVE_CANDIDATE',
    auditLabel: 'Archive candidate',
    auditTone: 'error',
    auditSummary:
      'Both the full-sample and holdout windows stayed negative after realistic costs, so it should not be treated as an active candidate.',
    operatorAction: 'Keep only as a historical comparison point until the logic is materially redesigned.',
    timeframeOptions: ['1h', '4h'],
    configPreset: { timeframe: '4h', riskPerTrade: 0.02, minPositionSize: 15, maxPositionSize: 100 },
  },
  {
    key: 'REGIME_FILTERED_MEAN_REVERSION',
    title: 'Regime-Filtered Mean Reversion',
    shortDescription:
      'Buys oversold snapbacks only when trend strength is muted and the market is still in a safer regime.',
    entryRule: 'Enter after an oversold lower-band close snaps back up while ADX stays in range territory.',
    exitRule: 'Exit at the mean, on a fixed time stop, or after ATR stop failure.',
    bestFor: 'Sideways or choppy environments where trend systems tend to whipsaw.',
    riskNotes: 'Performs poorly if a real breakdown is mistaken for a range-bound dip.',
    auditDisposition: 'ARCHIVE_CANDIDATE',
    auditLabel: 'Archive candidate',
    auditTone: 'error',
    auditSummary:
      'The audited windows stayed weak and sparse, which makes the current implementation a poor operator candidate.',
    operatorAction: 'Archive from active consideration until a tighter regime filter or different market fit is proven.',
    timeframeOptions: ['15m', '1h', '4h'],
    configPreset: { timeframe: '1h', riskPerTrade: 0.015, minPositionSize: 10, maxPositionSize: 80 },
  },
  {
    key: 'TREND_FIRST_ADAPTIVE_ENSEMBLE',
    title: 'Trend-First Adaptive Ensemble',
    shortDescription:
      'Routes capital to trend or mean-reversion logic depending on regime while still choosing the strongest asset in the dataset.',
    entryRule: 'Select the strongest symbol, then activate breakout/pullback logic in trend or mean reversion in range.',
    exitRule: 'Exit when regime routing invalidates the active layer or a stronger asset takes over.',
    bestFor: 'Higher-potential multi-signal research once standalone strategies exist.',
    riskNotes: 'More complex and more vulnerable to false confidence if overfit.',
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'The full sample was positive, but the frozen holdout never triggered an out-of-sample trade and the model remains complexity-heavy.',
    operatorAction: 'Keep for later robustness work after the simpler underlying components prove themselves.',
    timeframeOptions: ['4h', '1d'],
    configPreset: { timeframe: '4h', riskPerTrade: 0.015, minPositionSize: 20, maxPositionSize: 120 },
  },
  {
    key: 'SMA_CROSSOVER',
    title: 'SMA Crossover Trend Following',
    shortDescription:
      'Compares short and long moving averages and follows momentum after trend confirmation.',
    entryRule:
      'Enter when fast SMA crosses above slow SMA (or opposite for short-enabled variants).',
    exitRule:
      'Exit on opposite crossover or when portfolio/risk guardrails trigger.',
    bestFor: 'Directional trend phases with persistent momentum.',
    riskNotes: 'Can whipsaw and overtrade in choppy market with no clear trend.',
    auditDisposition: 'PAPER_MONITOR_CANDIDATE',
    auditLabel: 'Paper-monitor candidate',
    auditTone: 'success',
    auditSummary:
      'This remained the clearest encouraging path: holdout return stayed positive at 7.08%, but it still failed the stricter validator and is not promotion-ready.',
    operatorAction: 'Allow cautious shadow paper monitoring with explicit caveats, not live promotion.',
    timeframeOptions: ['1h', '4h', '1d'],
    configPreset: { timeframe: '4h', riskPerTrade: 0.02, minPositionSize: 15, maxPositionSize: 110 },
  },
  {
    key: 'BOLLINGER_BANDS',
    title: 'Bollinger Bands Mean Reversion',
    shortDescription:
      'Looks for short-term overreaction away from the average price band and expects a pullback.',
    entryRule:
      'Enter when price drops below lower Bollinger band and volatility supports a reversal setup.',
    exitRule:
      'Exit on move back to middle band, or when risk controls/circuit breaker are hit.',
    bestFor: 'Range-bound or sideways market with repeated rebounds.',
    riskNotes: 'Can perform poorly in strong one-direction trend where price keeps drifting away.',
    auditDisposition: 'ARCHIVE_CANDIDATE',
    auditLabel: 'Archive candidate',
    auditTone: 'error',
    auditSummary:
      'The hardened variant cut damage, but both audited windows still finished negative after costs.',
    operatorAction: 'Keep only as a constrained historical baseline unless the signal stack is materially reworked.',
    timeframeOptions: ['15m', '1h', '4h'],
    configPreset: { timeframe: '1h', riskPerTrade: 0.015, minPositionSize: 10, maxPositionSize: 90 },
  },
  {
    key: 'ICHIMOKU_TREND',
    title: 'Ichimoku Trend Filter',
    shortDescription:
      'Uses a long/cash Ichimoku cloud filter with historically projected spans so the signal stays trend-focused without look-ahead bias.',
    entryRule:
      'Enter when price is above the historical cloud, the conversion line is above the base line, and the current close is stronger than the 26-bar lagging reference.',
    exitRule:
      'Exit when price loses cloud/base support or the ATR fail-safe stop is breached.',
    bestFor: 'Lower-turnover directional regimes where trend confirmation matters more than early entries.',
    riskNotes: 'Can lag after sharp reversals, and the shifted-cloud logic must stay explicitly bias-free in tests and telemetry.',
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'The implementation is honest and bias-safe, but the holdout window stayed slightly negative with only one out-of-sample trade.',
    operatorAction: 'Retain for later multi-dataset reruns, not for immediate paper shadow monitoring.',
    timeframeOptions: ['4h', '1d'],
    configPreset: { timeframe: '1d', riskPerTrade: 0.015, minPositionSize: 20, maxPositionSize: 120 },
  },
  {
    key: 'OPENING_RANGE_VWAP_BREAKOUT',
    title: 'Opening Range VWAP Breakout',
    shortDescription:
      'Trades same-session breakouts only when price clears the opening range with VWAP support, elevated volume, and a non-bearish regime backdrop.',
    entryRule:
      'Enter after price closes above the opening-range high with breakout expansion, VWAP alignment, volume confirmation, and bullish session bias.',
    exitRule:
      'Exit on failed breakout, VWAP loss, ATR or structural stop breach, or mandatory session-cutoff flattening.',
    standAsideRule:
      'Stand aside when the opening range breaks without volume, price is not aligned with session VWAP, the regime is bearish, or the session is already too late.',
    bestFor: 'Liquid ETF or crypto session-open windows where early directional discovery can continue cleanly.',
    riskNotes: 'Opening volatility can fake out quickly, so late entries, weak volume, and oversized ATR conditions stay filtered out.',
    timeframeGuidance:
      'Run on 15m bars and treat it as a session-open strategy only; flatten by the configured cutoff instead of carrying overnight.',
    entryReasons: ['opening range cleared', 'VWAP aligned', 'volume confirmed', 'regime supportive'],
    exitReasons: ['breakout failed', 'VWAP lost', 'protective stop hit', 'session cutoff flatten'],
    standAsideReasons: ['weak volume', 'VWAP misaligned', 'bearish regime', 'late session'],
    indicatorChecklist: ['opening_range_high', 'session_vwap', 'volume_ratio_20', 'ema_20', 'ema_50', 'atr_14'],
    operatorNotes: ['research only', 'same-day flatten required', 'not audit-cleared'],
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'New Phase 3 small-account hypothesis. It is implemented for research and explainability, not yet promoted or audit-cleared.',
    operatorAction: 'Use only for controlled backtest research until the frozen audit protocol and paper evidence are completed.',
    timeframeOptions: ['15m'],
    configPreset: { timeframe: '15m', riskPerTrade: 0.01, minPositionSize: 10, maxPositionSize: 75 },
  },
  {
    key: 'VWAP_PULLBACK_CONTINUATION',
    title: 'VWAP Pullback Continuation',
    shortDescription:
      'Buys same-session pullbacks to VWAP or EMA support only after the trend resumes with RSI reset or fast-EMA reclaim confirmation.',
    entryRule:
      'Enter after a bullish higher-timeframe bias survives a pullback to session VWAP or EMA support and momentum re-accelerates.',
    exitRule:
      'Exit on VWAP loss, EMA support failure, protective-stop breach, or mandatory session-cutoff flattening.',
    standAsideRule:
      'Stand aside when the higher-timeframe bias is weak, the pullback never actually touches support, momentum does not reclaim, or the entry would be too late in the session.',
    bestFor: 'Liquid intraday names that trend cleanly but punish late chase entries more than controlled pullback entries.',
    riskNotes: 'Late-session entries, shallow pullbacks, and weak trend context are intentionally filtered out to avoid chasing exhausted moves.',
    timeframeGuidance:
      'Use 15m bars for same-session trend continuation and prefer names that respect VWAP or EMA support intraday.',
    entryReasons: ['higher-timeframe bias aligned', 'support touched', 'RSI reset confirmed', 'fast EMA reclaimed'],
    exitReasons: ['VWAP lost', 'EMA support lost', 'protective stop hit', 'session cutoff flatten'],
    standAsideReasons: ['bias disagreed', 'no real pullback', 'momentum failed to resume', 'late session'],
    indicatorChecklist: ['session_vwap', 'ema_5', 'ema_20', 'ema_50', 'rsi_5', 'atr_14'],
    operatorNotes: ['research only', 'same-day flatten required', 'not audit-cleared'],
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'New Phase 3 small-account hypothesis. It is implemented for research and telemetry review, not yet audit-cleared.',
    operatorAction: 'Use only in controlled backtest research until the frozen audit protocol and paper evidence are completed.',
    timeframeOptions: ['15m'],
    configPreset: { timeframe: '15m', riskPerTrade: 0.01, minPositionSize: 10, maxPositionSize: 75 },
  },
  {
    key: 'EXHAUSTION_REVERSAL_FADE',
    title: 'Exhaustion Reversal Fade',
    shortDescription:
      'Fades same-session downside exhaustion only after volatility expansion, downside stretch, and a bullish reversal candle all line up.',
    entryRule:
      'Enter after price extends below VWAP or the lower Bollinger band, volatility expands, RSI is oversold, and the reversal candle closes back in control.',
    exitRule:
      'Exit at the earlier of the mean-reversion target, time stop, hard stop, ATR stop breach, or mandatory session-cutoff flattening.',
    standAsideRule:
      'Stand aside when the selloff is ordinary rather than climactic, reversal confirmation never arrives, or the downtrend remains too strong to fade safely.',
    bestFor: 'Liquid intraday names where panic-style downside moves can mean revert cleanly without forcing overnight risk.',
    riskNotes: 'This is intentionally selective because fading ordinary downtrends is expensive; strong-trend overrides require climactic volume and deeper exhaustion evidence.',
    timeframeGuidance:
      'Use 15m bars and only on liquid names where panic-style extensions can revert before the session ends.',
    entryReasons: ['downside stretch confirmed', 'climactic volume present', 'RSI oversold', 'bullish reversal candle'],
    exitReasons: ['mean target hit', 'time stop hit', 'hard stop hit', 'session cutoff flatten'],
    standAsideReasons: ['stretch too shallow', 'volume not climactic', 'trend too strong to fade', 'no reversal candle'],
    indicatorChecklist: ['session_vwap', 'bb_lower_20', 'ema_20', 'ema_50', 'rsi_5', 'adx_14', 'volume_ratio_20', 'atr_14'],
    operatorNotes: ['research only', 'same-day flatten required', 'not audit-cleared'],
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'New Phase 3 small-account hypothesis. It is implemented for research and telemetry review, not yet audit-cleared.',
    operatorAction: 'Use only in controlled backtest research until the frozen audit protocol and paper evidence are completed.',
    timeframeOptions: ['15m'],
    configPreset: { timeframe: '15m', riskPerTrade: 0.01, minPositionSize: 10, maxPositionSize: 60 },
  },
  {
    key: 'MULTI_TIMEFRAME_EMA_ADX_PULLBACK',
    title: 'Multi-Timeframe EMA ADX Pullback',
    shortDescription:
      'Uses a slower EMA trend stack as the regime filter, then buys controlled pullbacks only when the lower-timeframe continuation trigger reasserts itself.',
    entryRule:
      'Enter when the slow EMA stack is bullish, price pulls back into the medium EMA zone without breaking structure, and the fast EMA reclaim confirms continuation with ADX or volatility support.',
    exitRule:
      'Exit on medium-EMA support failure, higher-timeframe misalignment, or protective-stop breach.',
    standAsideRule:
      'Stand aside when the EMA stack is misaligned, the pullback is too shallow or too deep, or the trigger EMA never reasserts the continuation.',
    bestFor: 'Liquid intraday or hourly names where multi-hour continuation is cleaner than raw breakout chasing.',
    riskNotes: 'This remains a proxy higher-timeframe model on one feed, so the alignment logic must stay honest and research-only until broader audit evidence exists.',
    timeframeGuidance:
      'Use 1h bars and treat the slower EMA stack as a same-feed higher-timeframe proxy, not as a substitute for true resampled data.',
    entryReasons: ['trend aligned', 'pullback detected', 'continuation resumed', 'ADX or volatility support'],
    exitReasons: ['support failed', 'trend misaligned', 'protective stop hit'],
    standAsideReasons: ['trend misaligned', 'pullback missing', 'trigger not reclaimed', 'volatility support absent'],
    indicatorChecklist: ['ema_8', 'ema_21', 'ema_50', 'ema_200', 'adx_14', 'atr_14'],
    operatorNotes: ['research only', 'same-feed higher-timeframe proxy', 'not audit-cleared'],
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'New Phase 3 small-account hypothesis. It is implemented for research and telemetry review, not yet audit-cleared.',
    operatorAction: 'Use only in controlled backtest research until the frozen audit protocol and paper evidence are completed.',
    timeframeOptions: ['1h'],
    configPreset: { timeframe: '1h', riskPerTrade: 0.01, minPositionSize: 15, maxPositionSize: 90 },
  },
  {
    key: 'SQUEEZE_BREAKOUT_REGIME_CONFIRMATION',
    title: 'Squeeze Breakout Regime Confirmation',
    shortDescription:
      'Waits for volatility compression, then only takes the breakout when momentum and the higher-timeframe regime confirm that the expansion is likely meaningful.',
    entryRule:
      'Enter after Bollinger-width compression resolves through the recent breakout high while momentum and ADX confirm and the broader regime is not bearish.',
    exitRule:
      'Exit on failed expansion back through the breakout level, protective-stop breach, or regime break.',
    standAsideRule:
      'Stand aside when compression exists without momentum, ADX stays weak, or the broader regime filter suggests the breakout is more likely to fail than follow through.',
    bestFor: 'Liquid hourly names where compression-breakout moves need more filtering than a plain breakout system.',
    riskNotes: 'Compression alone is not enough; the strategy is explicitly trying to reduce sideways false breaks and reports breakout failure rate for that reason.',
    timeframeGuidance:
      'Use 1h bars and treat this as a filtered expansion strategy, not a raw breakout system; review breakout failure rate alongside returns.',
    entryReasons: ['squeeze active', 'breakout confirmed', 'momentum confirmed', 'regime supportive'],
    exitReasons: ['failed expansion', 'regime break', 'protective stop hit'],
    standAsideReasons: ['compression missing', 'momentum absent', 'ADX weak', 'bearish regime'],
    indicatorChecklist: ['ema_50', 'ema_200', 'breakout_high_20', 'bb_upper_20', 'bb_lower_20', 'adx_14', 'atr_14'],
    operatorNotes: ['research only', 'track breakout failure rate', 'not audit-cleared'],
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'New Phase 3 small-account hypothesis. It is implemented for research, telemetry review, and breakout-quality reporting, not yet audit-cleared.',
    operatorAction: 'Use only in controlled backtest research until the frozen audit protocol and paper evidence are completed.',
    timeframeOptions: ['1h'],
    configPreset: { timeframe: '1h', riskPerTrade: 0.01, minPositionSize: 15, maxPositionSize: 85 },
  },
  {
    key: 'RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER',
    title: 'Relative Strength Rotation With Intraday Entry Filter',
    shortDescription:
      'Ranks only a fixed small liquid basket, then waits for fast EMA or breakout confirmation before rotating into the current leader.',
    entryRule:
      'Enter the top approved leader only when absolute momentum stays positive and the hourly timing trigger confirms with EMA or breakout follow-through.',
    exitRule:
      'Exit to cash when absolute momentum breaks or intraday support fails, and rotate only when a new approved leader clears the same timing gate.',
    standAsideRule:
      'Stand aside when the top-ranked leader is outside the approved liquid basket, absolute momentum is negative, or the fast timing trigger never confirms the leader cleanly.',
    bestFor: 'Small-account research that wants low-turnover leadership ranking without blindly buying every rank change.',
    riskNotes: 'A narrow basket and timing filter reduce unsupported broad-universe churn, but strong leaders can still reverse quickly after late-stage momentum bursts.',
    timeframeGuidance:
      'Use 1h bars and keep the basket fixed to a small approved liquid universe so the timing filter reduces churn instead of enabling broad-universe noise.',
    entryReasons: ['approved leader selected', 'absolute momentum positive', 'trigger EMA reclaimed or breakout confirmed'],
    exitReasons: ['absolute momentum broke', 'intraday support failed', 'new leader confirmed'],
    standAsideReasons: ['leader outside approved basket', 'absolute momentum negative', 'timing filter not confirmed'],
    indicatorChecklist: ['sma_200', 'ema_20', 'ema_5', 'return_21', 'return_63', 'breakout_high_5', 'rsi_5'],
    operatorNotes: ['research only', 'cash fallback only', 'not audit-cleared'],
    auditDisposition: 'RESEARCH_ONLY',
    auditLabel: 'Research only',
    auditTone: 'warning',
    auditSummary:
      'New Phase 3 small-account hypothesis. It is implemented for research and telemetry review, not yet audit-cleared.',
    operatorAction: 'Use only in controlled backtest research until the frozen audit protocol and paper evidence are completed.',
    timeframeOptions: ['1h'],
    configPreset: { timeframe: '1h', riskPerTrade: 0.01, minPositionSize: 15, maxPositionSize: 80 },
  },
];

const normalize = (value: string): string => value.trim().replace(/-/g, '_').toUpperCase();

export const getStrategyProfile = (strategyType: string): StrategyProfile | null => {
  const normalizedType = normalize(strategyType);
  return PROFILES.find((profile) => profile.key === normalizedType) ?? null;
};

export const getAllStrategyProfiles = (): StrategyProfile[] => PROFILES;
