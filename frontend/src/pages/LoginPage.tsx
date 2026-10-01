import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowRight, Check, Eye, EyeOff } from 'lucide-react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { BrandMark } from '../components/BrandMark'
import { ApiError } from '../lib/api'
import { roleLabels, rolePath } from '../lib/roles'
import { validateEmail } from '../lib/validation'
import type { Role } from '../types/auth'
import { useAuth } from '../auth/AuthContext'

interface DemoAccount {
  role: Role
  email: string
  initials: string
}

const demoAccounts: DemoAccount[] = [
  { role: 'TRANSLATOR', email: 'translator@easylang.local', initials: 'TR' },
  { role: 'CHIEF_EDITOR', email: 'editor@easylang.local', initials: 'CE' },
  { role: 'PROJECT_MANAGER', email: 'manager@easylang.local', initials: 'PM' },
]

export function LoginPage() {
  const { user, status, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const emailRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const clearFieldError = (field: string) => {
    setFieldErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  if (status === 'authenticated' && user) {
    return <Navigate to={rolePath(user.role)} replace />
  }

  const chooseDemoAccount = (account: DemoAccount) => {
    setEmail(account.email)
    setPassword('Demo123!')
    setFormError(null)
    setFieldErrors({})
    emailRef.current?.focus()
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})

    const clientErrors: Record<string, string> = {}
    const emailError = validateEmail(email)
    if (emailError) clientErrors.email = emailError
    if (!password) clientErrors.password = 'Enter your password.'
    if (password.length > 72) clientErrors.password = 'Password must be 72 characters or less.'
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors)
      if (clientErrors.email) emailRef.current?.focus()
      else passwordRef.current?.focus()
      return
    }

    setIsSubmitting(true)
    try {
      const authenticatedUser = await login(email.trim(), password)
      const requestedPath = (location.state as { from?: string } | null)?.from
      const destination = requestedPath === rolePath(authenticatedUser.role)
        ? requestedPath
        : rolePath(authenticatedUser.role)
      navigate(destination, { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        const hasFieldErrors = Object.keys(caught.fieldErrors).length > 0
        setFormError(caught.status === 401
          ? 'We could not sign you in with these details. Check your email and password, or ask an administrator to create or activate your account.'
          : hasFieldErrors ? null : caught.message)
        setFieldErrors(caught.fieldErrors)
        requestAnimationFrame(() => {
          if (caught.fieldErrors.email) emailRef.current?.focus()
          else if (caught.fieldErrors.password) passwordRef.current?.focus()
        })
      } else {
        setFormError('Could not reach the server. Check your connection and try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <header className="auth-header">
        <BrandMark />
      </header>

      <section className="auth-card" aria-labelledby="login-title">
        <div className="login-heading">
          <h1 id="login-title">Welcome back</h1>
          <p>Sign in to continue to your workspace.</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit} noValidate>
            {formError && (
              <div className="form-alert" role="alert">
                {formError}
              </div>
            )}

            <div className="field-group">
              <label htmlFor="email">Email</label>
              <input
                ref={emailRef}
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                spellCheck={false}
                placeholder="you@easylang.local"
                maxLength={160}
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value)
                  clearFieldError('email')
                  setFormError(null)
                }}
                aria-invalid={fieldErrors.email ? 'true' : undefined}
                aria-describedby={fieldErrors.email ? 'email-error' : undefined}
              />
              {fieldErrors.email && <p id="email-error" className="field-error">{fieldErrors.email}</p>}
            </div>

            <div className="field-group">
              <label htmlFor="password">Password</label>
              <div className="password-input">
                <input
                  id="password"
                  ref={passwordRef}
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  spellCheck={false}
                  maxLength={72}
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value)
                    clearFieldError('password')
                    setFormError(null)
                  }}
                  aria-invalid={fieldErrors.password ? 'true' : undefined}
                  aria-describedby={fieldErrors.password ? 'password-error' : undefined}
                />
                <button
                  type="button"
                  className="icon-button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword((visible) => !visible)}
                >
                  {showPassword ? <EyeOff size={19} aria-hidden="true" /> : <Eye size={19} aria-hidden="true" />}
                </button>
              </div>
              {fieldErrors.password && <p id="password-error" className="field-error">{fieldErrors.password}</p>}
            </div>

            <button
              className="button button--primary button--full"
              type="submit"
              disabled={isSubmitting}
              aria-busy={isSubmitting}
            >
              <span>{isSubmitting ? 'Signing in…' : 'Sign in'}</span>
              {!isSubmitting && <ArrowRight size={18} aria-hidden="true" />}
            </button>
        </form>

        <div className="quick-access">
          <p>Quick access</p>
          <div className="quick-access__list" aria-label="Fill credentials by role">
            {demoAccounts.map((account) => {
              const isSelected = email === account.email && password === 'Demo123!'
              return (
              <button
                key={account.role}
                type="button"
                className="role-option"
                aria-pressed={isSelected}
                onClick={() => chooseDemoAccount(account)}
              >
                <span className="role-option__avatar" aria-hidden="true">{account.initials}</span>
                <span>{roleLabels[account.role]}</span>
                {isSelected && <Check className="role-option__check" size={15} aria-hidden="true" />}
              </button>
              )
            })}
          </div>
        </div>
      </section>
    </main>
  )
}
