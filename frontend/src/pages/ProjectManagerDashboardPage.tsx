import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, ClipboardCheck, LayoutDashboard, RefreshCw, UsersRound } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { ApiError, projectManagerApi, type ManagerAssignment, type ManagerTranslator } from '../lib/api'

function replacementError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 400 || error.status === 409) {
      return 'That translator is no longer available. Refresh the list and choose someone else.'
    }
    if (error.status === 404) return 'This activity is no longer available in your projects. Refresh the list.'
    if (error.status === 401) return 'Your session has expired. Sign in again to replace a translator.'
    if (error.status === 403) return 'You do not have access to replace translators in this project. Contact your administrator.'
  }
  return 'We could not save the change. Check your connection and try again.'
}

function dashboardError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) return 'Your session has expired. Sign in again to view staffing.'
  if (error instanceof ApiError && error.status === 403) return 'Your account does not have access to this project-manager workspace.'
  return 'We could not load staffing data. Check your connection and try again.'
}

export function ProjectManagerDashboardPage() {
  const [translators, setTranslators] = useState<ManagerTranslator[]>([])
  const [assignments, setAssignments] = useState<ManagerAssignment[]>([])
  const [selectedProject, setSelectedProject] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [sessionExpired, setSessionExpired] = useState(false)
  const [hasLoaded, setHasLoaded] = useState(false)
  const [message, setMessage] = useState('')
  const [replacement, setReplacement] = useState<Record<number, number>>({})
  const [saving, setSaving] = useState<number | null>(null)
  const { retry: refreshSession } = useAuth()

  const load = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    setError('')
    setSessionExpired(false)
    try {
      const data = await projectManagerApi.dashboard()
      setTranslators(data.translators)
      setAssignments(data.assignments)
      setHasLoaded(true)
      setSelectedProject((current) => current || (data.assignments.some((a) => a.projectName === 'Central Asia Legal Portal')
        ? 'Central Asia Legal Portal'
        : data.assignments[0]?.projectName ?? ''))
    } catch (caught) {
      setError(dashboardError(caught))
      setSessionExpired(caught instanceof ApiError && caught.status === 401)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const replace = async (assignment: ManagerAssignment) => {
    const translatorId = replacement[assignment.activityId]
    if (!translatorId) return
    setSaving(assignment.activityId)
    setMessage('')
    setError('')
    setSessionExpired(false)
    try {
      await projectManagerApi.replaceTranslator(assignment.activityId, translatorId)
      setMessage(`Responsible translator updated for ${assignment.activityNumber}. Existing work records were kept.`)
      setReplacement((current) => {
        const next = { ...current }
        delete next[assignment.activityId]
        return next
      })
      await load(false)
    } catch (caught) {
      setError(replacementError(caught))
      setSessionExpired(caught instanceof ApiError && caught.status === 401)
    } finally {
      setSaving(null)
    }
  }

  const projects = [...new Set(assignments.map((assignment) => assignment.projectName))]
  const visibleAssignments = assignments.filter((assignment) => assignment.projectName === selectedProject)
  const replacementOptions = (assignment: ManagerAssignment) => {
    const responsible = assignment.translators.find((translator) => translator.responsible)
    const supportingIds = new Set(assignment.translators
      .filter((translator) => !translator.responsible)
      .map((translator) => translator.translatorId))
    return translators.filter((translator) => translator.id !== responsible?.translatorId
      && (translator.available || supportingIds.has(translator.id)))
  }
  const handleErrorAction = () => {
    if (sessionExpired) {
      void refreshSession().then(() => load())
      return
    }
    void load()
  }

  return (
    <WorkspaceLayout
      role="PROJECT_MANAGER"
      title="Translator staffing"
      navItems={[
        { end: true, icon: <LayoutDashboard size={18} aria-hidden="true" />, label: 'Staffing', to: '/project-manager' },
        { icon: <ClipboardCheck size={18} aria-hidden="true" />, label: 'Edit requests', to: '/project-manager/requests' },
      ]}
    >
      <div className="workspace-content">
        <section className="workspace-hero">
          <div><h1>Translator staffing</h1><p>Review current workload and find a replacement when an activity needs one.</p></div>
        </section>
        {error && <section className="inline-error" role="alert"><AlertCircle size={20} aria-hidden="true" /><p>{error}</p><button className="button button--secondary" type="button" onClick={handleErrorAction}>{sessionExpired ? 'Sign in again' : <><RefreshCw size={16} aria-hidden="true" /> Retry</>}</button></section>}
        {message && <p className="manager-message" role="status">{message}</p>}
        {!hasLoaded && loading && <section className="feature-card" aria-busy="true">Loading staffing data…</section>}
        {hasLoaded && <>
          <section className="profile-card" aria-labelledby="availability-title">
            <div className="profile-card__heading"><span className="profile-card__icon" aria-hidden="true"><UsersRound size={20} /></span><div><h2 id="availability-title">Translator availability</h2><p><strong>Available</strong> means no open activities in your projects. Finished, in-review, corrected, and completed activities do not count. This estimate does not include schedules or time off.</p></div></div>
            {translators.length === 0 ? <p className="manager-empty">No active translator accounts were found. Ask an administrator to add translators.</p> : <div className="manager-people">
              {translators.map((translator) => <article className="manager-person" key={translator.id}>
                <span className="user-chip__avatar" aria-hidden="true">{translator.initials}</span>
                <strong>{translator.name}</strong>
                <span className={`manager-status${translator.available ? ' manager-status--available' : ''}`}>{translator.available ? 'Available' : 'Busy'}</span>
                <small>{translator.openActivities} open {translator.openActivities === 1 ? 'activity' : 'activities'} in your projects</small>
              </article>)}
            </div>}
          </section>
          <section className="profile-card manager-workload" aria-labelledby="workload-title">
            <div className="profile-card__heading manager-workload__heading"><div><h2 id="workload-title">Current workload</h2><p>Each activity appears once. Supporting translators are shown with the responsible translator.</p></div><label>Project<select aria-label="Filter workload by project" disabled={projects.length === 0} value={selectedProject} onChange={(event) => setSelectedProject(event.target.value)}>{projects.length === 0 ? <option value="">No assigned projects</option> : projects.map((project) => <option key={project} value={project}>{project}</option>)}</select></label></div>
            {visibleAssignments.length === 0 ? <p className="manager-empty">No translator assignments in this project yet.</p> : <div className="record-table-wrapper"><table className="record-table manager-workload-table"><thead><tr><th>Activity</th><th>Project</th><th>Status</th><th>Assigned translators</th><th>Replace responsible</th></tr></thead><tbody>
              {visibleAssignments.map((assignment) => {
                const candidates = replacementOptions(assignment)
                const responsible = assignment.translators.find((translator) => translator.responsible)
                return <tr key={assignment.activityId}>
                  <td><strong>{assignment.activityNumber}</strong><br />{assignment.activityName}</td>
                  <td>{assignment.projectName}</td>
                  <td>{assignment.status.replaceAll('_', ' ').toLowerCase()}</td>
                  <td><div className="manager-assigned-translators">{assignment.translators.map((translator) => <span key={translator.translatorId}>{translator.translatorName}<small>{translator.responsible ? 'Responsible' : 'Supporting'}</small></span>)}</div></td>
                  <td>{responsible ? <div className="manager-replace"><select aria-label={`Replacement for ${assignment.activityNumber}`} aria-describedby={`replacement-help-${assignment.activityId}`} value={replacement[assignment.activityId] ?? ''} disabled={saving === assignment.activityId} onChange={(event) => setReplacement((current) => ({ ...current, [assignment.activityId]: Number(event.target.value) }))}><option value="">Choose a translator</option>{candidates.map((person) => <option key={person.id} value={person.id}>{person.name}{person.available ? '' : ' · supporting'}</option>)}</select><button className="button button--secondary button--compact" disabled={!replacement[assignment.activityId] || saving === assignment.activityId} onClick={() => void replace(assignment)} type="button">{saving === assignment.activityId ? 'Saving…' : 'Replace'}</button>{candidates.length === 0 && <small className="manager-no-options">No available or supporting translator can replace this person. The current assignment stays active.</small>}<small id={`replacement-help-${assignment.activityId}`} className="manager-replace__help">Choose someone available, or promote a supporting translator on this activity.</small></div> : <span className="manager-supporting">No responsible translator assigned</span>}</td>
                </tr>
              })}
            </tbody></table></div>}
          </section>
        </>}
      </div>
    </WorkspaceLayout>
  )
}
