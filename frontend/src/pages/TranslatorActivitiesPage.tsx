import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { AlertCircle, ClipboardList, RefreshCw, Search, SlidersHorizontal } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { activityStatusLabels, formatDate, formatVolume, statusClassName } from '../lib/activity-format'
import { translatorActivityApi } from '../lib/api'
import type { ActivityStatus, ActivitySummary } from '../types/activity'

function dateParam(value: string | null): string {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))
    ? value
    : ''
}

export function TranslatorActivitiesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [activities, setActivities] = useState<ActivitySummary[]>([])
  const query = searchParams.get('q') ?? ''
  const assignmentParam = searchParams.get('assignment')
  const assignment = assignmentParam === 'responsible' || assignmentParam === 'assisting'
    ? assignmentParam
    : 'all'
  const statusParam = searchParams.get('status')
  const status = Object.keys(activityStatusLabels).includes(statusParam ?? '')
    ? statusParam as ActivityStatus
    : null
  const assignedFrom = dateParam(searchParams.get('from'))
  const assignedTo = dateParam(searchParams.get('to'))
  const hasAdvancedFilters = Boolean(status || assignedFrom || assignedTo)
  const listSearch = new URLSearchParams()
  if (query.trim()) listSearch.set('q', query.trim())
  if (assignment !== 'all') listSearch.set('assignment', assignment)
  if (status) listSearch.set('status', status)
  if (assignedFrom) listSearch.set('from', assignedFrom)
  if (assignedTo) listSearch.set('to', assignedTo)
  const detailSearch = listSearch.toString()
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isFilterOpen, setIsFilterOpen] = useState(false)
  const [draftStatus, setDraftStatus] = useState<ActivityStatus | ''>('')
  const [draftFrom, setDraftFrom] = useState('')
  const [draftTo, setDraftTo] = useState('')
  const filterToolbarRef = useRef<HTMLElement>(null)
  const filterButtonRef = useRef<HTMLButtonElement>(null)
  const isDraftRangeInvalid = Boolean(draftFrom && draftTo && draftFrom > draftTo)

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

  useEffect(() => {
    if (!isFilterOpen) return
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!filterToolbarRef.current?.contains(event.target as Node)) setIsFilterOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsFilterOpen(false)
        filterButtonRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isFilterOpen])

  const visibleActivities = useMemo(() => activities.filter((activity) => (
    (assignment === 'all' || activity.responsible === (assignment === 'responsible'))
    && (!status || activity.status === status)
    && (!assignedFrom || activity.assignedDate >= assignedFrom)
    && (!assignedTo || activity.assignedDate <= assignedTo)
  )), [activities, assignment, status, assignedFrom, assignedTo])

  const setQuery = (value: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('q', value)
      else next.delete('q')
      return next
    }, { replace: true })
  }

  const setAssignment = (value: 'all' | 'responsible' | 'assisting') => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (value === 'all') next.delete('assignment')
      else next.set('assignment', value)
      return next
    })
  }

  const toggleAdvancedFilters = () => {
    if (!isFilterOpen) {
      setDraftStatus(status ?? '')
      setDraftFrom(assignedFrom)
      setDraftTo(assignedTo)
    }
    setIsFilterOpen((open) => !open)
  }

  const applyAdvancedFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isDraftRangeInvalid) return
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      for (const key of ['status', 'from', 'to']) next.delete(key)
      if (draftStatus) next.set('status', draftStatus)
      if (draftFrom) next.set('from', draftFrom)
      if (draftTo) next.set('to', draftTo)
      return next
    })
    setIsFilterOpen(false)
    filterButtonRef.current?.focus()
  }

  const clearAdvancedFilters = () => {
    setDraftStatus('')
    setDraftFrom('')
    setDraftTo('')
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      for (const key of ['status', 'from', 'to']) next.delete(key)
      return next
    })
    setIsFilterOpen(false)
    filterButtonRef.current?.focus()
  }

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
        <Breadcrumbs items={[{ label: 'Activities' }]} />
        <header className="translator-hero">
          <h1>Assigned activities</h1>
        </header>

        <section className="activity-toolbar" aria-label="Filter assigned activities" ref={filterToolbarRef}>
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
          <div className="activity-toolbar__controls">
            <div aria-label="Filter by assignment" className="assignment-filter" role="group">
              {([
                ['all', 'All'],
                ['responsible', 'Responsible'],
                ['assisting', 'Assisting'],
              ] as const).map(([value, label]) => (
                <button
                  aria-pressed={assignment === value}
                  className="assignment-filter__option"
                  key={value}
                  onClick={() => setAssignment(value)}
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
            <button
              aria-controls="activity-more-filters"
              aria-expanded={isFilterOpen}
              aria-label={hasAdvancedFilters ? 'More filters (active)' : 'More filters'}
              className={`button activity-filter-button${hasAdvancedFilters ? ' activity-filter-button--active' : ''}`}
              onClick={toggleAdvancedFilters}
              ref={filterButtonRef}
              title="More filters"
              type="button"
            >
              <SlidersHorizontal aria-hidden="true" size={18} />
            </button>
          </div>
          <form className="activity-filter-popover" hidden={!isFilterOpen} id="activity-more-filters" onSubmit={applyAdvancedFilters}>
              <strong>More filters</strong>
              <div className="field-group">
                <label htmlFor="activity-status-filter">Status</label>
                <select id="activity-status-filter" onChange={(event) => setDraftStatus(event.target.value as ActivityStatus | '')} value={draftStatus}>
                  <option value="">Any status</option>
                  {(Object.entries(activityStatusLabels) as [ActivityStatus, string][]).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </div>
              <div className="activity-filter-popover__dates">
                <div className="field-group">
                  <label htmlFor="activity-assigned-from">Assigned from</label>
                  <input aria-invalid={isDraftRangeInvalid} id="activity-assigned-from" onChange={(event) => setDraftFrom(event.target.value)} type="date" value={draftFrom} />
                </div>
                <div className="field-group">
                  <label htmlFor="activity-assigned-to">Assigned to</label>
                  <input aria-invalid={isDraftRangeInvalid} id="activity-assigned-to" onChange={(event) => setDraftTo(event.target.value)} type="date" value={draftTo} />
                </div>
              </div>
              {isDraftRangeInvalid && <p className="field-error" role="alert">Start date must be on or before end date.</p>}
              <div className="activity-filter-popover__actions">
                <button className="button button--ghost button--compact" onClick={clearAdvancedFilters} type="button">Clear</button>
                <button className="button button--primary button--compact" disabled={isDraftRangeInvalid} type="submit">Apply filters</button>
              </div>
          </form>
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

        {!isLoading && !error && visibleActivities.length === 0 && (
          <section className="empty-state">
            <h2>{query.trim() || hasAdvancedFilters ? 'No matching activities' : assignment !== 'all' ? 'No activities in this role' : 'No assigned activities'}</h2>
            <p>{query.trim()
              ? assignment !== 'all' || hasAdvancedFilters ? 'Try a different search or filter.' : 'Try a different activity number or name.'
              : hasAdvancedFilters ? 'Try a different status or assigned-date range.'
              : assignment !== 'all' ? 'Choose another assignment filter to see your activities.' : 'Assigned translation work will appear here.'}</p>
          </section>
        )}

        {!isLoading && !error && visibleActivities.length > 0 && (
          <section className="activity-list" aria-label="Assigned activities">
            {visibleActivities.map((activity) => (
              <Link className="activity-row" key={activity.id} to={`/translator/activities/${activity.id}${detailSearch ? `?${detailSearch}` : ''}`}>
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
