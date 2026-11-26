import { useEffect, useState } from 'react'
import { AlertCircle, Download, X } from 'lucide-react'
import { ReviewAnnotation } from '../types/review'
import type { ReviewCycle } from '../types/activity'
import { reviewApi } from '../lib/api'
import { PdfReviewViewer } from './PdfReviewViewer'

interface ReviewMarkedDocumentDialogProps {
  onClose: () => void
  review: ReviewCycle
}

export function ReviewMarkedDocumentDialog({ onClose, review }: ReviewMarkedDocumentDialogProps) {
  const [annotations, setAnnotations] = useState<ReviewAnnotation[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setIsLoading(true)
    setError(null)
    void reviewApi.annotations(review.id)
      .then((result) => {
        if (!cancelled) setAnnotations(result)
      })
      .catch(() => {
        if (!cancelled) setError('The review marks could not be loaded.')
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [review.id])

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section aria-label={`Marked document for cycle ${review.cycleNumber}`} aria-modal="true" className="marked-document-dialog" role="dialog">
        <header className="marked-document-dialog__header">
          <div>
            <span className={`review-kind review-kind--${(review.kind ?? 'FINAL').toLowerCase()}`}>{(review.kind ?? 'FINAL') === 'WEEKLY' ? 'Weekly version' : 'Final version'}</span>
            <h2>Chief Editor's marks · Cycle {review.cycleNumber}</h2>
            <p>{annotations.length} marks on your submitted version</p>
          </div>
          <div className="marked-document-dialog__actions">
            {review.document?.annotatedDownloadUrl && annotations.length > 0 && (
              <a className="button button--secondary button--compact" href={review.document.annotatedDownloadUrl}>
                <Download size={16} aria-hidden="true" /> PDF with notes
              </a>
            )}
            <button aria-label="Close marked document" className="button button--secondary button--compact" onClick={onClose} type="button">
              <X size={17} aria-hidden="true" />
            </button>
          </div>
        </header>
        {isLoading && <p className="quiet-empty" aria-busy="true">Loading marks…</p>}
        {!isLoading && error && (
          <div className="inline-error inline-error--compact" role="alert">
            <AlertCircle size={19} aria-hidden="true" />
            <p>{error}</p>
          </div>
        )}
        {!isLoading && !error && review.document && (
          <PdfReviewViewer annotations={annotations} fileUrl={review.document.downloadUrl} />
        )}
        {!isLoading && !error && !review.document && (
          <p className="quiet-empty">This cycle has no document.</p>
        )}
      </section>
    </div>
  )
}
