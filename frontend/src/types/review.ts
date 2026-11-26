export type ReviewResult = 'APPROVED' | 'CORRECTION_REQUESTED'
export type ReviewKind = 'WEEKLY' | 'FINAL'
export type AnnotationKind = 'HIGHLIGHT' | 'STRIKETHROUGH' | 'RECT'

export interface AnnotationRect {
  x: number
  y: number
  width: number
  height: number
}

export interface ReviewAnnotation {
  id: number
  pageNumber: number
  kind: AnnotationKind
  rects: AnnotationRect[]
  quotedText?: string | null
  comment?: string | null
  suggestedReplacement?: string | null
  createdAt: string
  updatedAt: string
}

export interface AnnotationPayload {
  pageNumber: number
  kind: AnnotationKind
  rects: AnnotationRect[]
  quotedText?: string | null
  comment?: string | null
  suggestedReplacement?: string | null
}

export interface AssignedProject {
  id: number
  projectName: string
  responsible: boolean
  createdDate: string
}

export interface ReviewQueueItem {
  id: number
  activityId: number
  activityNumber: string
  activityName: string
  projectId: number
  projectName: string
  cycleNumber: number
  kind: ReviewKind
  weekStartDate?: string | null
  submittedAt: string
  expectedCheckingHours: number | string | null
  hasDocument: boolean
  annotationCount: number
}

export interface ReviewQueue {
  reviews: ReviewQueueItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ReviewWorkload {
  openCount: number
  knownExpectedHours: number | string
  missingEstimateCount: number
  availableWeeklyHours?: number | string | null
  remainingHours?: number | string | null
  queueExceedsWeek?: boolean | null
  completeForecast: boolean
  availabilityUpdatedAt?: string | null
}

export interface EditorAvailability {
  weeklyHours?: number | string | null
  updatedAt?: string | null
}

export interface ReviewDocument {
  originalName: string
  contentType: string
  sizeBytes: number
  sha256: string
  downloadUrl: string
  annotatedDownloadUrl?: string | null
}

export interface ChiefEditorReview {
  id: number
  activityId: number
  activityNumber: string
  activityName: string
  projectId: number
  projectName: string
  activityStatus: string
  cycleNumber: number
  kind: ReviewKind
  weekStartDate?: string | null
  submittedAt: string
  result: ReviewResult | null
  feedback: string | null
  expectedCheckingHours: number | string | null
  correctionPages?: number | null
  correctionPageNumbers?: number[]
  decidedAt: string | null
  document: ReviewDocument | null
  annotations: ReviewAnnotation[]
  previousCycles: ReviewCycleSummary[]
}

export interface ReviewCycleSummary {
  id: number
  cycleNumber: number
  kind: ReviewKind
  weekStartDate?: string | null
  submittedAt: string
  result: ReviewResult | null
  feedback: string | null
  expectedCheckingHours: number | string | null
  correctionPages?: number | null
  correctionPageNumbers?: number[]
  decidedAt: string | null
  annotationCount: number
  document: ReviewDocument | null
}
