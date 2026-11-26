export type ActivityStatus =
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'FINISHED'
  | 'UNDER_CHECKING'
  | 'CORRECTION_REQUIRED'
  | 'CORRECTED'
  | 'COMPLETED'

export interface ProgressSummary {
  totalTranslatedVolume: number
  recordCount: number
  /**
   * Nullable. The API serializes with `non_null` inclusion, so the key is absent
   * rather than `null` when an activity has no records.
   */
  lastRecordDate?: string | null
}

export interface ActivitySummary {
  id: number
  activityNumber: string
  activityName: string
  projectName: string
  status: ActivityStatus
  responsible: boolean
  assignedDate: string
  progress: ProgressSummary
}

export interface WorkRecord {
  id: number
  recordDate: string
  translatedVolume: number
  reworkPages?: number | string
  /**
   * Nullable. The API serializes with `non_null` inclusion, so the key is absent
   * rather than `null` when work hours were never entered.
   */
  workHours?: number | null
}

export interface ActivityDetail extends ActivitySummary {
  createdDate: string
  workRecords: WorkRecord[]
  currentWeek: { startDate: string; endDate: string }
  currentWeekEstimate?: WeeklyEstimate | null
  weeklyEstimates: WeeklyEstimate[]
  rework?: ReworkSummary
  permissions: {
    canEditWorkRecords: boolean
    canEditWeeklyEstimate: boolean
    canFinish: boolean
    canSubmitForChecking: boolean
    canSubmitWeeklyVersion?: boolean
    hasResponsibleChiefEditor: boolean
    hasOpenReview?: boolean
    hasOutstandingRework?: boolean
  }
}

export interface ReworkSummary {
  correctionReviewId?: number | null
  correctionPages?: number | null
  reworkRecordedPages: number | string
  outstandingReworkPages: number | string
  remainingPages?: number | string | null
  totalRemainingPages: number | string
  hasWeeklyEstimate: boolean
}

export interface WeeklyEstimate {
  id: number
  weekStartDate: string
  remainingTextVolume: number
  updatedAt: string
}

export interface CreateWorkRecordPayload {
  recordDate: string
  translatedVolume: number
  workHours: number | null
  reworkPages?: number | null
}

export interface UpdateWorkRecordPayload {
  translatedVolume: number
  workHours: number | null
  reworkPages?: number | null
  editRequestId?: number
}

export type ReviewResult = 'APPROVED' | 'CORRECTION_REQUESTED'
export type ReviewKind = 'WEEKLY' | 'FINAL'

export interface ReviewDocument {
  originalName: string
  contentType: string
  sizeBytes: number
  sha256: string
  downloadUrl: string
  annotatedDownloadUrl?: string | null
}

export interface ReviewCycle {
  id: number
  cycleNumber: number
  kind?: ReviewKind
  weekStartDate?: string | null
  submittedAt: string
  result?: ReviewResult | null
  feedback?: string | null
  expectedCheckingHours?: number | null
  correctionPages?: number | null
  correctionPageNumbers?: number[]
  decidedAt?: string | null
  annotationCount?: number
  document?: ReviewDocument | null
}

export interface ReviewHistory {
  reviews: ReviewCycle[]
}
