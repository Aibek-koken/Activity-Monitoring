import { useCallback, useEffect, useMemo, useState } from 'react'
import { AlertCircle, ClipboardList, RefreshCw, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { activityStatusLabels, formatDate, formatVolume, statusClassName } from '../lib/activity-format'
import { translatorActivityApi } from '../lib/api'
import type { ActivitySummary } from '../types/activity'

export function TranslatorActivitiesPage() {
  const { user } = useAuth()
  const [activities, setActivities] = useState<ActivitySummary[]>([])
  const [query, setQuery] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const firstName = user?.fullName.split(' ')[0] ?? 'there'

  const loadActivities = useCallback(async (search: string) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await translatorActivityApi.list(search)
      setActivities(response.activities)
    } catch {
      setError('Assigned activities could not be loaded. Check the API connection and try again.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void loadActivities(query)
    }, 220)
    return () => window.clearTimeout(handle)
  }, [loadActivities, query])

  const metrics = useMemo(() => {
    const totalVolume = activities.reduce((sum, activity) => sum + activity.progress.totalTranslatedVolume, 0)
    return {
      assigned: activities.length,
      inProgress: activities.filter((activity) => activity.status === 'IN_PROGRESS').length,
      totalVolume,
    }
  }, [activities])

  return (
    <WorkspaceLayout
      navItems={[{
        end: true,
        icon: <ClipboardList size={18} aria-hidden="true" />,
        label: 'Activities',
        to: '/translator',
      }]}
      role="TRANSLATOR"
      title="Assigned activities"
    >
      <div className="workspace-content translator-workspace">
        <section className="workspace-hero translator-hero">
          <div>
            <h1>Assigned activities</h1>
            <p>{firstName}, these are the texts currently assigned to you.</p>
          </div>
        </section>

        <dl className="translator-metrics" aria-label="Activity summary">
          <div>
            <dt>Assigned</dt>
            <dd>{metrics.assigned}</dd>
          </div>
          <div>
            <dt>In progress</dt>
            <dd>{metrics.inProgress}</dd>
          </div>
          <div>
            <dt>Translated</dt>
            <dd>{formatVolume(metrics.totalVolume)}</dd>
          </div>
        </dl>

        <section className="activity-toolbar" aria-label="Search assigned activities">
          <label className="search-field" htmlFor="activity-search">
            <Search size={18} aria-hidden="true" />
            <span className="sr-only">Search by activity number or name</span>
            <input
              id="activity-search"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by number or name"
              type="search"
              value={query}
            />
          </label>
        </section>

        {isLoading && (
          <section className="activity-list activity-list--loading" aria-busy="true" aria-label="Loading activities">
            {[0, 1, 2].map((item) => (
              <div className="activity-row activity-row--skeleton" key={item}>
                <span className="skeleton skeleton--title" />
                <span className="skeleton skeleton--body" />
                <span className="skeleton skeleton--badge" />
              </div>
            ))}
          </section>
        )}

        {!isLoading && error && (
          <section className="inline-error" role="alert">
            <AlertCircle size={22} aria-hidden="true" />
            <div>
              <h2>Activities unavailable</h2>
              <p>{error}</p>
            </div>
            <button className="button button--secondary" type="button" onClick={() => void loadActivities(query)}>
              <RefreshCw size={17} aria-hidden="true" />
              Retry
            </button>
          </section>
        )}

        {!isLoading && !error && activities.length === 0 && (
          <section className="empty-state">
            <h2>{query.trim() ? 'No matching activities' : 'No assigned activities'}</h2>
            <p>{query.trim() ? 'Try a different activity number or name.' : 'Assigned translation work will appear here.'}</p>
          </section>
        )}

        {!isLoading && !error && activities.length > 0 && (
          <section className="activity-list" aria-label="Assigned activities">
            {activities.map((activity) => (
              <Link className="activity-row" key={activity.id} to={`/translator/activities/${activity.id}`}>
                <span className="activity-row__main">
                  <strong>{activity.activityNumber}</strong>
                  <span>{activity.activityName}</span>
                  <small>{activity.projectName}</small>
                </span>
                <span className="activity-row__meta">
                  <span className={statusClassName(activity.status)}>
                    {activityStatusLabels[activity.status]}
                  </span>
                  <span>{activity.responsible ? 'Responsible' : 'Assisting'}</span>
                </span>
                <span className="activity-row__progress">
                  <strong>{formatVolume(activity.progress.totalTranslatedVolume)}</strong>
                  <span>{formatDate(activity.progress.lastRecordDate)}</span>
                </span>
              </Link>
            ))}
          </section>
        )}
      </div>
    </WorkspaceLayout>
  )
}
