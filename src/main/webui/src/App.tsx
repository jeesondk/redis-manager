
import { Routes, Route, Link, Navigate, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { Button } from './components/ui/button'
import { Plus } from 'lucide-react'
import Login from './pages/Login'

function RequireAuth({ children }: { children: React.ReactNode }) {
    const [status, setStatus] = useState<'unknown' | 'authed' | 'unauth'>('unknown')
    useEffect(() => {
        let mounted = true
        fetch('/api/auth/me').then(res => {
            if (!mounted) return
            setStatus(res.ok ? 'authed' : 'unauth')
        }).catch(() => {
            if (!mounted) return
            setStatus('unauth')
        })
        return () => { mounted = false }
    }, [])

    if (status === 'unknown') return <p className="text-gray-500">Checking session…</p>
    if (status === 'unauth') return <Navigate to="/login" replace />
    return <>{children}</>
}

export default function App() {
    const navigate = useNavigate()
    async function logout() {
        try {
            await fetch('/api/auth/logout', { method: 'POST' })
        } finally {
            navigate('/login')
        }
    }

    return (
        <div className="p-4 max-w-3xl mx-auto">
            <h1 className="text-3xl font-bold mb-4">Quarkus + React</h1>
            <nav className="space-x-4 mb-6">
                <Link className="text-blue-600 hover:underline" to="/">Home</Link>
                <Link className="text-blue-600 hover:underline" to="/about">About</Link>
                <Link className="text-blue-600 hover:underline" to="/login">Login</Link>
                <button className="text-red-600 underline ml-2" onClick={logout}>Logout</button>
            </nav>

            <div className="mb-6 flex gap-3 items-center">
                <Button>
                    <Plus className="mr-2 h-4 w-4" /> Primary
                </Button>
                <Button variant="secondary">Secondary</Button>
                <Button variant="outline">Outline</Button>
                <Button variant="ghost">Ghost</Button>
                <Button variant="destructive">Delete</Button>
                <Button variant="link">Link</Button>
            </div>

            <Routes>
                <Route path="/login" element={<Login />} />
                <Route path="/" element={<RequireAuth><p className="text-gray-700">Hello from Quarkus + React!</p></RequireAuth>} />
                <Route path="/about" element={<RequireAuth><p className="text-gray-700">About page (pre-rendered)</p></RequireAuth>} />
            </Routes>
        </div>
    )
}
