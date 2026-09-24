import { AlertCircle, RefreshCw } from 'lucide-react'

interface FullPageStatusProps {
  kind: 'loading' | 'error'
  message?: string
  onRetry?: () => void
}

export function FullPageStatus({ kind, message, onRetry }: FullPageStatusProps) {
  if (kind === 'loading') {
    return (
      <main className="status-page" aria-busy="true" aria-label="Loading your workspace">
        <div className="status-card status-card--loading">
          <span className="skeleton skeleton--badge" />
          <span className="skeleton skeleton--title" />
          <span className="skeleton skeleton--body" />
        </div>
      </main>
    )
  }

  return (
    <main className="status-page">
      <section className="status-card" role="alert">
        <AlertCircle className="status-card__icon" aria-hidden="true" />
        <h1>Could not load your workspace</h1>
        <p>{message ?? 'This is usually a temporary connection problem.'}</p>
        {onRetry && (
          <button className="button button--secondary" type="button" onClick={onRetry}>
            <RefreshCw size={18} aria-hidden="true" />
            Try again
          </button>
        )}
      </section>
    </main>
  )
}

