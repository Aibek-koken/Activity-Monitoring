function isoDateFromParts(year: number, month: number, day: number): string {
  return [
    String(year).padStart(4, '0'),
    String(month).padStart(2, '0'),
    String(day).padStart(2, '0'),
  ].join('-')
}

export function todayLocalDateString(now = new Date()): string {
  return isoDateFromParts(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

export function latestAllowedRecordDateString(now = new Date()): string {
  const utcPlus14 = new Date(now.getTime() + 14 * 60 * 60 * 1000)
  return isoDateFromParts(
    utcPlus14.getUTCFullYear(),
    utcPlus14.getUTCMonth() + 1,
    utcPlus14.getUTCDate(),
  )
}
