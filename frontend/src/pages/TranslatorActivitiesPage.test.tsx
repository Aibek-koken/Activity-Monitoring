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

function renderPage() {
  return render(
    <MemoryRouter>
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
    expect(screen.getAllByText('20.75 volume units')).toHaveLength(2)
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
})
