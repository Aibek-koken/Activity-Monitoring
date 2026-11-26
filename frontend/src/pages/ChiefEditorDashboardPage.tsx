import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import {
  AlertCircle,
  BriefcaseBusiness,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Clock3,
  FileCheck2,
  Gauge,
  Highlighter,
  RefreshCw,
} from 'lucide-react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { formatCheckingTime, formatDate, formatDateTimeInAlmaty, toNumberOrNull } from '../lib/activity-format'
import { ApiError, chiefEditorApi } from '../lib/api'
import type { AssignedProject, ReviewQueue, ReviewWorkload } from '../types/review'

const reviewNav = [{
  end: false,
  icon: <ClipboardCheck size={18} aria-hidden="true" />,
  label: 'Review queue',
  to: '/chief-editor',
}]

function plural(value: number, singular: string, pluralForm = `${singular}s`) {
  return `${value} ${value === 1 ? singular : pluralForm}`
}

function errorMessage(caught: unknown, fallback: string) {
  return caught instanceof ApiError ? caught.message : fallback
}

export function ChiefEditorDashboardPage() {
  const location = useLocation()
  const decisionNotice = (location.state as { decisionNotice?: string } | null)?.decisionNotice
  const [projects, setProjects] = useState<AssignedProject[]>([])
  const [params, setParams] = useSearchParams()
  const [projectFilter, setProjectFilter] = useState('')
  const selectedProjectId = Number(params.get('project') ?? 0) || null
  const kindFilter = params.get('kind') ?? ''
  const reviewSearch = params.get('q') ?? ''
  const [reviewSearchDraft, setReviewSearchDraft] = useState(reviewSearch)
  const previousSearch = useRef(reviewSearch)
  const [queue, setQueue] = useState<ReviewQueue | null>(null)
  const [workload, setWorkload] = useState<ReviewWorkload | null>(null)
  const [availabilityInput, setAvailabilityInput] = useState('')
  const [page, setPage] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [isSavingAvailability, setIsSavingAvailability] = useState(false)
  const [availabilityMessage, setAvailabilityMessage] = useState<string | null>(null)
  const [availabilityError, setAvailabilityError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const requestSequence = useRef(0)

  const loadDashboard = useCallback(async (requestedPage = 0) => {
    const requestId = ++requestSequence.current
    setIsLoading(true)
    setError(null)
    try {
      const [projectResult, reviewResult, workloadResult, availabilityResult] = await Promise.all([
        chiefEditorApi.projects(),
        selectedProjectId ? chiefEditorApi.reviews(requestedPage, 20, selectedProjectId, kindFilter, reviewSearch) : Promise.resolve(null),
        chiefEditorApi.workload(),
        chiefEditorApi.availability(),
      ])
      if (requestId !== requestSequence.current) return
      setProjects(projectResult.projects)
      setQueue(reviewResult)
      setWorkload(workloadResult)
      setAvailabilityInput(availabilityResult.weeklyHours == null ? '' : String(availabilityResult.weeklyHours))
      setPage(reviewResult?.page ?? 0)
    } catch {
      if (requestId === requestSequence.current) setError('The review queue could not be loaded. Check the API connection and try again.')
    } finally {
      if (requestId === requestSequence.current) setIsLoading(false)
    }
  }, [selectedProjectId, kindFilter, reviewSearch])

  useEffect(() => {
    void loadDashboard(0)
  }, [loadDashboard])

  useEffect(() => {
    setReviewSearchDraft((current) => current === previousSearch.current ? reviewSearch : current)
    previousSearch.current = reviewSearch
  }, [reviewSearch])
  useEffect(() => {
    if (!selectedProjectId || reviewSearchDraft === reviewSearch) return
    const timer = window.setTimeout(() => setParams((current) => {
      const next = new URLSearchParams(current)
      if (reviewSearchDraft) next.set('q', reviewSearchDraft)
      else next.delete('q')
      return next
    }), 300)
    return () => window.clearTimeout(timer)
  }, [reviewSearchDraft, reviewSearch, selectedProjectId, setParams])

  const setFilter = (key: string, value: string) => {
    if (key === 'project') setQueue(null)
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next)
  }

  const saveAvailability = async (event: FormEvent) => {
    event.preventDefault()
    const value = Number(availabilityInput)
    if (!/^\d+(\.\d{1,2})?$/.test(availabilityInput) || !Number.isFinite(value) || value < 0.5 || value > 168) {
      setAvailabilityError('Enter between 0.5 and 168 hours.')
      setAvailabilityMessage(null)
      return
    }
    setIsSavingAvailability(true)
    setAvailabilityError(null)
    setAvailabilityMessage(null)
    try {
      const saved = await chiefEditorApi.saveAvailability(value)
      setAvailabilityInput(saved.weeklyHours == null ? '' : String(saved.weeklyHours))
      const refreshed = await chiefEditorApi.workload()
      setWorkload(refreshed)
      setAvailabilityMessage('Weekly availability saved.')
    } catch (caught) {
      setAvailabilityError(errorMessage(caught, 'Weekly availability could not be saved.'))
    } finally {
      setIsSavingAvailability(false)
    }
  }

  const knownHours = toNumberOrNull(workload?.knownExpectedHours) ?? 0
  const availableHours = toNumberOrNull(workload?.availableWeeklyHours)
  const remainingHours = toNumberOrNull(workload?.remainingHours)
  const queueExceeds = workload?.queueExceedsWeek
  const completeForecast = workload?.completeForecast ?? false

  return (
    <WorkspaceLayout navItems={reviewNav} role="CHIEF_EDITOR" title="Review queue">
      <div className="workspace-content chief-editor-workspace">
        {decisionNotice && <p className="form-success" role="status">{decisionNotice}</p>}
        <header className="chief-editor-heading">
          <div>
            <h1>{selectedProjectId ? projects.find((project) => project.id === selectedProjectId)?.projectName ?? 'Project reviews' : 'Your projects'}</h1>
            <p>{selectedProjectId ? 'Open versions for this project.' : 'Choose a project to see its activities and versions.'}</p>
          </div>
          {workload && (
            <div className="workload-summary" aria-label="Open review workload">
              <Clock3 size={19} aria-hidden="true" />
              <p>
                <strong>{plural(workload.openCount, 'open version')}</strong>
                <span>{formatCheckingTime(knownHours)} estimated</span>
                <span>{plural(workload.missingEstimateCount, 'without an estimate', 'without an estimate')}</span>
              </p>
            </div>
          )}
        </header>

        {isLoading && (
          <section className="review-loading" aria-busy="true" aria-label="Loading review queue">
            <span className="skeleton skeleton--title" />
            <span className="skeleton skeleton--body" />
            <span className="skeleton skeleton--body" />
          </section>
        )}

        {!isLoading && error && (
          <section className="inline-error" role="alert">
            <AlertCircle size={22} aria-hidden="true" />
            <div>
              <h2>Review queue unavailable</h2>
              <p>{error}</p>
            </div>
            <button className="button button--secondary" type="button" onClick={() => void loadDashboard(page)}>
              <RefreshCw size={17} aria-hidden="true" />
              Retry
            </button>
          </section>
        )}

        {!error && workload && (
          <>
            <section className="capacity-strip" aria-labelledby="capacity-title">
              <div className="capacity-strip__intro">
                <span aria-hidden="true"><Gauge size={20} /></span>
                <div>
                  <h2 id="capacity-title">This week's capacity</h2>
                  <p>Compare the whole open queue with the hours you can review this week.</p>
                </div>
              </div>
              <form className="capacity-form" onSubmit={(event) => void saveAvailability(event)}>
                <div className="field-group">
                  <label htmlFor="availability-hours">Available hours / week</label>
                  <input
                    aria-invalid={Boolean(availabilityError)}
                    id="availability-hours"
                    inputMode="decimal"
                    max="168"
                    min="0.5"
                    placeholder="e.g. 12"
                    step="0.5"
                    type="number"
                    value={availabilityInput}
                    onChange={(event) => {
                      setAvailabilityInput(event.target.value)
                      setAvailabilityError(null)
                      setAvailabilityMessage(null)
                    }}
                  />
                </div>
                <button className="button button--secondary button--compact" disabled={isSavingAvailability} type="submit">
                  {isSavingAvailability ? 'Saving…' : 'Save availability'}
                </button>
              </form>
              <div className={`capacity-verdict${queueExceeds ? ' capacity-verdict--over' : ''}`}>
                {availableHours == null ? (
                  <p><strong>Availability not set.</strong><span>Set your weekly hours to compare the queue.</span></p>
                ) : (
                  <p>
                    <strong>
                      {queueExceeds
                        ? 'The open queue exceeds one week of your time.'
                        : `The queue fits within your ${formatCheckingTime(availableHours)} week.`}
                    </strong>
                    <span>
                      {formatCheckingTime(knownHours)} estimated
                      {remainingHours != null && (remainingHours >= 0
                        ? ` · ${formatCheckingTime(remainingHours)} left`
                        : ` · ${formatCheckingTime(Math.abs(remainingHours))} over`)}
                      {!completeForecast && ' · not a complete forecast'}
                    </span>
                    {!completeForecast && (
                      <span>{plural(workload.missingEstimateCount, 'version has', 'versions have')} no estimate yet.</span>
                    )}
                  </p>
                )}
              </div>
              {availabilityError && <p className="field-error" role="alert">{availabilityError}</p>}
              {availabilityMessage && <p className="form-success" role="status">{availabilityMessage}</p>}
            </section>

            <div className="chief-editor-project-flow">
              {!selectedProjectId && <section className="assigned-projects" aria-labelledby="assigned-projects-title">
                <div className="review-section-heading">
                  <span aria-hidden="true"><BriefcaseBusiness size={18} /></span>
                  <div>
                    <h2 id="assigned-projects-title">Assigned projects</h2>
                    <p>Current project responsibility</p>
                  </div>
                </div>
                <label className="field-group chief-editor-project-search" htmlFor="project-search">Search projects
                  <input id="project-search" onChange={(event) => setProjectFilter(event.target.value)} placeholder="Project name" type="search" value={projectFilter} />
                </label>
                {projects.length === 0 ? (
                  <p className="quiet-empty">No projects are currently assigned.</p>
                ) : (
                  <ul className="project-list">
                    {projects.filter((project) => project.projectName.toLowerCase().includes(projectFilter.toLowerCase())).map((project) => (
                      <li key={project.id}>
                        <button className="project-choice" onClick={() => setFilter('project', String(project.id))} type="button">
                          <strong>{project.projectName}</strong>
                          <span>{project.responsible ? 'Responsible editor' : 'Assigned editor'}</span>
                          <small>Created {formatDate(project.createdDate)}</small>
                          <ChevronRight size={18} aria-hidden="true" />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </section>}

              {selectedProjectId && queue && <section className="review-queue" aria-labelledby="review-queue-title">
                <button className="button button--ghost button--compact" onClick={() => setParams({})} type="button"><ChevronLeft size={16} /> All projects</button>
                <div className="review-queue-filters">
                  <label className="field-group" htmlFor="review-search">Search activities
                    <input id="review-search" onChange={(event) => setReviewSearchDraft(event.target.value)} placeholder="Number or name" type="search" value={reviewSearchDraft} />
                  </label>
                  <label className="field-group" htmlFor="review-kind">Version
                    <select id="review-kind" onChange={(event) => setFilter('kind', event.target.value)} value={kindFilter}>
                      <option value="">All open</option><option value="WEEKLY">Weekly</option><option value="FINAL">Final</option>
                    </select>
                  </label>
                </div>
                <div className="review-section-heading review-section-heading--queue">
                  <span aria-hidden="true"><FileCheck2 size={18} /></span>
                  <div>
                    <h2 id="review-queue-title">Open versions</h2>
                    <p>Newest submissions first</p>
                  </div>
                  <strong>{queue.totalElements}</strong>
                </div>

                {queue.reviews.length === 0 ? (
                  <div className="empty-state empty-state--compact">
                    <h3>No versions waiting</h3>
                    <p>New assigned submissions will appear here.</p>
                  </div>
                ) : (
                  <div className="review-queue-list">
                    {queue.reviews.map((review) => (
                      <Link className="review-queue-row review-queue-row--rich" key={review.id} to={`/chief-editor/reviews/${review.id}`}>
                        <span className="review-queue-row__identity">
                          <span className={`review-kind review-kind--${(review.kind ?? 'FINAL').toLowerCase()}`}>
                            {review.kind === 'WEEKLY' ? 'Weekly' : 'Final'}
                          </span>
                          <strong>{review.activityName}</strong>
                          <small>
                            {review.activityNumber} · {review.projectName}
                            {review.weekStartDate ? ` · week of ${formatDate(review.weekStartDate)}` : ''}
                          </small>
                        </span>
                        <span className="review-queue-row__cycle">
                          <span>Cycle {review.cycleNumber}</span>
                          {review.annotationCount > 0 && (
                            <small><Highlighter size={13} aria-hidden="true" /> {review.annotationCount} {review.annotationCount === 1 ? 'mark' : 'marks'}</small>
                          )}
                        </span>
                        <span className="review-queue-row__time">
                          <strong>{review.expectedCheckingHours == null ? 'Estimate needed' : `${formatCheckingTime(review.expectedCheckingHours)} expected`}</strong>
                          <small><CalendarDays size={13} aria-hidden="true" /> Submitted {formatDateTimeInAlmaty(review.submittedAt)}</small>
                        </span>
                        <ChevronRight size={18} aria-hidden="true" />
                      </Link>
                    ))}
                  </div>
                )}

                {queue.totalPages > 1 && (
                  <nav className="review-pagination" aria-label="Review queue pages">
                    <button
                      aria-label="Previous review page"
                      className="button button--secondary button--compact"
                      disabled={page === 0}
                      type="button"
                      onClick={() => void loadDashboard(page - 1)}
                    >
                      <ChevronLeft size={16} aria-hidden="true" />
                      Previous
                    </button>
                    <span>Page {page + 1} of {queue.totalPages}</span>
                    <button
                      aria-label="Next review page"
                      className="button button--secondary button--compact"
                      disabled={page + 1 >= queue.totalPages}
                      type="button"
                      onClick={() => void loadDashboard(page + 1)}
                    >
                      Next
                      <ChevronRight size={16} aria-hidden="true" />
                    </button>
                  </nav>
                )}
              </section>}
            </div>
          </>
        )}
      </div>
    </WorkspaceLayout>
  )
}
