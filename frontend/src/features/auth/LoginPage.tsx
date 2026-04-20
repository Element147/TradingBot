import { Login as LoginIcon } from '@mui/icons-material';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';

import { useLoginMutation } from './authApi';
import { setCredentials } from './authSlice';

import { useAppDispatch } from '@/app/hooks';
import { PageContent, PageIntro } from '@/components/layout/PageContent';
import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { StatusPill, SurfacePanel } from '@/components/ui/Workbench';

const loginSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

const getErrorMessage = (error: unknown): string => {
  if (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    (error as { status?: unknown }).status === 'FETCH_ERROR'
  ) {
    return 'Login failed because the app could not reach the backend. Check that the backend is running and allows requests from this frontend.';
  }

  if (
    typeof error === 'object' &&
    error !== null &&
    'data' in error &&
    typeof (error as { data?: unknown }).data === 'object' &&
    (error as { data?: { message?: unknown } }).data?.message &&
    typeof (error as { data?: { message?: unknown } }).data?.message === 'string'
  ) {
    return (error as { data: { message: string } }).data.message;
  }

  return 'Login failed. Please check your credentials.';
};

export default function LoginPage() {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [login, { isLoading }] = useLoginMutation();

  const [formData, setFormData] = useState({
    username: '',
    password: '',
    rememberMe: false,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrors({});
    setApiError('');

    const result = loginSchema.safeParse(formData);
    if (!result.success) {
      const fieldErrors: Record<string, string> = {};
      if (result.error?.issues) {
        result.error.issues.forEach((err) => {
          if (err.path[0]) {
            fieldErrors[err.path[0].toString()] = err.message;
          }
        });
      }
      setErrors(fieldErrors);
      return;
    }

    try {
      const response = await login(formData).unwrap();

      dispatch(
        setCredentials({
          token: response.token,
          user: response.user,
          refreshToken: formData.rememberMe ? response.refreshToken : undefined,
        })
      );

      void navigate('/dashboard');
    } catch (error: unknown) {
      setApiError(getErrorMessage(error));
    }
  };

  return (
    <Box
      component="main"
      sx={{
        minHeight: '100vh',
        px: { xs: 2, sm: 3 },
        py: { xs: 3, md: 5 },
        background: (theme) =>
          `radial-gradient(circle at top left, ${theme.palette.primary.main}14 0%, transparent 34%),
           linear-gradient(180deg, ${theme.palette.background.default} 0%, ${theme.palette.background.paper} 100%)`,
      }}
    >
      <PageContent maxWidth="forms">
        <PageIntro
          eyebrow="Secure workstation sign-in"
          title="Research Workstation"
          description="Enter the workstation through the same safety-first shell used everywhere else: research first, paper second, and live context only when the product explicitly supports it."
          chips={
            <>
              <StatusPill label="Default posture: test" tone="success" variant="filled" />
              <StatusPill label="Paper remains simulated" tone="info" />
              <StatusPill
                label="Role and audit state stay visible after sign-in"
                tone="warning"
              />
            </>
          }
        />

        <Grid container spacing={2.5} alignItems="stretch">
          <Grid size={{ xs: 12, lg: 5 }}>
            <SurfacePanel
              title="Workstation flow"
              description="Use the platform in the same order the shell is designed around so evidence stays ahead of action."
              tone="info"
              sx={{ height: '100%' }}
            >
              <Stack spacing={1.25}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  1. Review Dashboard
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Start with posture, transport status, and the next safe action.
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  2. Validate in Backtest
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Keep costs, slippage, and dataset provenance explicit before you promote any idea.
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  3. Simulate in Paper
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Orders stay simulated while risk posture, stale state, and audit entries remain visible.
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  4. Monitor in Live
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Live context stays explicitly gated and should never appear by accident.
                </Typography>
              </Stack>
            </SurfacePanel>
          </Grid>

          <Grid size={{ xs: 12, lg: 7 }}>
            <SurfacePanel
              elevated
              title="Sign in"
              description="Use an existing workstation account to load your saved operator role, exchange posture, and route permissions."
              sx={{ height: '100%' }}
            >
              <Stack spacing={2.5}>
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Box
                    sx={{
                      width: 56,
                      height: 56,
                      display: 'grid',
                      placeItems: 'center',
                      border: '1px solid',
                      borderColor: 'divider',
                      backgroundColor: 'action.hover',
                    }}
                  >
                    <LoginIcon sx={{ fontSize: 32, color: 'primary.main' }} />
                  </Box>
                  <Box>
                    <Typography component="h1" variant="h4" fontWeight="bold">
                      AlgoTrading Workstation
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Authentication remains the normal entry path outside of local-only debug bypass mode.
                    </Typography>
                  </Box>
                </Stack>

                <Box component="form" onSubmit={(event) => void handleSubmit(event)} noValidate>
                  {apiError ? (
                    <Alert severity="error" sx={{ mb: 2 }}>
                      {apiError}
                    </Alert>
                  ) : null}

                  <FieldTooltip title="Account username. Wrong value prevents authentication.">
                    <TextField
                      margin="normal"
                      required
                      fullWidth
                      id="username"
                      label="Username"
                      name="username"
                      autoComplete="username"
                      value={formData.username}
                      onChange={(e) =>
                        setFormData((prev) => ({ ...prev, username: e.target.value }))
                      }
                      error={!!errors.username}
                      helperText={
                        errors.username ||
                        'Use the username created by Liquibase seed or your custom user.'
                      }
                      disabled={isLoading}
                    />
                  </FieldTooltip>

                  <FieldTooltip title="Account password. Repeated failures can trigger lockout depending on backend policy.">
                    <TextField
                      margin="normal"
                      required
                      fullWidth
                      name="password"
                      label="Password"
                      type="password"
                      id="password"
                      autoComplete="current-password"
                      value={formData.password}
                      onChange={(e) =>
                        setFormData((prev) => ({ ...prev, password: e.target.value }))
                      }
                      error={!!errors.password}
                      helperText={errors.password || 'Password for selected user account.'}
                      disabled={isLoading}
                    />
                  </FieldTooltip>

                  <FieldTooltip title="Stores refresh token in browser for longer sessions. Use only on trusted machines.">
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={formData.rememberMe}
                          onChange={(e) =>
                            setFormData((prev) => ({
                              ...prev,
                              rememberMe: e.target.checked,
                            }))
                          }
                          color="primary"
                          disabled={isLoading}
                        />
                      }
                      label="Remember me"
                      sx={{ mt: 1 }}
                    />
                  </FieldTooltip>

                  <Button
                    type="submit"
                    fullWidth
                    variant="contained"
                    sx={{ mt: 3, mb: 2, py: 1.5 }}
                    disabled={isLoading}
                    startIcon={
                      isLoading ? <CircularProgress size={20} color="inherit" /> : null
                    }
                  >
                    {isLoading ? 'Signing in...' : 'Sign In'}
                  </Button>
                </Box>
              </Stack>
            </SurfacePanel>
          </Grid>
        </Grid>
      </PageContent>
    </Box>
  );
}
