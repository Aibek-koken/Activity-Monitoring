const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const DECIMAL_PATTERN = /^\d+(\.\d{1,2})?$/

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

export function validateWorkHours(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (!DECIMAL_PATTERN.test(trimmed)) return 'Use hours with up to 2 decimal places.'

  const amount = Number(trimmed)
  if (amount < 0) return 'Work hours cannot be negative.'
  if (amount > 24) return 'Work hours must be 24.00 or less.'
  return null
}
