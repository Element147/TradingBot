import { createSlice, type PayloadAction, type AnyAction } from '@reduxjs/toolkit';

export type EnvironmentMode = 'test' | 'live';

export interface EnvironmentState {
  mode: EnvironmentMode;
  connectedExchange: string | null;
  lastSyncTime: string | null;
}

// Initialize from localStorage or default to 'test' for safety
const initializeEnvironment = (): EnvironmentState => {
  const savedMode = localStorage.getItem('environment_mode') as EnvironmentMode | null;
  return {
    mode: savedMode || 'test', // Default to test for safety
    connectedExchange: null,
    lastSyncTime: null,
  };
};

const environmentSlice = createSlice({
  name: 'environment',
  initialState: initializeEnvironment(),
  reducers: {
    setEnvironmentMode: (state, action: PayloadAction<EnvironmentMode>) => {
      state.mode = action.payload;
      // Persist to localStorage
      localStorage.setItem('environment_mode', action.payload);
    },
    setConnectedExchange: (state, action: PayloadAction<string | null>) => {
      state.connectedExchange = action.payload;
    },
    updateSyncTime: (state) => {
      state.lastSyncTime = new Date().toISOString();
    },
  },
});

export const { setEnvironmentMode, setConnectedExchange, updateSyncTime } = environmentSlice.actions;

// Selectors
export const selectEnvironmentMode = (state: { environment: EnvironmentState }) => state.environment.mode;
export const selectConnectedExchange = (state: { environment: EnvironmentState }) =>
  state.environment.connectedExchange;
export const selectLastSyncTime = (state: { environment: EnvironmentState }) =>
  state.environment.lastSyncTime;

const environmentBaseReducer = environmentSlice.reducer;

// Re-evaluate localStorage whenever a fresh store is created with undefined state.
const environmentReducer = (state: EnvironmentState | undefined, action: AnyAction) =>
  environmentBaseReducer(state ?? initializeEnvironment(), action);

export default environmentReducer;

