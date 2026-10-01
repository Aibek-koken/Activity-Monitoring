import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { translatorActivityApi } from '../lib/api'
import { todayLocalDateString } from '../lib/date'
import type { ActivityDetail } from '../types/activity'
import { TranslatorActivityDetailPage } from './TranslatorActivityDetailPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    logout: vi.fn(),
    user: {
      email: 'translator@easylang.local',
      fullName: 'Maya Chen',
      id: 1,
      initials: 'MC',
      role: 'TRANSLATOR',
    },
  }),
}))

vi.mock('../lib/api', () => ({
  ApiError: class ApiError extends Error {
    readonly fieldErrors: Record<string, string>
    readonly status: number

    constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
      super(message)
      this.status = status
      this.fieldErrors = fieldErrors
    }
  },
  translatorActivityApi: {
    createWorkRecord: vi.fn(),
    get: vi.fn(),
    updateWorkRecord: vi.fn(),
  },
}))

const baseActivity: ActivityDetail = {
  activityName: 'Privacy policy translation',
  activityNumber: 'EL-2026-001',
  assignedDate: '2026-09-22',
  createdDate: '2026-09-22',
  id: 101,
  progress: {
    lastRecordDate: null,
    recordCount: 0,
    totalTranslatedVolume: 0,
  },
  projectName: 'Central Asia Legal Portal',
  responsible: true,
  status: 'ASSIGNED',
  workRecords: [],
}

/**
 * Verbatim `GET /api/v1/translator/activities/{id}` responses. The API runs with
 * `spring.jackson.default-property-inclusion: non_null`, so null fields are absent
 * from the payload rather than present as `null`.
 */
const activityWithOmittedWorkHours = {
  assignedDate: '2026-09-25',
  activityName: 'Onboarding email sequence',
  activityNumber: 'EL-2026-003',
  createdDate: '2026-09-25',
  id: 103,
  progress: { lastRecordDate: '2026-09-30', recordCount: 2, totalTranslatedVolume: 14.0 },
  projectName: 'Retail Launch Localization',
  responsible: false,
  status: 'IN_PROGRESS',
  workRecords: [
    // work hours never entered -> key absent
    { id: 303, recordDate: '2026-09-30', translatedVolume: 5.75 },
    // explicitly null
    { id: 302, recordDate: '2026-09-29', translatedVolume: 8.25, workHours: null },
    // populated
    { id: 301, recordDate: '2026-09-28', translatedVolume: 12.5, workHours: 3 },
  ],
} as unknown as ActivityDetail

const activityWithoutRecords = {
  assignedDate: '2026-09-23',
  activityName: 'Supplier contract appendix',
  activityNumber: 'EL-2026-002',
  createdDate: '2026-09-23',
  id: 102,
  // lastRecordDate absent because there are no records
  progress: { totalTranslatedVolume: 0, recordCount: 0 },
  projectName: 'Central Asia Legal Portal',
  responsible: true,
  status: 'ASSIGNED',
  workRecords: [],
} as unknown as ActivityDetail

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/translator/activities/101']}>
      <Routes>
        <Route path="/translator/activities/:activityId" element={<TranslatorActivityDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('TranslatorActivityDetailPage', () => {
  beforeEach(() => {
    vi.mocked(translatorActivityApi.get).mockReset()
    vi.mocked(translatorActivityApi.createWorkRecord).mockReset()
    vi.mocked(translatorActivityApi.updateWorkRecord).mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('shows a loading state', () => {
    vi.mocked(translatorActivityApi.get).mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByLabelText('Loading activity details')).toHaveAttribute('aria-busy', 'true')
  })

  it('shows an empty daily history state', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()

    expect(await screen.findByRole('heading', { name: 'No daily records yet' })).toBeInTheDocument()
    expect(screen.getByText('Recorded translated volume will appear here.')).toBeInTheDocument()
  })

  it('shows an error state when the activity cannot load', async () => {
    vi.mocked(translatorActivityApi.get).mockRejectedValue(new Error('not found'))

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Activity unavailable' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('shows populated activity details and daily records', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      progress: {
        lastRecordDate: '2026-09-30',
        recordCount: 1,
        totalTranslatedVolume: 8.25,
      },
      status: 'IN_PROGRESS',
      workRecords: [{
        id: 301,
        recordDate: '2026-09-30',
        translatedVolume: 8.25,
        workHours: 2.5,
      }],
    })

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Privacy policy translation' })).toBeInTheDocument()
    expect(screen.getByText('EL-2026-001')).toBeInTheDocument()
    expect(screen.getAllByText('8.25 volume units')).toHaveLength(2)
    expect(screen.getByText('2.50')).toBeInTheDocument()
  })

  it('renders records whose work hours are absent, null or set', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(activityWithOmittedWorkHours)

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Onboarding email sequence' })).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Sep 30, 2026/ })).toHaveTextContent('Not entered')
    expect(screen.getByRole('row', { name: /Sep 29, 2026/ })).toHaveTextContent('Not entered')
    expect(screen.getByRole('row', { name: /Sep 28, 2026/ })).toHaveTextContent('3.00')
  })

  it('prefills the form with a numeric work hours value from an existing record', async () => {
    const user = userEvent.setup()
    const todayLocal = todayLocalDateString()
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      progress: { lastRecordDate: todayLocal, recordCount: 1, totalTranslatedVolume: 8.25 },
      workRecords: [{ id: 601, recordDate: todayLocal, translatedVolume: 8.25, workHours: 2.5 }],
    })

    renderPage()

    await screen.findByRole('heading', { name: "Edit today's record" })
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 601, {
      translatedVolume: 8.25,
      workHours: 2.5,
    })
  })

  it('sends null work hours when the existing record omits them', async () => {
    const user = userEvent.setup()
    const todayLocal = todayLocalDateString()
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      progress: { lastRecordDate: todayLocal, recordCount: 1, totalTranslatedVolume: 5.75 },
      workRecords: [{ id: 602, recordDate: todayLocal, translatedVolume: 5.75 }],
    })
    vi.mocked(translatorActivityApi.updateWorkRecord).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: "Edit today's record" })
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 602, {
      translatedVolume: 5.75,
      workHours: null,
    })
  })

  it('shows an activity with no records when last record date is absent', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(activityWithoutRecords)

    renderPage()

    expect(await screen.findByRole('heading', { name: 'No daily records yet' })).toBeInTheDocument()
    expect(screen.getAllByText('No records yet').length).toBeGreaterThan(0)
    expect(screen.getByRole('heading', { name: 'Record today' })).toBeInTheDocument()
  })

  it('shows inline validation before saving a daily record', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record today' })
    fireEvent.click(screen.getByRole('button', { name: 'Save record' }))

    expect(screen.getByText('Enter translated volume.')).toBeInTheDocument()
    expect(translatorActivityApi.createWorkRecord).not.toHaveBeenCalled()
  })

  it('creates a daily record and shows success', async () => {
    const user = userEvent.setup()
    const todayLocal = todayLocalDateString()
    const updatedActivity: ActivityDetail = {
      ...baseActivity,
      progress: {
        lastRecordDate: todayLocal,
        recordCount: 1,
        totalTranslatedVolume: 4.5,
      },
      status: 'IN_PROGRESS',
      workRecords: [{
        id: 501,
        recordDate: todayLocal,
        translatedVolume: 4.5,
        workHours: null,
      }],
    }
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)
    vi.mocked(translatorActivityApi.createWorkRecord).mockResolvedValue(updatedActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record today' })
    await user.type(screen.getByLabelText('Translated volume (units)'), '4.5')
    await user.click(screen.getByRole('button', { name: 'Save record' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Daily record saved.')
    expect(translatorActivityApi.createWorkRecord).toHaveBeenCalledWith(101, {
      recordDate: todayLocal,
      translatedVolume: 4.5,
      workHours: null,
    })
    expect(screen.getByRole('heading', { name: "Edit today's record" })).toBeInTheDocument()
  })
})
