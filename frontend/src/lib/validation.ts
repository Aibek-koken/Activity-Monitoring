const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const DECIMAL_PATTERN = /^\d+(\.\d{1,2})?$/
const INTEGER_PATTERN = /^\d+$/

export function validateEmail(value: string): string | null {
  const email = value.trim()
  if (!email) return 'Enter your email address.'
  if (email.length > 160) return 'Email must be 160 characters or less.'
  if (!EMAIL_PATTERN.test(email)) return 'Enter a valid email, for example name@example.com.'
  return null
}

export function validateLoginPassword(value: string): string | null {
  if (!value) return 'Enter your password.'
  if (value.length < 8) return 'Password must be at least 8 characters.'
  if (value.length > 72) return 'Password is too long.'
  return null
}

export function validateRecordDate(value: string, latestAllowedDate: string): string | null {
  if (!value) return 'Choose a record date.'
  if (value > latestAllowedDate) return 'Record date cannot be in the future.'
  return null
}

export function validateTranslatedVolume(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return 'Enter translated volume.'
  if (!DECIMAL_PATTERN.test(trimmed)) return 'Use a number with up to 2 decimal places.'

  const amount = Number(trimmed)
  if (amount <= 0) return 'Translated volume must be greater than 0.'
  if (amount > 1000) return 'Translated volume must be 1,000.00 or less.'
  return null
}

export function validateWorkTime(hoursValue: string, minutesValue: string): { workHours?: string; workMinutes?: string } {
  const hoursTrimmed = hoursValue.trim()
  const minutesTrimmed = minutesValue.trim()
  const errors: { workHours?: string; workMinutes?: string } = {}

  if (!hoursTrimmed && !minutesTrimmed) {
    return errors
  }

  if (hoursTrimmed && !INTEGER_PATTERN.test(hoursTrimmed)) {
    errors.workHours = 'Use whole hours.'
  }

  if (minutesTrimmed && !INTEGER_PATTERN.test(minutesTrimmed)) {
    errors.workMinutes = 'Use whole minutes.'
  }

  if (errors.workHours || errors.workMinutes) {
    return errors
  }

  const hours = hoursTrimmed ? Number(hoursTrimmed) : 0
  const minutes = minutesTrimmed ? Number(minutesTrimmed) : 0

  if (hours > 24) {
    errors.workHours = 'Work time must be 24 hours or less.'
  }

  if (minutes > 59) {
    errors.workMinutes = 'Minutes must be 0 to 59.'
  }

  if (hours === 24 && minutes > 0) {
    errors.workMinutes = '24 hours is the maximum, so minutes must be 0.'
  }

  return errors
}
