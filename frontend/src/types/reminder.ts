export type ReminderType = 'DAILY_RECORD_REMINDER' | 'WEEKLY_ESTIMATE_REMINDER'

export interface Reminder {
  id: number
  type: ReminderType
  periodDate: string
  message: string
  activityId?: number | null
  activityNumber?: string | null
  createdAt: string
}
