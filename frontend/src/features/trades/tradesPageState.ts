const STORAGE_KEY = 'trades_page_filters_v2';

export interface TradeFilterDraft {
  accountId: string;
  symbol: string;
  startDate: string;
  endDate: string;
  limit: string;
  searchId: string;
}

export const defaultTradeFilterDraft: TradeFilterDraft = {
  accountId: '',
  symbol: '',
  startDate: '',
  endDate: '',
  limit: '200',
  searchId: '',
};

export const toDateTimeParam = (value: string): string | undefined => {
  if (!value.trim()) {
    return undefined;
  }

  return value.length === 16 ? `${value}:00` : value;
};

export const readStoredTradeFilterDraft = (): TradeFilterDraft => {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return defaultTradeFilterDraft;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<TradeFilterDraft>;
    return {
      accountId: parsed.accountId ?? '',
      symbol: parsed.symbol ?? '',
      startDate: parsed.startDate ?? '',
      endDate: parsed.endDate ?? '',
      limit: parsed.limit ?? '200',
      searchId: parsed.searchId ?? '',
    };
  } catch {
    return defaultTradeFilterDraft;
  }
};

export const persistTradeFilterDraft = (draft: TradeFilterDraft) => {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
};
