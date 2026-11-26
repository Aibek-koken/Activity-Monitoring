import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { ResettingRouteErrorBoundary } from './components/RouteErrorBoundary'
import { LoginPage } from './pages/LoginPage'
import { LandingPage } from './pages/LandingPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { TranslatorActivitiesPage } from './pages/TranslatorActivitiesPage'
import { TranslatorActivityDetailPage } from './pages/TranslatorActivityDetailPage'
import { ChiefEditorDashboardPage } from './pages/ChiefEditorDashboardPage'
import { ChiefEditorReviewDetailPage } from './pages/ChiefEditorReviewDetailPage'
import { ProjectManagerRequestsPage } from './pages/ProjectManagerRequestsPage'

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute allowedRole="TRANSLATOR" />}>
            <Route element={<ResettingRouteErrorBoundary />}>
              <Route path="/translator" element={<TranslatorActivitiesPage />} />
              <Route path="/translator/activities/:activityId" element={<TranslatorActivityDetailPage />} />
            </Route>
          </Route>
          <Route element={<ProtectedRoute allowedRole="CHIEF_EDITOR" />}>
            <Route element={<ResettingRouteErrorBoundary />}>
              <Route path="/chief-editor" element={<ChiefEditorDashboardPage />} />
              <Route path="/chief-editor/reviews/:reviewId" element={<ChiefEditorReviewDetailPage />} />
            </Route>
          </Route>
          <Route element={<ProtectedRoute allowedRole="PROJECT_MANAGER" />}>
            <Route path="/project-manager" element={<ProjectManagerRequestsPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
