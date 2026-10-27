import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, translatorActivityApi } from '../lib/api'
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
    finish: vi.fn(),
    saveCurrentEstimate: vi.fn(),
    submitForChecking: vi.fn(),
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
  currentWeek: { startDate: '2026-10-19', endDate: '2026-10-25' },
  weeklyEstimates: [],
  permissions: {
    canEditWorkRecords: true,
    canEditWeeklyEstimate: true,
    canFinish: false,
    canSubmitForChecking: false,
  },
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
  currentWeek: baseActivity.currentWeek,
  weeklyEstimates: [],
  permissions: { ...baseActivity.permissions, canEditWeeklyEstimate: false },
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
  currentWeek: baseActivity.currentWeek,
  weeklyEstimates: [],
  permissions: baseActivity.permissions,
  workRecords: [],
} as unknown as ActivityDetail

function renderPage(url = '/translator/activities/101') {
  return render(
    <MemoryRouter initialEntries={[url]}>
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
    vi.mocked(translatorActivityApi.saveCurrentEstimate).mockReset()
    vi.mocked(translatorActivityApi.finish).mockReset()
    vi.mocked(translatorActivityApi.submitForChecking).mockReset()
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

    await screen.findByRole('heading', { name: 'Daily history' })
    expect(screen.queryByRole('heading', { name: 'No daily records yet' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    expect(await screen.findByRole('heading', { name: 'No daily records yet' })).toBeInTheDocument()
    expect(screen.getByText('Recorded translated volume will appear here.')).toBeInTheDocument()
  })

  it('keeps the daily task first and opens the estimate from the finish guide', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()
    const dailyHeading = await screen.findByRole('heading', { name: 'Record daily work' })
    const estimateToggle = screen.getByRole('button', { name: /Weekly remaining estimate/ })
    expect(dailyHeading.compareDocumentPosition(estimateToggle) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(estimateToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByText(/logged by you/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save estimate' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Update remaining estimate' }))
    expect(estimateToggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('Remaining volume (units)')).toHaveFocus()
  })

  it('opens the weekly estimate directly from a reminder link', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage('/translator/activities/101#weekly-estimate')

    expect(await screen.findByRole('button', { name: /Weekly remaining estimate/ })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('Remaining volume (units)')).toBeVisible()
  })

  it('validates and saves the current weekly estimate', async () => {
    const user = userEvent.setup()
    const saved: ActivityDetail = {
      ...baseActivity,
      currentWeekEstimate: { id: 1, weekStartDate: '2026-10-19', remainingTextVolume: 0, updatedAt: '2026-10-22T12:00:00Z' },
      weeklyEstimates: [{ id: 1, weekStartDate: '2026-10-19', remainingTextVolume: 0, updatedAt: '2026-10-22T12:00:00Z' }],
      permissions: { ...baseActivity.permissions, canFinish: true },
    }
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)
    vi.mocked(translatorActivityApi.saveCurrentEstimate).mockResolvedValue(saved)

    renderPage()
    await user.click(await screen.findByRole('button', { name: /Weekly remaining estimate/ }))
    await user.type(screen.getByLabelText('Remaining volume (units)'), '-1')
    await user.click(screen.getByRole('button', { name: 'Save estimate' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a non-negative number')
    expect(translatorActivityApi.saveCurrentEstimate).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Remaining volume (units)'))
    await user.type(screen.getByLabelText('Remaining volume (units)'), '0')
    await user.click(screen.getByRole('button', { name: 'Save estimate' }))
    expect(translatorActivityApi.saveCurrentEstimate).toHaveBeenCalledWith(101, 0)
    expect(await screen.findByText('0 units left')).toBeInTheDocument()
    expect(screen.getByText('Week of Oct 19, 2026')).not.toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Show estimate history' }))
    expect(screen.getByText('Week of Oct 19, 2026')).toBeInTheDocument()
  })

  it('shows estimates read-only to an assisting translator', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      responsible: false,
      currentWeekEstimate: { id: 2, weekStartDate: '2026-10-19', remainingTextVolume: 120, updatedAt: '2026-10-22T12:00:00Z' },
      weeklyEstimates: [{ id: 2, weekStartDate: '2026-10-19', remainingTextVolume: 120, updatedAt: '2026-10-22T12:00:00Z' }],
      permissions: { ...baseActivity.permissions, canEditWeeklyEstimate: false },
    })

    renderPage()
    await screen.findByRole('button', { name: /Weekly remaining estimate/ })
    expect(screen.queryByRole('button', { name: 'Save estimate' })).not.toBeInTheDocument()
    expect(screen.getByText('120 units left')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Weekly remaining estimate/ }))
    expect(screen.getByText('Only the responsible translator can update this estimate.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finish translation' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit for checking' })).not.toBeInTheDocument()
  })

  it('confirms finish, locks records, then submits for checking', async () => {
    const user = userEvent.setup()
    const ready: ActivityDetail = {
      ...baseActivity,
      currentWeekEstimate: { id: 1, weekStartDate: '2026-10-19', remainingTextVolume: 0, updatedAt: '2026-10-22T12:00:00Z' },
      weeklyEstimates: [{ id: 1, weekStartDate: '2026-10-19', remainingTextVolume: 0, updatedAt: '2026-10-22T12:00:00Z' }],
      permissions: { ...baseActivity.permissions, canFinish: true },
    }
    const finished: ActivityDetail = {
      ...ready,
      status: 'FINISHED',
      permissions: { canEditWorkRecords: false, canEditWeeklyEstimate: false, canFinish: false, canSubmitForChecking: true },
    }
    const submitted: ActivityDetail = {
      ...finished,
      status: 'UNDER_CHECKING',
      permissions: { ...finished.permissions, canSubmitForChecking: false },
    }
    vi.mocked(translatorActivityApi.get).mockResolvedValue(ready)
    vi.mocked(translatorActivityApi.finish).mockResolvedValue(finished)
    vi.mocked(translatorActivityApi.submitForChecking).mockResolvedValue(submitted)

    renderPage()
    await screen.findByRole('button', { name: 'Finish translation' })
    await user.click(screen.getByRole('button', { name: 'Finish translation' }))
    expect(screen.getByText(/Finishing locks daily records/)).toBeInTheDocument()
    expect(translatorActivityApi.finish).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Confirm finish' }))
    expect(translatorActivityApi.finish).toHaveBeenCalledWith(101)
    expect(await screen.findByRole('button', { name: 'Submit for checking' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Record daily work' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Submit for checking' }))
    expect(translatorActivityApi.submitForChecking).toHaveBeenCalledWith(101)
    expect(await screen.findByRole('heading', { name: 'Under checking' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit for checking' })).not.toBeInTheDocument()
  })

  it('keeps a finished activity on screen when submission has no editor', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      status: 'FINISHED',
      permissions: { canEditWorkRecords: false, canEditWeeklyEstimate: false, canFinish: false, canSubmitForChecking: true },
    })
    vi.mocked(translatorActivityApi.submitForChecking).mockRejectedValue(
      new ApiError(409, 'No responsible Chief Editor is assigned to this project'),
    )

    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Submit for checking' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No responsible Chief Editor is assigned to this project')
    expect(screen.getByRole('button', { name: 'Submit for checking' })).toBeInTheDocument()
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
    expect(screen.getAllByText('EL-2026-001')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    expect(screen.getByText('8.25 units')).toBeInTheDocument()
    expect(screen.getByText('8.25')).toBeInTheDocument()
    expect(screen.getByText('2 h 30 min')).toBeInTheDocument()
  })

  it('renders records whose work hours are absent, null or set', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(activityWithOmittedWorkHours)

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Onboarding email sequence' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    expect(screen.getByRole('row', { name: /Sep 30, 2026/ })).toHaveTextContent('Not entered')
    expect(screen.getByRole('row', { name: /Sep 29, 2026/ })).toHaveTextContent('Not entered')
    expect(screen.getByRole('row', { name: /Sep 28, 2026/ })).toHaveTextContent('3 h')
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

    await screen.findByRole('heading', { name: 'Edit daily record' })
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

    await screen.findByRole('heading', { name: 'Edit daily record' })
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 602, {
      translatedVolume: 5.75,
      workHours: null,
    })
  })

  it('shows an activity with no records when last record date is absent', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(activityWithoutRecords)

    renderPage()

    await screen.findByRole('heading', { name: 'Daily history' })
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    expect(screen.getByRole('heading', { name: 'No daily records yet' })).toBeInTheDocument()
    expect(screen.getByText('0 units')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Record daily work' })).toBeInTheDocument()
  })

  it('shows inline validation before saving a daily record', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record daily work' })
    fireEvent.click(screen.getByRole('button', { name: 'Save record' }))

    expect(screen.getByText('Enter translated volume.')).toBeInTheDocument()
    expect(translatorActivityApi.createWorkRecord).not.toHaveBeenCalled()
  })

  it('rejects minutes greater than 59 before saving work time', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record daily work' })
    await user.type(screen.getByLabelText('Translated volume (units)'), '4.5')
    await user.type(screen.getByLabelText('Hours (optional)'), '3')
    await user.type(screen.getByLabelText('Minutes'), '90')
    await user.click(screen.getByRole('button', { name: 'Save record' }))

    expect(screen.getByText('Minutes must be 0 to 59.')).toBeInTheDocument()
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

    await screen.findByRole('heading', { name: 'Record daily work' })
    await user.type(screen.getByLabelText('Translated volume (units)'), '4.5')
    await user.click(screen.getByRole('button', { name: 'Save record' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Daily record saved.')
    expect(translatorActivityApi.createWorkRecord).toHaveBeenCalledWith(101, {
      recordDate: todayLocal,
      translatedVolume: 4.5,
      workHours: null,
    })
    expect(screen.getByRole('heading', { name: 'Edit daily record' })).toBeInTheDocument()
  })

  it('can select an older record from history for editing without changing its date', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      progress: {
        lastRecordDate: '2026-09-30',
        recordCount: 2,
        totalTranslatedVolume: 14,
      },
      workRecords: [
        { id: 701, recordDate: '2026-09-30', translatedVolume: 8.25, workHours: 2.5 },
        { id: 700, recordDate: '2026-09-29', translatedVolume: 5.75, workHours: null },
      ],
    })
    vi.mocked(translatorActivityApi.updateWorkRecord).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record daily work' })
    await user.click(screen.getByRole('button', { name: 'Show history' }))
    await user.click(screen.getAllByRole('button', { name: 'Edit' })[1])

    expect(screen.getByRole('heading', { name: 'Edit daily record' })).toBeInTheDocument()
    expect(screen.getByLabelText('Record date')).toHaveValue('Sep 29, 2026')
    expect(screen.getByLabelText('Translated volume (units)')).toHaveValue('5.75')

    await user.clear(screen.getByLabelText('Translated volume (units)'))
    await user.type(screen.getByLabelText('Translated volume (units)'), '6.25')
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 700, {
      translatedVolume: 6.25,
      workHours: null,
    })
  })

  it('returns to the same list filters through the breadcrumb', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage('/translator/activities/101?q=privacy&assignment=responsible')

    await screen.findByRole('heading', { name: 'Privacy policy translation' })
    const breadcrumb = await screen.findByRole('navigation', { name: 'Breadcrumb' })
    expect(within(breadcrumb).getByRole('link', { name: 'Activities' })).toHaveAttribute(
      'href', '/translator?q=privacy&assignment=responsible',
    )
    expect(within(breadcrumb).getByText('EL-2026-001')).toHaveAttribute('aria-current', 'page')
  })

  it('filters the expanded history by inclusive dates and restores all records', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      progress: { lastRecordDate: '2026-09-30', recordCount: 3, totalTranslatedVolume: 18 },
      workRecords: [
        { id: 703, recordDate: '2026-09-30', translatedVolume: 7 },
        { id: 702, recordDate: '2026-09-29', translatedVolume: 6 },
        { id: 701, recordDate: '2026-09-28', translatedVolume: 5 },
      ],
    })

    renderPage()
    await screen.findByRole('heading', { name: 'Daily history' })
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(3)

    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-09-29' } })
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-09-30' } })
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(2)
    expect(screen.getByRole('row', { name: /Sep 29, 2026/ })).toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /Sep 28, 2026/ })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-10-01' } })
    expect(screen.getByRole('alert')).toHaveTextContent('The start date must be on or before the end date.')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Clear dates' }))
    expect(screen.getByLabelText('From date')).toHaveValue('')
    expect(screen.getByLabelText('To date')).toHaveValue('')
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(3)
  })

  it('shows an empty date range without changing lifetime progress', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      progress: { lastRecordDate: '2026-09-30', recordCount: 1, totalTranslatedVolume: 8.25 },
      workRecords: [{ id: 701, recordDate: '2026-09-30', translatedVolume: 8.25 }],
    })

    renderPage()
    await screen.findByRole('heading', { name: 'Daily history' })
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-09-29' } })

    expect(screen.getByRole('heading', { name: 'No records in this period' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Record daily work' })).toBeInTheDocument()
    expect(screen.getByText('8.25 units')).toBeInTheDocument()
  })
})
