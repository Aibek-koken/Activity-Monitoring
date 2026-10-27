import type { ActivityStatus } from '../types/activity'

/**
 * The UI is English-only, so pin one locale instead of following the browser.
 */
export const UI_LOCALE = 'en-US'
export const BUSINESS_TIME_ZONE = 'Asia/Almaty'

export const activityStatusLabels: Record<ActivityStatus, string> = {
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  FINISHED: 'Finished',
  UNDER_CHECKING: 'Under checking',
  CORRECTION_REQUIRED: 'Correction required',
  CORRECTED: 'Corrected',
  COMPLETED: 'Completed',
}

/**
 * Coerces a nullable numeric API field into a number, or `null` when it is absent.
 * Guards against the API omitting nulls and against `BigDecimal` arriving as a string.
 */
export function toNumberOrNull(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function formatVolume(value: number | string | null | undefined): string {
  const amount = toNumberOrNull(value) ?? 0
  return new Intl.NumberFormat(UI_LOCALE, {
    maximumFractionDigits: 2,
    minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
  }).format(amount)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return 'No records yet'
  const date = new Date(`${value}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return 'No records yet'
  return new Intl.DateTimeFormat(UI_LOCALE, {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
    year: 'numeric',
  }).format(date)
}

export function formatWeekRange(value: string | null | undefined): string {
  if (!value) return 'Week unavailable'
  const start = new Date(`${value}T00:00:00Z`)
  if (Number.isNaN(start.getTime())) return 'Week unavailable'

  const end = new Date(start)
  end.setUTCDate(end.getUTCDate() + 6)
  const shortDate = (date: Date) => new Intl.DateTimeFormat(UI_LOCALE, {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(date)

  return `${shortDate(start)}–${shortDate(end)}, ${end.getUTCFullYear()}`
}

export function formatDateTimeInAlmaty(value: string | null | undefined): string {
  if (!value) return 'Update time unavailable'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Update time unavailable'

  const parts = new Intl.DateTimeFormat(UI_LOCALE, {
    day: 'numeric',
    hour: '2-digit',
    hourCycle: 'h23',
    minute: '2-digit',
    month: 'short',
    timeZone: BUSINESS_TIME_ZONE,
  }).formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? ''

  return `${part('month')} ${part('day')}, ${part('hour')}:${part('minute')}`
}

export function formatWorkHours(value: number | string | null | undefined): string {
  const hours = toNumberOrNull(value)
  if (hours === null) return 'Not entered'

  const totalMinutes = Math.round(hours * 60)
  const wholeHours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (wholeHours === 0 && minutes === 0) return '0 h'
  if (minutes === 0) return `${wholeHours} h`
  if (wholeHours === 0) return `${minutes} min`
  return `${wholeHours} h ${minutes} min`
}

export function statusClassName(status: ActivityStatus): string {
  return `status-pill status-pill--${status.toLowerCase().replaceAll('_', '-')}`
}
