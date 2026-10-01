import { useCallback, useEffect, useState } from 'react'
import {
  AlertCircle,
  LayoutDashboard,
  RefreshCw,
  UserRound,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { workspaceApi } from '../lib/api'
import { roleLabels, rolePath } from '../lib/roles'
import type { Role, WorkspaceData } from '../types/auth'

interface WorkspaceConfig {
  description: string
  scope: string
}

const workspaceConfig: Record<Role, WorkspaceConfig> = {
  TRANSLATOR: {
    description: 'You are signed in with translator permissions.',
    scope: 'Assigned translation activities',
  },
  CHIEF_EDITOR: {
    description: 'You are signed in with chief editor permissions.',
    scope: 'Assigned project reviews',
  },
  PROJECT_MANAGER: {
    description: 'You are signed in with project manager permissions.',
    scope: 'Projects and team activity',
  },
}

export function WorkspacePage({ role }: { role: Role }) {
  const { user } = useAuth()
  const [data, setData] = useState<WorkspaceData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const config = workspaceConfig[role]
  const firstName = user?.fullName.split(' ')[0] ?? 'there'

  const loadWorkspace = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setData(await workspaceApi.load(role))
    } catch {
      setError('We could not load this workspace. Check the API connection and try again.')
    } finally {
      setIsLoading(false)
    }
  }, [role])

  useEffect(() => {
    void loadWorkspace()
  }, [loadWorkspace])

  return (
    <WorkspaceLayout
      navItems={[{
        end: true,
        icon: <LayoutDashboard size={18} aria-hidden="true" />,
        label: 'Overview',
        to: rolePath(role),
      }]}
      role={role}
      title="Overview"
    >
      <div className="workspace-content">
        <section className="workspace-hero">
          <div>
            <h1>Welcome, {firstName}</h1>
            <p>{config.description}</p>
          </div>
        </section>

        {isLoading && (
          <section className="workspace-grid" aria-busy="true" aria-label="Loading workspace data">
            {[0, 1, 2].map((item) => (
              <div className="feature-card feature-card--skeleton" key={item}>
                <span className="skeleton skeleton--badge" />
                <span className="skeleton skeleton--title" />
                <span className="skeleton skeleton--body" />
              </div>
            ))}
          </section>
        )}

        {!isLoading && error && (
          <section className="inline-error" role="alert">
            <AlertCircle size={22} aria-hidden="true" />
            <div>
              <h2>Workspace unavailable</h2>
              <p>{error}</p>
            </div>
            <button className="button button--secondary" type="button" onClick={() => void loadWorkspace()}>
              <RefreshCw size={17} aria-hidden="true" />
              Retry
            </button>
          </section>
        )}

        {!isLoading && data && (
          <section className="profile-card" aria-labelledby="profile-title">
            <div className="profile-card__heading">
              <span className="profile-card__icon" aria-hidden="true"><UserRound size={20} /></span>
              <div>
                <h2 id="profile-title">Your workspace</h2>
                <p>Account and access details</p>
              </div>
            </div>
            <dl className="profile-details">
              <div>
                <dt>Name</dt>
                <dd>{user?.fullName}</dd>
              </div>
              <div>
                <dt>Role</dt>
                <dd>{roleLabels[role]}</dd>
              </div>
              <div>
                <dt>Access</dt>
                <dd>{config.scope}</dd>
              </div>
            </dl>
          </section>
        )}
      </div>
    </WorkspaceLayout>
  )
}
