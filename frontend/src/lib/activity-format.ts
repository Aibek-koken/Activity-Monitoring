import type { ActivityStatus } from '../types/activity'

export const activityStatusLabels: Record<ActivityStatus, string> = {
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  FINISHED: 'Finished',
  UNDER_CHECKING: 'Under checking',
  CORRECTION_REQUIRED: 'Correction required',
  CORRECTED: 'Corrected',
  COMPLETED: 'Completed',
}

export function formatVolume(value: number): string {
  return `${new Intl.NumberFormat(undefined, {
    maximumFractionDigits: 2,
    minimumFractionDigits: value % 1 === 0 ? 0 : 2,
  }).format(value)} volume units`
}

export function formatDate(value: string | null): string {
  if (!value) return 'No records yet'
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00Z`))
}

export function statusClassName(status: ActivityStatus): string {
  return `status-pill status-pill--${status.toLowerCase().replaceAll('_', '-')}`
}
