import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { LandingPage } from './pages/LandingPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { TranslatorActivitiesPage } from './pages/TranslatorActivitiesPage'
import { TranslatorActivityDetailPage } from './pages/TranslatorActivityDetailPage'
import { WorkspacePage } from './pages/WorkspacePage'

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute allowedRole="TRANSLATOR" />}>
            <Route path="/translator" element={<TranslatorActivitiesPage />} />
            <Route path="/translator/activities/:activityId" element={<TranslatorActivityDetailPage />} />
          </Route>
          <Route element={<ProtectedRoute allowedRole="CHIEF_EDITOR" />}>
            <Route path="/chief-editor" element={<WorkspacePage role="CHIEF_EDITOR" />} />
          </Route>
          <Route element={<ProtectedRoute allowedRole="PROJECT_MANAGER" />}>
            <Route path="/project-manager" element={<WorkspacePage role="PROJECT_MANAGER" />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
