import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { ResettingRouteErrorBoundary } from './RouteErrorBoundary'

function Boom(): never {
  throw new TypeError('Cannot read properties of undefined (reading \'toFixed\')')
}

function renderBounded() {
  return render(
    <MemoryRouter initialEntries={['/translator/activities/1']}>
      <Routes>
        <Route
          element={
            <ResettingRouteErrorBoundary>
              <Boom />
            </ResettingRouteErrorBoundary>
          }
          path="/translator/activities/:activityId"
        />
        <Route element={<p>Activities list</p>} path="/translator" />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ResettingRouteErrorBoundary', () => {
  afterEach(() => {
    cleanup()
  })

  it('shows a readable message instead of a blank page when a route crashes', async () => {
    const user = userEvent.setup()

    renderBounded()

    expect(screen.getByRole('heading', { name: 'This page could not be displayed' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Retry/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to activities/ })).toHaveAttribute('href', '/translator')
    await user.click(screen.getByRole('button', { name: /Retry/ }))
  })
})