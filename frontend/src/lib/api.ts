import type { Role, User, WorkspaceData } from '../types/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

interface ProblemResponse {
  status: number
  message: string
  fieldErrors?: Record<string, string>
}

interface CsrfResponse {
  token: string
  headerName: string
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as ProblemResponse | null
    throw new ApiError(
      response.status,
      problem?.message ?? 'The server could not complete the request.',
      problem?.fieldErrors,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

async function csrf(): Promise<CsrfResponse> {
  return request<CsrfResponse>('/v1/auth/csrf')
}

export const authApi = {
  async login(email: string, password: string): Promise<User> {
    const csrfToken = await csrf()
    const result = await request<{ user: User }>('/v1/auth/login', {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify({ email, password }),
    })
    return result.user
  },

  me(): Promise<User> {
    return request<User>('/v1/auth/me')
  },

  async logout(): Promise<void> {
    const csrfToken = await csrf()
    await request<void>('/v1/auth/logout', {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
    })
  },
}

const workspacePaths: Record<Role, string> = {
  TRANSLATOR: '/v1/workspaces/translator',
  CHIEF_EDITOR: '/v1/workspaces/chief-editor',
  PROJECT_MANAGER: '/v1/workspaces/project-manager',
}

export const workspaceApi = {
  load(role: Role): Promise<WorkspaceData> {
    return request<WorkspaceData>(workspacePaths[role])
  },
}

