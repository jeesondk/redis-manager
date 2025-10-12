import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import LoginRoutes from '@/routes/LoginRoutes'
import RedisLanding from './pages/RedisLanding'
import AppLayout from '@/components/AppLayout.tsx'


export default function App() {
    const location = useLocation()
    const [status, setStatus] = useState<'unknown' | 'authed' | 'unauth'>('unknown')

    // Check auth on mount and whenever the route changes
    useEffect(() => {
        let mounted = true
        fetch('/api/auth/me')
            .then(res => { if (mounted) setStatus(res.ok ? 'authed' : 'unauth') })
            .catch(() => { if (mounted) setStatus('unauth') })
        return () => { mounted = false }
    }, [location.pathname, location.search])

    if (status === 'unknown') {
        return <p className="text-gray-500 p-4">Checking session…</p>
    }

    if (status === 'unauth') {
        return <LoginRoutes />
    }

    // Authenticated: render the full app layout
    return (
        <AppLayout
            userName="admin"
            breadcrumbs={[{ label: 'Home', href: '/' }]}
        >
            <Routes>
                <Route path="/" element={<RedisLanding />} />
            </Routes>
        </AppLayout>
    )
}
