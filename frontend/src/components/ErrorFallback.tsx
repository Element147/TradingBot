import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Stack,
  Typography,
} from '@mui/material';
import type { ErrorInfo } from 'react';

interface ErrorFallbackProps {
  error: Error | null;
  errorInfo: ErrorInfo | null;
  onReset: () => void;
}

const ErrorFallback: React.FC<ErrorFallbackProps> = ({
  error,
  errorInfo,
  onReset,
}) => {
  const isDevelopment = import.meta.env.DEV;

  const handleReload = (): void => {
    onReset();
    setTimeout(() => {
      window.location.reload();
    }, 100);
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        bgcolor: 'background.default',
        display: 'flex',
        alignItems: 'center',
        py: { xs: 4, md: 6 },
      }}
    >
      <Container maxWidth="md">
        <Card>
          <CardContent sx={{ p: { xs: 3, md: 4 } }}>
            <Stack spacing={2.5}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label="Safe default preserved" color="success" variant="outlined" />
                <Chip label="Reload to recover" color="warning" variant="outlined" />
              </Stack>

              <Box>
                <ErrorOutlineIcon sx={{ fontSize: 56, color: 'error.main', mb: 1.5 }} />
                <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
                  The workstation hit a rendering error
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  Reload the page, then re-check the affected route before taking any follow-up
                  action. Safety-critical state stays backend owned, so this error does not imply
                  orders or risk settings were silently changed.
                </Typography>
              </Box>

              <Alert severity="warning">
                Re-open the route and confirm environment mode, breaker posture, and recent action
                results before proceeding.
              </Alert>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <Button
                  variant="contained"
                  startIcon={<RefreshIcon />}
                  onClick={handleReload}
                >
                  Reload Page
                </Button>
                <Button variant="outlined" onClick={onReset}>
                  Retry Rendering
                </Button>
              </Stack>

              {isDevelopment && error ? (
                <Box
                  sx={{
                    p: 2,
                    bgcolor: 'background.paper',
                    borderRadius: 4,
                    border: '1px solid',
                    borderColor: 'divider',
                    overflow: 'auto',
                  }}
                >
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    Error details
                  </Typography>

                  <Typography
                    variant="body2"
                    component="pre"
                    sx={{
                      fontFamily: 'Consolas, "Courier New", monospace',
                      fontSize: '0.875rem',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      color: 'error.main',
                    }}
                  >
                    {error.toString()}
                  </Typography>

                  {error.stack ? (
                    <Typography
                      variant="body2"
                      component="pre"
                      sx={{
                        fontFamily: 'Consolas, "Courier New", monospace',
                        fontSize: '0.75rem',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        mt: 2,
                        color: 'text.secondary',
                      }}
                    >
                      {error.stack}
                    </Typography>
                  ) : null}

                  {errorInfo?.componentStack ? (
                    <Typography
                      variant="body2"
                      component="pre"
                      sx={{
                        fontFamily: 'Consolas, "Courier New", monospace',
                        fontSize: '0.75rem',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        mt: 2,
                        color: 'text.secondary',
                      }}
                    >
                      {errorInfo.componentStack}
                    </Typography>
                  ) : null}
                </Box>
              ) : null}
            </Stack>
          </CardContent>
        </Card>
      </Container>
    </Box>
  );
};

export default ErrorFallback;
