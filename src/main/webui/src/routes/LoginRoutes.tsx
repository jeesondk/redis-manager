import { Routes, Route, Navigate } from 'react-router-dom'
import Login from '@/pages/Login'

export const LOGIN_PATH = '/'

/**
 * Renders the unauthenticated routing table:
 * - / (root) shows the login page
 * - any other path redirects to /
 */
export default function LoginRoutes() {
  return (
    <Routes>
      <Route path={LOGIN_PATH} element={<Login />} />
      <Route path="*" element={<Navigate to={LOGIN_PATH} replace />} />
    </Routes>
  )
}
