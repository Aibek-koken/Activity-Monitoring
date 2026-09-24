import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <main className="status-page">
      <section className="status-card">
        <span className="eyebrow">404</span>
        <h1>Page not found</h1>
        <p>The address may be incorrect, or this feature has not been added yet.</p>
        <Link className="button button--secondary" to="/">
          <ArrowLeft size={18} aria-hidden="true" />
          Return to EasyLang
        </Link>
      </section>
    </main>
  )
}

