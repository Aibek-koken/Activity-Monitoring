import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { AlertCircle, ArrowLeft, ClipboardList, Pencil, Plus, RefreshCw, Save } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { activityStatusLabels, formatDate, formatVolume, formatWorkHours, statusClassName, toNumberOrNull } from '../lib/activity-format'
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
  const todayLocal = todayLocalDateString()
  const latestAllowedDate = latestAllowedRecordDateString()
  const workRecords = useMemo(() => activity?.workRecords ?? [], [activity?.workRecords])
  const todayRecord = useMemo(
    () => workRecords.find((record) => record.recordDate === todayLocal) ?? null,
    [workRecords, todayLocal],
  )
  const editingRecord = useMemo(
    () => workRecords.find((record) => recordMatches(record, editingRecordId)) ?? null,
    [editingRecordId, workRecords],
  )

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
    if (!activity) return

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

            <dl className="detail-grid" aria-label="Activity progress">
              <div>
                <dt>Translated</dt>
                <dd>{formatVolume(activity.progress.totalTranslatedVolume)}</dd>
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
            </dl>

            <section className="work-record-panel" aria-labelledby="record-form-title">
              <div className="section-heading">
                <div>
                  <h2 id="record-form-title">{editingRecord ? 'Edit daily record' : 'Record translated volume'}</h2>
                  <p>
                    {editingRecord
                      ? `You are editing ${formatDate(editingRecord.recordDate)}. The date stays locked; change the volume or hours only.`
                      : 'Choose today or a past date. Future dates are blocked.'}
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
            </section>

            <section className="record-section" aria-labelledby="history-title">
              <div className="section-heading">
                <h2 id="history-title">Daily history</h2>
                <p>{activity.responsible ? 'Responsible translator' : 'Assisting translator'}</p>
              </div>

              {workRecords.length === 0 ? (
                <div className="empty-state empty-state--compact">
                  <h3>No daily records yet</h3>
                  <p>Recorded translated volume will appear here.</p>
                </div>
              ) : (
                <div className="record-table-wrapper">
                  <table className="record-table">
                    <thead>
                      <tr>
                        <th scope="col">Date</th>
                        <th scope="col">Translated volume</th>
                        <th scope="col">Work hours</th>
                        <th scope="col">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {workRecords.map((record) => (
                        <tr className={recordMatches(record, editingRecordId) ? 'record-table__row--selected' : undefined} key={record.id}>
                          <td>{formatDate(record.recordDate)}</td>
                          <td>{formatVolume(record.translatedVolume)}</td>
                          <td>{formatWorkHours(record.workHours)}</td>
                          <td>
                            <button
                              className="button button--ghost button--compact"
                              disabled={isSaving}
                              type="button"
                              onClick={() => fillFormFromRecord(record)}
                            >
                              <Pencil size={16} aria-hidden="true" />
                              Edit
                            </button>
                          </td>
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
