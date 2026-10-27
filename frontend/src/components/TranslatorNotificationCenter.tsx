import { useCallback, useEffect, useRef, useState } from 'react'
import { Bell, CalendarClock, Check, ClipboardPenLine, RefreshCw, X } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { reminderApi } from '../lib/api'
import type { Reminder } from '../types/reminder'

function reminderLink(reminder: Reminder, search: string): string {
  if (reminder.type === 'WEEKLY_ESTIMATE_REMINDER' && reminder.activityId) {
    return `/translator/activities/${reminder.activityId}${search}#weekly-estimate`
  }
  return `/translator${search}#activity-list`
}

function reminderLabel(reminder: Reminder): string {
  return reminder.type === 'DAILY_RECORD_REMINDER' ? 'Daily record' : 'Weekly estimate'
}

export function TranslatorNotificationCenter() {
  const location = useLocation()
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [readingIds, setReadingIds] = useState<number[]>([])
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  const loadReminders = useCallback(async () => {
    try {
      const response = await reminderApi.list()
      setReminders(response.reminders)
      setError(null)
    } catch {
      setError('Notifications could not be loaded.')
    }
  }, [])

  useEffect(() => {
    void loadReminders()
  }, [loadReminders])

  useEffect(() => {
    if (!isOpen) return

    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false)
        triggerRef.current?.focus()
      }
    }

    document.addEventListener('pointerdown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isOpen])

  const markRead = async (reminderId: number) => {
    if (readingIds.includes(reminderId)) return
    setReadingIds((current) => [...current, reminderId])
    setError(null)
    try {
      await reminderApi.markRead(reminderId)
      setReminders((current) => current.filter((reminder) => reminder.id !== reminderId))
    } catch {
      setError('The notification could not be marked as read.')
    } finally {
      setReadingIds((current) => current.filter((id) => id !== reminderId))
    }
  }

  const markAllRead = async () => {
    const ids = reminders.map((reminder) => reminder.id)
    if (ids.length === 0 || readingIds.length > 0) return
    setReadingIds(ids)
    setError(null)
    const results = await Promise.allSettled(ids.map((id) => reminderApi.markRead(id)))
    const failedIds = ids.filter((_, index) => results[index].status === 'rejected')
    setReminders((current) => current.filter((reminder) => failedIds.includes(reminder.id)))
    setReadingIds([])
    if (failedIds.length > 0) setError('Some notifications could not be marked as read.')
  }

  const openReminder = (reminderId: number) => {
    setIsOpen(false)
    void markRead(reminderId)
  }

  return (
    <div className="notification-center" ref={containerRef}>
      <button
        aria-controls="translator-notifications"
        aria-expanded={isOpen}
        aria-label={reminders.length > 0 ? `Notifications, ${reminders.length} unread` : 'Notifications'}
        className="notification-trigger"
        onClick={() => setIsOpen((open) => !open)}
        ref={triggerRef}
        type="button"
      >
        <Bell aria-hidden="true" size={20} />
        {reminders.length > 0 && <span className="notification-trigger__badge">{reminders.length > 99 ? '99+' : reminders.length}</span>}
      </button>

      <section className="notification-popover" hidden={!isOpen} id="translator-notifications" aria-label="Notifications">
        <header className="notification-popover__header">
          <div>
            <h2>Notifications</h2>
            {reminders.length > 0 && <span>{reminders.length} unread</span>}
          </div>
          <div className="notification-popover__actions">
            {reminders.length > 0 && (
              <button className="notification-mark-all" disabled={readingIds.length > 0} onClick={() => void markAllRead()} type="button">
                Mark all read
              </button>
            )}
            <button aria-label="Close notifications" className="notification-close" onClick={() => setIsOpen(false)} type="button">
              <X aria-hidden="true" size={20} />
            </button>
          </div>
        </header>

        {error && (
          <div className="notification-error" role="alert">
            <span>{error}</span>
            <button aria-label="Retry notifications" onClick={() => void loadReminders()} type="button"><RefreshCw aria-hidden="true" size={16} /></button>
          </div>
        )}

        {reminders.length === 0 && !error && (
          <div className="notification-empty">
            <span><Check aria-hidden="true" size={20} /></span>
            <h3>You're all caught up</h3>
            <p>New work reminders will appear here.</p>
          </div>
        )}

        {reminders.length > 0 && (
          <ul className="notification-list">
            {reminders.map((reminder) => {
              const isDaily = reminder.type === 'DAILY_RECORD_REMINDER'
              return (
                <li className={isDaily ? 'notification-item notification-item--daily' : 'notification-item notification-item--weekly'} key={reminder.id}>
                  <span className="notification-item__icon" aria-hidden="true">
                    {isDaily ? <ClipboardPenLine size={20} /> : <CalendarClock size={20} />}
                  </span>
                  <Link className="notification-item__content" onClick={() => openReminder(reminder.id)} to={reminderLink(reminder, location.search)}>
                    <span>{reminderLabel(reminder)}</span>
                    <strong>{reminder.message}</strong>
                    {reminder.activityNumber && <small>{reminder.activityNumber}</small>}
                    <span className="notification-item__action">{isDaily ? 'View activities' : 'Open estimate'} <span aria-hidden="true">›</span></span>
                  </Link>
                  <button
                    aria-label={`Mark ${reminderLabel(reminder).toLowerCase()} notification read`}
                    className="notification-item__read"
                    disabled={readingIds.includes(reminder.id)}
                    onClick={() => void markRead(reminder.id)}
                    title="Mark as read"
                    type="button"
                  >
                    <span />
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
