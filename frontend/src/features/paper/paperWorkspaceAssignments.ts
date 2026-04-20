export interface PaperWorkspaceAssignment {
  exchangeConnectionId: string;
  strategyIds: number[];
}

const STORAGE_KEY = 'paper-workspace-assignments';

const isAssignment = (value: unknown): value is PaperWorkspaceAssignment => {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const assignment = value as Record<string, unknown>;
  return (
    typeof assignment.exchangeConnectionId === 'string' &&
    Array.isArray(assignment.strategyIds) &&
    assignment.strategyIds.every((strategyId) => typeof strategyId === 'number')
  );
};

export const loadPaperWorkspaceAssignments = (): PaperWorkspaceAssignment[] => {
  if (typeof window === 'undefined') {
    return [];
  }

  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.filter(isAssignment) : [];
  } catch {
    return [];
  }
};

export const savePaperWorkspaceAssignments = (
  assignments: PaperWorkspaceAssignment[]
) => {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(assignments));
};

export const togglePaperWorkspaceAssignment = (
  assignments: PaperWorkspaceAssignment[],
  exchangeConnectionId: string,
  strategyId: number
) => {
  const nextAssignments = [...assignments];
  const existingIndex = nextAssignments.findIndex(
    (assignment) => assignment.exchangeConnectionId === exchangeConnectionId
  );

  if (existingIndex === -1) {
    nextAssignments.push({ exchangeConnectionId, strategyIds: [strategyId] });
    savePaperWorkspaceAssignments(nextAssignments);
    return nextAssignments;
  }

  const existing = nextAssignments[existingIndex];
  const strategyIds = existing.strategyIds.includes(strategyId)
    ? existing.strategyIds.filter((id) => id !== strategyId)
    : [...existing.strategyIds, strategyId];

  if (strategyIds.length === 0) {
    nextAssignments.splice(existingIndex, 1);
  } else {
    nextAssignments[existingIndex] = {
      ...existing,
      strategyIds,
    };
  }

  savePaperWorkspaceAssignments(nextAssignments);
  return nextAssignments;
};
