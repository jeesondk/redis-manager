import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../components/ui/button'

export default function Login() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data?.error || 'Login failed')
      }
      // Cookie is set by server (HttpOnly). Redirect to home.
      navigate('/')
    } catch (err: any) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
      <div className="w-full max-w-md">
        <div className="bg-white shadow rounded-lg border border-gray-200">
          <div className="px-6 py-8">
            <h1 className="text-2xl font-semibold text-gray-900 mb-2">Welcome back</h1>
            <p className="text-sm text-gray-600 mb-6">Enter your username and password to sign in</p>
            {error && (
              <div className="mb-4 text-red-700 bg-red-50 border border-red-200 rounded p-2 text-sm">
                {error}
              </div>
            )}
            <form onSubmit={onSubmit} className="space-y-4">
              <div className="space-y-1">
                <label htmlFor="username" className="text-sm font-medium text-gray-700">Username</label>
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-600"
                  placeholder="jdoe"
                  autoComplete="username"
                  required
                />
              </div>
              <div className="space-y-1">
                <label htmlFor="password" className="text-sm font-medium text-gray-700">Password</label>
                <input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-600"
                  placeholder="********"
                  autoComplete="current-password"
                  required
                />
              </div>
              <Button disabled={loading} className="w-full">
                {loading ? 'Signing in…' : 'Sign in'}
              </Button>
            </form>
          </div>
          <div className="px-6 py-4 border-t border-gray-200 text-sm text-gray-600">
            <span className="mr-1">Don’t have credentials?</span>
            <Link className="text-blue-600 hover:underline" to="/">Return home</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
