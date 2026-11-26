import { useCallback, useEffect, useLayoutEffect, useMemo, useState, type FormEvent } from 'react'
import { AlertCircle, ChevronDown, ClipboardList, Download, Eye, FileUp, FolderOpen, Highlighter, Languages, Pencil, Plus, RefreshCw, Save } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ReviewMarkedDocumentDialog } from '../components/ReviewMarkedDocumentDialog'
import { TranslatorReviewHistory } from '../components/TranslatorReviewHistory'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { activityStatusLabels, formatCheckingTime, formatDate, formatDateTimeInAlmaty, formatPages, formatVolume, formatWeekRange, formatWorkHours, statusClassName, toNumberOrNull } from '../lib/activity-format'
import { ApiError, editRequestApi, translatorActivityApi } from '../lib/api'
import type { WorkRecordEditRequest } from '../types/edit-request'
import { latestAllowedRecordDateString, todayLocalDateString } from '../lib/date'
import { validateRecordDate, validateTranslatedVolume, validateWorkTime } from '../lib/validation'
import type { ActivityDetail, ReviewCycle, WorkRecord } from '../types/activity'

interface RecordFieldErrors {
  recordDate?: string
  translatedVolume?: string
  workHours?: string
  workMinutes?: string
  reworkPages?: string
}

function validateReworkPages(value: string): string | undefined {
  const trimmed = value.trim()
  if (!trimmed) return undefined
  if (!/^\d{1,4}(?:\.\d{1,2})?$/.test(trimmed)) {
    return 'Use a number with up to 2 decimal places.'
  }
  const number = Number(trimmed)
  if (number < 0 || number > 1000) return 'Rework pages must be between 0 and 1,000.'
  return undefined
}

function inputValue(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

function recordMatches(record: WorkRecord, recordId: number | null): boolean {
  return recordId !== null && record.id === recordId
}

function workTimeParts(value: number | string | null | undefined): { hours: string; minutes: string } {
  const decimalHours = toNumberOrNull(value)
  if (decimalHours === null) {
    return { hours: '', minutes: '' }
  }

  const totalMinutes = Math.round(decimalHours * 60)
  return {
    hours: String(Math.floor(totalMinutes / 60)),
    minutes: String(totalMinutes % 60),
  }
}

function workTimePayload(hoursValue: string, minutesValue: string): number | null {
  const hoursTrimmed = hoursValue.trim()
  const minutesTrimmed = minutesValue.trim()
  if (!hoursTrimmed && !minutesTrimmed) {
    return null
  }

  const hours = hoursTrimmed ? Number(hoursTrimmed) : 0
  const minutes = minutesTrimmed ? Number(minutesTrimmed) : 0
  return Number((hours + minutes / 60).toFixed(2))
}

export function TranslatorActivityDetailPage() {
  const { activityId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [activity, setActivity] = useState<ActivityDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [recordDate, setRecordDate] = useState('')
  const [translatedVolume, setTranslatedVolume] = useState('')
  const [reworkPages, setReworkPages] = useState('')
  const [workHours, setWorkHours] = useState('')
  const [workMinutes, setWorkMinutes] = useState('')
  const [fieldErrors, setFieldErrors] = useState<RecordFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [formSuccess, setFormSuccess] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [editingRecordId, setEditingRecordId] = useState<number | null>(null)
  const [editRequests, setEditRequests] = useState<WorkRecordEditRequest[]>([])
  const [requestedRecordId, setRequestedRecordId] = useState<number | null>(null)
  const [requestReason, setRequestReason] = useState('')
  const [requestError, setRequestError] = useState<string | null>(null)
  const [requestSaving, setRequestSaving] = useState(false)
  const [showReceipt, setShowReceipt] = useState(true)
  const [isHistoryOpen, setIsHistoryOpen] = useState(false)
  const [historyFrom, setHistoryFrom] = useState('')
  const [historyTo, setHistoryTo] = useState('')
  const [estimateValue, setEstimateValue] = useState('')
  const [estimateError, setEstimateError] = useState<string | null>(null)
  const [estimateSuccess, setEstimateSuccess] = useState<string | null>(null)
  const [isEstimateSaving, setIsEstimateSaving] = useState(false)
  const [isEstimateOpen, setIsEstimateOpen] = useState(location.hash === '#weekly-estimate')
  const [isEstimateHistoryOpen, setIsEstimateHistoryOpen] = useState(false)
  const [isFinishConfirming, setIsFinishConfirming] = useState(false)
  const [isLifecycleSaving, setIsLifecycleSaving] = useState(false)
  const [lifecycleError, setLifecycleError] = useState<string | null>(null)
  const [lifecycleSuccess, setLifecycleSuccess] = useState<string | null>(null)
  const [submissionFile, setSubmissionFile] = useState<File | null>(null)
  const [weeklyFile, setWeeklyFile] = useState<File | null>(null)
  const [correctedFile, setCorrectedFile] = useState<File | null>(null)
  const [markedReview, setMarkedReview] = useState<ReviewCycle | null>(null)
  const [reviews, setReviews] = useState<ReviewCycle[]>([])
  const [isReviewsLoading, setIsReviewsLoading] = useState(false)
  const [reviewsError, setReviewsError] = useState<string | null>(null)
  const [isReviewHistoryOpen, setIsReviewHistoryOpen] = useState(location.hash === '#review-history')
  const [attachingReviewId, setAttachingReviewId] = useState<number | null>(null)
  const [attachmentError, setAttachmentError] = useState<string | null>(null)
  const loadedActivityId = activity?.id
  // Keep the form's initial day stable while the page is open. A midnight
  // rollover must not reset an older record the translator is editing.
  const [todayLocal] = useState(todayLocalDateString)
  const latestAllowedDate = latestAllowedRecordDateString()
  const workRecords = useMemo(() => activity?.workRecords ?? [], [activity?.workRecords])
  const isHistoryRangeInvalid = Boolean(historyFrom && historyTo && historyFrom > historyTo)
  const visibleWorkRecords = useMemo(() => workRecords.filter((record) => (
    (!historyFrom || record.recordDate >= historyFrom) && (!historyTo || record.recordDate <= historyTo)
  )), [historyFrom, historyTo, workRecords])
  const editingRecord = useMemo(
    () => workRecords.find((record) => recordMatches(record, editingRecordId)) ?? null,
    [editingRecordId, workRecords],
  )
  const approvedEdit = editRequests.find((request) => request.workRecordId === editingRecordId && request.status === 'APPROVED')
  const isEstimateHistoryOnly = activity != null
    && activity.status !== 'ASSIGNED'
    && activity.status !== 'IN_PROGRESS'
  const latestReview = reviews[0] ?? null
  const latestCorrectionReview = reviews.find((review) => review.result === 'CORRECTION_REQUESTED') ?? null
  const hasPendingCorrection = latestReview?.result === 'CORRECTION_REQUESTED'
  const hasOutstandingRework = (toNumberOrNull(activity?.rework?.outstandingReworkPages) ?? 0) > 0
  const isFinalCorrection = activity?.status === 'CORRECTION_REQUIRED'
  const canRecordReworkOnly = isFinalCorrection || hasOutstandingRework

  const fillFormFromRecord = (record: WorkRecord, clearMessages = true) => {
    setEditingRecordId(record.id)
    setRecordDate(record.recordDate)
    setTranslatedVolume(inputValue(toNumberOrNull(record.translatedVolume) ?? 0))
    setReworkPages(inputValue(toNumberOrNull(record.reworkPages) ?? 0) === '0' ? '' : inputValue(toNumberOrNull(record.reworkPages) ?? 0))
    const recordWorkTime = workTimeParts(record.workHours)
    setWorkHours(recordWorkTime.hours)
    setWorkMinutes(recordWorkTime.minutes)
    setFieldErrors({})
    if (clearMessages) {
      setFormError(null)
      setFormSuccess(null)
    }
  }

  const startNewRecord = () => {
    setEditingRecordId(null)
    setRecordDate(todayLocalDateString())
    setTranslatedVolume('')
    setReworkPages('')
    setWorkHours('')
    setWorkMinutes('')
    setFieldErrors({})
    setFormError(null)
    setFormSuccess(null)
  }

  const loadEditRequests = useCallback(async () => {
    try {
      const response = await editRequestApi.translatorList()
      setEditRequests(response.requests)
    } catch { setRequestError('Edit request status could not be loaded.') }
  }, [])

  const sendEditRequest = async (event: FormEvent) => {
    event.preventDefault()
    if (!activity || requestedRecordId == null || !requestReason.trim()) {
      setRequestError('Enter a reason for the change.')
      return
    }
    setRequestSaving(true)
    setRequestError(null)
    try {
      await editRequestApi.create(activity.id, requestedRecordId, requestReason.trim())
      await loadEditRequests()
      setRequestedRecordId(null)
      setRequestReason('')
    } catch (caught) {
      setRequestError(caught instanceof ApiError ? caught.message : 'Edit request could not be sent.')
    } finally { setRequestSaving(false) }
  }

  const loadReviews = useCallback(async (id: number) => {
    setIsReviewsLoading(true)
    setReviewsError(null)
    setReviews([])
    try {
      const response = await translatorActivityApi.reviews(id)
      setReviews(response.reviews)
    } catch {
      setReviewsError('Review history could not be loaded.')
    } finally {
      setIsReviewsLoading(false)
    }
  }, [])

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
    setFormError(null)
    setFormSuccess(null)
    try {
      const loadedActivity = await translatorActivityApi.get(parsedId)
      setActivity(loadedActivity)
      void loadEditRequests()
      if (loadedActivity.responsible) {
        void loadReviews(parsedId)
      } else {
        setReviews([])
        setReviewsError(null)
      }
    } catch {
      setActivity(null)
      setError('Activity not found or not assigned to you.')
    } finally {
      setIsLoading(false)
    }
  }, [activityId, loadReviews, loadEditRequests])

  useEffect(() => {
    void loadActivity()
  }, [loadActivity])

  useEffect(() => {
    if (!activity?.responsible || activity.status !== 'UNDER_CHECKING') return
    let cancelled = false
    const refreshChecking = async () => {
      try {
        const [detail, history] = await Promise.all([
          translatorActivityApi.get(activity.id), translatorActivityApi.reviews(activity.id),
        ])
        if (!cancelled) {
          setActivity(detail)
          setReviews(history.reviews)
        }
      } catch { /* Keep the last visible checking state during a temporary network error. */ }
    }
    const timer = window.setInterval(() => { if (!document.hidden) void refreshChecking() }, 45000)
    window.addEventListener('focus', refreshChecking)
    return () => { cancelled = true; window.clearInterval(timer); window.removeEventListener('focus', refreshChecking) }
  }, [activity?.id, activity?.responsible, activity?.status])

  useEffect(() => {
    if (location.hash === '#weekly-estimate') setIsEstimateOpen(true)
    if (location.hash === '#review-history') setIsReviewHistoryOpen(true)
  }, [location.hash])

  useEffect(() => {
    if (loadedActivityId && location.hash === '#weekly-estimate') {
      document.getElementById('weekly-estimate')?.scrollIntoView?.({ block: 'start' })
    }
  }, [loadedActivityId, location.hash])

  useEffect(() => {
    if (loadedActivityId && (location.hash === '#correction' || location.hash === '#review-history')) {
      document.getElementById(location.hash.slice(1))?.scrollIntoView?.({ block: 'start' })
    }
  }, [loadedActivityId, location.hash, reviews])

  useLayoutEffect(() => {
    if (!activity) return

    setEstimateValue(activity.currentWeekEstimate == null
      ? ''
      : inputValue(toNumberOrNull(activity.currentWeekEstimate.remainingTextVolume) ?? 0))

    setEditingRecordId(null)
    setRecordDate(todayLocal)
    setTranslatedVolume('')
    setReworkPages('')
    setWorkHours('')
    setWorkMinutes('')
    setFieldErrors({})
    setFormError(null)
  }, [activity, todayLocal])

  const clearFieldError = (field: keyof RecordFieldErrors) => {
    setFieldErrors((current) => {
      const next = { ...current }
      delete next[field]
      return next
    })
    setFormError(null)
    setFormSuccess(null)
  }

  const handleRecordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!activity) return

    const workTimeErrors = validateWorkTime(workHours, workMinutes)
    const nextErrors: RecordFieldErrors = {
      recordDate: editingRecord ? undefined : validateRecordDate(recordDate, latestAllowedDate) ?? undefined,
      translatedVolume: canRecordReworkOnly ? undefined : validateTranslatedVolume(translatedVolume) ?? undefined,
      reworkPages: validateReworkPages(reworkPages),
      ...workTimeErrors,
    }

    if (nextErrors.recordDate || nextErrors.translatedVolume || nextErrors.workHours || nextErrors.workMinutes || nextErrors.reworkPages) {
      setFieldErrors(nextErrors)
      setFormError(null)
      setFormSuccess(null)
      return
    }

    const payload = {
      translatedVolume: canRecordReworkOnly && !translatedVolume.trim() ? 0 : Number(translatedVolume.trim()),
      workHours: workTimePayload(workHours, workMinutes),
      ...(reworkPages.trim() ? { reworkPages: Number(reworkPages.trim()) } : {}),
    }

    setIsSaving(true)
    setFieldErrors({})
    setFormError(null)
    setFormSuccess(null)

    try {
      const existingRecordForDate = workRecords.find((record) => record.recordDate === recordDate) ?? null
      if (!editingRecord && existingRecordForDate) {
        setFormError('A record already exists for this date. Request permission to edit it from Daily history.')
        return
      }
      const recordToUpdate = editingRecord
      const updatedActivity = recordToUpdate
        ? await translatorActivityApi.updateWorkRecord(activity.id, recordToUpdate.id, { ...payload, editRequestId: approvedEdit?.id })
        : await translatorActivityApi.createWorkRecord(activity.id, { recordDate, ...payload })
      setActivity(updatedActivity)
      setEditingRecordId(recordToUpdate?.id ?? null)
      setFormSuccess(recordToUpdate ? 'Daily record updated.' : 'Daily record saved.')
      if (recordToUpdate) { setEditingRecordId(null); void loadEditRequests() }
    } catch (saveError) {
      if (saveError instanceof ApiError) {
        setFieldErrors(saveError.fieldErrors)
        setFormError(saveError.message)
        if (saveError.status === 409) {
          await loadActivity()
          setFormError('A record for this date already exists. Select it in the history and save changes.')
        }
      } else {
        setFormError('Daily record could not be saved. Try again.')
      }
    } finally {
      setIsSaving(false)
    }
  }

  const handleEstimateSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!activity || isEstimateSaving) return

    const value = estimateValue.trim()
    if (!/^\d{1,12}(?:\.\d{1,2})?$/.test(value)) {
      setEstimateError('Enter a non-negative number with up to 2 decimal places.')
      setEstimateSuccess(null)
      return
    }

    setIsEstimateSaving(true)
    setEstimateError(null)
    setEstimateSuccess(null)
    try {
      setActivity(await translatorActivityApi.saveCurrentEstimate(activity.id, Number(value)))
      setEstimateSuccess('Weekly estimate saved.')
    } catch (saveError) {
      setEstimateError(saveError instanceof ApiError
        ? saveError.fieldErrors.remainingTextVolume ?? saveError.message
        : 'Weekly estimate could not be saved. Try again.')
      if (saveError instanceof ApiError && saveError.status === 409) {
        await loadActivity()
      }
    } finally {
      setIsEstimateSaving(false)
    }
  }

  const handleFinish = async () => {
    if (!activity || isLifecycleSaving) return
    setIsLifecycleSaving(true)
    setLifecycleError(null)
    setLifecycleSuccess(null)
    try {
      const updated = await translatorActivityApi.finish(activity.id)
      setActivity(updated)
      setIsFinishConfirming(false)
      setLifecycleSuccess('Activity finished.')
    } catch (actionError) {
      setLifecycleError(actionError instanceof ApiError
        ? actionError.message
        : 'The activity could not be updated. Try again.')
      if (actionError instanceof ApiError && actionError.status === 409) {
        await loadActivity()
      }
    } finally {
      setIsLifecycleSaving(false)
    }
  }

  const handleDocumentSubmit = async (event: FormEvent<HTMLFormElement>, action: 'submit' | 'weekly' | 'resubmit') => {
    event.preventDefault()
    if (!activity || isLifecycleSaving) return
    const file = action === 'submit' ? submissionFile : action === 'weekly' ? weeklyFile : correctedFile
    if (!file) {
      setLifecycleError(`Choose ${action === 'resubmit' ? 'a corrected' : action === 'weekly' ? 'this week\'s' : 'a final'} PDF or DOCX file.`)
      setLifecycleSuccess(null)
      return
    }

    setIsLifecycleSaving(true)
    setLifecycleError(null)
    setLifecycleSuccess(null)
    try {
      const updated = action === 'submit'
        ? await translatorActivityApi.submitForChecking(activity.id, file)
        : action === 'weekly'
          ? await translatorActivityApi.submitWeeklyVersion(activity.id, file)
          : await translatorActivityApi.resubmitForChecking(activity.id, file)
      setActivity(updated)
      setSubmissionFile(null)
      setWeeklyFile(null)
      setCorrectedFile(null)
      setLifecycleSuccess(action === 'submit'
        ? 'Final version submitted for checking.'
        : action === 'weekly'
          ? 'This week\'s version sent. You can keep recording daily work.'
          : 'Corrected document submitted for checking.')
      if (action !== 'weekly') {
        setShowReceipt(true)
        navigate(`${location.pathname}${location.search}`, { replace: true })
      }
      await loadReviews(activity.id)
    } catch (actionError) {
      setLifecycleError(actionError instanceof ApiError
        ? actionError.message
        : 'The document could not be uploaded. Try again.')
      if (actionError instanceof ApiError && actionError.status === 409) {
        await loadActivity()
      }
    } finally {
      setIsLifecycleSaving(false)
    }
  }

  const handleLegacyAttachment = async (reviewId: number, file: File) => {
    if (!activity || attachingReviewId != null) return
    setAttachingReviewId(reviewId)
    setAttachmentError(null)
    try {
      const updatedReview = await translatorActivityApi.attachLegacyDocument(activity.id, reviewId, file)
      setReviews((current) => current.map((review) => review.id === reviewId ? updatedReview : review))
      setLifecycleSuccess('Document attached to the existing review.')
    } catch (actionError) {
      setAttachmentError(actionError instanceof ApiError
        ? actionError.message
        : 'The document could not be attached. Try again.')
      if (actionError instanceof ApiError && actionError.status === 409) {
        await loadReviews(activity.id)
      }
    } finally {
      setAttachingReviewId(null)
    }
  }

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
      <div className="workspace-content translator-workspace translator-workspace--detail">
        <Breadcrumbs items={[
          { label: 'Activities', to: `/translator${location.search}` },
          { label: activity?.activityNumber ?? 'Activity' },
        ]} />

        {isLoading && (
          <section className="activity-detail activity-detail--loading" aria-busy="true" aria-label="Loading activity details">
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

        {!isLoading && activity && activity.responsible && showReceipt && !location.hash
          && (activity.status === 'UNDER_CHECKING' || activity.status === 'COMPLETED') && (
          <section className="activity-receipt" aria-labelledby="activity-receipt-title">
            <span className="activity-receipt__icon" aria-hidden="true"><ClipboardList size={32} /></span>
            <p className="activity-receipt__number">{activity.activityNumber}</p>
            <h1 id="activity-receipt-title">{activity.status === 'COMPLETED' ? 'Activity completed' : 'Your final version is with the Chief Editor'}</h1>
            <p>{activity.status === 'COMPLETED'
              ? `${activity.activityName} was approved. Your work and version history are ready to view.`
              : `Great work. You submitted ${activity.activityName} for checking. We’ll notify you when the editor responds.`}</p>
            {latestReview?.expectedCheckingHours != null && activity.status === 'UNDER_CHECKING' &&
              <p>Estimated checking time: {formatCheckingTime(latestReview.expectedCheckingHours)}</p>}
            <div className="activity-receipt__actions">
              <Link className="button button--primary" to="/translator">Back to activities</Link>
              <Link className="button button--secondary" onClick={() => setShowReceipt(false)} to="#details">View activity details</Link>
            </div>
          </section>
        )}

        {!isLoading && activity && !(activity.responsible && showReceipt && !location.hash
          && (activity.status === 'UNDER_CHECKING' || activity.status === 'COMPLETED')) && (
          <>
            <div className="activity-workspace-card">
              <header className="activity-detail">
                <div className="activity-detail__identity">
                  <span className="activity-detail__mark" aria-hidden="true"><Languages size={24} /></span>
                  <div className="activity-detail__copy">
                    <div className="activity-detail__context">
                      <span>{activity.activityNumber}</span>
                      <span className={statusClassName(activity.status)}>{activityStatusLabels[activity.status]}</span>
                      <span className="assignment-pill">{activity.responsible ? 'Responsible' : 'Assisting'}</span>
                    </div>
                    <h1>{activity.activityName}</h1>
                    <p className="activity-detail__project"><FolderOpen aria-hidden="true" size={16} />{activity.projectName}</p>
                  </div>
                </div>
                <div className="activity-detail__volume">
                  <span>Logged by you</span>
                  <strong>{formatPages(activity.progress.totalTranslatedVolume)}</strong>
                </div>
              </header>

              <section className="rework-strip" aria-label="Pages remaining overview">
                <div>
                  <span>This week remaining</span>
                  <strong>{activity.rework?.hasWeeklyEstimate
                    ? formatPages(activity.rework.remainingPages ?? 0)
                    : 'Not set'}</strong>
                </div>
                <div>
                  <span>Unfinished rework</span>
                  <strong className={hasOutstandingRework ? 'rework-strip__attention' : undefined}>
                    {formatPages(activity.rework?.outstandingReworkPages ?? 0)}
                  </strong>
                  {(toNumberOrNull(activity.rework?.correctionPages) ?? 0) > 0 && (
                    <small>{formatVolume(activity.rework?.reworkRecordedPages ?? 0)} of {formatVolume(activity.rework?.correctionPages ?? 0)} reworked</small>
                  )}
                </div>
                <div>
                  <span>Total remaining</span>
                  <strong>{!activity.rework?.hasWeeklyEstimate && !hasOutstandingRework
                    ? 'Not set'
                    : formatPages(activity.rework?.totalRemainingPages ?? 0)}</strong>
                </div>
              </section>

              {activity.permissions?.canEditWorkRecords && <section className="work-record-panel" aria-labelledby="record-form-title">
                <div className="section-heading">
                  <div>
                    <h2 id="record-form-title">{editingRecord ? 'Edit daily record' : 'Record daily work'}</h2>
                    <p>
                      {editingRecord
                        ? `You are editing ${formatDate(editingRecord.recordDate)}. The date stays locked; change the pages or hours only.`
                        : isFinalCorrection
                          ? 'Record rework pages and the time spent. Rework does not add first-time translated pages.'
                          : 'Log the pages you translated on this date. You can choose a past date.'}
                    </p>
                  </div>
                  {editingRecord && (
                    <button className="button button--secondary" disabled={isSaving} type="button" onClick={startNewRecord}>
                      <Plus size={17} aria-hidden="true" />
                      New date
                    </button>
                  )}
                </div>

                <form className="work-record-form" noValidate onSubmit={(event) => void handleRecordSubmit(event)}>
                  <div className="field-group">
                    <label htmlFor="record-date">Record date</label>
                    {editingRecord ? (
                      <input
                        className="readonly-field"
                        id="record-date"
                        readOnly
                        type="text"
                        value={formatDate(recordDate)}
                      />
                    ) : (
                      <input
                        aria-describedby={fieldErrors.recordDate ? 'record-date-error' : undefined}
                        aria-invalid={fieldErrors.recordDate ? 'true' : 'false'}
                        disabled={isSaving}
                        id="record-date"
                        max={latestAllowedDate}
                        onChange={(event) => {
                          const nextDate = event.target.value
                          const existingRecord = workRecords.find((record) => record.recordDate === nextDate)
                          if (existingRecord) {
                            fillFormFromRecord(existingRecord)
                          } else {
                            setRecordDate(nextDate)
                            setEditingRecordId(null)
                            clearFieldError('recordDate')
                          }
                        }}
                        type="date"
                        value={recordDate}
                      />
                    )}
                    {fieldErrors.recordDate && <p className="field-error" id="record-date-error">{fieldErrors.recordDate}</p>}
                  </div>

                  {!isFinalCorrection && (
                  <div className="field-group">
                    <label htmlFor="translated-volume">Translated pages</label>
                    <input
                      aria-describedby={fieldErrors.translatedVolume ? 'translated-volume-error' : undefined}
                      aria-invalid={fieldErrors.translatedVolume ? 'true' : 'false'}
                      disabled={isSaving}
                      id="translated-volume"
                      inputMode="decimal"
                      onChange={(event) => {
                        setTranslatedVolume(event.target.value)
                        clearFieldError('translatedVolume')
                      }}
                      placeholder="0.00"
                      type="text"
                      value={translatedVolume}
                    />
                    {fieldErrors.translatedVolume && (
                      <p className="field-error" id="translated-volume-error">{fieldErrors.translatedVolume}</p>
                    )}
                  </div>
                  )}

                  {(activity.permissions.hasOutstandingRework || isFinalCorrection) && (
                    <div className="field-group">
                      <label htmlFor="rework-pages">Pages reworked</label>
                      <input
                        aria-describedby={fieldErrors.reworkPages ? 'rework-pages-error' : 'rework-pages-hint'}
                        aria-invalid={fieldErrors.reworkPages ? 'true' : 'false'}
                        disabled={isSaving}
                        id="rework-pages"
                        inputMode="decimal"
                        onChange={(event) => {
                          setReworkPages(event.target.value)
                          clearFieldError('reworkPages')
                        }}
                        placeholder="0"
                        type="text"
                        value={reworkPages}
                      />
                      <p className="field-hint" id="rework-pages-hint">
                        Decreases unfinished rework only; hours below still count as working time.
                      </p>
                      {fieldErrors.reworkPages && <p className="field-error" id="rework-pages-error">{fieldErrors.reworkPages}</p>}
                    </div>
                  )}

                  <div className="field-group">
                    <label htmlFor="work-hours">Hours (optional)</label>
                    <input
                      aria-describedby={fieldErrors.workHours ? 'work-hours-error' : undefined}
                      aria-invalid={fieldErrors.workHours ? 'true' : 'false'}
                      disabled={isSaving}
                      id="work-hours"
                      inputMode="numeric"
                      onChange={(event) => {
                        setWorkHours(event.target.value)
                        clearFieldError('workHours')
                      }}
                      placeholder="0"
                      type="text"
                      value={workHours}
                    />
                    {fieldErrors.workHours && <p className="field-error" id="work-hours-error">{fieldErrors.workHours}</p>}
                  </div>

                  <div className="field-group">
                    <label htmlFor="work-minutes">Minutes</label>
                    <input
                      aria-describedby={fieldErrors.workMinutes ? 'work-minutes-error' : undefined}
                      aria-invalid={fieldErrors.workMinutes ? 'true' : 'false'}
                      disabled={isSaving}
                      id="work-minutes"
                      inputMode="numeric"
                      onChange={(event) => {
                        setWorkMinutes(event.target.value)
                        clearFieldError('workMinutes')
                      }}
                      placeholder="0"
                      type="text"
                      value={workMinutes}
                    />
                    {fieldErrors.workMinutes && <p className="field-error" id="work-minutes-error">{fieldErrors.workMinutes}</p>}
                  </div>

                  <button aria-busy={isSaving} className="button button--primary" disabled={isSaving} type="submit">
                    <Save size={17} aria-hidden="true" />
                    {isSaving ? 'Saving…' : editingRecord ? 'Update record' : 'Save record'}
                  </button>
                </form>

                {formError && <p className="form-alert" role="alert">{formError}</p>}
                {formSuccess && <p aria-live="polite" className="form-success" role="status">{formSuccess}</p>}
              </section>}
            </div>

            {activity.permissions?.canFinish && (
              <section className="next-step-panel next-step-panel--ready lifecycle-panel" aria-labelledby="finish-title">
                <div className="section-heading">
                  <div>
                    <h2 id="finish-title">Translation ready to finish</h2>
                    <p>You reported 0 pages remaining. Finish when all translation work is recorded.</p>
                  </div>
                  {!isFinishConfirming && (
                    <button className="button button--primary" disabled={isLifecycleSaving} onClick={() => setIsFinishConfirming(true)} type="button">
                      Finish translation
                    </button>
                  )}
                </div>
                {isFinishConfirming && (
                  <div className="lifecycle-panel__confirmation" role="group" aria-label="Confirm finish activity">
                    <p>Finishing locks daily records and weekly estimates. You can then submit the activity for checking.</p>
                    <div className="lifecycle-panel__actions">
                      <button className="button button--primary" disabled={isLifecycleSaving} onClick={() => void handleFinish()} type="button">
                        {isLifecycleSaving ? 'Finishing…' : 'Confirm finish'}
                      </button>
                      <button className="button button--secondary" disabled={isLifecycleSaving} onClick={() => setIsFinishConfirming(false)} type="button">
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </section>
            )}

            {(activity.status === 'ASSIGNED' || activity.status === 'IN_PROGRESS') && activity.responsible
              && activity.permissions?.canSubmitWeeklyVersion && !hasPendingCorrection && (
              <section className="next-step-panel next-step-panel--weekly lifecycle-panel" aria-labelledby="weekly-submit-title" id="weekly-submit">
                <div className="section-heading document-step-heading">
                  <div>
                    <h2 id="weekly-submit-title">This week's version</h2>
                    <p>
                      Send the current PDF or DOCX for checking without finishing. Daily work stays open;
                      you can keep recording pages until the final version.
                    </p>
                  </div>
                  <form className="document-upload-form" onSubmit={(event) => void handleDocumentSubmit(event, 'weekly')}>
                    <div className="field-group">
                      <label htmlFor="weekly-translation-document">Weekly translation document</label>
                      <div className="document-file-picker">
                        <input
                          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                          className="sr-only"
                          disabled={isLifecycleSaving}
                          id="weekly-translation-document"
                          onChange={(event) => {
                            setWeeklyFile(event.target.files?.[0] ?? null)
                            setLifecycleError(null)
                          }}
                          type="file"
                        />
                        <label className="button button--secondary" htmlFor="weekly-translation-document">Choose file</label>
                        <span className="document-file-picker__name">{weeklyFile?.name ?? 'No file selected'}</span>
                      </div>
                    </div>
                    <button className="button button--primary" disabled={isLifecycleSaving} type="submit">
                      <FileUp aria-hidden="true" size={17} />
                      {isLifecycleSaving ? 'Sending…' : 'Send weekly version'}
                    </button>
                  </form>
                </div>
              </section>
            )}

            {(activity.status === 'ASSIGNED' || activity.status === 'IN_PROGRESS') && latestReview && latestReview.result == null && (
              <section className="next-step-panel lifecycle-panel next-step-panel--checking" aria-labelledby="weekly-checking-title">
                <div className="section-heading">
                  <div>
                    <h2 id="weekly-checking-title">Weekly version under checking</h2>
                    <p>
                      Cycle {latestReview.cycleNumber} was submitted {formatDateTimeInAlmaty(latestReview.submittedAt)}.
                      Keep recording daily work; the activity stays open.
                    </p>
                  </div>
                  {latestReview.document?.annotatedDownloadUrl && (latestReview.annotationCount ?? 0) > 0 && (
                    <button className="button button--secondary" onClick={() => setMarkedReview(latestReview)} type="button">
                      <Eye size={17} aria-hidden="true" /> View marks
                    </button>
                  )}
                </div>
              </section>
            )}

            {activity.status === 'FINISHED' && activity.responsible && (
              <section className={`next-step-panel lifecycle-panel${activity.permissions?.canSubmitForChecking ? ' next-step-panel--ready' : ' next-step-panel--blocked'}`} aria-labelledby="submit-title">
                <div className="section-heading document-step-heading">
                  <div>
                    <h2 id="submit-title">{activity.permissions?.canSubmitForChecking ? 'Ready for checking' : 'Waiting for Chief Editor assignment'}</h2>
                    <p>{activity.permissions?.canSubmitForChecking
                      ? 'Records are locked. Upload the final PDF or DOCX for the responsible Chief Editor.'
                      : 'A Project Manager must assign a responsible Chief Editor before this activity can be submitted.'}</p>
                  </div>
                  {activity.permissions?.canSubmitForChecking ? (
                    <form className="document-upload-form" onSubmit={(event) => void handleDocumentSubmit(event, 'submit')}>
                      <div className="field-group">
                        <label htmlFor="translation-document">Translation document</label>
                        <div className="document-file-picker">
                        <input
                          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                          className="sr-only"
                          disabled={isLifecycleSaving}
                          id="translation-document"
                          onChange={(event) => {
                            setSubmissionFile(event.target.files?.[0] ?? null)
                            setLifecycleError(null)
                          }}
                          type="file"
                        />
                        <label className="button button--secondary" htmlFor="translation-document">Choose file</label>
                        <span className="document-file-picker__name">{submissionFile?.name ?? 'No file selected'}</span>
                        </div>
                      </div>
                      <button className="button button--primary" disabled={isLifecycleSaving} type="submit">
                        <FileUp aria-hidden="true" size={17} />
                        {isLifecycleSaving ? 'Submitting…' : 'Submit for checking'}
                      </button>
                    </form>
                  ) : (
                    <button className="button button--secondary" disabled type="button">Submit unavailable</button>
                  )}
                </div>
              </section>
            )}

            {hasPendingCorrection && activity.responsible && (
              <section className="next-step-panel next-step-panel--correction lifecycle-panel correction-panel" aria-labelledby="correction-title" id="correction">
                <div className="correction-panel__copy">
                  <div>
                    <h2 id="correction-title">Corrections requested</h2>
                    <p>
                      {latestCorrectionReview?.kind === 'WEEKLY' ? 'Weekly version' : 'Final version'}
                      {' · '}Cycle {latestCorrectionReview?.cycleNumber}.
                      Review the marks, correct the document, record rework pages, then upload the new version.
                    </p>
                  </div>
                  {latestCorrectionReview?.feedback ? (
                    <blockquote>{latestCorrectionReview.feedback}</blockquote>
                  ) : reviewsError ? (
                    <p className="form-alert" role="alert">{reviewsError}</p>
                  ) : (
                    <p className="estimate-panel__note">Loading correction feedback…</p>
                  )}
                  <div className="correction-panel__meta">
                    {latestCorrectionReview?.correctionPages != null && (
                      <span>{latestCorrectionReview.correctionPageNumbers?.length
                        ? `Pages ${latestCorrectionReview.correctionPageNumbers.join(', ')} to redo`
                        : `${latestCorrectionReview.correctionPages} pages to redo`}</span>
                    )}
                    <span>{formatPages(activity.rework?.outstandingReworkPages ?? 0)} still unfinished</span>
                  </div>
                  <div className="correction-panel__actions">
                    {(latestCorrectionReview?.annotationCount ?? 0) > 0 && latestCorrectionReview?.document && (
                      <button
                        className="button button--secondary button--compact"
                        onClick={() => setMarkedReview(latestCorrectionReview)}
                        type="button"
                      >
                        <Highlighter aria-hidden="true" size={15} />
                        Open marks ({latestCorrectionReview?.annotationCount ?? 0})
                      </button>
                    )}
                    {latestCorrectionReview?.document && (
                      <a
                        aria-label={`Download ${latestCorrectionReview.document.originalName}`}
                        className="button button--secondary button--compact correction-panel__download"
                        href={latestCorrectionReview.document.downloadUrl}
                      >
                        <Download aria-hidden="true" size={15} />
                        Download reviewed file
                      </a>
                    )}
                  </div>
                </div>
                <form className="document-upload-form document-upload-form--correction" onSubmit={(event) => void handleDocumentSubmit(event, 'resubmit')}>
                  <div className="field-group">
                    <label htmlFor="corrected-translation-document">Corrected translation document</label>
                    <div className="document-file-picker">
                    <input
                      accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                      className="sr-only"
                      disabled={isLifecycleSaving}
                      id="corrected-translation-document"
                      onChange={(event) => {
                        setCorrectedFile(event.target.files?.[0] ?? null)
                        setLifecycleError(null)
                      }}
                      type="file"
                    />
                    <label className="button button--secondary" htmlFor="corrected-translation-document">Choose file</label>
                    <span className="document-file-picker__name">{correctedFile?.name ?? 'No file selected'}</span>
                    </div>
                  </div>
                  <button className="button button--primary" disabled={isLifecycleSaving || isReviewsLoading} type="submit">
                    <FileUp aria-hidden="true" size={17} />
                    {isLifecycleSaving ? 'Uploading…' : 'Upload corrected file'}
                  </button>
                </form>
              </section>
            )}

            {activity.status === 'UNDER_CHECKING' && (
              <section className="next-step-panel lifecycle-panel" aria-labelledby="under-checking-title">
                <div className="section-heading">
                  <div>
                    <h2 id="under-checking-title">Under checking</h2>
                    <p>The activity is with the Chief Editor. Daily records and estimates are read-only.</p>
                    {latestReview?.expectedCheckingHours != null && <p>Estimated checking time: {formatCheckingTime(latestReview.expectedCheckingHours)}</p>}
                  </div>
                </div>
              </section>
            )}

            {lifecycleError && <p className="form-alert" role="alert">{lifecycleError}</p>}
            {lifecycleSuccess && <p aria-live="polite" className="form-success" role="status">{lifecycleSuccess}</p>}

            {activity.responsible
              && (reviews.length > 0 || isReviewsLoading || reviewsError != null
                || activity.status === 'UNDER_CHECKING'
                || activity.status === 'CORRECTION_REQUIRED'
                || activity.status === 'COMPLETED') && (
              <TranslatorReviewHistory
                actionError={attachmentError}
                attachingReviewId={attachingReviewId}
                canAttachLegacy={activity.status === 'UNDER_CHECKING'}
                error={reviewsError}
                isLoading={isReviewsLoading}
                isOpen={isReviewHistoryOpen}
                onAttach={handleLegacyAttachment}
                onOpenMarkedDocument={(review) => setMarkedReview(review)}
                onRetry={() => void loadReviews(activity.id)}
                onToggle={() => setIsReviewHistoryOpen((open) => !open)}
                reviews={reviews}
              />
            )}

            {isEstimateHistoryOnly ? (
              <section className="record-section estimate-history-section" aria-labelledby="estimate-history-title" id="weekly-estimate">
                <div className="history-disclosure">
                  <div>
                    <h2 id="estimate-history-title">Weekly estimate history</h2>
                    <p>{(activity.weeklyEstimates ?? []).length} {(activity.weeklyEstimates ?? []).length === 1 ? 'record' : 'records'}</p>
                  </div>
                  <button
                    aria-controls="finished-estimate-history-content"
                    aria-expanded={isEstimateHistoryOpen}
                    aria-label={isEstimateHistoryOpen ? 'Hide weekly estimate history' : 'Show weekly estimate history'}
                    className="button button--secondary history-disclosure__toggle"
                    onClick={() => setIsEstimateHistoryOpen((open) => !open)}
                    type="button"
                  >
                    {isEstimateHistoryOpen ? 'Hide history' : 'Show history'}
                    <ChevronDown aria-hidden="true" className={isEstimateHistoryOpen ? 'history-disclosure__chevron history-disclosure__chevron--open' : 'history-disclosure__chevron'} size={17} />
                  </button>
                </div>
                <div className="history-content" hidden={!isEstimateHistoryOpen} id="finished-estimate-history-content">
                  {(activity.weeklyEstimates ?? []).length === 0 ? (
                    <p className="estimate-panel__note">No weekly estimates were recorded.</p>
                  ) : (
                    <ul className="estimate-history__list">
                      {(activity.weeklyEstimates ?? []).map((estimate) => (
                        <li key={estimate.id}>
                          <span>{formatWeekRange(estimate.weekStartDate)} · Updated {formatDateTimeInAlmaty(estimate.updatedAt)}</span>
                          <strong>{formatPages(estimate.remainingTextVolume)}</strong>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </section>
            ) : (
            <section className="estimate-disclosure" aria-labelledby="estimate-title" id="weekly-estimate">
              <button
                aria-controls="estimate-content"
                aria-expanded={isEstimateOpen}
                className="estimate-disclosure__toggle"
                onClick={() => setIsEstimateOpen((open) => !open)}
                type="button"
              >
                <span className="estimate-disclosure__intro">
                  <span className="estimate-disclosure__title" id="estimate-title">Weekly remaining estimate</span>
                  <span className="estimate-disclosure__caption">{activity.currentWeek
                    ? `${formatDate(activity.currentWeek.startDate)} – ${formatDate(activity.currentWeek.endDate)}`
                    : 'Current week'}</span>
                </span>
                <span className="estimate-disclosure__right">
                  <strong>{activity.currentWeekEstimate
                    ? `${formatPages(activity.currentWeekEstimate.remainingTextVolume)} left`
                    : 'No estimate yet'}</strong>
                  <ChevronDown aria-hidden="true" className={isEstimateOpen ? 'history-disclosure__chevron history-disclosure__chevron--open' : 'history-disclosure__chevron'} size={18} />
                </span>
              </button>
              <div className="estimate-disclosure__content" hidden={!isEstimateOpen} id="estimate-content">
                {activity.permissions?.canEditWeeklyEstimate ? (
                  <form className="estimate-form" noValidate onSubmit={(event) => void handleEstimateSubmit(event)}>
                      <div className="field-group">
                        <label htmlFor="remaining-volume">Remaining pages</label>
                        <input
                          aria-describedby={estimateError ? 'estimate-error' : undefined}
                          aria-invalid={estimateError ? 'true' : 'false'}
                          disabled={isEstimateSaving}
                          id="remaining-volume"
                          inputMode="decimal"
                          onChange={(event) => {
                            setEstimateValue(event.target.value)
                            setEstimateError(null)
                            setEstimateSuccess(null)
                          }}
                          placeholder="0.00"
                          type="text"
                          value={estimateValue}
                        />
                      </div>
                      <button className="button button--secondary" disabled={isEstimateSaving} type="submit">
                        <Save size={17} aria-hidden="true" />
                        {isEstimateSaving ? 'Saving…' : 'Save estimate'}
                      </button>
                  </form>
                ) : (
                  <p className="estimate-panel__note">{activity.responsible
                    ? 'This estimate is read-only after finishing.'
                    : 'Only the responsible translator can update this estimate.'}</p>
                )}
                {estimateError && <p className="form-alert" id="estimate-error" role="alert">{estimateError}</p>}
                {estimateSuccess && <p aria-live="polite" className="form-success" role="status">{estimateSuccess}</p>}
                <div className="estimate-history">
                  <button
                    aria-controls="estimate-history-content"
                    aria-expanded={isEstimateHistoryOpen}
                    className="button button--ghost button--compact"
                    onClick={() => setIsEstimateHistoryOpen((open) => !open)}
                    type="button"
                  >
                    {isEstimateHistoryOpen ? 'Hide estimate history' : 'Show estimate history'}
                    <ChevronDown aria-hidden="true" className={isEstimateHistoryOpen ? 'history-disclosure__chevron history-disclosure__chevron--open' : 'history-disclosure__chevron'} size={17} />
                  </button>
                  <div hidden={!isEstimateHistoryOpen} id="estimate-history-content">
                    {(activity.weeklyEstimates ?? []).length === 0 ? (
                      <p className="estimate-panel__note">No weekly estimates yet.</p>
                    ) : (
                      <ul className="estimate-history__list">
                        {(activity.weeklyEstimates ?? []).map((estimate) => (
                          <li key={estimate.id}>
                            <span>{formatWeekRange(estimate.weekStartDate)} · Updated {formatDateTimeInAlmaty(estimate.updatedAt)}</span>
                            <strong>{formatPages(estimate.remainingTextVolume)}</strong>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              </div>
            </section>
            )}

            <section className="record-section" aria-labelledby="history-title">
              <div className="history-disclosure">
                <div>
                  <h2 id="history-title">Daily history</h2>
                  <p>{workRecords.length} {workRecords.length === 1 ? 'record' : 'records'} · {activity.responsible ? 'Responsible' : 'Assisting'} translator</p>
                </div>
                <button
                  aria-controls="daily-history-content"
                  aria-expanded={isHistoryOpen}
                  className="button button--secondary history-disclosure__toggle"
                  onClick={() => setIsHistoryOpen((open) => !open)}
                  type="button"
                >
                  {isHistoryOpen ? 'Hide history' : 'Show history'}
                  <ChevronDown aria-hidden="true" className={isHistoryOpen ? 'history-disclosure__chevron history-disclosure__chevron--open' : 'history-disclosure__chevron'} size={17} />
                </button>
              </div>

              <div className="history-content" hidden={!isHistoryOpen} id="daily-history-content">
                  <div aria-label="Filter daily history by date" className="history-toolbar" role="group">
                    <div className="field-group">
                      <label htmlFor="history-from">From date</label>
                      <input
                        aria-invalid={isHistoryRangeInvalid}
                        id="history-from"
                        onChange={(event) => setHistoryFrom(event.target.value)}
                        type="date"
                        value={historyFrom}
                      />
                    </div>
                    <div className="field-group">
                      <label htmlFor="history-to">To date</label>
                      <input
                        aria-invalid={isHistoryRangeInvalid}
                        id="history-to"
                        onChange={(event) => setHistoryTo(event.target.value)}
                        type="date"
                        value={historyTo}
                      />
                    </div>
                    <button
                      className="button button--ghost button--compact"
                      disabled={!historyFrom && !historyTo}
                      onClick={() => { setHistoryFrom(''); setHistoryTo('') }}
                      type="button"
                    >
                      Clear dates
                    </button>
                  </div>
                  {isHistoryRangeInvalid ? (
                    <p className="history-range-error" role="alert">The start date must be on or before the end date.</p>
                  ) : visibleWorkRecords.length === 0 ? (
                    <div className="empty-state empty-state--compact">
                      <h3>{workRecords.length === 0 ? 'No daily records yet' : 'No records in this period'}</h3>
                      <p>{workRecords.length === 0 ? 'Recorded pages will appear here.' : 'Choose a wider date range or clear the dates.'}</p>
                    </div>
                  ) : (
                    <div className="record-table-wrapper">
                      <table className="record-table">
                        <thead>
                          <tr>
                            <th scope="col">Date</th>
                            <th scope="col">Translated pages</th>
                            <th scope="col">Rework pages</th>
                            <th scope="col">Work hours</th>
                            <th scope="col"><span className="sr-only">Actions</span></th>
                          </tr>
                        </thead>
                        <tbody>
                          {visibleWorkRecords.map((record) => (
                            <tr className={recordMatches(record, editingRecordId) ? 'record-table__row--selected' : undefined} key={record.id}>
                              <td data-label="Date">{formatDate(record.recordDate)}</td>
                              <td data-label="Pages">{formatVolume(record.translatedVolume)}</td>
                              <td data-label="Rework">{(toNumberOrNull(record.reworkPages) ?? 0) > 0 ? formatVolume(record.reworkPages) : '—'}</td>
                              <td data-label="Work time">{formatWorkHours(record.workHours)}</td>
                              <td>
                                {(activity.status === 'ASSIGNED' || activity.status === 'IN_PROGRESS') && <button
                                  className="button button--ghost button--compact"
                                  disabled={isSaving || editRequests.some((request) => request.workRecordId === record.id && request.status === 'PENDING')}
                                  type="button"
                                  onClick={() => {
                                    const approved = editRequests.find((request) => request.workRecordId === record.id && request.status === 'APPROVED')
                                    if (approved) fillFormFromRecord(record)
                                    else { setRequestedRecordId(record.id); setRequestReason(''); setRequestError(null) }
                                  }}
                                >
                                  <Pencil size={16} aria-hidden="true" />
                                  {editRequests.some((request) => request.workRecordId === record.id && request.status === 'PENDING')
                                    ? 'Request pending' : editRequests.some((request) => request.workRecordId === record.id && request.status === 'APPROVED')
                                      ? 'Edit once' : 'Request edit'}
                                </button>}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
              </div>
            </section>
            {requestedRecordId != null && <div className="notification-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setRequestedRecordId(null) }}>
              <section aria-modal="true" aria-labelledby="edit-request-title" className="notification-dialog" role="dialog">
                <header><h2 id="edit-request-title">Request a record edit</h2><button aria-label="Close request" onClick={() => setRequestedRecordId(null)} type="button">×</button></header>
                <p>Explain why this daily record needs a change. Your project manager can allow one edit.</p>
                <form onSubmit={(event) => void sendEditRequest(event)}>
                  <div className="field-group"><label htmlFor="edit-request-reason">Reason</label>
                    <textarea id="edit-request-reason" maxLength={1000} onChange={(event) => setRequestReason(event.target.value)} required value={requestReason} /></div>
                  {requestError && <p className="field-error" role="alert">{requestError}</p>}
                  <div className="notification-dialog__actions"><button className="button button--secondary" onClick={() => setRequestedRecordId(null)} type="button">Cancel</button>
                    <button className="button button--primary" disabled={requestSaving} type="submit">Send request</button></div>
                </form>
              </section>
            </div>}
            {markedReview && (
              <ReviewMarkedDocumentDialog onClose={() => setMarkedReview(null)} review={markedReview} />
            )}
          </>
        )}
      </div>
    </WorkspaceLayout>
  )
}
