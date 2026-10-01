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
  /**
   * Nullable. The API serializes with `non_null` inclusion, so the key is absent
   * rather than `null` when work hours were never entered.
   */
  workHours?: number | null
}

export interface ActivityDetail extends ActivitySummary {
  createdDate: string
  workRecords: WorkRecord[]
}

export interface CreateWorkRecordPayload {
  recordDate: string
  translatedVolume: number
  workHours: number | null
}

export interface UpdateWorkRecordPayload {
  translatedVolume: number
  workHours: number | null
}
