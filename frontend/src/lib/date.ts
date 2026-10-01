export function todayUtcDateString(now = new Date()): string {
  return now.toISOString().slice(0, 10)
}

export function isFutureUtcDate(value: string, now = new Date()): boolean {
  return value > todayUtcDateString(now)
}
