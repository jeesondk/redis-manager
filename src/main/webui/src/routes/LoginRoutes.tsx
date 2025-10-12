import { Routes, Route } from 'react-router-dom'
import Login from '@/pages/Login'

export const LOGIN_PATH = '/'

/**
 * Renders the unauthenticated routing table:
 * - / (root) shows the login page
 * Note: We intentionally do NOT add a catch-all redirect here to avoid interfering
 * with backend endpoints like Quarkus Swagger UI (/q/swagger-ui) when the SPA is not
 * responsible for rendering those paths.
 */
export default function LoginRoutes() {
  return (
    <Routes>
      <Route path={LOGIN_PATH} element={<Login />} />
    </Routes>
  )
}
