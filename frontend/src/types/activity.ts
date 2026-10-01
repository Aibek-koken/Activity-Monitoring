export type ActivityStatus =
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'FINISHED'
  | 'UNDER_CHECKING'
  | 'CORRECTION_REQUIRED'
  | 'CORRECTED'
  | 'COMPLETED'

export interface ProgressSummary {
  totalTranslatedPages: number
  recordCount: number
  lastRecordDate: string | null
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
  translatedVolumePages: number
  workHours: number | null
}

export interface ActivityDetail extends ActivitySummary {
  createdDate: string
  workRecords: WorkRecord[]
}
