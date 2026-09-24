import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, authApi } from '../lib/api'
import type { User } from '../types/auth'

type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error'

interface AuthContextValue {
  user: User | null
  status: AuthStatus
  error: string | null
  login: (email: string, password: string) => Promise<User>
  logout: () => Promise<void>
  retry: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [error, setError] = useState<string | null>(null)

  const loadSession = useCallback(async () => {
    setStatus('loading')
    setError(null)
    try {
      const currentUser = await authApi.me()
      setUser(currentUser)
      setStatus('authenticated')
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        setUser(null)
        setStatus('anonymous')
        return
      }
      setUser(null)
      setError('Could not connect to EasyLang. Check that the API is running, then try again.')
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    void loadSession()
  }, [loadSession])

  const login = useCallback(async (email: string, password: string) => {
    const authenticatedUser = await authApi.login(email, password)
    setUser(authenticatedUser)
    setStatus('authenticated')
    setError(null)
    return authenticatedUser
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo(
    () => ({ user, status, error, login, logout, retry: loadSession }),
    [error, loadSession, login, logout, status, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}

