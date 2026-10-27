import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { AlertCircle, ChevronDown, ClipboardList, FolderOpen, Languages, Pencil, Plus, RefreshCw, Save } from 'lucide-react'
import { useLocation, useParams } from 'react-router-dom'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { activityStatusLabels, formatDate, formatDateTimeInAlmaty, formatVolume, formatWeekRange, formatWorkHours, statusClassName, toNumberOrNull } from '../lib/activity-format'
import { ApiError, translatorActivityApi } from '../lib/api'
import { latestAllowedRecordDateString, todayLocalDateString } from '../lib/date'
import { validateRecordDate, validateTranslatedVolume, validateWorkTime } from '../lib/validation'
import type { ActivityDetail, WorkRecord } from '../types/activity'

interface RecordFieldErrors {
  recordDate?: string
  translatedVolume?: string
  workHours?: string
  workMinutes?: string
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
  const [activity, setActivity] = useState<ActivityDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [recordDate, setRecordDate] = useState('')
  const [translatedVolume, setTranslatedVolume] = useState('')
  const [workHours, setWorkHours] = useState('')
  const [workMinutes, setWorkMinutes] = useState('')
  const [fieldErrors, setFieldErrors] = useState<RecordFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [formSuccess, setFormSuccess] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [editingRecordId, setEditingRecordId] = useState<number | null>(null)
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
  const loadedActivityId = activity?.id
  const todayLocal = todayLocalDateString()
  const latestAllowedDate = latestAllowedRecordDateString()
  const workRecords = useMemo(() => activity?.workRecords ?? [], [activity?.workRecords])
  const isHistoryRangeInvalid = Boolean(historyFrom && historyTo && historyFrom > historyTo)
  const visibleWorkRecords = useMemo(() => workRecords.filter((record) => (
    (!historyFrom || record.recordDate >= historyFrom) && (!historyTo || record.recordDate <= historyTo)
  )), [historyFrom, historyTo, workRecords])
  const todayRecord = useMemo(
    () => workRecords.find((record) => record.recordDate === todayLocal) ?? null,
    [workRecords, todayLocal],
  )
  const editingRecord = useMemo(
    () => workRecords.find((record) => recordMatches(record, editingRecordId)) ?? null,
    [editingRecordId, workRecords],
  )
  const isEstimateHistoryOnly = activity != null
    && activity.status !== 'ASSIGNED'
    && activity.status !== 'IN_PROGRESS'

  const fillFormFromRecord = (record: WorkRecord, clearMessages = true) => {
    setEditingRecordId(record.id)
    setRecordDate(record.recordDate)
    setTranslatedVolume(inputValue(toNumberOrNull(record.translatedVolume) ?? 0))
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
    setRecordDate(todayLocal)
    setTranslatedVolume('')
    setWorkHours('')
    setWorkMinutes('')
    setFieldErrors({})
    setFormError(null)
    setFormSuccess(null)
  }

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

  useEffect(() => {
    if (location.hash === '#weekly-estimate') setIsEstimateOpen(true)
  }, [location.hash])

  useEffect(() => {
    if (loadedActivityId && location.hash === '#weekly-estimate') {
      document.getElementById('weekly-estimate')?.scrollIntoView?.({ block: 'start' })
    }
  }, [loadedActivityId, location.hash])

  useEffect(() => {
    if (!activity) return

    setEstimateValue(activity.currentWeekEstimate == null
      ? ''
      : inputValue(toNumberOrNull(activity.currentWeekEstimate.remainingTextVolume) ?? 0))

    if (todayRecord) {
      fillFormFromRecord(todayRecord, false)
    } else {
      setEditingRecordId(null)
      setRecordDate(todayLocal)
      setTranslatedVolume('')
      setWorkHours('')
      setWorkMinutes('')
    }
    setFieldErrors({})
    setFormError(null)
  }, [activity, todayRecord, todayLocal])

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
      translatedVolume: validateTranslatedVolume(translatedVolume) ?? undefined,
      ...workTimeErrors,
    }

    if (nextErrors.recordDate || nextErrors.translatedVolume || nextErrors.workHours || nextErrors.workMinutes) {
      setFieldErrors(nextErrors)
      setFormError(null)
      setFormSuccess(null)
      return
    }

    const payload = {
      translatedVolume: Number(translatedVolume.trim()),
      workHours: workTimePayload(workHours, workMinutes),
    }

    setIsSaving(true)
    setFieldErrors({})
    setFormError(null)
    setFormSuccess(null)

    try {
      const existingRecordForDate = workRecords.find((record) => record.recordDate === recordDate) ?? null
      const recordToUpdate = editingRecord ?? existingRecordForDate
      const updatedActivity = recordToUpdate
        ? await translatorActivityApi.updateWorkRecord(activity.id, recordToUpdate.id, payload)
        : await translatorActivityApi.createWorkRecord(activity.id, { recordDate, ...payload })
      setActivity(updatedActivity)
      setEditingRecordId(recordToUpdate?.id ?? null)
      setFormSuccess(recordToUpdate ? 'Daily record updated.' : 'Daily record saved.')
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

  const handleLifecycleAction = async (action: 'finish' | 'submit') => {
    if (!activity || isLifecycleSaving) return
    setIsLifecycleSaving(true)
    setLifecycleError(null)
    setLifecycleSuccess(null)
    try {
      const updated = action === 'finish'
        ? await translatorActivityApi.finish(activity.id)
        : await translatorActivityApi.submitForChecking(activity.id)
      setActivity(updated)
      setIsFinishConfirming(false)
      setLifecycleSuccess(action === 'finish' ? 'Activity finished.' : 'Activity submitted for checking.')
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

        {!isLoading && activity && (
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
                  <strong>{formatVolume(activity.progress.totalTranslatedVolume)} units</strong>
                </div>
              </header>

              {activity.permissions?.canEditWorkRecords && <section className="work-record-panel" aria-labelledby="record-form-title">
                <div className="section-heading">
                  <div>
                    <h2 id="record-form-title">{editingRecord ? 'Edit daily record' : 'Record daily work'}</h2>
                    <p>
                      {editingRecord
                        ? `You are editing ${formatDate(editingRecord.recordDate)}. The date stays locked; change the volume or hours only.`
                        : 'Log the units you translated on this date. You can choose a past date.'}
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

                  <div className="field-group">
                    <label htmlFor="translated-volume">Translated volume (units)</label>
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
                    <p>You reported 0 units remaining. Finish when all translation work is recorded.</p>
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
                      <button className="button button--primary" disabled={isLifecycleSaving} onClick={() => void handleLifecycleAction('finish')} type="button">
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

            {activity.status === 'FINISHED' && activity.responsible && (
              <section className={`next-step-panel lifecycle-panel${activity.permissions?.canSubmitForChecking ? ' next-step-panel--ready' : ' next-step-panel--blocked'}`} aria-labelledby="submit-title">
                <div className="section-heading">
                  <div>
                    <h2 id="submit-title">{activity.permissions?.canSubmitForChecking ? 'Ready for checking' : 'Waiting for Chief Editor assignment'}</h2>
                    <p>{activity.permissions?.canSubmitForChecking
                      ? 'Records are locked. Send this activity to the responsible Chief Editor.'
                      : 'A Project Manager must assign a responsible Chief Editor before this activity can be submitted.'}</p>
                  </div>
                  <button
                    className={activity.permissions?.canSubmitForChecking ? 'button button--primary' : 'button button--secondary'}
                    disabled={!activity.permissions?.canSubmitForChecking || isLifecycleSaving}
                    onClick={() => void handleLifecycleAction('submit')}
                    type="button"
                  >
                    {isLifecycleSaving ? 'Submitting…' : activity.permissions?.canSubmitForChecking ? 'Submit for checking' : 'Submit unavailable'}
                  </button>
                </div>
              </section>
            )}

            {activity.status === 'UNDER_CHECKING' && (
              <section className="next-step-panel lifecycle-panel" aria-labelledby="under-checking-title">
                <div className="section-heading">
                  <div>
                    <h2 id="under-checking-title">Under checking</h2>
                    <p>The activity is with the Chief Editor. Daily records and estimates are read-only.</p>
                  </div>
                </div>
              </section>
            )}

            {lifecycleError && <p className="form-alert" role="alert">{lifecycleError}</p>}
            {lifecycleSuccess && <p aria-live="polite" className="form-success" role="status">{lifecycleSuccess}</p>}

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
                          <strong>{formatVolume(estimate.remainingTextVolume)} units</strong>
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
                    ? `${formatVolume(activity.currentWeekEstimate.remainingTextVolume)} units left`
                    : 'No estimate yet'}</strong>
                  <ChevronDown aria-hidden="true" className={isEstimateOpen ? 'history-disclosure__chevron history-disclosure__chevron--open' : 'history-disclosure__chevron'} size={18} />
                </span>
              </button>
              <div className="estimate-disclosure__content" hidden={!isEstimateOpen} id="estimate-content">
                {activity.permissions?.canEditWeeklyEstimate ? (
                  <form className="estimate-form" noValidate onSubmit={(event) => void handleEstimateSubmit(event)}>
                      <div className="field-group">
                        <label htmlFor="remaining-volume">Remaining volume (units)</label>
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
                            <strong>{formatVolume(estimate.remainingTextVolume)} units</strong>
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
                      <p>{workRecords.length === 0 ? 'Recorded translated volume will appear here.' : 'Choose a wider date range or clear the dates.'}</p>
                    </div>
                  ) : (
                    <div className="record-table-wrapper">
                      <table className="record-table">
                        <thead>
                          <tr>
                            <th scope="col">Date</th>
                            <th scope="col">Translated volume</th>
                            <th scope="col">Work hours</th>
                            <th scope="col"><span className="sr-only">Actions</span></th>
                          </tr>
                        </thead>
                        <tbody>
                          {visibleWorkRecords.map((record) => (
                            <tr className={recordMatches(record, editingRecordId) ? 'record-table__row--selected' : undefined} key={record.id}>
                              <td data-label="Date">{formatDate(record.recordDate)}</td>
                              <td data-label="Volume">{formatVolume(record.translatedVolume)}</td>
                              <td data-label="Work time">{formatWorkHours(record.workHours)}</td>
                              <td>
                                {activity.permissions?.canEditWorkRecords && <button
                                  className="button button--ghost button--compact"
                                  disabled={isSaving}
                                  type="button"
                                  onClick={() => fillFormFromRecord(record)}
                                >
                                  <Pencil size={16} aria-hidden="true" />
                                  Edit
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
          </>
        )}
      </div>
    </WorkspaceLayout>
  )
}
