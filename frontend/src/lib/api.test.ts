import { afterEach, describe, expect, it, vi } from 'vitest'
import { translatorActivityApi } from './api'

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  })
}

describe('translatorActivityApi document uploads', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('submits the original document as multipart data without overriding its boundary', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(jsonResponse({ id: 101 }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['%PDF-1.7'], 'translation.pdf', { type: 'application/pdf' })

    await translatorActivityApi.submitForChecking(101, file)

    const [, request] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(request.method).toBe('POST')
    expect(request.headers).toEqual({ 'X-XSRF-TOKEN': 'csrf-token' })
    expect(request.body).toBeInstanceOf(FormData)
    expect((request.body as FormData).get('file')).toBe(file)
  })

  it('uploads a corrected document to the correction endpoint', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(jsonResponse({ id: 101 }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['PK'], 'translation-corrected.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })

    await translatorActivityApi.resubmitForChecking(101, file)

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/translator/activities/101/resubmit-for-checking')
    const request = fetchMock.mock.calls[1][1] as RequestInit
    expect((request.body as FormData).get('file')).toBe(file)
    expect(request.headers).toEqual({ 'X-XSRF-TOKEN': 'csrf-token' })
  })


  it('retries a mutation once when the CSRF token has expired', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'first-token' }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Your security token expired. Refresh the page and try again.',
        status: 403,
      }), { headers: { 'Content-Type': 'application/json' }, status: 403 }))
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'second-token' }))
      .mockResolvedValueOnce(jsonResponse({ id: 101, status: 'ASSIGNED' }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['%PDF-1.7'], 'weekly.pdf', { type: 'application/pdf' })

    await translatorActivityApi.submitWeeklyVersion(101, file)

    const mutationCalls = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(mutationCalls).toHaveLength(2)
    expect(mutationCalls[0][1].headers).toEqual({ 'X-XSRF-TOKEN': 'first-token' })
    expect(mutationCalls[1][1].headers).toEqual({ 'X-XSRF-TOKEN': 'second-token' })
  })

  it('uploads a weekly version without finishing the activity', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(jsonResponse({ id: 101, status: 'IN_PROGRESS' }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['%PDF-1.7'], 'week-46.pdf', { type: 'application/pdf' })

    await translatorActivityApi.submitWeeklyVersion(101, file)

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/translator/activities/101/weekly-versions')
    const request = fetchMock.mock.calls[1][1] as RequestInit
    expect((request.body as FormData).get('file')).toBe(file)
    expect(request.headers).toEqual({ 'X-XSRF-TOKEN': 'csrf-token' })
  })

  it('loads translator review history from the activity-scoped endpoint', async () => {
    const history = { reviews: [{ id: 901, cycleNumber: 1, submittedAt: '2026-11-08T08:15:00Z' }] }
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(history))
    vi.stubGlobal('fetch', fetchMock)

    await expect(translatorActivityApi.reviews(101)).resolves.toEqual(history)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/translator/activities/101/reviews', {
      credentials: 'include',
      headers: {},
    })
  })

  it('attaches a legacy document to the selected review cycle as multipart data', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(jsonResponse({ id: 800, cycleNumber: 1 }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['%PDF-1.7'], 'legacy.pdf', { type: 'application/pdf' })

    await translatorActivityApi.attachLegacyDocument(101, 800, file)

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/translator/activities/101/reviews/800/legacy-attachment')
    const request = fetchMock.mock.calls[1][1] as RequestInit
    expect((request.body as FormData).get('file')).toBe(file)
    expect(request.headers).toEqual({ 'X-XSRF-TOKEN': 'csrf-token' })
  })
})
