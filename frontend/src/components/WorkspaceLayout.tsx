import { useState, type ReactNode } from 'react'
import { LogOut } from 'lucide-react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { roleLabels } from '../lib/roles'
import type { Role } from '../types/auth'
import { BrandMark } from './BrandMark'

interface WorkspaceNavItem {
  to: string
  label: string
  icon: ReactNode
  end?: boolean
}

interface WorkspaceLayoutProps {
  role: Role
  title: string
  navItems: WorkspaceNavItem[]
  children: ReactNode
}

export function WorkspaceLayout({ role, title, navItems, children }: WorkspaceLayoutProps) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  const handleLogout = async () => {
    setIsLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } finally {
      setIsLoggingOut(false)
    }
  }

  return (
    <div className="workspace-shell">
      <aside className="workspace-sidebar">
        <BrandMark compact />
        <div className="workspace-sidebar__role">
          <span>Workspace</span>
          <strong>{roleLabels[role]}</strong>
        </div>
        <nav aria-label="Workspace navigation">
          {navItems.map((item) => (
            <NavLink
              className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
              end={item.end}
              key={item.to}
              to={item.to}
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="workspace-sidebar__footer">
          <div className="user-chip">
            <span className="user-chip__avatar" aria-hidden="true">{user?.initials}</span>
            <span>
              <strong>{user?.fullName}</strong>
              <small>{user?.email}</small>
            </span>
          </div>
          <button
            aria-busy={isLoggingOut}
            className="button button--ghost button--full"
            disabled={isLoggingOut}
            type="button"
            onClick={() => void handleLogout()}
          >
            <LogOut size={18} aria-hidden="true" />
            {isLoggingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </aside>

      <main className="workspace-main">
        <header className="workspace-topbar">
          <strong>{title}</strong>
          <span>{roleLabels[role]}</span>
        </header>
        {children}
      </main>
    </div>
  )
}
