import type { Role, User, WorkspaceData } from '../types/auth'
import type {
  ActivityDetail,
  ActivitySummary,
  CreateWorkRecordPayload,
  ReviewCycle,
  ReviewHistory,
  UpdateWorkRecordPayload,
} from '../types/activity'
import type { Reminder } from '../types/reminder'
import type { WorkRecordEditRequest } from '../types/edit-request'
import type {
  AnnotationPayload,
  AssignedProject,
  ChiefEditorReview,
  EditorAvailability,
  ReviewAnnotation,
  ReviewQueue,
  ReviewResult,
  ReviewWorkload,
} from '../types/review'

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
  const hasJsonBody = init.body != null && !(init.body instanceof FormData)
  let headers: Record<string, string> = {
    ...(hasJsonBody ? { 'Content-Type': 'application/json' } : {}),
    ...(init.headers as Record<string, string> | undefined),
  }

  for (let attempt = 0; attempt < 2; attempt++) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      credentials: 'include',
      headers,
    })

    if (!response.ok) {
      const problem = (await response.json().catch(() => null)) as ProblemResponse | null
      const csrfExpired = response.status === 403
        && problem?.message?.toLowerCase().includes('security token')
      if (csrfExpired && attempt === 0 && init.method && init.method !== 'GET' && init.method !== 'HEAD') {
        const fresh = await csrf()
        const refreshed = { ...headers }
        for (const key of Object.keys(refreshed)) {
          if (key.toLowerCase() === 'x-xsrf-token') delete refreshed[key]
        }
        refreshed[fresh.headerName] = fresh.token
        headers = refreshed
        continue
      }
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

  throw new ApiError(403, 'Your security token expired. Refresh the page and try again.')
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

export const translatorActivityApi = {
  list(query = ''): Promise<{ activities: ActivitySummary[] }> {
    const search = query.trim()
    const suffix = search ? `?${new URLSearchParams({ q: search }).toString()}` : ''
    return request<{ activities: ActivitySummary[] }>(`/v1/translator/activities${suffix}`)
  },

  get(activityId: number): Promise<ActivityDetail> {
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}`)
  },

  async saveCurrentEstimate(activityId: number, remainingTextVolume: number): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/weekly-estimates/current`, {
      method: 'PUT',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify({ remainingTextVolume }),
    })
  },

  async finish(activityId: number): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/finish`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
    })
  },

  reviews(activityId: number): Promise<ReviewHistory> {
    return request<ReviewHistory>(`/v1/translator/activities/${activityId}/reviews`)
  },

  async submitForChecking(activityId: number, file: File): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    const body = new FormData()
    body.append('file', file)
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/submit-for-checking`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body,
    })
  },

  async submitWeeklyVersion(activityId: number, file: File): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    const body = new FormData()
    body.append('file', file)
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/weekly-versions`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body,
    })
  },

  async resubmitForChecking(activityId: number, file: File): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    const body = new FormData()
    body.append('file', file)
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/resubmit-for-checking`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body,
    })
  },

  async attachLegacyDocument(activityId: number, reviewId: number, file: File): Promise<ReviewCycle> {
    const csrfToken = await csrf()
    const body = new FormData()
    body.append('file', file)
    return request<ReviewCycle>(`/v1/translator/activities/${activityId}/reviews/${reviewId}/legacy-attachment`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body,
    })
  },

  async createWorkRecord(activityId: number, payload: CreateWorkRecordPayload): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/work-records`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify(payload),
    })
  },

  async updateWorkRecord(
    activityId: number,
    recordId: number,
    payload: UpdateWorkRecordPayload,
  ): Promise<ActivityDetail> {
    const csrfToken = await csrf()
    return request<ActivityDetail>(`/v1/translator/activities/${activityId}/work-records/${recordId}`, {
      method: 'PUT',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify(payload),
    })
  },
}

export const reviewApi = {
  annotations(reviewId: number): Promise<ReviewAnnotation[]> {
    return request<ReviewAnnotation[]>(`/v1/reviews/${reviewId}/annotations`)
  },
}

export const reminderApi = {
  async list(page = 0): Promise<{ reminders: Reminder[]; page?: number; totalPages?: number; totalElements?: number; unreadCount?: number }> {
    const result = await request<{ notifications: Reminder[]; page: number; totalPages: number; totalElements: number; unreadCount: number }>(`/v1/notifications?page=${page}&size=20`)
    return { ...result, reminders: result.notifications }
  },

  async markRead(reminderId: number): Promise<void> {
    const csrfToken = await csrf()
    await request<void>(`/v1/notifications/${reminderId}/read`, {
      method: 'PATCH',
      headers: { [csrfToken.headerName]: csrfToken.token },
    })
  },

  async markAllRead(): Promise<void> {
    const csrfToken = await csrf()
    await request<void>('/v1/notifications/read-all', {
      method: 'PATCH',
      headers: { [csrfToken.headerName]: csrfToken.token },
    })
  },

  async delete(reminderId: number): Promise<void> {
    const csrfToken = await csrf()
    await request<void>(`/v1/notifications/${reminderId}`, {
      method: 'DELETE',
      headers: { [csrfToken.headerName]: csrfToken.token },
    })
  },
}

export const editRequestApi = {
  translatorList(): Promise<{ requests: WorkRecordEditRequest[] }> {
    return request('/v1/translator/work-record-edit-requests')
  },
  managerList(): Promise<{ requests: WorkRecordEditRequest[] }> {
    return request('/v1/project-manager/work-record-edit-requests')
  },
  async create(activityId: number, recordId: number, reason: string): Promise<WorkRecordEditRequest> {
    const token = await csrf()
    return request(`/v1/translator/activities/${activityId}/work-records/${recordId}/edit-requests`, {
      method: 'POST', headers: { [token.headerName]: token.token }, body: JSON.stringify({ reason }),
    })
  },
  async decide(id: number, approved: boolean, comment: string): Promise<WorkRecordEditRequest> {
    const token = await csrf()
    return request(`/v1/project-manager/work-record-edit-requests/${id}/decision`, {
      method: 'POST', headers: { [token.headerName]: token.token }, body: JSON.stringify({ approved, comment }),
    })
  },
}

export const chiefEditorApi = {
  projects(): Promise<{ projects: AssignedProject[] }> {
    return request<{ projects: AssignedProject[] }>('/v1/chief-editor/projects')
  },

  reviews(page = 0, size = 20, projectId?: number | null, kind?: string, search?: string): Promise<ReviewQueue> {
    const query = new URLSearchParams({ page: String(page), size: String(size), status: 'open' })
    if (projectId) query.set('projectId', String(projectId))
    if (kind) query.set('kind', kind)
    if (search) query.set('q', search)
    return request<ReviewQueue>(`/v1/chief-editor/reviews?${query.toString()}`)
  },

  workload(): Promise<ReviewWorkload> {
    return request<ReviewWorkload>('/v1/chief-editor/workload')
  },

  getReview(reviewId: number): Promise<ChiefEditorReview> {
    return request<ChiefEditorReview>(`/v1/chief-editor/reviews/${reviewId}`)
  },

  async updateExpectedTime(reviewId: number, expectedCheckingHours: number): Promise<ChiefEditorReview> {
    const csrfToken = await csrf()
    return request<ChiefEditorReview>(`/v1/chief-editor/reviews/${reviewId}/expected-time`, {
      method: 'PUT',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify({ expectedCheckingHours }),
    })
  },

  async decide(
    reviewId: number,
    result: ReviewResult,
    feedback: string,
    correctionPageNumbers: number[] | null = null,
  ): Promise<ChiefEditorReview> {
    const csrfToken = await csrf()
    return request<ChiefEditorReview>(`/v1/chief-editor/reviews/${reviewId}/decision`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify({
        feedback: feedback.trim() || null,
        result,
        correctionPageNumbers: result === 'CORRECTION_REQUESTED' ? correctionPageNumbers : null,
      }),
    })
  },

  availability(): Promise<EditorAvailability> {
    return request<EditorAvailability>('/v1/chief-editor/availability')
  },

  async saveAvailability(weeklyHours: number): Promise<EditorAvailability> {
    const csrfToken = await csrf()
    return request<EditorAvailability>('/v1/chief-editor/availability', {
      method: 'PUT',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify({ weeklyHours }),
    })
  },

  async createAnnotation(reviewId: number, payload: AnnotationPayload): Promise<ReviewAnnotation> {
    const csrfToken = await csrf()
    return request<ReviewAnnotation>(`/v1/chief-editor/reviews/${reviewId}/annotations`, {
      method: 'POST',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify(payload),
    })
  },

  async updateAnnotation(reviewId: number, annotationId: number, payload: AnnotationPayload): Promise<ReviewAnnotation> {
    const csrfToken = await csrf()
    return request<ReviewAnnotation>(`/v1/chief-editor/reviews/${reviewId}/annotations/${annotationId}`, {
      method: 'PUT',
      headers: { [csrfToken.headerName]: csrfToken.token },
      body: JSON.stringify(payload),
    })
  },

  async deleteAnnotation(reviewId: number, annotationId: number): Promise<void> {
    const csrfToken = await csrf()
    await request<void>(`/v1/chief-editor/reviews/${reviewId}/annotations/${annotationId}`, {
      method: 'DELETE',
      headers: { [csrfToken.headerName]: csrfToken.token },
    })
  },
}
