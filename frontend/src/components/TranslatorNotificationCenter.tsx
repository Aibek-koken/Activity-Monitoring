import { useCallback, useEffect, useRef, useState } from 'react'
import { Bell, ChevronLeft, ChevronRight, Trash2, X } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { reminderApi } from '../lib/api'
import type { Reminder, ReminderType } from '../types/reminder'

const labels: Record<ReminderType, string> = {
  DAILY_RECORD_REMINDER: 'Daily record', WEEKLY_ESTIMATE_REMINDER: 'Weekly estimate',
  WEEKLY_SUBMISSION_REMINDER: 'Weekly version', CORRECTION_REQUESTED: 'Correction requested',
  RECORD_EDIT_REQUESTED: 'Edit request', RECORD_EDIT_APPROVED: 'Edit approved',
  RECORD_EDIT_REJECTED: 'Edit declined',
}

const details: Record<ReminderType, string> = {
  DAILY_RECORD_REMINDER: 'Add the pages and time you worked on today. Your daily record stays in the activity history.',
  WEEKLY_ESTIMATE_REMINDER: 'Open the activity to update the remaining work estimate for this week.',
  WEEKLY_SUBMISSION_REMINDER: 'Open the activity to submit the current weekly version for checking.',
  CORRECTION_REQUESTED: 'The Chief Editor returned a version for correction. Open the activity to see the marked document, feedback, and pages to redo.',
  RECORD_EDIT_REQUESTED: 'A translator has asked to edit a daily record. Open the request to review the reason and approve or decline it.',
  RECORD_EDIT_APPROVED: 'Your Project Manager approved one edit of the daily record. Open the activity and use the approved edit before it expires.',
  RECORD_EDIT_REJECTED: 'Your Project Manager declined the edit request. Open the activity to review its status and request another edit if needed.',
}

function target(item: Reminder, search: string): string {
  if (item.type === 'RECORD_EDIT_REQUESTED') return '/project-manager#edit-requests'
  if (!item.activityId) return `/translator${search}#activity-list`
  const anchor = item.type === 'CORRECTION_REQUESTED' ? 'correction'
    : item.type === 'WEEKLY_ESTIMATE_REMINDER' ? 'weekly-estimate'
      : item.type === 'WEEKLY_SUBMISSION_REMINDER' ? 'weekly-submit' : 'daily-history'
  return `/translator/activities/${item.activityId}${search}#${anchor}`
}

export function TranslatorNotificationCenter() {
  const location = useLocation()
  const [items, setItems] = useState<Reminder[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [unreadCount, setUnreadCount] = useState(0)
  const [isOpen, setIsOpen] = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const selected = items.find((item) => item.id === selectedId)

  const load = useCallback(async (requestedPage: number) => {
    try {
      const data = await reminderApi.list(requestedPage)
      setItems(data.reminders)
      setPage(data.page ?? requestedPage)
      setTotalPages(data.totalPages ?? 1)
      setUnreadCount(data.unreadCount ?? data.reminders.filter((item) => !item.readAt).length)
      setError(null)
    } catch { setError('Notifications could not be loaded.') }
  }, [])
  useEffect(() => { void load(0) }, [load])
  useEffect(() => {
    if (!isOpen) return
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setSelectedId(null); setIsOpen(false); trigger.current?.focus() }
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [isOpen])

  const read = async (id: number) => {
    const item = items.find((entry) => entry.id === id)
    if (!item || item.readAt) return
    try {
      await reminderApi.markRead(id)
      setItems((current) => current.map((entry) => entry.id === id ? { ...entry, readAt: new Date().toISOString() } : entry))
      if (item.actionable !== false) setUnreadCount((count) => Math.max(0, count - 1))
    } catch { setError('The notification could not be marked as read.') }
  }
  const remove = async (id: number) => {
    try {
      await reminderApi.delete(id)
      const item = items.find((entry) => entry.id === id)
      setItems((current) => current.filter((entry) => entry.id !== id))
      if (item && !item.readAt && item.actionable !== false) setUnreadCount((count) => Math.max(0, count - 1))
      setSelectedId(null)
      if (totalPages > 1) await load(page > 0 && items.length === 1 ? page - 1 : page)
    } catch { setError('The notification could not be deleted.') }
  }
  const markAll = async () => {
    try {
      await reminderApi.markAllRead()
      setItems((current) => current.map((item) => ({ ...item, readAt: item.readAt ?? new Date().toISOString() })))
      setUnreadCount(0)
    } catch { setError('Notifications could not be marked as read.') }
  }

  return <div className="notification-center">
    <button aria-controls="translator-notifications" aria-expanded={isOpen}
      aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
      className="notification-trigger" onClick={() => setIsOpen((open) => !open)} ref={trigger} type="button">
      <Bell aria-hidden="true" size={20} />
      {unreadCount > 0 && <span className="notification-trigger__badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
    </button>
    <section aria-label="Notifications" className="notification-popover" hidden={!isOpen} id="translator-notifications">
      <header className="notification-popover__header"><div><h2>Notifications</h2><span>{unreadCount} unread</span></div>
        <div className="notification-popover__actions">
          {items.some((item) => !item.readAt) && <button className="notification-mark-all" onClick={() => void markAll()} type="button">Mark all read</button>}
          <button aria-label="Close notifications" className="notification-close" onClick={() => setIsOpen(false)} type="button"><X size={20} /></button>
        </div>
      </header>
      {error && <p className="notification-error" role="alert">{error} <button onClick={() => void load(page)} type="button">Retry</button></p>}
      {items.length === 0 && !error && <div className="notification-empty"><p>No notifications yet.</p></div>}
      <ul className="notification-list">{items.map((item) => <li className={`notification-item ${item.readAt || item.actionable === false ? 'notification-item--read' : ''}`} key={item.id}>
        <div className="notification-item__content"><span>{labels[item.type]}</span><strong>{item.message}</strong>
          {item.activityNumber && <small>{item.activityNumber}</small>}
          {item.actionable === false && <small>Past reminder</small>}
          <button className="notification-item__action" onClick={() => { setSelectedId(item.id); void read(item.id) }} type="button">Read details ›</button>
        </div>
        <button aria-label={`Delete ${labels[item.type].toLowerCase()} notification`} className="notification-item__delete" onClick={() => void remove(item.id)} type="button"><Trash2 size={16} /></button>
      </li>)}</ul>
      {totalPages > 1 && <nav aria-label="Notification pages" className="notification-pagination">
        <button disabled={page === 0} onClick={() => void load(page - 1)} type="button"><ChevronLeft size={16} /> Previous</button>
        <span>{page + 1} / {totalPages}</span>
        <button disabled={page + 1 >= totalPages} onClick={() => void load(page + 1)} type="button">Next <ChevronRight size={16} /></button>
      </nav>}
    </section>
    {selected && <div className="notification-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedId(null) }}>
      <section aria-labelledby="notification-detail-title" aria-modal="true" className="notification-dialog" role="dialog">
        <header><h2 id="notification-detail-title">{labels[selected.type]}</h2><button aria-label="Close notification detail" onClick={() => setSelectedId(null)} type="button"><X size={20} /></button></header>
        <p>{selected.message}</p>
        <p>{details[selected.type]}</p>
        {selected.activityNumber && <p className="notification-dialog__context">Activity {selected.activityNumber}</p>}
        <p className="notification-dialog__context">Received {new Date(selected.createdAt).toLocaleString()}</p>
        <div className="notification-dialog__actions">
          <button className="button button--ghost" onClick={() => void remove(selected.id)} type="button"><Trash2 size={16} /> Delete</button>
          <Link className="button button--primary" onClick={() => { setSelectedId(null); setIsOpen(false) }} to={target(selected, location.search)}>
            {selected.type === 'RECORD_EDIT_REQUESTED' ? 'Open requests' : 'Open activity'}
          </Link>
        </div>
      </section>
    </div>}
  </div>
}
