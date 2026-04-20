import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import DownloadIcon from '@mui/icons-material/Download';
import RestoreFromTrashIcon from '@mui/icons-material/RestoreFromTrash';
import {
  Alert,
  Button,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';

import type {
  BacktestDataset,
  BacktestDatasetRetentionReport,
} from './backtestApi';
import {
  retentionLabel,
} from './backtestPageState';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import {
  InteractiveTable,
  useInteractiveTableState,
} from '@/components/ui/InteractiveTable';
import { NumericText, StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import {
  formatDateTime,
  formatNumber,
} from '@/utils/formatters';

interface BacktestDatasetInventoryPanelProps {
  datasetName: string;
  datasetFile: File | null;
  retentionReport?: BacktestDatasetRetentionReport;
  datasets: BacktestDataset[];
  hasActiveDatasets: boolean;
  isUploading: boolean;
  datasetLifecycleBusy: boolean;
  onDatasetNameChange: (value: string) => void;
  onDatasetFileChange: (file: File | null) => void;
  onUploadDataset: () => void;
  onDownloadDataset: (datasetId: number) => void | Promise<void>;
  onArchiveDataset: (dataset: BacktestDataset) => void | Promise<void>;
  onRestoreDataset: (datasetId: number) => void | Promise<void>;
}

const retentionTone = (
  status: BacktestDataset['retentionStatus']
): 'success' | 'warning' | 'default' => {
  if (status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'ARCHIVED') {
    return 'warning';
  }
  return 'default';
};

export function BacktestDatasetInventoryPanel({
  datasetName,
  datasetFile,
  retentionReport,
  datasets,
  hasActiveDatasets,
  isUploading,
  datasetLifecycleBusy,
  onDatasetNameChange,
  onDatasetFileChange,
  onUploadDataset,
  onDownloadDataset,
  onArchiveDataset,
  onRestoreDataset,
}: BacktestDatasetInventoryPanelProps) {
  const tableStateControls = useInteractiveTableState({
    tableId: 'backtest-dataset-inventory',
    initialPageSize: 10,
  });

  const columns = useMemo<ColumnDef<BacktestDataset>[]>(
    () => [
      {
        accessorKey: 'name',
        header: 'Name',
        size: 220,
        minSize: 180,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Name',
          headerDescription: 'Dataset label and original file name.',
        },
        cell: ({ row }) => (
          <Stack spacing={0.35}>
            <Typography variant="body2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
              {row.original.name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.original.originalFilename}
            </Typography>
            {row.original.archiveReason ? (
              <Typography variant="caption" color="text.secondary">
                {row.original.archiveReason}
              </Typography>
            ) : null}
          </Stack>
        ),
      },
      {
        accessorKey: 'symbolsCsv',
        header: 'Symbols',
        size: 210,
        minSize: 180,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Symbols',
          headerDescription: 'Symbols available for single-symbol or dataset-universe runs.',
        },
      },
      {
        accessorKey: 'rowCount',
        header: 'Rows',
        size: 110,
        minSize: 96,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Total number of historical rows in the uploaded file.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatNumber(row.original.rowCount)}</NumericText>,
      },
      {
        accessorKey: 'retentionStatus',
        header: 'Retention',
        size: 170,
        minSize: 150,
        meta: {
          filterVariant: 'select',
          filterOptions: [
            { label: 'Active', value: 'ACTIVE' },
            { label: 'Duplicate retained', value: 'ACTIVE_DUPLICATE_RETAINED' },
            { label: 'Stale retained', value: 'ACTIVE_STALE_RETAINED' },
            { label: 'Archive candidate duplicate', value: 'ARCHIVE_CANDIDATE_DUPLICATE' },
            { label: 'Archive candidate unused', value: 'ARCHIVE_CANDIDATE_UNUSED' },
            { label: 'Archived', value: 'ARCHIVED' },
          ],
          headerDescription: 'Lifecycle state for active, retained duplicate, or archived datasets.',
        },
        cell: ({ row }) => (
          <StatusPill
            label={retentionLabel(row.original.retentionStatus)}
            tone={retentionTone(row.original.retentionStatus)}
            variant="filled"
          />
        ),
      },
      {
        accessorKey: 'usageCount',
        header: 'Usage',
        size: 102,
        minSize: 90,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'How many backtests reference this dataset.',
        },
        cell: ({ row }) => <NumericText variant="body2">{row.original.usageCount}</NumericText>,
      },
      {
        accessorKey: 'lastUsedAt',
        header: 'Last used',
        size: 170,
        minSize: 148,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Most recent run timestamp using this dataset.',
        },
        cell: ({ row }) => (
          <Typography variant="body2" color="text.secondary">
            {row.original.lastUsedAt ? formatDateTime(row.original.lastUsedAt) : 'Not used yet'}
          </Typography>
        ),
      },
      {
        accessorKey: 'uploadedAt',
        header: 'Uploaded',
        size: 170,
        minSize: 148,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Upload timestamp used for retention review and provenance.',
        },
        cell: ({ row }) => <Typography variant="body2">{formatDateTime(row.original.uploadedAt)}</Typography>,
      },
      {
        id: 'schemaChecksum',
        header: 'Schema / checksum',
        accessorFn: (row) => `${row.schemaVersion} ${row.checksumSha256}`,
        size: 240,
        minSize: 220,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Schema',
          headerDescription: 'Schema version and checksum used for reproducibility.',
        },
        cell: ({ row }) => (
          <Stack spacing={0.35}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {row.original.schemaVersion}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {row.original.checksumSha256.slice(0, 18)}...
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'actions',
        header: 'Actions',
        enableSorting: false,
        enableColumnFilter: false,
        size: 214,
        minSize: 204,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Download, archive, or restore the dataset.',
        },
        cell: ({ row }) => (
          <Stack
            direction={{ xs: 'column', xl: 'row' }}
            spacing={0.75}
            onClick={(event) => event.stopPropagation()}
          >
            <Button
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void onDownloadDataset(row.original.id)}
            >
              Download
            </Button>
            {row.original.archived ? (
              <Button
                size="small"
                startIcon={<RestoreFromTrashIcon />}
                onClick={() => void onRestoreDataset(row.original.id)}
                disabled={datasetLifecycleBusy}
              >
                Restore
              </Button>
            ) : (
              <Button
                size="small"
                color="inherit"
                startIcon={<ArchiveOutlinedIcon />}
                onClick={() => void onArchiveDataset(row.original)}
                disabled={datasetLifecycleBusy}
              >
                Archive
              </Button>
            )}
          </Stack>
        ),
      },
    ],
    [datasetLifecycleBusy, onArchiveDataset, onDownloadDataset, onRestoreDataset]
  );

  return (
    <Stack spacing={2}>
      <SurfacePanel
        title="Dataset lifecycle"
        description="Dataset intake stays separate from the inventory grid so uploads remain approachable while the catalog itself behaves like an operational table."
        actions={
          <StatusPill
            label={hasActiveDatasets ? 'Active datasets ready' : 'No active dataset'}
            tone={hasActiveDatasets ? 'success' : 'warning'}
            variant="filled"
          />
        }
      >
        <Stack spacing={2}>
          <Alert severity="info">
            Start with one clean symbol dataset first, then move to multi-symbol universe research
            once the baseline behavior is understood.
          </Alert>
          {retentionReport ? (
            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
              <StatusPill label={`${retentionReport.activeDatasets} active`} tone="success" variant="filled" />
              <StatusPill label={`${retentionReport.archivedDatasets} archived`} />
              <StatusPill
                label={`${retentionReport.archiveCandidateDatasets} archive candidates`}
                tone={retentionReport.archiveCandidateDatasets > 0 ? 'warning' : 'default'}
              />
              <StatusPill
                label={`${retentionReport.referencedDatasetCount} referenced by runs`}
                tone="info"
              />
            </Stack>
          ) : null}
          <FieldTooltip title="Human-readable dataset label. Clear naming prevents running backtests on the wrong file.">
            <TextField
              label="Dataset name (optional)"
              value={datasetName}
              onChange={(event) => onDatasetNameChange(event.target.value)}
              placeholder="BTC 1h 2025"
              helperText="Optional label to identify symbol, timeframe, and date range."
            />
          </FieldTooltip>
          <FieldTooltip title="CSV upload defines the historical data source. Incorrect format or timeframe invalidates results.">
            <Button variant="outlined" component="label">
              {datasetFile ? `Selected: ${datasetFile.name}` : 'Choose CSV file'}
              <input
                hidden
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => onDatasetFileChange(event.target.files?.[0] ?? null)}
              />
            </Button>
          </FieldTooltip>
          <Button variant="contained" onClick={onUploadDataset} disabled={!datasetFile || isUploading}>
            Upload dataset
          </Button>
          <Typography variant="caption" color="text.secondary">
            CSV format: timestamp,symbol,open,high,low,close,volume
          </Typography>
          {!hasActiveDatasets ? (
            <Alert severity="warning">
              No active datasets are available for new runs. Restore an archived dataset or upload a
              new CSV.
            </Alert>
          ) : null}
        </Stack>
      </SurfacePanel>

      <Divider />

      <InteractiveTable
        title="Dataset inventory"
        description="The catalog now uses the same compact table behavior as run history: sticky headers, quick search, inline filters, and persistent column widths."
        data={datasets}
        columns={columns}
        stateControls={tableStateControls}
        emptyTitle="No datasets recorded yet"
        emptyDescription="Upload a CSV file above to seed the dataset catalog."
        loading={false}
        globalFilterPlaceholder="Name, symbol, schema, or checksum"
        stats={
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <StatusPill
              label={`${datasets.length} datasets`}
              tone={datasets.length > 0 ? 'info' : 'default'}
              variant="filled"
            />
            <StatusPill
              label={`${datasets.filter((dataset) => !dataset.archived).length} active`}
              tone={hasActiveDatasets ? 'success' : 'warning'}
            />
            <StatusPill
              label={`${datasets.filter((dataset) => dataset.archived).length} archived`}
            />
          </Stack>
        }
        getRowId={(row) => String(row.id)}
        maxHeight={680}
      />
    </Stack>
  );
}
