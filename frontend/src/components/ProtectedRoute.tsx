import { CircularProgress, Box } from '@mui/material';
import { type ReactNode, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { useAppSelector, useAppDispatch } from '@/app/hooks';
import {
  checkSessionTimeout,
  hydrateDevBypassSession,
  restoreSession,
} from '@/features/auth/authSlice';
import { DEV_AUTH_BYPASS_ENABLED, DEV_AUTH_BYPASS_USER } from '@/features/auth/devAuth';

interface ProtectedRouteProps {
  children: ReactNode;
  requiredRole?: 'admin' | 'trader';
}

/**
 * ProtectedRoute component that guards routes requiring authentication
 * 
 * Features:
 * - Redirects to login if not authenticated
 * - Implements role-based access control (admin vs trader)
 * - Shows loading state while checking authentication
 * - Automatically restores session from storage
 * - Periodically checks for session timeout
 * 
 * @param children - The protected content to render
 * @param requiredRole - Optional role requirement ('admin' or 'trader')
 */
export default function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const dispatch = useAppDispatch();
  const location = useLocation();
  const { isAuthenticated, user, loading } = useAppSelector((state) => state.auth);
  const [isCheckingAuth, setIsCheckingAuth] = useState(!DEV_AUTH_BYPASS_ENABLED);
  const effectiveUser = DEV_AUTH_BYPASS_ENABLED ? (user ?? DEV_AUTH_BYPASS_USER) : user;
  const hasAccess = DEV_AUTH_BYPASS_ENABLED || isAuthenticated;

  // Restore session on mount
  useEffect(() => {
    if (DEV_AUTH_BYPASS_ENABLED) {
      dispatch(hydrateDevBypassSession());
      return;
    }

    dispatch(restoreSession());
    const timer = setTimeout(() => {
      setIsCheckingAuth(false);
    }, 100);

    return () => clearTimeout(timer);
  }, [dispatch]);

  // Check session timeout periodically
  useEffect(() => {
    if (DEV_AUTH_BYPASS_ENABLED) {
      return;
    }

    const interval = setInterval(() => {
      dispatch(checkSessionTimeout());
    }, 60000); // Check every minute

    return () => clearInterval(interval);
  }, [dispatch]);

  // Show loading state while checking authentication
  if (isCheckingAuth || loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
        data-testid="protected-route-loading"
      >
        <CircularProgress />
      </Box>
    );
  }

  // Check authentication
  if (!hasAccess) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Check role-based access
  if (requiredRole && effectiveUser?.role !== requiredRole) {
    return (
      <Box
        display="flex"
        flexDirection="column"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
        padding={4}
        textAlign="center"
        data-testid="access-denied"
      >
        <h1>Access Denied</h1>
        <p>You do not have permission to access this page.</p>
        <p>Required role: {requiredRole}</p>
        <p>Your role: {effectiveUser?.role}</p>
      </Box>
    );
  }

  return <>{children}</>;
}
