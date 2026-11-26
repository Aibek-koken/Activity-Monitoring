import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { editRequestApi, reminderApi, translatorActivityApi } from '../lib/api'
import type { WorkRecordEditRequest } from '../types/edit-request'
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
  reminderApi: {
    list: vi.fn(),
    markRead: vi.fn(),
    delete: vi.fn(),
  },
  editRequestApi: { translatorList: vi.fn(), create: vi.fn() },
  translatorActivityApi: {
    attachLegacyDocument: vi.fn(),
    createWorkRecord: vi.fn(),
    get: vi.fn(),
    finish: vi.fn(),
    reviews: vi.fn(),
    saveCurrentEstimate: vi.fn(),
    resubmitForChecking: vi.fn(),
    submitForChecking: vi.fn(),
    submitWeeklyVersion: vi.fn(),
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
    hasResponsibleChiefEditor: true,
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

function approvedRequest(workRecordId: number): WorkRecordEditRequest {
  return { id: 900 + workRecordId, activityId: 101, activityNumber: 'EL-2026-001',
    projectName: 'Central Asia Legal Portal', workRecordId, recordDate: '2026-09-29',
    translatorName: 'Maya Chen', reason: 'Correct a typo', status: 'APPROVED',
    requestedAt: '2026-10-01T10:00:00Z' }
}

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
    vi.mocked(reminderApi.list).mockReset()
    vi.mocked(reminderApi.list).mockResolvedValue({ reminders: [] })
    vi.mocked(reminderApi.markRead).mockReset()
    vi.mocked(reminderApi.markRead).mockResolvedValue(undefined)
    vi.mocked(editRequestApi.translatorList).mockReset()
    vi.mocked(editRequestApi.translatorList).mockResolvedValue({ requests: [] })
    vi.mocked(editRequestApi.create).mockReset()
    vi.mocked(translatorActivityApi.get).mockReset()
    vi.mocked(translatorActivityApi.reviews).mockReset()
    vi.mocked(translatorActivityApi.reviews).mockResolvedValue({ reviews: [] })
    vi.mocked(translatorActivityApi.attachLegacyDocument).mockReset()
    vi.mocked(translatorActivityApi.createWorkRecord).mockReset()
    vi.mocked(translatorActivityApi.saveCurrentEstimate).mockReset()
    vi.mocked(translatorActivityApi.finish).mockReset()
    vi.mocked(translatorActivityApi.submitForChecking).mockReset()
    vi.mocked(translatorActivityApi.resubmitForChecking).mockReset()
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
    expect(screen.getByText('Recorded pages will appear here.')).toBeInTheDocument()
  })

  it('keeps the daily task first and combines finish guidance with the weekly estimate', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()
    const dailyHeading = await screen.findByRole('heading', { name: 'Record daily work' })
    const estimateToggle = screen.getByRole('button', { name: /Weekly remaining estimate/ })
    expect(dailyHeading.compareDocumentPosition(estimateToggle) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(estimateToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByText(/logged by you/i)).toBeInTheDocument()
    expect(screen.queryByText(/Daily records show completed work/)).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'When the translation is done' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Update remaining estimate' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save estimate' })).not.toBeInTheDocument()

    await user.click(estimateToggle)
    expect(estimateToggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('Remaining pages')).toBeVisible()
  })

  it('opens the weekly estimate directly from a reminder link', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage('/translator/activities/101#weekly-estimate')

    expect(await screen.findByRole('button', { name: /Weekly remaining estimate/ })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('Remaining pages')).toBeVisible()
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
    await user.type(screen.getByLabelText('Remaining pages'), '-1')
    await user.click(screen.getByRole('button', { name: 'Save estimate' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a non-negative number')
    expect(translatorActivityApi.saveCurrentEstimate).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Remaining pages'))
    await user.type(screen.getByLabelText('Remaining pages'), '0')
    await user.click(screen.getByRole('button', { name: 'Save estimate' }))
    expect(translatorActivityApi.saveCurrentEstimate).toHaveBeenCalledWith(101, 0)
    expect(await screen.findByText('0 pages left')).toBeInTheDocument()
    expect(screen.getByText('Oct 19–Oct 25, 2026 · Updated Oct 22, 17:00')).not.toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Show estimate history' }))
    expect(screen.getByText('Oct 19–Oct 25, 2026 · Updated Oct 22, 17:00')).toBeInTheDocument()
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
    expect(screen.getByText('120 pages left')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Weekly remaining estimate/ }))
    expect(screen.getByText('Only the responsible translator can update this estimate.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finish translation' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit for checking' })).not.toBeInTheDocument()
  })

  it('shows estimate history directly after the activity is finished', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      status: 'FINISHED',
      currentWeekEstimate: null,
      weeklyEstimates: [{ id: 1, weekStartDate: '2026-10-19', remainingTextVolume: 0, updatedAt: '2026-10-22T12:00:00Z' }],
      permissions: { canEditWorkRecords: false, canEditWeeklyEstimate: false, canFinish: false, canSubmitForChecking: true, hasResponsibleChiefEditor: true },
    })

    renderPage()

    const estimateHistory = (await screen.findByRole('heading', { name: 'Weekly estimate history' })).closest('section')
    expect(estimateHistory).not.toBeNull()
    expect(within(estimateHistory!).getByText('Oct 19–Oct 25, 2026 · Updated Oct 22, 17:00')).not.toBeVisible()
    fireEvent.click(within(estimateHistory!).getByRole('button', { name: 'Show weekly estimate history' }))
    expect(within(estimateHistory!).getByText('Oct 19–Oct 25, 2026 · Updated Oct 22, 17:00')).toBeVisible()
    expect(within(estimateHistory!).getByText('0 pages')).toBeVisible()
    expect(screen.queryByText('No estimate yet')).not.toBeInTheDocument()
    expect(screen.queryByText('This estimate is read-only after finishing.')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Weekly remaining estimate/ })).not.toBeInTheDocument()
  })

  it('confirms finish, locks records, then submits a selected document for checking', async () => {
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
      permissions: { canEditWorkRecords: false, canEditWeeklyEstimate: false, canFinish: false, canSubmitForChecking: true, hasResponsibleChiefEditor: true },
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
    expect(screen.getByText('Choose file')).toBeInTheDocument()
    expect(screen.getByText('No file selected')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Record daily work' })).not.toBeInTheDocument()

    const original = new File(['%PDF-1.7'], 'translation.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('Translation document'), original)
    expect(screen.getByText('translation.pdf')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Submit for checking' }))
    expect(translatorActivityApi.submitForChecking).toHaveBeenCalledWith(101, original)
    expect(await screen.findByRole('heading', { name: 'Your final version is with the Chief Editor' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit for checking' })).not.toBeInTheDocument()
  })

  it('shows correction feedback and immutable review downloads, then uploads a corrected file', async () => {
    const user = userEvent.setup()
    const correctionRequired: ActivityDetail = {
      ...baseActivity,
      status: 'CORRECTION_REQUIRED',
      permissions: {
        canEditWorkRecords: false,
        canEditWeeklyEstimate: false,
        canFinish: false,
        canSubmitForChecking: false,
        hasResponsibleChiefEditor: true,
      },
    }
    const resubmitted: ActivityDetail = { ...correctionRequired, status: 'UNDER_CHECKING' }
    vi.mocked(translatorActivityApi.get).mockResolvedValue(correctionRequired)
    vi.mocked(translatorActivityApi.reviews)
      .mockResolvedValueOnce({ reviews: [{
        id: 901,
        cycleNumber: 1,
        submittedAt: '2026-11-08T08:15:00Z',
        result: 'CORRECTION_REQUESTED',
        feedback: 'Correct the terminology in sections 2 and 4.',
        expectedCheckingHours: 1.5,
        decidedAt: '2026-11-09T10:00:00Z',
        document: {
          originalName: 'translation-v1.pdf',
          contentType: 'application/pdf',
          sizeBytes: 2048,
          sha256: 'abc123',
          downloadUrl: '/api/v1/reviews/901/document',
        },
      }] })
      .mockResolvedValueOnce({ reviews: [
        {
          id: 902,
          cycleNumber: 2,
          submittedAt: '2026-11-10T09:00:00Z',
          document: {
            originalName: 'translation-v2.docx',
            contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            sizeBytes: 4096,
            sha256: 'def456',
            downloadUrl: '/api/v1/reviews/902/document',
          },
        },
        {
          id: 901,
          cycleNumber: 1,
          submittedAt: '2026-11-08T08:15:00Z',
          result: 'CORRECTION_REQUESTED',
          feedback: 'Correct the terminology in sections 2 and 4.',
          expectedCheckingHours: 1.5,
          decidedAt: '2026-11-09T10:00:00Z',
          document: {
            originalName: 'translation-v1.pdf',
            contentType: 'application/pdf',
            sizeBytes: 2048,
            sha256: 'abc123',
            downloadUrl: '/api/v1/reviews/901/document',
          },
        },
      ] })
    vi.mocked(translatorActivityApi.resubmitForChecking).mockResolvedValue(resubmitted)

    renderPage('/translator/activities/101#correction')

    const correctionPanel = (await screen.findByRole('heading', { name: 'Corrections requested' })).closest('section')
    expect(correctionPanel).not.toBeNull()
    expect(within(correctionPanel!).getByText('Correct the terminology in sections 2 and 4.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Download translation-v1.pdf/ })).toHaveAttribute(
      'href', '/api/v1/reviews/901/document',
    )

    const corrected = new File(['PK'], 'translation-v2.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    await user.upload(screen.getByLabelText('Corrected translation document'), corrected)
    await user.click(screen.getByRole('button', { name: 'Upload corrected file' }))

    expect(translatorActivityApi.resubmitForChecking).toHaveBeenCalledWith(101, corrected)
    expect(await screen.findByRole('heading', { name: 'Your final version is with the Chief Editor' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'View activity details' }))
    await user.click(screen.getByRole('button', { name: 'Show review history' }))
    expect(screen.getByText('Cycle 2')).toBeInTheDocument()
    expect(screen.getByText('Cycle 1')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Download translation-v2.docx/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Download translation-v1.pdf/ })).toBeInTheDocument()
  })

  it('lets the responsible translator attach a file to an open legacy review once', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      status: 'UNDER_CHECKING',
      permissions: {
        canEditWorkRecords: false,
        canEditWeeklyEstimate: false,
        canFinish: false,
        canSubmitForChecking: false,
        hasResponsibleChiefEditor: true,
      },
    })
    vi.mocked(translatorActivityApi.reviews).mockResolvedValue({ reviews: [{
      id: 800,
      cycleNumber: 1,
      submittedAt: '2026-10-25T08:00:00Z',
    }] })
    vi.mocked(translatorActivityApi.attachLegacyDocument).mockResolvedValue({
      id: 800,
      cycleNumber: 1,
      submittedAt: '2026-10-25T08:00:00Z',
      document: {
        originalName: 'legacy-translation.pdf',
        contentType: 'application/pdf',
        sizeBytes: 1024,
        sha256: 'legacy-sha',
        downloadUrl: '/api/v1/reviews/800/document',
      },
    })

    renderPage('/translator/activities/101#details')
    await screen.findByRole('heading', { name: 'Under checking' })
    await user.click(screen.getByRole('button', { name: 'Show review history' }))
    const legacy = new File(['%PDF-1.7'], 'legacy-translation.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('Document for cycle 1'), legacy)
    await user.click(screen.getByRole('button', { name: 'Attach document' }))

    expect(translatorActivityApi.attachLegacyDocument).toHaveBeenCalledWith(101, 800, legacy)
    expect(await screen.findByRole('link', { name: /Download legacy-translation.pdf/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Attach document' })).not.toBeInTheDocument()
  })

  it('disables submission when the project has no responsible editor', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue({
      ...baseActivity,
      status: 'FINISHED',
      permissions: { canEditWorkRecords: false, canEditWeeklyEstimate: false, canFinish: false, canSubmitForChecking: false, hasResponsibleChiefEditor: false },
    })

    renderPage()
    expect(await screen.findByRole('heading', { name: 'Waiting for Chief Editor assignment' })).toBeInTheDocument()
    expect(screen.getByText(/Project Manager must assign/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit unavailable' })).toBeDisabled()
    expect(translatorActivityApi.submitForChecking).not.toHaveBeenCalled()
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
    expect(screen.getByText('8.25 pages')).toBeInTheDocument()
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
    vi.mocked(editRequestApi.translatorList).mockResolvedValue({ requests: [approvedRequest(601)] })

    renderPage()

    await screen.findByRole('heading', { name: 'Daily history' })
    await user.click(screen.getByRole('button', { name: 'Show history' }))
    await user.click(await screen.findByRole('button', { name: 'Edit once' }))
    expect(screen.getByLabelText('Hours (optional)')).toHaveValue('2')
    expect(screen.getByLabelText('Minutes')).toHaveValue('30')
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 601, {
      translatedVolume: 8.25,
      workHours: 2.5,
      editRequestId: 1501,
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
    vi.mocked(editRequestApi.translatorList).mockResolvedValue({ requests: [approvedRequest(602)] })

    renderPage()

    await screen.findByRole('heading', { name: 'Daily history' })
    await user.click(screen.getByRole('button', { name: 'Show history' }))
    await user.click(await screen.findByRole('button', { name: 'Edit once' }))
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 602, {
      translatedVolume: 5.75,
      workHours: null,
      editRequestId: 1502,
    })
  })

  it('shows an activity with no records when last record date is absent', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(activityWithoutRecords)

    renderPage()

    await screen.findByRole('heading', { name: 'Daily history' })
    fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
    expect(screen.getByRole('heading', { name: 'No daily records yet' })).toBeInTheDocument()
    expect(screen.getAllByText('0 pages').length).toBeGreaterThan(0)
    expect(screen.getByRole('heading', { name: 'Record daily work' })).toBeInTheDocument()
  })

  it('shows inline validation before saving a daily record', async () => {
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record daily work' })
    fireEvent.click(screen.getByRole('button', { name: 'Save record' }))

    expect(await screen.findByText('Enter translated volume.')).toBeInTheDocument()
    expect(translatorActivityApi.createWorkRecord).not.toHaveBeenCalled()
  })

  it('rejects minutes greater than 59 before saving work time', async () => {
    const user = userEvent.setup()
    vi.mocked(translatorActivityApi.get).mockResolvedValue(baseActivity)

    renderPage()

    await screen.findByRole('heading', { name: 'Record daily work' })
    await user.type(screen.getByLabelText('Translated pages'), '4.5')
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
    await user.type(screen.getByLabelText('Translated pages'), '4.5')
    await user.click(screen.getByRole('button', { name: 'Save record' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Daily record saved.')
    expect(translatorActivityApi.createWorkRecord).toHaveBeenCalledWith(101, {
      recordDate: todayLocal,
      translatedVolume: 4.5,
      workHours: null,
    })
    expect(screen.getByRole('heading', { name: 'Record daily work' })).toBeInTheDocument()
  })


  it('sends the current weekly version while the activity stays active', async () => {
    const user = userEvent.setup()
    const weeklyReady: ActivityDetail = {
      ...baseActivity,
      permissions: { ...baseActivity.permissions, canSubmitWeeklyVersion: true },
    }
    const submitted: ActivityDetail = {
      ...weeklyReady,
      permissions: { ...weeklyReady.permissions, canSubmitWeeklyVersion: false, hasOpenReview: true },
    }
    vi.mocked(translatorActivityApi.get).mockResolvedValue(weeklyReady)
    vi.mocked(translatorActivityApi.reviews).mockResolvedValue({ reviews: [{
      id: 900,
      cycleNumber: 1,
      kind: 'WEEKLY',
      weekStartDate: '2026-10-19',
      submittedAt: '2026-10-23T08:00:00Z',
      annotationCount: 2,
    }] })
    vi.mocked(translatorActivityApi.submitWeeklyVersion).mockResolvedValue(submitted)

    renderPage()
    await screen.findByRole('heading', { name: "This week's version" })
    const weekly = new File(['%PDF-1.7'], 'week-43.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('Weekly translation document'), weekly)
    await user.click(screen.getByRole('button', { name: 'Send weekly version' }))

    expect(translatorActivityApi.submitWeeklyVersion).toHaveBeenCalledWith(101, weekly)
    expect(await screen.findByRole('heading', { name: 'Weekly version under checking' })).toBeInTheDocument()
    expect(screen.getByText(/Keep recording daily work/)).toBeInTheDocument()
  })

  it('records rework pages without counting them as first-time translated pages', async () => {
    const user = userEvent.setup()
    const todayLocal = todayLocalDateString()
    const correction: ActivityDetail = {
      ...baseActivity,
      status: 'CORRECTION_REQUIRED',
      rework: {
        correctionPages: 3,
        hasWeeklyEstimate: false,
        outstandingReworkPages: 3,
        remainingPages: null,
        reworkRecordedPages: 0,
        totalRemainingPages: 3,
      },
      permissions: {
        ...baseActivity.permissions,
        canEditWeeklyEstimate: false,
        hasOutstandingRework: true,
      },
    }
    vi.mocked(translatorActivityApi.get).mockResolvedValue(correction)
    vi.mocked(translatorActivityApi.reviews).mockResolvedValue({ reviews: [{
      id: 901,
      cycleNumber: 1,
      kind: 'FINAL',
      submittedAt: '2026-10-20T08:00:00Z',
      result: 'CORRECTION_REQUESTED',
      feedback: 'Redo three pages.',
      correctionPages: 3,
      annotationCount: 2,
    }] })
    vi.mocked(translatorActivityApi.createWorkRecord).mockResolvedValue(correction)

    renderPage()
    await screen.findByRole('heading', { name: 'Corrections requested' })
    await user.type(screen.getByLabelText('Pages reworked'), '2')
    await user.type(screen.getByLabelText('Hours (optional)'), '1')
    await user.type(screen.getByLabelText('Minutes'), '30')
    await user.click(screen.getByRole('button', { name: 'Save record' }))

    expect(translatorActivityApi.createWorkRecord).toHaveBeenCalledWith(101, {
      recordDate: todayLocal,
      translatedVolume: 0,
      workHours: 1.5,
      reworkPages: 2,
    })
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
    vi.mocked(editRequestApi.translatorList).mockResolvedValue({ requests: [approvedRequest(700)] })

    renderPage()

    await screen.findByRole('heading', { name: 'Record daily work' })
    await user.click(screen.getByRole('button', { name: 'Show history' }))
    await user.click(await screen.findByRole('button', { name: 'Edit once' }))

    expect(screen.getByRole('heading', { name: 'Edit daily record' })).toBeInTheDocument()
    expect(screen.getByLabelText('Record date')).toHaveValue('Sep 29, 2026')
    expect(screen.getByLabelText('Translated pages')).toHaveValue('5.75')

    await user.clear(screen.getByLabelText('Translated pages'))
    await user.type(screen.getByLabelText('Translated pages'), '6.25')
    await user.click(screen.getByRole('button', { name: 'Update record' }))

    expect(translatorActivityApi.updateWorkRecord).toHaveBeenCalledWith(101, 700, {
      translatedVolume: 6.25,
      workHours: null,
      editRequestId: 1600,
    })
  })

  it('keeps an older record selected when the local date crosses midnight', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    try {
      vi.setSystemTime(new Date('2026-11-10T23:59:59+05:00'))
      vi.mocked(translatorActivityApi.get).mockResolvedValue({
        ...baseActivity,
        workRecords: [{ id: 700, recordDate: '2026-09-29', translatedVolume: 5.75, workHours: null }],
      })
      vi.mocked(editRequestApi.translatorList).mockResolvedValue({ requests: [approvedRequest(700)] })
      renderPage()

      await screen.findByRole('heading', { name: 'Record daily work' })
      fireEvent.click(screen.getByRole('button', { name: 'Show history' }))
      vi.setSystemTime(new Date('2026-11-11T00:00:01+05:00'))
      fireEvent.click(await screen.findByRole('button', { name: 'Edit once' }))

      expect(screen.getByRole('heading', { name: 'Edit daily record' })).toBeInTheDocument()
      expect(screen.getByLabelText('Record date')).toHaveValue('Sep 29, 2026')
    } finally {
      vi.useRealTimers()
    }
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
    expect(screen.getAllByRole('button', { name: 'Request edit' })).toHaveLength(3)

    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-09-29' } })
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-09-30' } })
    expect(screen.getAllByRole('button', { name: 'Request edit' })).toHaveLength(2)
    expect(screen.getByRole('row', { name: /Sep 29, 2026/ })).toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /Sep 28, 2026/ })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-10-01' } })
    expect(screen.getByRole('alert')).toHaveTextContent('The start date must be on or before the end date.')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Clear dates' }))
    expect(screen.getByLabelText('From date')).toHaveValue('')
    expect(screen.getByLabelText('To date')).toHaveValue('')
    expect(screen.getAllByRole('button', { name: 'Request edit' })).toHaveLength(3)
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
    expect(screen.getByText('8.25 pages')).toBeInTheDocument()
  })
})
