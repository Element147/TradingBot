export interface ForwardTestingNote {
  id: string;
  strategyId: number;
  strategyName: string;
  body: string;
  createdAt: string;
}

const STORAGE_KEY = 'forward-testing-notes';

const isForwardTestingNote = (value: unknown): value is ForwardTestingNote => {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const note = value as Record<string, unknown>;
  return (
    typeof note.id === 'string' &&
    typeof note.strategyId === 'number' &&
    typeof note.strategyName === 'string' &&
    typeof note.body === 'string' &&
    typeof note.createdAt === 'string'
  );
};

export const loadForwardTestingNotes = (): ForwardTestingNote[] => {
  if (typeof window === 'undefined') {
    return [];
  }

  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(isForwardTestingNote);
  } catch {
    return [];
  }
};

export const saveForwardTestingNotes = (notes: ForwardTestingNote[]) => {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(notes));
};

export const appendForwardTestingNote = (
  note: Omit<ForwardTestingNote, 'id' | 'createdAt'>
): ForwardTestingNote[] => {
  const nextNotes = [
    {
      ...note,
      id: `${note.strategyId}-${Date.now()}`,
      createdAt: new Date().toISOString(),
    },
    ...loadForwardTestingNotes(),
  ].slice(0, 100);

  saveForwardTestingNotes(nextNotes);
  return nextNotes;
};
