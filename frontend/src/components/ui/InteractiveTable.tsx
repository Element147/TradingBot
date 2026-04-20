/* eslint-disable react-refresh/only-export-components */
import NorthRoundedIcon from '@mui/icons-material/NorthRounded';
import SouthRoundedIcon from '@mui/icons-material/SouthRounded';
import UnfoldMoreRoundedIcon from '@mui/icons-material/UnfoldMoreRounded';
import {
  Box,
  Button,
  MenuItem,
  Pagination,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import {
  flexRender,
  functionalUpdate,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type ColumnFiltersState,
  type ColumnSizingState,
  type OnChangeFn,
  type PaginationState,
  type Row,
  type RowData,
  type SortingState,
} from '@tanstack/react-table';
import { useEffect, useMemo, useState, type ReactNode } from 'react';

import { EmptyState, StatusPill, SurfacePanel } from './Workbench';

/* eslint-disable @typescript-eslint/no-unused-vars */
declare module '@tanstack/react-table' {
  interface ColumnMeta<TData extends RowData, TValue> {
    align?: 'left' | 'right' | 'center';
    filterVariant?: 'text' | 'select' | 'none';
    filterPlaceholder?: string;
    filterOptions?: Array<{
      label: string;
      value: string;
    }>;
    headerDescription?: string;
  }
}
/* eslint-enable @typescript-eslint/no-unused-vars */

export interface InteractiveTablePersistedState {
  sorting: SortingState;
  columnFilters: ColumnFiltersState;
  globalFilter: string;
  columnSizing: ColumnSizingState;
  pagination: PaginationState;
}

export interface InteractiveTableStateControls {
  state: InteractiveTablePersistedState;
  onSortingChange: OnChangeFn<SortingState>;
  onColumnFiltersChange: OnChangeFn<ColumnFiltersState>;
  onGlobalFilterChange: (value: string) => void;
  onColumnSizingChange: OnChangeFn<ColumnSizingState>;
  onPaginationChange: OnChangeFn<PaginationState>;
  resetState: () => void;
}

interface UseInteractiveTableStateOptions {
  tableId: string;
  initialPageSize?: number;
}

const buildDefaultState = (initialPageSize: number): InteractiveTablePersistedState => ({
  sorting: [],
  columnFilters: [],
  globalFilter: '',
  columnSizing: {},
  pagination: {
    pageIndex: 0,
    pageSize: initialPageSize,
  },
});

const storageKeyFor = (tableId: string) => `interactive-table:${tableId}`;

const loadPersistedState = (
  tableId: string,
  initialPageSize: number
): InteractiveTablePersistedState => {
  if (typeof window === 'undefined') {
    return buildDefaultState(initialPageSize);
  }

  try {
    const raw = window.localStorage.getItem(storageKeyFor(tableId));
    if (!raw) {
      return buildDefaultState(initialPageSize);
    }

    const parsed = JSON.parse(raw) as Partial<InteractiveTablePersistedState>;
    return {
      sorting: Array.isArray(parsed.sorting) ? parsed.sorting : [],
      columnFilters: Array.isArray(parsed.columnFilters) ? parsed.columnFilters : [],
      globalFilter: typeof parsed.globalFilter === 'string' ? parsed.globalFilter : '',
      columnSizing:
        parsed.columnSizing && typeof parsed.columnSizing === 'object'
          ? parsed.columnSizing
          : {},
      pagination: {
        pageIndex:
          typeof parsed.pagination?.pageIndex === 'number' && parsed.pagination.pageIndex >= 0
            ? parsed.pagination.pageIndex
            : 0,
        pageSize:
          typeof parsed.pagination?.pageSize === 'number' && parsed.pagination.pageSize > 0
            ? parsed.pagination.pageSize
            : initialPageSize,
      },
    };
  } catch {
    return buildDefaultState(initialPageSize);
  }
};

export function useInteractiveTableState({
  tableId,
  initialPageSize = 25,
}: UseInteractiveTableStateOptions): InteractiveTableStateControls {
  const [state, setState] = useState<InteractiveTablePersistedState>(() =>
    loadPersistedState(tableId, initialPageSize)
  );

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    window.localStorage.setItem(storageKeyFor(tableId), JSON.stringify(state));
  }, [state, tableId]);

  const onSortingChange: OnChangeFn<SortingState> = (updater) => {
    setState((current) => ({
      ...current,
      sorting: functionalUpdate(updater, current.sorting),
      pagination: {
        ...current.pagination,
        pageIndex: 0,
      },
    }));
  };

  const onColumnFiltersChange: OnChangeFn<ColumnFiltersState> = (updater) => {
    setState((current) => ({
      ...current,
      columnFilters: functionalUpdate(updater, current.columnFilters),
      pagination: {
        ...current.pagination,
        pageIndex: 0,
      },
    }));
  };

  const onGlobalFilterChange = (value: string) => {
    setState((current) => ({
      ...current,
      globalFilter: value,
      pagination: {
        ...current.pagination,
        pageIndex: 0,
      },
    }));
  };

  const onColumnSizingChange: OnChangeFn<ColumnSizingState> = (updater) => {
    setState((current) => ({
      ...current,
      columnSizing: functionalUpdate(updater, current.columnSizing),
    }));
  };

  const onPaginationChange: OnChangeFn<PaginationState> = (updater) => {
    setState((current) => ({
      ...current,
      pagination: functionalUpdate(updater, current.pagination),
    }));
  };

  const resetState = () => {
    setState(buildDefaultState(initialPageSize));
  };

  return {
    state,
    onSortingChange,
    onColumnFiltersChange,
    onGlobalFilterChange,
    onColumnSizingChange,
    onPaginationChange,
    resetState,
  };
}

interface InteractiveTableProps<TData extends RowData> {
  title: ReactNode;
  description?: ReactNode;
  data: TData[];
  columns: ColumnDef<TData, unknown>[];
  stateControls: InteractiveTableStateControls;
  actions?: ReactNode;
  stats?: ReactNode;
  toolbarFilters?: ReactNode;
  activeFilterPills?: ReactNode;
  emptyTitle: string;
  emptyDescription: string;
  loading?: boolean;
  error?: string | null;
  loadingLabel?: string;
  globalFilterPlaceholder?: string;
  manualFiltering?: boolean;
  manualSorting?: boolean;
  manualPagination?: boolean;
  totalRows?: number;
  enableGlobalFilter?: boolean;
  onRowClick?: (row: TData) => void;
  isRowSelected?: (row: TData) => boolean;
  getRowId?: (row: TData, index: number, parent?: Row<TData>) => string;
  pageSizeOptions?: number[];
  maxHeight?: number | string;
}

const defaultPageSizeOptions = [10, 25, 50, 100];

const alignText = (align: 'left' | 'right' | 'center' | undefined) => {
  if (align === 'right') {
    return 'right';
  }
  if (align === 'center') {
    return 'center';
  }
  return 'left';
};

const renderSortIcon = (direction: false | 'asc' | 'desc') => {
  if (direction === 'asc') {
    return <NorthRoundedIcon fontSize="inherit" />;
  }
  if (direction === 'desc') {
    return <SouthRoundedIcon fontSize="inherit" />;
  }
  return <UnfoldMoreRoundedIcon fontSize="inherit" />;
};

const headerLabel = (headerContent: ReactNode): string => {
  if (typeof headerContent === 'string') {
    return headerContent;
  }
  return '';
};

export function InteractiveTable<TData extends RowData>({
  title,
  description,
  data,
  columns,
  stateControls,
  actions,
  stats,
  toolbarFilters,
  activeFilterPills,
  emptyTitle,
  emptyDescription,
  loading = false,
  error = null,
  loadingLabel = 'Loading rows...',
  globalFilterPlaceholder = 'Quick search',
  manualFiltering = false,
  manualSorting = false,
  manualPagination = false,
  totalRows,
  enableGlobalFilter = true,
  onRowClick,
  isRowSelected,
  getRowId,
  pageSizeOptions = defaultPageSizeOptions,
  maxHeight = 680,
}: InteractiveTableProps<TData>) {
  const { state } = stateControls;
  // TanStack Table owns imperative sizing and sorting handlers, so this hook must stay local here.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
    columns,
    state,
    getRowId,
    columnResizeMode: 'onChange',
    manualFiltering,
    manualSorting,
    manualPagination,
    enableMultiSort: true,
    sortDescFirst: false,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: manualFiltering ? undefined : getFilteredRowModel(),
    getSortedRowModel: manualSorting ? undefined : getSortedRowModel(),
    getPaginationRowModel: manualPagination ? undefined : getPaginationRowModel(),
    onSortingChange: stateControls.onSortingChange,
    onColumnFiltersChange: stateControls.onColumnFiltersChange,
    onColumnSizingChange: stateControls.onColumnSizingChange,
    onPaginationChange: stateControls.onPaginationChange,
    globalFilterFn: 'includesString',
  });

  const headerHeight = 44;
  const tableRows = table.getRowModel().rows;
  const resolvedTotalRows = totalRows ?? (manualPagination ? data.length : table.getPrePaginationRowModel().rows.length);
  const pageCount = manualPagination
    ? Math.max(1, Math.ceil((resolvedTotalRows || 0) / state.pagination.pageSize))
    : Math.max(1, table.getPageCount());

  const derivedFilterPills = useMemo(() => {
    const pills: ReactNode[] = [];

    if (state.globalFilter.trim()) {
      pills.push(
        <StatusPill
          key="global-filter-pill"
          label={`Search: ${state.globalFilter.trim()}`}
          tone="info"
          variant="filled"
        />
      );
    }

    state.columnFilters.forEach((filter) => {
      if (typeof filter.value !== 'string' || !filter.value.trim()) {
        return;
      }

      const column = table.getColumn(filter.id);
      if (!column) {
        return;
      }

      const rawHeader = column.columnDef.header;
      const label =
        typeof rawHeader === 'string'
          ? rawHeader
          : typeof column.columnDef.meta?.filterPlaceholder === 'string'
            ? column.columnDef.meta.filterPlaceholder
            : filter.id;
      pills.push(
        <StatusPill
          key={`column-filter-pill-${filter.id}`}
          label={`${label}: ${filter.value}`}
          tone="default"
          variant="filled"
        />
      );
    });

    return pills;
  }, [state.columnFilters, state.globalFilter, table]);

  return (
    <SurfacePanel
      title={title}
      description={description}
      actions={actions}
      contentSx={{ gap: 1.25 }}
    >
      <Stack spacing={1}>
        <Stack
          direction={{ xs: 'column', lg: 'row' }}
          spacing={1}
          justifyContent="space-between"
          alignItems={{ xs: 'stretch', lg: 'center' }}
        >
          {enableGlobalFilter ? (
            <TextField
              size="small"
              label="Quick search"
              value={state.globalFilter}
              onChange={(event) => stateControls.onGlobalFilterChange(event.target.value)}
              placeholder={globalFilterPlaceholder}
              sx={{ minWidth: { xs: '100%', lg: 280 } }}
            />
          ) : (
            <Box />
          )}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <TextField
              select
              size="small"
              label="Page size"
              value={state.pagination.pageSize}
              onChange={(event) =>
                stateControls.onPaginationChange((current) => ({
                  ...current,
                  pageSize: Number(event.target.value),
                  pageIndex: 0,
                }))
              }
              sx={{ minWidth: 116 }}
            >
              {pageSizeOptions.map((option) => (
                <MenuItem key={option} value={option}>
                  {option} rows
                </MenuItem>
              ))}
            </TextField>
            <StatusPill label={`${derivedFilterPills.length} filters`} tone="info" />
            <StatusPill label={`${resolvedTotalRows} rows`} />
            <StatusPill label={`${pageCount} pages`} />
            <Button variant="outlined" onClick={stateControls.resetState}>
              Reset table
            </Button>
          </Stack>
        </Stack>

        {stats ? <Box>{stats}</Box> : null}
        {toolbarFilters ? <Box>{toolbarFilters}</Box> : null}

        {derivedFilterPills.length > 0 || activeFilterPills ? (
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            {derivedFilterPills}
            {activeFilterPills}
          </Stack>
        ) : null}
      </Stack>

      {error ? (
        <EmptyState title="Unable to load table" description={error} tone="error" />
      ) : null}

      {!error ? (
        <TableContainer
          sx={{
            maxHeight,
            borderRadius: 2.5,
            border: (theme) => `1px solid ${theme.palette.divider}`,
            backgroundColor: 'background.paper',
          }}
        >
          <Table stickyHeader size="small" sx={{ tableLayout: 'fixed', minWidth: '100%' }}>
            <colgroup>
              {table.getVisibleLeafColumns().map((column) => (
                <col key={column.id} style={{ width: column.getSize() }} />
              ))}
            </colgroup>
            <TableHead>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => {
                    const { column } = header;
                    const { columnDef } = column;
                    const { meta } = columnDef;
                    const sortDirection = column.getIsSorted();
                    const canSort = column.getCanSort();
                    const canResize = column.getCanResize();
                    const headerContent = header.isPlaceholder
                      ? null
                      : flexRender(columnDef.header, header.getContext());
                    const label = headerLabel(headerContent);

                    return (
                      <TableCell
                        key={header.id}
                        align={meta?.align}
                        sx={{
                          position: 'sticky',
                          top: 0,
                          zIndex: 4,
                          backgroundColor: (theme) => alpha(theme.palette.background.default, 0.96),
                          minWidth: header.getSize(),
                          width: header.getSize(),
                          px: 1.25,
                          py: 1.1,
                        }}
                      >
                        <Stack
                          direction="row"
                          spacing={0.75}
                          alignItems="center"
                          justifyContent={
                            meta?.align === 'right'
                              ? 'flex-end'
                              : meta?.align === 'center'
                                ? 'center'
                                : 'flex-start'
                          }
                          sx={{ minHeight: 18 }}
                        >
                          <Box
                            role={canSort ? 'button' : undefined}
                            onClick={canSort ? column.getToggleSortingHandler() : undefined}
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 0.5,
                              cursor: canSort ? 'pointer' : 'default',
                              minWidth: 0,
                              userSelect: 'none',
                            }}
                          >
                            <Typography
                              variant="caption"
                              sx={{
                                fontWeight: 800,
                                letterSpacing: '0.06em',
                                color: 'text.secondary',
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {label || headerContent}
                            </Typography>
                            {canSort ? (
                              <Box
                                component="span"
                                sx={{
                                  display: 'inline-flex',
                                  fontSize: '0.95rem',
                                  color: sortDirection ? 'text.primary' : 'text.secondary',
                                }}
                              >
                                {renderSortIcon(sortDirection)}
                              </Box>
                            ) : null}
                          </Box>
                          {meta?.headerDescription ? (
                            <Tooltip title={meta.headerDescription} arrow>
                              <Typography
                                component="span"
                                variant="caption"
                                color="text.secondary"
                                sx={{ cursor: 'help' }}
                              >
                                i
                              </Typography>
                            </Tooltip>
                          ) : null}
                        </Stack>
                        {canResize ? (
                          <Box
                            onMouseDown={header.getResizeHandler()}
                            onTouchStart={header.getResizeHandler()}
                            sx={{
                              position: 'absolute',
                              top: 0,
                              right: -5,
                              width: 10,
                              height: '100%',
                              cursor: 'col-resize',
                              zIndex: 6,
                              '&::after': {
                                content: '""',
                                position: 'absolute',
                                top: 8,
                                bottom: 8,
                                left: '50%',
                                width: 2,
                                borderRadius: 999,
                                transform: 'translateX(-50%)',
                                backgroundColor: column.getIsResizing() ? 'primary.main' : 'transparent',
                              },
                              '&:hover::after': {
                                backgroundColor: 'primary.main',
                              },
                            }}
                          />
                        ) : null}
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))}
              <TableRow>
                {table.getVisibleLeafColumns().map((column) => {
                  const { columnDef } = column;
                  const { meta } = columnDef;
                  const filterVariant = meta?.filterVariant ?? 'none';
                  const filterValue = column.getFilterValue();
                  const currentValue = typeof filterValue === 'string' ? filterValue : '';

                  return (
                    <TableCell
                      key={`filter-${column.id}`}
                      align={meta?.align}
                      sx={{
                        position: 'sticky',
                        top: headerHeight,
                        zIndex: 3,
                        backgroundColor: (theme) => alpha(theme.palette.background.paper, 0.98),
                        px: 1,
                        py: 0.9,
                      }}
                    >
                      {filterVariant === 'text' ? (
                        <TextField
                          size="small"
                          fullWidth
                          value={currentValue}
                          placeholder={meta?.filterPlaceholder ?? 'Filter'}
                          onChange={(event) => column.setFilterValue(event.target.value)}
                          slotProps={{ input: { sx: { fontSize: '0.8rem' } } }}
                        />
                      ) : null}
                      {filterVariant === 'select' ? (
                        <TextField
                          select
                          size="small"
                          fullWidth
                          value={currentValue}
                          onChange={(event) => column.setFilterValue(event.target.value)}
                          slotProps={{ input: { sx: { fontSize: '0.8rem' } } }}
                        >
                          <MenuItem value="">All</MenuItem>
                          {meta?.filterOptions?.map((option: { label: string; value: string }) => (
                            <MenuItem key={`${column.id}-${option.value}`} value={option.value}>
                              {option.label}
                            </MenuItem>
                          ))}
                        </TextField>
                      ) : null}
                    </TableCell>
                  );
                })}
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={table.getVisibleLeafColumns().length}>
                    <Typography variant="body2" color="text.secondary">
                      {loadingLabel}
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : null}
              {!loading && tableRows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={table.getVisibleLeafColumns().length} sx={{ p: 0 }}>
                    <EmptyState
                      title={emptyTitle}
                      description={emptyDescription}
                      tone="info"
                      sx={{ border: 0, borderRadius: 0 }}
                    />
                  </TableCell>
                </TableRow>
              ) : null}
              {!loading
                ? tableRows.map((row) => {
                    const rowSelected = isRowSelected ? isRowSelected(row.original) : false;

                    return (
                      <TableRow
                        key={row.id}
                        hover
                        selected={rowSelected}
                        onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                        sx={{
                          cursor: onRowClick ? 'pointer' : 'default',
                          '& td': {
                            px: 1.25,
                            py: 1.1,
                          },
                        }}
                      >
                        {row.getVisibleCells().map((cell) => {
                          const { column } = cell;
                          const { columnDef } = column;
                          const { meta } = columnDef;
                          const resolvedAlign: 'left' | 'center' | 'right' | undefined =
                            meta?.align === 'left' || meta?.align === 'center' || meta?.align === 'right'
                              ? meta.align
                              : undefined;
                          return (
                            <TableCell
                              key={cell.id}
                              align={alignText(resolvedAlign)}
                              sx={{
                                minWidth: column.getSize(),
                                width: column.getSize(),
                              }}
                            >
                              {flexRender(columnDef.cell, cell.getContext())}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                    );
                  })
                : null}
            </TableBody>
          </Table>
        </TableContainer>
      ) : null}

      {!error && pageCount > 1 ? (
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={1.5}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'center' }}
        >
          <Typography variant="body2" color="text.secondary">
            Page {state.pagination.pageIndex + 1} of {pageCount} | {resolvedTotalRows} total rows
          </Typography>
          <Pagination
            color="primary"
            count={pageCount}
            page={state.pagination.pageIndex + 1}
            onChange={(_event, nextPage) =>
              stateControls.onPaginationChange((current) => ({
                ...current,
                pageIndex: nextPage - 1,
              }))
            }
          />
        </Stack>
      ) : null}
    </SurfacePanel>
  );
}
