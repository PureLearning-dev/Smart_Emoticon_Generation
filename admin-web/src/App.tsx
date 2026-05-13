import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { isLoggedIn } from '@/config/auth'
import AdminLayout from '@/layout/AdminLayout'
import CrawlPage from '@/pages/Crawl'
import DashboardPage from '@/pages/Dashboard'
import GeneratedImagesPage from '@/pages/GeneratedImages'
import MemeAssetsPage from '@/pages/MemeAssets'
import LoginPage from '@/pages/Login'
import PlazaManagePage from '@/pages/PlazaManage'
import UsersPage from '@/pages/Users'

function RequireAuth({ children }: { children: ReactNode }) {
  if (!isLoggedIn()) {
    return <Navigate to="/login" replace />
  }
  return children
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={isLoggedIn() ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route
        element={
          <RequireAuth>
            <AdminLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="/generated-images" element={<GeneratedImagesPage />} />
        <Route path="/plaza" element={<PlazaManagePage />} />
        <Route path="/crawl" element={<CrawlPage />} />
        <Route path="/crawled-assets" element={<MemeAssetsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
