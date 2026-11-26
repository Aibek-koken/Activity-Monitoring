import { useEffect, useRef, useState, type FormEvent, type PointerEvent as ReactPointerEvent } from 'react'
import {
  AlertCircle,
  Check,
  FileSearch,
  Highlighter,
  Maximize2,
  Minus,
  MousePointer2,
  Plus,
  Strikethrough,
  Square,
  X,
} from 'lucide-react'
import type { AnnotationKind, AnnotationRect, ReviewAnnotation } from '../types/review'

interface PdfReviewViewerProps {
  activeAnnotationId?: number | null
  annotations: ReviewAnnotation[]
  fileUrl: string
  onCreateAnnotation?: (payload: {
    pageNumber: number
    kind: AnnotationKind
    rects: AnnotationRect[]
    quotedText: string | null
    comment: string | null
    suggestedReplacement: string | null
  }) => Promise<void>
  onSelectAnnotation?: (annotationId: number) => void
}

type Tool = 'read' | 'HIGHLIGHT' | 'STRIKETHROUGH' | 'RECT'

interface RenderedPage {
  pageNumber: number
  width: number
  height: number
}

interface SelectionDraft {
  pageNumber: number
  kind: AnnotationKind
  rects: AnnotationRect[]
  quotedText: string | null
}

const toolLabels: Record<Tool, string> = {
  read: 'Read and select text',
  HIGHLIGHT: 'Highlight text',
  STRIKETHROUGH: 'Strike through text',
  RECT: 'Draw a box around an area',
}

function normalizedRect(rect: DOMRect, page: DOMRect): AnnotationRect | null {
  const left = Math.max(rect.left, page.left)
  const top = Math.max(rect.top, page.top)
  const right = Math.min(rect.right, page.right)
  const bottom = Math.min(rect.bottom, page.bottom)
  if (right - left < 1 || bottom - top < 1) return null
  return {
    x: Number(((left - page.left) / page.width).toFixed(5)),
    y: Number(((top - page.top) / page.height).toFixed(5)),
    width: Number(((right - left) / page.width).toFixed(5)),
    height: Number(((bottom - top) / page.height).toFixed(5)),
  }
}

function mergeRects(rects: AnnotationRect[]): AnnotationRect[] {
  return rects.filter((rect) => rect.width > 0.001 && rect.height > 0.001)
}

export function PdfReviewViewer({
  activeAnnotationId,
  annotations,
  fileUrl,
  onCreateAnnotation,
  onSelectAnnotation,
}: PdfReviewViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const pagesRef = useRef<Map<number, HTMLDivElement>>(new Map())
  const rectStartRef = useRef<{ pageNumber: number; x: number; y: number; page: DOMRect } | null>(null)
  const [pdf, setPdf] = useState<import('pdfjs-dist').PDFDocumentProxy | null>(null)
  const [pages, setPages] = useState<RenderedPage[]>([])
  const [tool, setTool] = useState<Tool>('read')
  const [zoom, setZoom] = useState(1)
  const fitDoneRef = useRef(false)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState<SelectionDraft | null>(null)
  const [draftRect, setDraftRect] = useState<{ pageNumber: number; rect: AnnotationRect } | null>(null)
  const [comment, setComment] = useState('')
  const [replacement, setReplacement] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    if (onCreateAnnotation) return
    setTool('read')
    setDraft(null)
    setDraftRect(null)
    rectStartRef.current = null
  }, [onCreateAnnotation])

  useEffect(() => {
    let cancelled = false
    fitDoneRef.current = false
    setIsLoading(true)
    setError(null)
    setPages([])
    setPdf(null)
    void (async () => {
      try {
        const pdfjs = await import('pdfjs-dist')
        const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default
        pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
        const document = await pdfjs.getDocument({ url: fileUrl, withCredentials: true }).promise
        if (!cancelled) {
          setPdf(document)
          if (!fitDoneRef.current) {
            fitDoneRef.current = true
            const firstPage = await document.getPage(1)
            const baseWidth = firstPage.getViewport({ scale: 1 }).width
            const available = containerRef.current?.clientWidth ?? 640
            const fitted = Math.min(1, Math.max(0.45, Number(((available - 29) / baseWidth).toFixed(2))))
            setZoom(fitted)
          }
        }
      } catch {
        if (!cancelled) setError('The PDF could not be opened in the browser. You can still download it and record feedback.')
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [fileUrl])

  useEffect(() => {
    const pdfDocument = pdf
    if (!pdfDocument) return
    let cancelled = false
    const render = async () => {
      try {
        const pdfjs = await import('pdfjs-dist')
        const nextPages: RenderedPage[] = []
        for (let pageNumber = 1; pageNumber <= pdfDocument.numPages; pageNumber++) {
          if (cancelled) return
          const page = await pdfDocument.getPage(pageNumber)
          const viewport = page.getViewport({ scale: zoom })
          const wrapper = pagesRef.current.get(pageNumber)
          if (!wrapper) continue
          const canvas = wrapper.querySelector('canvas')
          if (!canvas) continue
          const context = canvas.getContext('2d')
          if (!context) continue
          const ratio = window.devicePixelRatio || 1
          canvas.width = Math.floor(viewport.width * ratio)
          canvas.height = Math.floor(viewport.height * ratio)
          canvas.style.width = `${viewport.width}px`
          canvas.style.height = `${viewport.height}px`
          await page.render({
            canvasContext: context,
            viewport,
            transform: ratio !== 1 ? [ratio, 0, 0, ratio, 0, 0] : undefined,
          }).promise
          const textLayer = wrapper.querySelector('.pdf-page__text')
          if (textLayer) {
            textLayer.innerHTML = ''
            const content = await page.getTextContent()
            for (const item of content.items) {
              if (!('str' in item) || !item.str) continue
              const transform = pdfjs.Util.transform(viewport.transform, item.transform)
              const angle = Math.atan2(transform[1], transform[0])
              const fontHeight = Math.hypot(transform[3], transform[2])
              const span = window.document.createElement('span')
              span.textContent = item.str
              span.style.left = `${transform[4]}px`
              span.style.top = `${transform[5] - fontHeight}px`
              span.style.fontSize = `${fontHeight}px`
              span.style.fontFamily = item.fontName || 'sans-serif'
              if (angle !== 0) {
                span.style.transform = `rotate(${angle}rad)`
                span.style.transformOrigin = '0 0'
              }
              if ('width' in item) span.style.width = `${item.width * zoom}px`
              textLayer.appendChild(span)
            }
          }
          nextPages.push({ pageNumber, width: viewport.width, height: viewport.height })
        }
        if (!cancelled) setPages(nextPages)
      } catch {
        if (!cancelled) setError('The PDF pages could not be rendered. Download the original to continue checking.')
      }
    }
    void render()
    return () => {
      cancelled = true
    }
  }, [pdf, zoom])

  useEffect(() => {
    if (activeAnnotationId == null) return
    const annotation = annotations.find((item) => item.id === activeAnnotationId)
    if (!annotation) return
    const page = pagesRef.current.get(annotation.pageNumber)
    page?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [activeAnnotationId, annotations])

  const clearDraft = () => {
    setDraft(null)
    setDraftRect(null)
    setComment('')
    setReplacement('')
    setFormError(null)
  }

  const handleTextSelection = (pageNumber: number) => {
    if (tool !== 'HIGHLIGHT' && tool !== 'STRIKETHROUGH') return
    const selection = window.getSelection()
    if (!selection || selection.isCollapsed || selection.rangeCount === 0) return
    const wrapper = pagesRef.current.get(pageNumber)
    if (!wrapper) return
    const page = wrapper.getBoundingClientRect()
    const range = selection.getRangeAt(0)
    const rects: AnnotationRect[] = []
    for (const clientRect of Array.from(range.getClientRects())) {
      const rect = normalizedRect(clientRect, page)
      if (rect) rects.push(rect)
    }
    if (rects.length === 0) return
    const nextDraft: SelectionDraft = {
      pageNumber,
      kind: tool,
      rects: mergeRects(rects),
      quotedText: selection.toString().trim() || null,
    }
    if (nextDraft.rects.length === 0) return
    setDraft(nextDraft)
    setDraftRect(null)
    selection.removeAllRanges()
  }

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>, pageNumber: number) => {
    if (tool !== 'RECT') return
    const wrapper = pagesRef.current.get(pageNumber)
    if (!wrapper) return
    const page = wrapper.getBoundingClientRect()
    const x = (event.clientX - page.left) / page.width
    const y = (event.clientY - page.top) / page.height
    rectStartRef.current = { pageNumber, x, y, page }
    setDraftRect({
      pageNumber,
      rect: { x, y, width: 0, height: 0 },
    })
  }

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const start = rectStartRef.current
    if (!start || tool !== 'RECT') return
    const x = (event.clientX - start.page.left) / start.page.width
    const y = (event.clientY - start.page.top) / start.page.height
    setDraftRect({
      pageNumber: start.pageNumber,
      rect: {
        x: Math.min(start.x, x),
        y: Math.min(start.y, y),
        width: Math.abs(x - start.x),
        height: Math.abs(y - start.y),
      },
    })
  }

  const handlePointerUp = () => {
    if (tool !== 'RECT') return
    const current = draftRect
    rectStartRef.current = null
    if (!current || current.rect.width < 0.005 || current.rect.height < 0.005) {
      setDraftRect(null)
      return
    }
    setDraft({
      pageNumber: current.pageNumber,
      kind: 'RECT',
      rects: [current.rect],
      quotedText: null,
    })
    setDraftRect(null)
    setComment('')
    setReplacement('')
    setFormError(null)
  }

  const saveDraft = async (event: FormEvent) => {
    event.preventDefault()
    if (!draft || !onCreateAnnotation) return
    if (!comment.trim() && !replacement.trim() && !draft.quotedText) {
      setFormError('Add a comment, selected text, or suggested replacement.')
      return
    }
    setIsSaving(true)
    setFormError(null)
    try {
      await onCreateAnnotation({
        pageNumber: draft.pageNumber,
        kind: draft.kind,
        rects: draft.rects,
        quotedText: draft.quotedText,
        comment: comment.trim() || null,
        suggestedReplacement: replacement.trim() || null,
      })
      setTool('read')
      clearDraft()
    } catch {
      setFormError('The annotation could not be saved.')
    } finally {
      setIsSaving(false)
    }
  }

  const activeToolIsMark = tool !== 'read'
  const annotationNumber = (id: number) => annotations.findIndex((item) => item.id === id) + 1

  return (
    <section className="pdf-review" aria-label="PDF review viewer">
      <div className="pdf-review__toolbar" role="toolbar" aria-label="Document tools">
        <div className="pdf-tool-group" role="group" aria-label="Markup tool">
          <button
            aria-pressed={tool === 'read'}
            className={`pdf-tool${tool === 'read' ? ' pdf-tool--active' : ''}`}
            onClick={() => { setTool('read'); clearDraft() }}
            title={toolLabels.read}
            type="button"
          >
            <MousePointer2 size={17} aria-hidden="true" />
            <span>Select</span>
          </button>
          <button
            aria-pressed={tool === 'HIGHLIGHT'}
            className={`pdf-tool${tool === 'HIGHLIGHT' ? ' pdf-tool--active' : ''}`}
            disabled={!onCreateAnnotation}
            onClick={() => { setTool('HIGHLIGHT'); clearDraft() }}
            title={toolLabels.HIGHLIGHT}
            type="button"
          >
            <Highlighter size={17} aria-hidden="true" />
            <span>Highlight</span>
          </button>
          <button
            aria-pressed={tool === 'STRIKETHROUGH'}
            className={`pdf-tool${tool === 'STRIKETHROUGH' ? ' pdf-tool--active' : ''}`}
            disabled={!onCreateAnnotation}
            onClick={() => { setTool('STRIKETHROUGH'); clearDraft() }}
            title={toolLabels.STRIKETHROUGH}
            type="button"
          >
            <Strikethrough size={17} aria-hidden="true" />
            <span>Strike</span>
          </button>
          <button
            aria-pressed={tool === 'RECT'}
            className={`pdf-tool${tool === 'RECT' ? ' pdf-tool--active' : ''}`}
            disabled={!onCreateAnnotation}
            onClick={() => { setTool('RECT'); clearDraft() }}
            title={toolLabels.RECT}
            type="button"
          >
            <Square size={17} aria-hidden="true" />
            <span>Box</span>
          </button>
        </div>
        <div className="pdf-zoom-group" role="group" aria-label="Zoom">
          <button
            aria-label="Zoom out"
            className="pdf-tool pdf-tool--icon"
            disabled={zoom <= 0.6}
            onClick={() => setZoom((value) => Math.max(0.6, Number((value - 0.15).toFixed(2))))}
            type="button"
          >
            <Minus size={17} aria-hidden="true" />
          </button>
          <span className="pdf-zoom-value">{Math.round(zoom * 100)}%</span>
          <button
            aria-label="Zoom in"
            className="pdf-tool pdf-tool--icon"
            disabled={zoom >= 2.5}
            onClick={() => setZoom((value) => Math.min(2.5, Number((value + 0.15).toFixed(2))))}
            type="button"
          >
            <Plus size={17} aria-hidden="true" />
          </button>
          <button
            aria-label="Reset zoom"
            className="pdf-tool pdf-tool--icon"
            onClick={() => setZoom(1)}
            title="Reset zoom"
            type="button"
          >
            <Maximize2 size={17} aria-hidden="true" />
          </button>
        </div>
      </div>

      {activeToolIsMark && (
        <p className="pdf-review__hint" role="status">
          {tool === 'RECT'
            ? 'Drag a rectangle around the area to mark.'
            : 'Select text in the document, then review the note form.'}
        </p>
      )}

      {draft && (
        <form className="annotation-draft" onSubmit={(event) => void saveDraft(event)}>
          <div className="annotation-draft__heading">
            <strong>New {draft.kind === 'HIGHLIGHT' ? 'highlight' : draft.kind === 'STRIKETHROUGH' ? 'strikethrough' : 'box'}</strong>
            <span>Page {draft.pageNumber}</span>
            <button aria-label="Cancel annotation" className="pdf-tool pdf-tool--icon" disabled={isSaving} onClick={clearDraft} type="button">
              <X size={16} aria-hidden="true" />
            </button>
          </div>
          {draft.quotedText && <blockquote className="annotation-draft__quote">{draft.quotedText}</blockquote>}
          <div className="field-group">
            <label htmlFor="annotation-comment">Comment</label>
            <textarea
              id="annotation-comment"
              maxLength={4000}
              placeholder="Explain the mistake and what to change."
              rows={3}
              value={comment}
              onChange={(event) => { setComment(event.target.value); setFormError(null) }}
            />
          </div>
          <div className="field-group">
            <label htmlFor="annotation-replacement">Suggested replacement</label>
            <textarea
              id="annotation-replacement"
              maxLength={4000}
              placeholder="Optional replacement text."
              rows={2}
              value={replacement}
              onChange={(event) => setReplacement(event.target.value)}
            />
          </div>
          {formError && <p className="field-error" role="alert">{formError}</p>}
          <div className="annotation-draft__actions">
            <button className="button button--secondary button--compact" disabled={isSaving} onClick={clearDraft} type="button">
              Cancel
            </button>
            <button className="button button--primary button--compact" disabled={isSaving} type="submit">
              <Check size={15} aria-hidden="true" />
              {isSaving ? 'Saving…' : 'Save note'}
            </button>
          </div>
        </form>
      )}

      {isLoading && (
        <div className="pdf-review__state" aria-busy="true">
          <span className="skeleton skeleton--title" />
          <span className="skeleton skeleton--body" />
        </div>
      )}
      {!isLoading && error && (
        <div className="inline-error inline-error--compact" role="alert">
          <AlertCircle size={20} aria-hidden="true" />
          <p>{error}</p>
        </div>
      )}

      <div className="pdf-review__pages" ref={containerRef}>
        {!isLoading && !error && pages.length === 0 && pdf && (
          <p className="quiet-empty"><FileSearch size={18} aria-hidden="true" /> Preparing pages…</p>
        )}
        {pdf && Array.from({ length: pdf.numPages }, (_, index) => index + 1).map((pageNumber) => {
          const rendered = pages.find((page) => page.pageNumber === pageNumber)
          const pageAnnotations = annotations.filter((annotation) => annotation.pageNumber === pageNumber)
          return (
            <div className="pdf-page-shell" key={pageNumber}>
              <span className="pdf-page-label">Page {pageNumber}</span>
              <div
                className={`pdf-page${tool === 'HIGHLIGHT' || tool === 'STRIKETHROUGH' ? ' pdf-page--text-tool' : ''}${tool === 'RECT' ? ' pdf-page--box-tool' : ''}`}
                data-page={pageNumber}
                onPointerDown={(event) => handlePointerDown(event, pageNumber)}
                onPointerMove={handlePointerMove}
                onPointerUp={handlePointerUp}
                ref={(node) => {
                  if (node) pagesRef.current.set(pageNumber, node)
                  else pagesRef.current.delete(pageNumber)
                }}
                style={rendered ? { width: rendered.width, height: rendered.height } : undefined}
              >
                <canvas aria-label={`PDF page ${pageNumber}`} />
                <div
                  className="pdf-page__text"
                  onMouseUp={() => handleTextSelection(pageNumber)}
                />
                <div className="pdf-page__marks">
                  {pageAnnotations.map((annotation) => {
                    const first = annotation.rects[0]
                    return (
                      <div
                        className={`pdf-mark pdf-mark--${annotation.kind.toLowerCase()}${activeAnnotationId === annotation.id ? ' pdf-mark--active' : ''}`}
                        key={annotation.id}
                      >
                        {annotation.rects.map((rect, rectIndex) => (
                          <button
                            aria-label={`Mark ${annotationNumber(annotation.id)} on page ${annotation.pageNumber}`}
                            className="pdf-mark__rect"
                            key={rectIndex}
                            onClick={(event) => {
                              event.stopPropagation()
                              onSelectAnnotation?.(annotation.id)
                            }}
                            style={{
                              left: `${rect.x * 100}%`,
                              top: `${rect.y * 100}%`,
                              width: `${rect.width * 100}%`,
                              height: `${rect.height * 100}%`,
                            }}
                            type="button"
                          />
                        ))}
                        {first && (
                          <button
                            aria-label={`Focus mark ${annotationNumber(annotation.id)}`}
                            className="pdf-mark__number"
                            onClick={(event) => {
                              event.stopPropagation()
                              onSelectAnnotation?.(annotation.id)
                            }}
                            style={{
                              left: `${first.x * 100}%`,
                              top: `${first.y * 100}%`,
                            }}
                            type="button"
                          >
                            {annotationNumber(annotation.id)}
                          </button>
                        )}
                      </div>
                    )
                  })}
                  {draftRect?.pageNumber === pageNumber && (
                    <span
                      className="pdf-draft-rect"
                      style={{
                        left: `${draftRect.rect.x * 100}%`,
                        top: `${draftRect.rect.y * 100}%`,
                        width: `${draftRect.rect.width * 100}%`,
                        height: `${draftRect.rect.height * 100}%`,
                      }}
                    />
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
