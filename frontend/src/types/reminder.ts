export type ReminderType = 'DAILY_RECORD_REMINDER' | 'WEEKLY_ESTIMATE_REMINDER' | 'WEEKLY_SUBMISSION_REMINDER' | 'CORRECTION_REQUESTED' | 'RECORD_EDIT_REQUESTED' | 'RECORD_EDIT_APPROVED' | 'RECORD_EDIT_REJECTED'

export interface Reminder {
  id: number
  type: ReminderType
  periodDate: string
  message: string
  activityId?: number | null
  activityNumber?: string | null
  createdAt: string
  readAt?: string | null
  actionable?: boolean
}
