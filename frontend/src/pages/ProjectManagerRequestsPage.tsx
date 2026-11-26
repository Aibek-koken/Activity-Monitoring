import { useEffect, useState } from 'react'
import { ClipboardCheck } from 'lucide-react'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { editRequestApi, ApiError } from '../lib/api'
import { formatDate } from '../lib/activity-format'
import type { WorkRecordEditRequest } from '../types/edit-request'

export function ProjectManagerRequestsPage() {
  const [requests, setRequests] = useState<WorkRecordEditRequest[]>([])
  const [comments, setComments] = useState<Record<number, string>>({})
  const [saving, setSaving] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void editRequestApi.managerList().then((data) => setRequests(data.requests))
      .catch(() => setError('Edit requests could not be loaded.'))
      .finally(() => setLoading(false))
  }, [])

  const decide = async (id: number, approved: boolean) => {
    setSaving(id)
    setError(null)
    try {
      const updated = await editRequestApi.decide(id, approved, comments[id] ?? '')
      setRequests((current) => current.map((item) => item.id === id ? updated : item))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The decision could not be saved.')
    } finally { setSaving(null) }
  }

  return <WorkspaceLayout role="PROJECT_MANAGER" title="Edit requests" navItems={[{
    to: '/project-manager', label: 'Edit requests', icon: <ClipboardCheck size={18} />, end: true,
  }]}>
    <div className="workspace-content manager-requests" id="edit-requests">
      <header><h1>Daily record edit requests</h1><p>Review the reason before allowing a one-time change.</p></header>
      {loading && <p role="status">Loading edit requests…</p>}
      {error && <p className="form-alert" role="alert">{error}</p>}
      {!loading && requests.length === 0 && !error && <div className="empty-state"><h2>No requests yet</h2><p>Translator requests will appear here.</p></div>}
      <div className="manager-request-list">{requests.map((item) => <article className="manager-request" key={item.id}>
        <div><strong>{item.activityNumber} · {item.projectName}</strong><p>{item.translatorName} asks to edit the record for {formatDate(item.recordDate)}.</p><blockquote>{item.reason}</blockquote>
          <small>Status: {item.status.toLowerCase()}{item.decisionComment ? ` · ${item.decisionComment}` : ''}</small></div>
        {item.status === 'PENDING' && <div className="manager-request__actions">
          <label htmlFor={`decision-${item.id}`}>Comment (optional)</label>
          <textarea id={`decision-${item.id}`} maxLength={1000} onChange={(event) => setComments((current) => ({ ...current, [item.id]: event.target.value }))} value={comments[item.id] ?? ''} />
          <div><button className="button button--secondary" disabled={saving === item.id} onClick={() => void decide(item.id, false)} type="button">Decline</button>
            <button className="button button--primary" disabled={saving === item.id} onClick={() => void decide(item.id, true)} type="button">Approve one edit</button></div>
        </div>}
      </article>)}</div>
    </div>
  </WorkspaceLayout>
}
