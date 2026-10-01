const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(value: string): string | null {
  const email = value.trim()
  if (!email) return 'Enter your email address.'
  if (email.length > 160) return 'Email must be 160 characters or less.'
  if (!EMAIL_PATTERN.test(email)) return 'Enter a valid email, for example name@example.com.'
  return null
}
