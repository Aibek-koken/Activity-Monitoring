import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  AlertCircle,
  Check,
  ChevronDown,
  ClipboardCheck,
  Clock3,
  Download,
  FileText,
  Highlighter,
  Pencil,
  RefreshCw,
  RotateCcw,
  Square,
  Strikethrough,
  Trash2,
  X,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { PdfReviewViewer } from '../components/PdfReviewViewer'
import { WorkspaceLayout } from '../components/WorkspaceLayout'
import { formatCheckingTime, formatDate, formatDateTimeInAlmaty, formatPages, toNumberOrNull } from '../lib/activity-format'
import { ApiError, chiefEditorApi } from '../lib/api'
import { parseCorrectionPages } from '../lib/correction-pages'
import type { AnnotationKind, ChiefEditorReview, ReviewAnnotation, ReviewResult } from '../types/review'

const reviewNav = [{
  end: false,
  icon: <ClipboardCheck size={18} aria-hidden="true" />,
  label: 'Review queue',
  to: '/chief-editor',
}]

const resultLabels: Record<ReviewResult, string> = {
  APPROVED: 'Approved',
  CORRECTION_REQUESTED: 'Returned for correction',
}

const kindLabels = {
  WEEKLY: 'Weekly version',
  FINAL: 'Final version',
}

const kindIcons: Record<AnnotationKind, typeof Highlighter> = {
  HIGHLIGHT: Highlighter,
  STRIKETHROUGH: Strikethrough,
  RECT: Square,
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function documentKind(contentType: string) {
  return contentType === 'application/pdf' ? 'PDF document' : 'DOCX document'
}

function errorMessage(caught: unknown, fallback: string) {
  return caught instanceof ApiError ? caught.message : fallback
}

export function ChiefEditorReviewDetailPage() {
  const navigate = useNavigate()
  const { reviewId: reviewIdParam } = useParams()
  const reviewId = Number(reviewIdParam)
  const [review, setReview] = useState<ChiefEditorReview | null>(null)
  const [hours, setHours] = useState('')
  const [minutes, setMinutes] = useState('')
  const [feedback, setFeedback] = useState('')
  const [correctionPages, setCorrectionPages] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSavingTime, setIsSavingTime] = useState(false)
  const [decisionInProgress, setDecisionInProgress] = useState<ReviewResult | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [timeError, setTimeError] = useState<string | null>(null)
  const [decisionError, setDecisionError] = useState<string | null>(null)
  const [timeSaved, setTimeSaved] = useState(false)
  const [activeAnnotationId, setActiveAnnotationId] = useState<number | null>(null)
  const [editingAnnotation, setEditingAnnotation] = useState<ReviewAnnotation | null>(null)
  const [editComment, setEditComment] = useState('')
  const [editReplacement, setEditReplacement] = useState('')
  const [annotationError, setAnnotationError] = useState<string | null>(null)
  const [deleteCandidateId, setDeleteCandidateId] = useState<number | null>(null)
  const [skipDeleteConfirm, setSkipDeleteConfirm] = useState(() => localStorage.getItem('easylang:skip-mark-delete-confirm') === 'true')
  const [rememberSkipDelete, setRememberSkipDelete] = useState(false)
  const [showCycles, setShowCycles] = useState(false)

  const loadReview = useCallback(async () => {
    if (!Number.isSafeInteger(reviewId) || reviewId <= 0) {
      setLoadError('This review address is invalid.')
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setLoadError(null)
    try {
      const loaded = await chiefEditorApi.getReview(reviewId)
      setReview(loaded)
      const totalMinutes = loaded.expectedCheckingHours == null ? null : Math.round(Number(loaded.expectedCheckingHours) * 60)
      setHours(totalMinutes == null ? '' : String(Math.floor(totalMinutes / 60)))
      setMinutes(totalMinutes == null ? '' : String(totalMinutes % 60))
      setFeedback(loaded.feedback ?? '')
      setCorrectionPages(loaded.correctionPageNumbers?.length ? loaded.correctionPageNumbers.join(', ') : '')
    } catch (caught) {
      setLoadError(errorMessage(caught, 'This review could not be loaded. Check the API connection and try again.'))
    } finally {
      setIsLoading(false)
    }
  }, [reviewId])

  useEffect(() => {
    void loadReview()
  }, [loadReview])

  useEffect(() => {
    if (activeAnnotationId == null) return
    document.querySelector('.annotation-item--active')?.scrollIntoView?.({ block: 'nearest' })
  }, [activeAnnotationId])

  const saveExpectedTime = async (event: FormEvent) => {
    event.preventDefault()
    const hourCount = Number(hours || 0)
    const minuteCount = Number(minutes || 0)
    const totalMinutes = hourCount * 60 + minuteCount
    if (!/^\d{0,4}$/.test(hours) || !/^\d{0,2}$/.test(minutes)
      || !Number.isInteger(hourCount) || !Number.isInteger(minuteCount)
      || minuteCount > 59 || totalMinutes < 1 || totalMinutes > 60000) {
      setTimeError('Enter 1 minute to 1000 hours. Minutes must be between 0 and 59.')
      setTimeSaved(false)
      return
    }
    setIsSavingTime(true)
    setTimeError(null)
    setTimeSaved(false)
    try {
      const value = Math.round(totalMinutes / 60 * 100) / 100
      const updated = await chiefEditorApi.updateExpectedTime(reviewId, value)
      setReview(updated)
      const savedMinutes = Math.round(Number(updated.expectedCheckingHours ?? value) * 60)
      setHours(String(Math.floor(savedMinutes / 60)))
      setMinutes(String(savedMinutes % 60))
      setTimeSaved(true)
    } catch (caught) {
      setTimeError(errorMessage(caught, 'Expected time could not be saved.'))
    } finally {
      setIsSavingTime(false)
    }
  }

  const decide = async (result: ReviewResult) => {
    if (result === 'CORRECTION_REQUESTED') {
      if (!feedback.trim()) {
        setDecisionError('Enter feedback before returning the version.')
        return
      }
      if (!parseCorrectionPages(correctionPages)) {
        setDecisionError('Enter page numbers such as 1, 2, 5–7.')
        return
      }
    }
    setDecisionError(null)
    setDecisionInProgress(result)
    try {
      const updated = await chiefEditorApi.decide(
        reviewId,
        result,
        feedback,
        result === 'CORRECTION_REQUESTED' ? parseCorrectionPages(correctionPages) : null,
      )
      setReview(updated)
      setFeedback(updated.feedback ?? '')
      setEditingAnnotation(null)
      navigate(`/chief-editor?project=${updated.projectId}`, {
        replace: true,
        state: { decisionNotice: `${updated.activityNumber}: ${resultLabels[result].toLowerCase()}. The version is closed and removed from the open queue.` },
      })
    } catch (caught) {
      setDecisionError(errorMessage(caught, 'The review decision could not be recorded.'))
    } finally {
      setDecisionInProgress(null)
    }
  }

  const createAnnotation = async (payload: {
    pageNumber: number
    kind: AnnotationKind
    rects: { x: number; y: number; width: number; height: number }[]
    quotedText: string | null
    comment: string | null
    suggestedReplacement: string | null
  }) => {
    const created = await chiefEditorApi.createAnnotation(reviewId, payload)
    setReview((current) => current ? { ...current, annotations: [...current.annotations, created] } : current)
    setActiveAnnotationId(created.id)
    setAnnotationError(null)
  }

  const startEditing = (annotation: ReviewAnnotation) => {
    setEditingAnnotation(annotation)
    setActiveAnnotationId(annotation.id)
    setEditComment(annotation.comment ?? '')
    setEditReplacement(annotation.suggestedReplacement ?? '')
    setAnnotationError(null)
  }

  const saveAnnotation = async (event: FormEvent) => {
    event.preventDefault()
    if (!editingAnnotation) return
    if (!editComment.trim() && !editReplacement.trim() && !editingAnnotation.quotedText) {
      setAnnotationError('Keep a comment, selected text, or suggested replacement.')
      return
    }
    try {
      const updated = await chiefEditorApi.updateAnnotation(reviewId, editingAnnotation.id, {
        pageNumber: editingAnnotation.pageNumber,
        kind: editingAnnotation.kind,
        rects: editingAnnotation.rects,
        quotedText: editingAnnotation.quotedText ?? null,
        comment: editComment.trim() || null,
        suggestedReplacement: editReplacement.trim() || null,
      })
      setReview((current) => current
        ? { ...current, annotations: current.annotations.map((item) => item.id === updated.id ? updated : item) }
        : current)
      setEditingAnnotation(null)
      setAnnotationError(null)
    } catch (caught) {
      setAnnotationError(errorMessage(caught, 'The annotation could not be updated.'))
    }
  }

  const deleteAnnotation = async (annotationId: number) => {
    try {
      await chiefEditorApi.deleteAnnotation(reviewId, annotationId)
      setReview((current) => current
        ? { ...current, annotations: current.annotations.filter((item) => item.id !== annotationId) }
        : current)
      if (editingAnnotation?.id === annotationId) setEditingAnnotation(null)
      if (activeAnnotationId === annotationId) setActiveAnnotationId(null)
      setDeleteCandidateId(null)
      if (rememberSkipDelete) {
        localStorage.setItem('easylang:skip-mark-delete-confirm', 'true')
        setSkipDeleteConfirm(true)
      }
      setAnnotationError(null)
    } catch (caught) {
      setAnnotationError(errorMessage(caught, 'The annotation could not be deleted.'))
      if (caught instanceof ApiError && caught.status === 404) void loadReview()
    }
  }

  const isOpen = review?.result == null
  const isPdf = review?.document?.contentType === 'application/pdf'
  const knownHours = toNumberOrNull(review?.expectedCheckingHours)
  const totalAnnotations = review?.annotations.length ?? 0
  const annotationIndex = useMemo(() => {
    const map = new Map<number, number>()
    review?.annotations.forEach((annotation, index) => map.set(annotation.id, index + 1))
    return map
  }, [review?.annotations])

  return (
    <WorkspaceLayout navItems={reviewNav} role="CHIEF_EDITOR" title="Review detail">
      <div className="workspace-content chief-editor-workspace chief-editor-review-detail">
        {isLoading && (
          <section className="review-loading" aria-busy="true" aria-label="Loading review detail">
            <span className="skeleton skeleton--title" />
            <span className="skeleton skeleton--body" />
            <span className="skeleton skeleton--body" />
          </section>
        )}

        {!isLoading && loadError && (
          <section className="inline-error" role="alert">
            <AlertCircle size={22} aria-hidden="true" />
            <div>
              <h2>Review unavailable</h2>
              <p>{loadError}</p>
            </div>
            <div className="inline-error__actions">
              <Link className="button button--secondary" to="/chief-editor">Back to queue</Link>
              <button className="button button--secondary" type="button" onClick={() => void loadReview()}>
                <RefreshCw size={17} aria-hidden="true" /> Retry
              </button>
            </div>
          </section>
        )}

        {!isLoading && review && (
          <>
            <Breadcrumbs items={[
              { label: 'Review queue', to: '/chief-editor' },
              { label: `${review.activityNumber} · Cycle ${review.cycleNumber}` },
            ]} />

            <header className="review-detail-heading">
              <div>
                <div className="review-detail-heading__context">
                  <span>{review.activityNumber}</span>
                  <span className={`review-kind review-kind--${review.kind.toLowerCase()}`}>{kindLabels[review.kind]}</span>
                  <span>Cycle {review.cycleNumber}</span>
                  <span className={`review-result review-result--${review.result?.toLowerCase() ?? 'open'}`}>
                    {review.result ? resultLabels[review.result] : 'Open review'}
                  </span>
                </div>
                <h1>{review.activityName}</h1>
                <p>
                  {review.projectName}
                  {' · '}
                  Submitted {formatDateTimeInAlmaty(review.submittedAt)}
                  {review.kind === 'WEEKLY' && review.weekStartDate
                    ? ` · Week of ${formatDate(review.weekStartDate)}`
                    : ''}
                </p>
              </div>
              {review.document && (
                <div className="review-heading-actions">
                  <a className="button button--secondary" href={review.document.downloadUrl}>
                    <Download size={17} aria-hidden="true" /> Download original
                  </a>
                  {review.document.annotatedDownloadUrl && totalAnnotations > 0 && (
                    <a className="button button--secondary" href={review.document.annotatedDownloadUrl}>
                      <Download size={17} aria-hidden="true" /> PDF with notes
                    </a>
                  )}
                </div>
              )}
            </header>

            <div className="review-workbench">
              <section className="review-document-panel" aria-labelledby="review-document-title">
                <div className="review-section-heading">
                  <span aria-hidden="true"><FileText size={19} /></span>
                  <div>
                    <h2 id="review-document-title">Submitted document</h2>
                    <p>
                      {review.document
                        ? `${documentKind(review.document.contentType)} · ${formatBytes(review.document.sizeBytes)}`
                        : 'No document is attached to this cycle.'}
                    </p>
                  </div>
                </div>

                {!review.document && (
                  <div className="empty-state empty-state--compact">
                    <h3>Document unavailable</h3>
                    <p>This legacy review has no attached file. Use the feedback field to record the decision.</p>
                  </div>
                )}

                {review.document && isPdf && (
                  <PdfReviewViewer
                    activeAnnotationId={activeAnnotationId}
                    annotations={review.annotations}
                    fileUrl={review.document.downloadUrl}
                    onCreateAnnotation={isOpen ? createAnnotation : undefined}
                    onSelectAnnotation={(annotationId) => {
                      setActiveAnnotationId(annotationId)
                      setEditingAnnotation(null)
                    }}
                  />
                )}

                {review.document && !isPdf && (
                  <div className="docx-review-note">
                    <FileText size={26} aria-hidden="true" />
                    <div>
                      <h3>DOCX review</h3>
                      <p>
                        In-app text marks are available for PDF only. Download the DOCX, check it in your editor,
                        then record feedback and the number of pages to redo here.
                      </p>
                      <a className="button button--secondary button--compact" href={review.document.downloadUrl}>
                        <Download size={16} aria-hidden="true" /> Download DOCX
                      </a>
                    </div>
                  </div>
                )}
              </section>

              <aside className="review-side">
                <section className="annotation-panel" aria-labelledby="annotation-panel-title">
                  <div className="review-section-heading review-section-heading--compact">
                    <span aria-hidden="true"><Highlighter size={18} /></span>
                    <div>
                      <h2 id="annotation-panel-title">Feedback marks</h2>
                      <p>{totalAnnotations === 0 ? 'No marks yet' : `${totalAnnotations} on this version`}</p>
                    </div>
                  </div>

                  {totalAnnotations === 0 ? (
                    <p className="quiet-empty">
                      {isPdf
                        ? 'Select text or draw a box in the document to add a mark.'
                        : 'Marks are available for PDF. Record your feedback below.'}
                    </p>
                  ) : (
                    <ol className="annotation-list">
                      {review.annotations.map((annotation) => {
                        const Icon = kindIcons[annotation.kind]
                        const isEditing = editingAnnotation?.id === annotation.id
                        return (
                          <li
                            className={`annotation-item${activeAnnotationId === annotation.id ? ' annotation-item--active' : ''}`}
                            key={annotation.id}
                          >
                            <button
                              className="annotation-item__focus"
                              onClick={() => setActiveAnnotationId(annotation.id)}
                              type="button"
                            >
                              <span className="annotation-item__number">{annotationIndex.get(annotation.id)}</span>
                              <span className="annotation-item__body">
                                <span className="annotation-item__meta">
                                  <Icon size={14} aria-hidden="true" />
                                  Page {annotation.pageNumber}
                                </span>
                                {annotation.quotedText && <span className="annotation-item__quote">“{annotation.quotedText}”</span>}
                                {annotation.comment && <span>{annotation.comment}</span>}
                                {annotation.suggestedReplacement && (
                                  <span className="annotation-item__replacement">Use: {annotation.suggestedReplacement}</span>
                                )}
                              </span>
                            </button>
                            {isOpen && (
                              <div className="annotation-item__actions">
                                <button
                                  aria-label={`Edit mark ${annotationIndex.get(annotation.id)}`}
                                  className="pdf-tool pdf-tool--icon"
                                  onClick={() => startEditing(annotation)}
                                  type="button"
                                >
                                  <Pencil size={14} aria-hidden="true" />
                                </button>
                                <button
                                  aria-label={`Delete mark ${annotationIndex.get(annotation.id)}`}
                                  className="pdf-tool pdf-tool--icon pdf-tool--danger"
                                  onClick={() => skipDeleteConfirm ? void deleteAnnotation(annotation.id) : setDeleteCandidateId(annotation.id)}
                                  type="button"
                                >
                                  <Trash2 size={14} aria-hidden="true" />
                                </button>
                              </div>
                            )}
                            {isEditing && (
                              <form className="annotation-edit" onSubmit={(event) => void saveAnnotation(event)}>
                                <div className="field-group">
                                  <label htmlFor={`annotation-comment-${annotation.id}`}>Comment</label>
                                  <textarea
                                    id={`annotation-comment-${annotation.id}`}
                                    maxLength={4000}
                                    rows={3}
                                    value={editComment}
                                    onChange={(event) => setEditComment(event.target.value)}
                                  />
                                </div>
                                <div className="field-group">
                                  <label htmlFor={`annotation-replacement-${annotation.id}`}>Suggested replacement</label>
                                  <textarea
                                    id={`annotation-replacement-${annotation.id}`}
                                    maxLength={4000}
                                    rows={2}
                                    value={editReplacement}
                                    onChange={(event) => setEditReplacement(event.target.value)}
                                  />
                                </div>
                                {annotationError && <p className="field-error" role="alert">{annotationError}</p>}
                                <div className="annotation-draft__actions">
                                  <button className="button button--secondary button--compact" onClick={() => setEditingAnnotation(null)} type="button">
                                    <X size={15} aria-hidden="true" /> Cancel
                                  </button>
                                  <button className="button button--primary button--compact" type="submit">
                                    <Check size={15} aria-hidden="true" /> Save
                                  </button>
                                </div>
                              </form>
                            )}
                          </li>
                        )
                      })}
                    </ol>
                  )}
                </section>

                {isOpen ? (
                  <>
                    <section className="review-time-panel review-side-card" aria-labelledby="expected-time-title">
                      <div className="review-section-heading review-section-heading--compact">
                        <span aria-hidden="true"><Clock3 size={18} /></span>
                        <div>
                          <h2 id="expected-time-title">Expected time</h2>
                          <p>Used for your weekly queue estimate.</p>
                        </div>
                      </div>
                      <form onSubmit={(event) => void saveExpectedTime(event)}>
                        <div className="review-time-fields">
                          <div className="field-group">
                            <label htmlFor="expected-hours">Hours</label>
                            <input
                              aria-invalid={Boolean(timeError)}
                              id="expected-hours"
                              inputMode="numeric"
                              max="1000"
                              min="0"
                              placeholder="0"
                              step="1"
                              type="number"
                              value={hours}
                              onChange={(event) => {
                                setHours(event.target.value)
                                setTimeError(null)
                                setTimeSaved(false)
                              }}
                            />
                          </div>
                          <div className="field-group">
                            <label htmlFor="expected-minutes">Minutes</label>
                            <input
                              aria-invalid={Boolean(timeError)}
                              id="expected-minutes"
                              inputMode="numeric"
                              max="59"
                              min="0"
                              placeholder="0"
                              step="1"
                              type="number"
                              value={minutes}
                              onChange={(event) => {
                                setMinutes(event.target.value)
                                setTimeError(null)
                                setTimeSaved(false)
                              }}
                            />
                          </div>
                        </div>
                        {timeError && <p className="field-error" role="alert">{timeError}</p>}
                        {timeSaved && <p className="form-success">Expected time saved.</p>}
                        <button className="button button--secondary button--full" disabled={isSavingTime} type="submit">
                          {isSavingTime ? 'Saving…' : 'Save expected time'}
                        </button>
                      </form>
                    </section>

                    <section className="review-decision-panel review-side-card" aria-labelledby="review-decision-title">
                      <div className="review-section-heading review-section-heading--compact">
                        <span aria-hidden="true"><ClipboardCheck size={19} /></span>
                        <div>
                          <h2 id="review-decision-title">Decision</h2>
                          <p>One decision closes this version.</p>
                        </div>
                      </div>
                      <div className="field-group">
                        <label htmlFor="review-feedback">Feedback</label>
                        <textarea
                          id="review-feedback"
                          maxLength={4000}
                          placeholder="Describe what is good or what must change."
                          rows={5}
                          value={feedback}
                          onChange={(event) => {
                            setFeedback(event.target.value)
                            setDecisionError(null)
                          }}
                        />
                      </div>
                      <div className="field-group">
                        <label htmlFor="correction-pages">Pages to redo if returned</label>
                        <input
                          id="correction-pages"
                          inputMode="text"
                          placeholder="e.g. 1, 2, 5–7"
                          type="text"
                          value={correctionPages}
                          onChange={(event) => {
                            setCorrectionPages(event.target.value)
                            setDecisionError(null)
                          }}
                        />
                        <p className="field-hint">Enter page numbers separated by commas. The translator sees these pages.</p>
                      </div>
                      {decisionError && <p className="form-alert" role="alert">{decisionError}</p>}
                      <div className="review-decision-actions">
                        <button
                          className="button button--secondary review-correction-button"
                          disabled={decisionInProgress != null}
                          type="button"
                          onClick={() => void decide('CORRECTION_REQUESTED')}
                        >
                          <RotateCcw size={17} aria-hidden="true" />
                          {decisionInProgress === 'CORRECTION_REQUESTED' ? 'Returning…' : 'Return for correction'}
                        </button>
                        <button
                          className="button button--primary"
                          disabled={decisionInProgress != null}
                          type="button"
                          onClick={() => void decide('APPROVED')}
                        >
                          <Check size={17} aria-hidden="true" />
                          {decisionInProgress === 'APPROVED' ? 'Approving…' : 'Approve version'}
                        </button>
                      </div>
                      <p className="review-decision-note">
                        {review.kind === 'WEEKLY'
                          ? 'Approving a weekly version keeps the activity open for daily work.'
                          : 'Approving the final version completes the activity.'}
                      </p>
                    </section>
                  </>
                ) : (
                  <section className="review-history-panel review-side-card" aria-labelledby="review-history-title">
                    <div className="review-section-heading review-section-heading--compact">
                      <span aria-hidden="true"><ClipboardCheck size={19} /></span>
                      <div>
                        <h2 id="review-history-title">Closed review</h2>
                        <p>This version is read-only.</p>
                      </div>
                    </div>
                    <dl className="review-history-list">
                      <div>
                        <dt>Result</dt>
                        <dd>{resultLabels[review.result!]}</dd>
                      </div>
                      <div>
                        <dt>Expected time</dt>
                        <dd>{knownHours == null ? 'Not recorded' : formatCheckingTime(knownHours)}</dd>
                      </div>
                      {review.correctionPages != null && (
                        <div>
                          <dt>Pages to redo</dt>
                          <dd>{review.correctionPageNumbers?.length ? review.correctionPageNumbers.join(', ') : formatPages(review.correctionPages)}</dd>
                        </div>
                      )}
                      <div>
                        <dt>Decision recorded</dt>
                        <dd>{formatDateTimeInAlmaty(review.decidedAt)}</dd>
                      </div>
                      <div className="review-history-list__feedback">
                        <dt>Feedback</dt>
                        <dd>{review.feedback || 'No feedback was recorded.'}</dd>
                      </div>
                    </dl>
                  </section>
                )}

                {review.previousCycles.length > 0 && (
                  <section className="review-side-card review-cycles-panel">
                    <button
                      aria-expanded={showCycles}
                      className="review-cycles-toggle"
                      onClick={() => setShowCycles((value) => !value)}
                      type="button"
                    >
                      <span>Earlier cycles ({review.previousCycles.length})</span>
                      <ChevronDown className={showCycles ? 'review-cycles-toggle__icon review-cycles-toggle__icon--open' : 'review-cycles-toggle__icon'} size={17} aria-hidden="true" />
                    </button>
                    <div hidden={!showCycles}>
                      <ol className="review-cycle-list review-cycle-list--compact">
                        {review.previousCycles.map((cycle) => (
                          <li className="review-cycle" key={cycle.id}>
                            <div className="review-cycle__heading">
                              <div>
                                <strong>Cycle {cycle.cycleNumber}</strong>
                                <span>{kindLabels[cycle.kind]} · {cycle.result ? resultLabels[cycle.result] : 'Open'}</span>
                              </div>
                              <time dateTime={cycle.submittedAt}>{formatDateTimeInAlmaty(cycle.submittedAt)}</time>
                            </div>
                            {cycle.feedback && <p className="review-cycle__feedback">{cycle.feedback}</p>}
                            <div className="review-cycle__meta">
                              {cycle.correctionPages != null && <span>Pages {cycle.correctionPageNumbers?.length ? cycle.correctionPageNumbers.join(', ') : formatPages(cycle.correctionPages)} to redo</span>}
                              <span>{cycle.annotationCount} marks</span>
                              {cycle.document && <a href={cycle.document.downloadUrl}>Download</a>}
                            </div>
                          </li>
                        ))}
                      </ol>
                    </div>
                  </section>
                )}
              </aside>
            </div>
          </>
        )}
        {deleteCandidateId != null && <div className="notification-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setDeleteCandidateId(null) }}>
          <section aria-labelledby="mark-delete-title" aria-modal="true" className="notification-dialog" role="dialog">
            <header><h2 id="mark-delete-title">Delete this mark?</h2><button aria-label="Cancel deletion" onClick={() => setDeleteCandidateId(null)} type="button"><X size={20} /></button></header>
            <p>The mark and its comment will be removed from this open review.</p>
            <label className="review-delete-preference"><input checked={rememberSkipDelete} onChange={(event) => setRememberSkipDelete(event.target.checked)} type="checkbox" /> Don’t ask again in this browser</label>
            <div className="notification-dialog__actions"><button className="button button--secondary" onClick={() => setDeleteCandidateId(null)} type="button">Cancel</button>
              <button className="button button--primary" onClick={() => void deleteAnnotation(deleteCandidateId)} type="button">Delete mark</button></div>
          </section>
        </div>}
      </div>
    </WorkspaceLayout>
  )
}
