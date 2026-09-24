import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { rolePath } from '../lib/roles'
import type { Role } from '../types/auth'
import { useAuth } from './AuthContext'
import { FullPageStatus } from '../components/FullPageStatus'

export function ProtectedRoute({ allowedRole }: { allowedRole?: Role }) {
  const { user, status, error, retry } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <FullPageStatus kind="loading" />
  }

  if (status === 'error') {
    return <FullPageStatus kind="error" message={error ?? undefined} onRetry={() => void retry()} />
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (allowedRole && user.role !== allowedRole) {
    return <Navigate to={rolePath(user.role)} replace />
  }

  return <Outlet />
}

