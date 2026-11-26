import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProjectManagerRequestsPage } from './ProjectManagerRequestsPage'
import { editRequestApi } from '../lib/api'

vi.mock('../components/WorkspaceLayout', () => ({ WorkspaceLayout: ({ children }: { children: ReactNode }) => <>{children}</> }))
vi.mock('../lib/api', () => ({ editRequestApi: { managerList: vi.fn(), decide: vi.fn() } }))

const request = {
  id: 7, activityId: 11, activityNumber: 'EL-2026-011', projectName: 'Medical manuals',
  workRecordId: 9, recordDate: '2026-11-17', translatorName: 'Maya', reason: 'I entered the wrong hours',
  status: 'PENDING' as const, decisionComment: null, requestedAt: '2026-11-18T08:00:00Z', decidedAt: null,
}

describe('ProjectManagerRequestsPage', () => {
  beforeEach(() => {
    vi.mocked(editRequestApi.managerList).mockResolvedValue({ requests: [request] })
    vi.mocked(editRequestApi.decide).mockResolvedValue({ ...request, status: 'APPROVED' as const, decisionComment: 'Correct the hours' })
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it('shows the reason and saves a one-time approval', async () => {
    render(<ProjectManagerRequestsPage />)
    expect(await screen.findByText('I entered the wrong hours')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Comment (optional)'), { target: { value: 'Correct the hours' } })
    fireEvent.click(screen.getByRole('button', { name: 'Approve one edit' }))
    await waitFor(() => expect(editRequestApi.decide).toHaveBeenCalledWith(7, true, 'Correct the hours'))
    expect(await screen.findByText(/Status: approved/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve one edit' })).not.toBeInTheDocument()
  })
})
