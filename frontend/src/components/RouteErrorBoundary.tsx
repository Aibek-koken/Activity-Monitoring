import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertOctagon, ArrowLeft, RefreshCw } from 'lucide-react'
import { Link, Outlet, useLocation } from 'react-router-dom'

interface RouteErrorBoundaryProps {
  children?: ReactNode
}

interface RouteErrorBoundaryState {
  error: Error | null
}

/**
 * Catches render-time crashes inside a route tree so a bad API payload shows a
 * readable message instead of a blank page. Retry remounts the subtree; navigating
 * away resets the boundary for the next visit.
 */
export class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): RouteErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('Route crashed during render:', error, errorInfo.componentStack)
  }

  private handleRetry = (): void => {
    this.setState({ error: null })
  }

  render(): ReactNode {
    const { error } = this.state
    const { children } = this.props

    if (!error) return children ?? <Outlet />

    return (
      <div className="workspace-content">
        <section className="inline-error" role="alert">
          <AlertOctagon size={22} aria-hidden="true" />
          <div>
            <h2>This page could not be displayed</h2>
            <p>
              The page ran into a problem while loading your data. Nothing was changed. Try again, or go
              back to your activities.
            </p>
          </div>
          <div className="inline-error__actions">
            <button className="button button--secondary" onClick={this.handleRetry} type="button">
              <RefreshCw size={17} aria-hidden="true" />
              Retry
            </button>
            <Link className="button button--ghost" to="/translator">
              <ArrowLeft size={17} aria-hidden="true" />
              Back to activities
            </Link>
          </div>
        </section>
      </div>
    )
  }
}

/**
 * Resets the boundary when the location changes so a later visit renders fresh.
 */
export function ResettingRouteErrorBoundary({ children }: RouteErrorBoundaryProps) {
  const location = useLocation()
  return <RouteErrorBoundary key={location.pathname}>{children}</RouteErrorBoundary>
}