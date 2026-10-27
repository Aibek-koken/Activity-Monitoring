import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reminderApi, translatorActivityApi } from '../lib/api'
import type { ActivitySummary } from '../types/activity'
import { TranslatorActivitiesPage } from './TranslatorActivitiesPage'

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
  reminderApi: {
    list: vi.fn(),
    markRead: vi.fn(),
  },
  translatorActivityApi: {
    list: vi.fn(),
  },
}))

const assignedActivities: ActivitySummary[] = [
  {
    activityName: 'Privacy policy translation',
    activityNumber: 'EL-2026-001',
    assignedDate: '2026-09-22',
    id: 101,
    progress: {
      lastRecordDate: '2026-09-30',
      recordCount: 2,
      totalTranslatedVolume: 20.75,
    },
    projectName: 'Central Asia Legal Portal',
    responsible: true,
    status: 'IN_PROGRESS',
  },
]

function renderPage(url = '/translator') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <TranslatorActivitiesPage />
    </MemoryRouter>,
  )
}

async function runDebouncedLoad() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(250)
  })
}

describe('TranslatorActivitiesPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(translatorActivityApi.list).mockReset()
    vi.mocked(reminderApi.list).mockReset()
    vi.mocked(reminderApi.list).mockResolvedValue({ reminders: [] })
    vi.mocked(reminderApi.markRead).mockReset()
    vi.mocked(reminderApi.markRead).mockResolvedValue(undefined)
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('shows a loading state', () => {
    vi.mocked(translatorActivityApi.list).mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByLabelText('Loading activities')).toHaveAttribute('aria-busy', 'true')
  })

  it('shows populated activities', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: assignedActivities })

    renderPage()
    await runDebouncedLoad()

    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    expect(screen.getByText('EL-2026-001')).toBeInTheDocument()
    expect(screen.getAllByText('20.75 units')).toHaveLength(1)
    expect(screen.getByText(/Logged by you/)).toBeInTheDocument()
    expect(screen.queryByText('Shown')).not.toBeInTheDocument()
  })

  it('shows daily and weekly notifications in the top-right notification panel', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: assignedActivities })
    vi.mocked(reminderApi.list).mockResolvedValue({ reminders: [
      { id: 1, type: 'DAILY_RECORD_REMINDER', periodDate: '2026-10-23', message: "Record today's translated volume.", createdAt: '2026-10-23T13:00:00Z' },
      { id: 2, type: 'WEEKLY_ESTIMATE_REMINDER', periodDate: '2026-10-19', message: 'Add an estimate for EL-2026-001.', activityId: 101, activityNumber: 'EL-2026-001', createdAt: '2026-10-23T13:00:00Z' },
      { id: 3, type: 'WEEKLY_ESTIMATE_REMINDER', periodDate: '2026-10-19', message: 'Add an estimate for EL-2026-002.', activityId: 102, activityNumber: 'EL-2026-002', createdAt: '2026-10-23T13:00:00Z' },
    ] })

    renderPage('/translator?assignment=responsible')
    await runDebouncedLoad()
    expect(screen.getByRole('button', { name: 'Notifications, 3 unread' })).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(screen.getByRole('button', { name: 'Notifications, 3 unread' }))
    expect(screen.getByRole('heading', { name: 'Notifications' })).toBeInTheDocument()
    expect(screen.getByText('Daily record')).toBeInTheDocument()
    expect(screen.getAllByText('Weekly estimate')).toHaveLength(2)
    expect(screen.getByText("Record today's translated volume.").closest('a')).toHaveAttribute('href', '/translator?assignment=responsible#activity-list')
    expect(screen.getByText('Add an estimate for EL-2026-001.').closest('a')).toHaveAttribute('href', '/translator/activities/101?assignment=responsible#weekly-estimate')

    fireEvent.click(screen.getByText("Record today's translated volume."))
    await act(async () => {})
    expect(reminderApi.markRead).toHaveBeenCalledWith(1)
    expect(screen.queryByText("Record today's translated volume.")).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Notifications, 2 unread' }))

    const firstWeeklyNotification = screen.getByText('Add an estimate for EL-2026-001.').closest('li')
    fireEvent.click(within(firstWeeklyNotification!).getByRole('button', { name: 'Mark weekly estimate notification read' }))
    await act(async () => {})
    expect(reminderApi.markRead).toHaveBeenCalledWith(2)
    expect(screen.queryByText('Add an estimate for EL-2026-001.')).not.toBeInTheDocument()
    expect(screen.getByText('Add an estimate for EL-2026-002.')).toBeInTheDocument()
  })

  it('keeps the activity list usable when notifications fail to load', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: assignedActivities })
    vi.mocked(reminderApi.list).mockRejectedValue(new Error('offline'))

    renderPage()
    await runDebouncedLoad()
    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Notifications' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Notifications could not be loaded.')
  })

  it('shows an empty state when no activities are assigned', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: [] })

    renderPage()
    await runDebouncedLoad()

    expect(screen.getByRole('heading', { name: 'No assigned activities' })).toBeInTheDocument()
    expect(screen.getByText('Assigned translation work will appear here.')).toBeInTheDocument()
  })

  it('shows an error state when activities cannot load', async () => {
    vi.mocked(translatorActivityApi.list).mockRejectedValue(new Error('offline'))

    renderPage()
    await runDebouncedLoad()

    expect(screen.getByRole('heading', { name: 'Activities unavailable' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('shows a search no-results state', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: [] })

    renderPage()
    await runDebouncedLoad()

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'zzz' } })
    await runDebouncedLoad()

    expect(screen.getByRole('heading', { name: 'No matching activities' })).toBeInTheDocument()
    expect(screen.getByText('Try a different activity number or name.')).toBeInTheDocument()
  })

  it('filters responsible and assisting activities while preserving list context in links', async () => {
    const assistingActivity: ActivitySummary = {
      ...assignedActivities[0],
      activityName: 'Product launch copy',
      activityNumber: 'EL-2026-002',
      id: 102,
      progress: { lastRecordDate: null, recordCount: 0, totalTranslatedVolume: 0 },
      responsible: false,
    }
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: [...assignedActivities, assistingActivity] })

    renderPage('/translator?q=launch&assignment=assisting')
    await runDebouncedLoad()

    expect(translatorActivityApi.list).toHaveBeenCalledWith('launch')
    expect(screen.getByRole('button', { name: 'Assisting' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Product launch copy')).toBeInTheDocument()
    expect(screen.queryByText('Privacy policy translation')).not.toBeInTheDocument()
    expect(screen.getByText('Product launch copy').closest('a')).toHaveAttribute(
      'href', '/translator/activities/102?q=launch&assignment=assisting',
    )
    expect(screen.queryByText('Shown')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Responsible' }))
    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    expect(screen.queryByText('Product launch copy')).not.toBeInTheDocument()
    expect(screen.getByText('Privacy policy translation').closest('a')).toHaveAttribute(
      'href', '/translator/activities/101?q=launch&assignment=responsible',
    )
  })

  it('hides finished activities from quick views and shows them through the status filter', async () => {
    const finishedActivity: ActivitySummary = {
      ...assignedActivities[0],
      activityName: 'Completed policy translation',
      activityNumber: 'EL-2026-009',
      id: 109,
      status: 'FINISHED',
    }
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: [...assignedActivities, finishedActivity] })

    renderPage()
    await runDebouncedLoad()

    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    expect(screen.queryByText('Completed policy translation')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Responsible' }))
    expect(screen.queryByText('Completed policy translation')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'More filters' }))
    expect(screen.getByLabelText('Status')).toHaveDisplayValue('Exclude finished')
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'FINISHED' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }))

    expect(screen.getByText('Completed policy translation')).toBeInTheDocument()
    expect(screen.queryByText('Privacy policy translation')).not.toBeInTheDocument()
  })

  it('keeps the role filter selected when search changes', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: assignedActivities })

    renderPage('/translator?assignment=assisting')
    await runDebouncedLoad()
    expect(screen.getByRole('heading', { name: 'No activities in this role' })).toBeInTheDocument()

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'policy' } })
    await runDebouncedLoad()

    expect(translatorActivityApi.list).toHaveBeenLastCalledWith('policy')
    expect(screen.getByRole('button', { name: 'Assisting' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('heading', { name: 'No matching activities' })).toBeInTheDocument()
  })

  it('applies status and inclusive assigned-date filters without changing the API query', async () => {
    const secondActivity: ActivitySummary = {
      ...assignedActivities[0],
      activityName: 'Product launch copy',
      activityNumber: 'EL-2026-002',
      assignedDate: '2026-09-24',
      id: 102,
      status: 'ASSIGNED',
    }
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: [...assignedActivities, secondActivity] })

    renderPage('/translator?q=policy&assignment=responsible')
    await runDebouncedLoad()

    fireEvent.click(screen.getByRole('button', { name: 'More filters' }))
    expect(screen.getByRole('button', { name: 'More filters' })).toHaveAttribute('aria-expanded', 'true')
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'IN_PROGRESS' } })
    fireEvent.change(screen.getByLabelText('Assigned from'), { target: { value: '2026-09-22' } })
    fireEvent.change(screen.getByLabelText('Assigned to'), { target: { value: '2026-09-22' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }))

    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    expect(screen.queryByText('Product launch copy')).not.toBeInTheDocument()
    expect(screen.getByText('Privacy policy translation').closest('a')).toHaveAttribute(
      'href', '/translator/activities/101?q=policy&assignment=responsible&status=IN_PROGRESS&from=2026-09-22&to=2026-09-22',
    )
    expect(translatorActivityApi.list).toHaveBeenLastCalledWith('policy')
    expect(screen.getByRole('button', { name: 'More filters (active)' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('rejects an inverted date range and clears advanced filters', async () => {
    vi.mocked(translatorActivityApi.list).mockResolvedValue({ activities: assignedActivities })
    renderPage('/translator?status=ASSIGNED&assignment=responsible')
    await runDebouncedLoad()
    expect(screen.getByRole('heading', { name: 'No matching activities' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'More filters (active)' }))
    expect(screen.getByLabelText('Status')).toHaveValue('ASSIGNED')
    fireEvent.change(screen.getByLabelText('Assigned from'), { target: { value: '2026-09-30' } })
    fireEvent.change(screen.getByLabelText('Assigned to'), { target: { value: '2026-09-22' } })
    expect(screen.getByRole('alert')).toHaveTextContent('Start date must be on or before end date.')
    expect(screen.getByRole('button', { name: 'Apply filters' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Clear' }))
    expect(screen.getByText('Privacy policy translation')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'More filters' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('button', { name: 'Responsible' })).toHaveAttribute('aria-pressed', 'true')
  })
})
