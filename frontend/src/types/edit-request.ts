export type EditRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'USED'
export interface WorkRecordEditRequest {
  id: number
  activityId: number
  activityNumber: string
  projectName: string
  workRecordId: number
  recordDate: string
  translatorName: string
  reason: string
  status: EditRequestStatus
  decisionComment?: string | null
  requestedAt: string
  decidedAt?: string | null
}
