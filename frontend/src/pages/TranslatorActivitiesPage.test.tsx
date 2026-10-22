import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { translatorActivityApi } from '../lib/api'
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
    expect(screen.getAllByText('20.75')).toHaveLength(1)
    expect(screen.queryByText('Shown')).not.toBeInTheDocument()
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
