import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, ArrowLeft, ClipboardList, RefreshCw } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { activityStatusLabels, formatDate, formatPages, statusClassName } from '../lib/activity-format'
import { translatorActivityApi } from '../lib/api'
import type { ActivityDetail } from '../types/activity'

export function TranslatorActivityDetailPage() {
  const { activityId } = useParams()
  const [activity, setActivity] = useState<ActivityDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const loadActivity = useCallback(async () => {
    const parsedId = Number(activityId)
    if (!Number.isInteger(parsedId) || parsedId <= 0) {
      setActivity(null)
      setError('Activity not found or not assigned to you.')
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setError(null)
    try {
      setActivity(await translatorActivityApi.get(parsedId))
    } catch {
      setActivity(null)
      setError('Activity not found or not assigned to you.')
    } finally {
      setIsLoading(false)
    }
  }, [activityId])

  useEffect(() => {
    void loadActivity()
  }, [loadActivity])

  return (
    <WorkspaceLayout
      navItems={[{
        icon: <ClipboardList size={18} aria-hidden="true" />,
        label: 'Activities',
        to: '/translator',
      }]}
      role="TRANSLATOR"
      title="Activity details"
    >
      <div className="workspace-content translator-workspace">
        <Link className="back-link" to="/translator">
          <ArrowLeft size={17} aria-hidden="true" />
          Activities
        </Link>

        {isLoading && (
          <section className="activity-detail activity-detail--loading" aria-busy="true">
            <span className="skeleton skeleton--title" />
            <span className="skeleton skeleton--body" />
            <span className="skeleton skeleton--body" />
          </section>
        )}

        {!isLoading && error && (
          <section className="inline-error" role="alert">
            <AlertCircle size={22} aria-hidden="true" />
            <div>
              <h2>Activity unavailable</h2>
              <p>{error}</p>
            </div>
            <button className="button button--secondary" type="button" onClick={() => void loadActivity()}>
              <RefreshCw size={17} aria-hidden="true" />
              Retry
            </button>
          </section>
        )}

        {!isLoading && activity && (
          <>
            <header className="activity-detail">
              <div>
                <span className={statusClassName(activity.status)}>
                  {activityStatusLabels[activity.status]}
                </span>
                <h1>{activity.activityName}</h1>
                <p>{activity.projectName}</p>
              </div>
              <div className="activity-detail__number">
                <span>Number</span>
                <strong>{activity.activityNumber}</strong>
              </div>
            </header>

            <section className="detail-grid" aria-label="Activity progress">
              <div>
                <dt>Translated</dt>
                <dd>{formatPages(activity.progress.totalTranslatedPages)}</dd>
              </div>
              <div>
                <dt>Daily records</dt>
                <dd>{activity.progress.recordCount}</dd>
              </div>
              <div>
                <dt>Last record</dt>
                <dd>{formatDate(activity.progress.lastRecordDate)}</dd>
              </div>
              <div>
                <dt>Assigned</dt>
                <dd>{formatDate(activity.assignedDate)}</dd>
              </div>
            </section>

            <section className="record-section" aria-labelledby="history-title">
              <div className="section-heading">
                <h2 id="history-title">Daily history</h2>
                <p>{activity.responsible ? 'Responsible translator' : 'Assisting translator'}</p>
              </div>

              {activity.workRecords.length === 0 ? (
                <div className="empty-state empty-state--compact">
                  <h3>No daily records yet</h3>
                  <p>Recorded translated pages will appear here.</p>
                </div>
              ) : (
                <div className="record-table-wrapper">
                  <table className="record-table">
                    <thead>
                      <tr>
                        <th scope="col">Date</th>
                        <th scope="col">Translated pages</th>
                        <th scope="col">Work hours</th>
                      </tr>
                    </thead>
                    <tbody>
                      {activity.workRecords.map((record) => (
                        <tr key={record.id}>
                          <td>{formatDate(record.recordDate)}</td>
                          <td>{formatPages(record.translatedVolumePages)}</td>
                          <td>{record.workHours === null ? 'Not entered' : record.workHours.toFixed(2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </WorkspaceLayout>
  )
}
