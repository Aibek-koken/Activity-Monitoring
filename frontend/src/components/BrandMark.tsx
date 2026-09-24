import { Languages } from 'lucide-react'

export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand-mark" aria-label="EasyLang Activity Monitoring">
      <span className="brand-mark__icon" aria-hidden="true">
        <Languages size={compact ? 18 : 22} strokeWidth={2} />
      </span>
      <span className="brand-mark__copy">
        <strong>EasyLang</strong>
        {!compact && <small>Activity Monitoring</small>}
      </span>
    </div>
  )
}

