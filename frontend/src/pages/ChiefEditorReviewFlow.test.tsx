import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import type { ChiefEditorReview } from '../types/review'

const editor = {
  email: 'editor@easylang.local',
  fullName: 'Elena Petrova',
  id: 4,
  initials: 'EP',
  role: 'CHIEF_EDITOR',
}

const queueReview = {
  activityId: 101,
  activityName: 'Privacy policy translation',
  activityNumber: 'EL-2026-001',
  cycleNumber: 2,
  kind: 'FINAL',
  annotationCount: 0,
  expectedCheckingHours: null,
  hasDocument: true,
  id: 902,
  projectId: 21,
  projectName: 'Central Asia Legal Portal',
  submittedAt: '2026-11-09T06:30:00Z',
}

const openReview: ChiefEditorReview = {
  ...queueReview,
  activityStatus: 'UNDER_CHECKING',
  kind: 'FINAL',
  annotations: [],
  previousCycles: [],
  decidedAt: null,
  document: {
    contentType: 'application/pdf',
    downloadUrl: '/api/v1/reviews/902/document',
    originalName: 'privacy-policy-corrected.pdf',
    sha256: '9fc58f1b2fe47f3a089c51d0eea8441d83aa21844f3d764883d00bf1a9b31824',
    sizeBytes: 245760,
  },
  feedback: null,
  result: null,
}

const closedReview: ChiefEditorReview = {
  ...openReview,
  decidedAt: '2026-11-10T08:45:00Z',
  expectedCheckingHours: 1.5,
  feedback: 'Terminology and date formatting corrected.',
  result: 'APPROVED',
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status,
  })
}

function installFetch(review = openReview) {
  let decided = false
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)

    if (url.endsWith('/v1/auth/me')) return jsonResponse(editor)
    if (url.endsWith('/v1/chief-editor/projects')) {
      return jsonResponse({ projects: [{ createdDate: '2026-08-15', id: 21, projectName: 'Central Asia Legal Portal', responsible: true }] })
    }
    if (url.includes('/v1/chief-editor/reviews?')) {
      return jsonResponse({ page: 0, reviews: decided ? [] : [queueReview], size: 20, totalElements: decided ? 0 : 1, totalPages: 1 })
    }
    if (url.endsWith('/v1/chief-editor/workload')) {
      return jsonResponse({
        availableWeeklyHours: null,
        completeForecast: false,
        knownExpectedHours: 3.5,
        missingEstimateCount: 1,
        openCount: 2,
        queueExceedsWeek: null,
        remainingHours: null,
      })
    }
    if (url.endsWith('/v1/chief-editor/availability')) {
      return jsonResponse({ weeklyHours: null, updatedAt: null })
    }
    if (url.endsWith('/v1/chief-editor/reviews/902') && (!init?.method || init.method === 'GET')) {
      return jsonResponse(review)
    }
    if (url.endsWith('/v1/auth/csrf')) {
      return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
    }
    if (url.endsWith('/v1/chief-editor/reviews/902/expected-time')) {
      const payload = JSON.parse(String(init?.body)) as { expectedCheckingHours: number }
      return jsonResponse({ ...openReview, expectedCheckingHours: payload.expectedCheckingHours })
    }
    if (url.endsWith('/v1/chief-editor/reviews/902/decision')) {
      decided = true
      const payload = JSON.parse(String(init?.body)) as { feedback: string; result: string }
      return jsonResponse({
        ...openReview,
        decidedAt: '2026-11-10T08:45:00Z',
        feedback: payload.feedback,
        result: payload.result,
      })
    }
    if (url.endsWith('/v1/workspaces/chief-editor')) {
      return jsonResponse({ message: 'Ready', role: 'CHIEF_EDITOR', userName: 'Elena Petrova' })
    }

    throw new Error(`Unhandled request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderAt(path: string) {
  window.history.pushState({}, '', path)
  return render(<App />)
}

describe('Chief Editor review flow', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/')
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows assigned projects, the open review queue, and one compact workload summary', async () => {
    installFetch()
    renderAt('/chief-editor')

    expect(await screen.findByRole('heading', { name: 'Your projects' })).toBeInTheDocument()
    expect(await screen.findByText('Central Asia Legal Portal', {}, { timeout: 10000 })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Central Asia Legal Portal/ }))
    expect(await screen.findByRole('heading', { name: 'Open versions' }, { timeout: 10000 })).toBeInTheDocument()
    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    expect(screen.getByText('2 open versions')).toBeInTheDocument()
    expect(screen.getByText('3 h 30 min estimated')).toBeInTheDocument()
    expect(screen.getByText('1 without an estimate')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Privacy policy translation/ })).toHaveAttribute(
      'href',
      '/chief-editor/reviews/902',
    )
  })

  it('shows document download, expected time, feedback, and both decisions for an open review', async () => {
    installFetch()
    renderAt('/chief-editor/reviews/902')

    expect(await screen.findByRole('heading', { name: 'Privacy policy translation' })).toBeInTheDocument()
    expect(screen.getByText('Cycle 2')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Download original/ })).toHaveAttribute(
      'href',
      '/api/v1/reviews/902/document',
    )
    expect(screen.getByLabelText('Hours')).toBeInTheDocument()
    expect(screen.getByLabelText('Minutes')).toBeInTheDocument()
    expect(screen.getByLabelText('Feedback')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve version' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Return for correction' })).toBeInTheDocument()
    expect(screen.queryByText(/preview/i)).not.toBeInTheDocument()
  })

  it('saves hours and minutes with CSRF and refreshes the visible value', async () => {
    const fetchMock = installFetch()
    renderAt('/chief-editor/reviews/902')

    const hours = await screen.findByLabelText('Hours', {}, { timeout: 10000 })
    const minutes = screen.getByLabelText('Minutes')
    fireEvent.change(hours, { target: { value: '1' } })
    fireEvent.change(minutes, { target: { value: '10' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save expected time' }))

    expect(await screen.findByText('Expected time saved.')).toBeInTheDocument()
    expect(hours).toHaveValue(1)
    expect(minutes).toHaveValue(10)
    const [, request] = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/expected-time'))!
    expect(request).toMatchObject({
      body: JSON.stringify({ expectedCheckingHours: 1.17 }),
      headers: { 'X-XSRF-TOKEN': 'csrf-token' },
      method: 'PUT',
    })
  })

  it('requires feedback for correction and returns to the queue after closing the version', async () => {
    const fetchMock = installFetch()
    renderAt('/chief-editor/reviews/902')

    await screen.findByRole('heading', { name: 'Privacy policy translation' })
    fireEvent.click(screen.getByRole('button', { name: 'Return for correction' }))
    expect(screen.getByText('Enter feedback before returning the version.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/decision'))).toBe(false)

    fireEvent.change(screen.getByLabelText('Feedback'), { target: { value: 'Fix the company name and the final date.' } })
    fireEvent.change(screen.getByLabelText('Pages to redo if returned'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Return for correction' }))

    expect(await screen.findByRole('status')).toHaveTextContent('EL-2026-001: returned for correction')
    expect(window.location.pathname).toBe('/chief-editor')
    expect(screen.queryByText('Privacy policy translation')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Feedback')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve version' })).not.toBeInTheDocument()
  })

  it('shows a previously closed review and its cycle history read-only', async () => {
    installFetch(closedReview)
    renderAt('/chief-editor/reviews/902')

    expect(await screen.findAllByText('Approved')).toHaveLength(2)
    expect(screen.getByText('Terminology and date formatting corrected.')).toBeInTheDocument()
    expect(screen.getByText('1 h 30 min')).toBeInTheDocument()
    expect(screen.getByText(/Decision recorded/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Download original/ })).toBeInTheDocument()
    expect(screen.queryByLabelText('Hours')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Request correction' })).not.toBeInTheDocument()
  })

  it('keeps a failed review decision editable and shows the backend error', async () => {
    const fetchMock = installFetch()
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/v1/auth/me')) return jsonResponse(editor)
      if (url.endsWith('/v1/chief-editor/projects')) return jsonResponse({ projects: [] })
      if (url.includes('/v1/chief-editor/reviews?')) return jsonResponse({ page: 0, reviews: [], size: 20, totalElements: 0, totalPages: 0 })
      if (url.endsWith('/v1/chief-editor/workload')) return jsonResponse({ availableWeeklyHours: null, completeForecast: true, knownExpectedHours: 0, missingEstimateCount: 0, openCount: 0, queueExceedsWeek: null, remainingHours: null })
      if (url.endsWith('/v1/chief-editor/availability')) return jsonResponse({ weeklyHours: null, updatedAt: null })
      if (url.endsWith('/v1/chief-editor/reviews/902') && (!init?.method || init.method === 'GET')) return jsonResponse(openReview)
      if (url.endsWith('/v1/auth/csrf')) return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
      if (url.endsWith('/decision')) return jsonResponse({ message: 'This review was already closed.', status: 409 }, 409)
      throw new Error(`Unhandled request: ${url}`)
    })
    renderAt('/chief-editor/reviews/902')

    await screen.findByLabelText('Feedback')
    fireEvent.click(screen.getByRole('button', { name: 'Approve version' }))

    expect(await screen.findByText('This review was already closed.')).toBeInTheDocument()
    expect(screen.getByLabelText('Feedback')).toBeInTheDocument()
  })
})
