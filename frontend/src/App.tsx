import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { WorkspacePage } from './pages/WorkspacePage'

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute allowedRole="TRANSLATOR" />}>
            <Route path="/translator" element={<WorkspacePage role="TRANSLATOR" />} />
          </Route>
          <Route element={<ProtectedRoute allowedRole="CHIEF_EDITOR" />}>
            <Route path="/chief-editor" element={<WorkspacePage role="CHIEF_EDITOR" />} />
          </Route>
          <Route element={<ProtectedRoute allowedRole="PROJECT_MANAGER" />}>
            <Route path="/project-manager" element={<WorkspacePage role="PROJECT_MANAGER" />} />
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Navigate to="/login" replace />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

