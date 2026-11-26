import { useState, type FormEvent } from 'react'
import { ChevronDown, Download, Eye, FileText, Highlighter, Paperclip, RefreshCw } from 'lucide-react'
import { formatCheckingTime, formatDate, formatPages } from '../lib/activity-format'
import type { ReviewCycle } from '../types/activity'

interface TranslatorReviewHistoryProps {
  actionError: string | null
  attachingReviewId: number | null
  canAttachLegacy: boolean
  error: string | null
  isLoading: boolean
  isOpen: boolean
  onAttach: (reviewId: number, file: File) => Promise<void>
  onOpenMarkedDocument: (review: ReviewCycle) => void
  onRetry: () => void
  onToggle: () => void
  reviews: ReviewCycle[]
}

function formatReviewTime(value: string | null | undefined): string {
  if (!value) return 'Time unavailable'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Almaty',
  }).format(date)
}

function formatFileSize(sizeBytes: number): string {
  if (sizeBytes < 1024) return `${sizeBytes} B`
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(sizeBytes < 10 * 1024 ? 1 : 0)} KB`
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
}

function resultLabel(review: ReviewCycle): string {
  if (review.result === 'APPROVED') return 'Approved'
  if (review.result === 'CORRECTION_REQUESTED') return 'Correction requested'
  return 'Under checking'
}

function kindLabel(review: ReviewCycle): string {
  return review.kind === 'WEEKLY' ? `Weekly · ${review.weekStartDate ? formatDate(review.weekStartDate) : 'this week'}` : 'Final version'
}

export function TranslatorReviewHistory({
  actionError,
  attachingReviewId,
  canAttachLegacy,
  error,
  isLoading,
  isOpen,
  onAttach,
  onOpenMarkedDocument,
  onRetry,
  onToggle,
  reviews,
}: TranslatorReviewHistoryProps) {
  const [files, setFiles] = useState<Record<number, File | null>>({})
  const [fileErrors, setFileErrors] = useState<Record<number, string>>({})

  const submitAttachment = async (event: FormEvent<HTMLFormElement>, review: ReviewCycle) => {
    event.preventDefault()
    const file = files[review.id]
    if (!file) {
      setFileErrors((current) => ({ ...current, [review.id]: 'Choose a PDF or DOCX file.' }))
      return
    }
    setFileErrors((current) => ({ ...current, [review.id]: '' }))
    await onAttach(review.id, file)
  }

  return (
    <section className="record-section review-history-section" aria-labelledby="review-history-title" id="review-history">
      <div className="history-disclosure">
        <div>
          <h2 id="review-history-title">Version history</h2>
          <p>{isLoading ? 'Loading review cycles…' : `${reviews.length} ${reviews.length === 1 ? 'cycle' : 'cycles'}`}</p>
        </div>
        <button
          aria-controls="review-history-content"
          aria-expanded={isOpen}
          aria-label={isOpen ? 'Hide review history' : 'Show review history'}
          className="button button--secondary history-disclosure__toggle"
          disabled={isLoading}
          onClick={onToggle}
          type="button"
        >
          {isOpen ? 'Hide history' : 'Show history'}
          <ChevronDown aria-hidden="true" className={isOpen ? 'history-disclosure__chevron history-disclosure__chevron--open' : 'history-disclosure__chevron'} size={17} />
        </button>
      </div>

      <div className="history-content" hidden={!isOpen} id="review-history-content">
        {error ? (
          <div className="review-history__error" role="alert">
            <span>{error}</span>
            <button className="button button--secondary button--compact" onClick={onRetry} type="button">
              <RefreshCw aria-hidden="true" size={15} />
              Retry
            </button>
          </div>
        ) : reviews.length === 0 ? (
          <p className="estimate-panel__note">No review cycles yet.</p>
        ) : (
          <ol className="review-cycle-list">
            {reviews.map((review, index) => {
              const canAttachThisReview = canAttachLegacy && index === 0 && !review.result && !review.document
              return (
                <li className={`review-cycle${review.result === 'CORRECTION_REQUESTED' ? ' review-cycle--correction' : ''}`} key={review.id}>
                  <div className="review-cycle__heading">
                    <div>
                      <strong>Cycle {review.cycleNumber}</strong>
                      <span className={`review-kind review-kind--${(review.kind ?? 'FINAL').toLowerCase()}`}>{kindLabel(review)}</span>
                      <span>{resultLabel(review)}</span>
                    </div>
                    <time dateTime={review.submittedAt}>Submitted {formatReviewTime(review.submittedAt)}</time>
                  </div>

                  {review.feedback && (
                    <blockquote className="review-cycle__feedback">{review.feedback}</blockquote>
                  )}

                  <div className="review-cycle__meta">
                    {review.correctionPages != null && <span>{review.correctionPageNumbers?.length
                      ? `Pages ${review.correctionPageNumbers.join(', ')} to redo`
                      : `${formatPages(review.correctionPages)} to redo`}</span>}
                    {(review.annotationCount ?? 0) > 0 && <span><Highlighter size={13} aria-hidden="true" /> {review.annotationCount} {review.annotationCount === 1 ? 'mark' : 'marks'}</span>}
                    {review.expectedCheckingHours != null && <span>{formatCheckingTime(review.expectedCheckingHours)} checking time</span>}
                    {review.decidedAt && <span>Decided {formatReviewTime(review.decidedAt)}</span>}
                  </div>

                  {review.document ? (
                    <div className="review-document">
                      <span className="review-document__icon" aria-hidden="true"><FileText size={18} /></span>
                      <span>
                        <strong>{review.document.originalName}</strong>
                        <small>{formatFileSize(review.document.sizeBytes)}</small>
                      </span>
                      <div className="review-document__actions">
                        {(review.annotationCount ?? 0) > 0 && (
                          <button
                            className="button button--secondary button--compact"
                            onClick={() => onOpenMarkedDocument(review)}
                            type="button"
                          >
                            <Eye aria-hidden="true" size={15} />
                            View marks
                          </button>
                        )}
                        <a
                          aria-label={`Download ${review.document.originalName}`}
                          className="button button--secondary button--compact"
                          href={review.document.downloadUrl}
                        >
                          <Download aria-hidden="true" size={15} />
                          Download
                        </a>
                      </div>
                    </div>
                  ) : canAttachThisReview ? (
                    <form className="legacy-attachment-form" onSubmit={(event) => void submitAttachment(event, review)}>
                      <div className="field-group">
                        <label htmlFor={`legacy-document-${review.id}`}>Document for cycle {review.cycleNumber}</label>
                        <input
                          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                          disabled={attachingReviewId === review.id}
                          id={`legacy-document-${review.id}`}
                          onChange={(event) => {
                            setFiles((current) => ({ ...current, [review.id]: event.target.files?.[0] ?? null }))
                            setFileErrors((current) => ({ ...current, [review.id]: '' }))
                          }}
                          type="file"
                        />
                        {fileErrors[review.id] && <p className="field-error">{fileErrors[review.id]}</p>}
                      </div>
                      <button className="button button--secondary" disabled={attachingReviewId === review.id} type="submit">
                        <Paperclip aria-hidden="true" size={16} />
                        {attachingReviewId === review.id ? 'Attaching…' : 'Attach document'}
                      </button>
                    </form>
                  ) : (
                    <p className="review-cycle__missing-document">No document is attached to this cycle.</p>
                  )}
                </li>
              )
            })}
          </ol>
        )}
        {actionError && <p className="form-alert" role="alert">{actionError}</p>}
      </div>
    </section>
  )
}
