import { Routes, Route, useLocation } from 'react-router-dom'
import { useState, useEffect, useMemo } from 'react'
import LoginRoutes from '@/routes/LoginRoutes'
import RedisLanding from './pages/RedisLanding'
import AppLayout from '@/components/AppLayout.tsx'


export default function App() {
    const location = useLocation()
    const [status, setStatus] = useState<'unknown' | 'authed' | 'unauth'>('unknown')
    // Connections for resolving breadcrumb names
    type ConnectionLite = { id: number; name: string; serverInfo?: { defaultDb?: number } }
    const [connections, setConnections] = useState<ConnectionLite[]>([])

    // Check auth on mount and whenever the route changes
    useEffect(() => {
        let mounted = true
        fetch('/api/auth/me', { credentials: 'include' })
            .then(res => { if (mounted) setStatus(res.ok ? 'authed' : 'unauth') })
            .catch(() => { if (mounted) setStatus('unauth') })
        return () => { mounted = false }
    }, [location.pathname, location.search])

    // Load connections to resolve names for breadcrumbs
    useEffect(() => {
        let cancelled = false
        ;(async () => {
            try {
                const res = await fetch('/api/redis/connections', { credentials: 'include' })
                if (!res.ok) return
                const data = await res.json()
                if (!cancelled) setConnections(Array.isArray(data) ? data : [])
            } catch {
                // ignore
            }
        })()
        const onChanged = () => {
            // refresh connections list when updated elsewhere
            fetch('/api/redis/connections', { credentials: 'include' })
                .then(r => r.ok ? r.json() : [])
                .then(d => setConnections(Array.isArray(d) ? d : []))
                .catch(() => {})
        }
        window.addEventListener('connections:changed', onChanged as EventListener)
        return () => {
            cancelled = true
            window.removeEventListener('connections:changed', onChanged as EventListener)
        }
    }, [])

    // Compute breadcrumb parts from URL and connections
    const selectedId = useMemo(() => {
        const sp = new URLSearchParams(location.search)
        const v = sp.get('conn')
        return v ? Number(v) : null
    }, [location.search])

    const selectedConn = useMemo(() => connections.find(c => String(c.id) === String(selectedId)) || null, [connections, selectedId])

    const activeDb = useMemo(() => {
        const sp = new URLSearchParams(location.search)
        const v = sp.get('db')
        const n = v ? Number(v) : null
        const fallback = (selectedConn?.serverInfo && typeof selectedConn.serverInfo.defaultDb === 'number') ? selectedConn.serverInfo.defaultDb! : 0
        return Number.isFinite(n as any) && n != null ? (n as number) : fallback
    }, [location.search, selectedConn])

    const crumbs = useMemo(() => {
        const base = [{ label: 'Home', href: '/' }]
        if (selectedId != null) {
            base.push({ label: selectedConn?.name || `Connection ${selectedId}`, href: `/?conn=${selectedId}` })
            base.push({ label: `DB ${activeDb}` })
        }
        return base
    }, [selectedId, selectedConn, activeDb])

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
                breadcrumbs={crumbs}
        >
            <Routes>
                <Route path="/" element={<RedisLanding />} />
            </Routes>
        </AppLayout>
    )
}
