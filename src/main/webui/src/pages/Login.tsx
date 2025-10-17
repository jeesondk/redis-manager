import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../components/ui/button'

export default function Login() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [provider, setProvider] = useState<'Basic' | 'OIDC' | 'EntraID' | null>(null)
  const [providerError, setProviderError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function loadProvider() {
      try {
        const res = await fetch('/api/auth/provider', { credentials: 'include' })
        if (!res.ok) throw new Error('Failed to detect auth provider')
        const data = await res.json()
        if (!cancelled) setProvider(data?.provider ?? null)
      } catch (e: any) {
        if (!cancelled) {
          setProviderError(e?.message || 'Failed to detect auth provider')
          // Fallback to Basic to keep existing behavior if provider cannot be detected
          setProvider('Basic')
        }
      }
    }
    loadProvider()
    return () => { cancelled = true }
  }, [])

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
        credentials: 'include'
      })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data?.error || 'Login failed')
      }
      // Cookie is set by server (HttpOnly). Redirect to home with a search param to trigger auth re-check.
      navigate('/?loggedin=1')
    } catch (err: any) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  function startOidcLogin() {
    // Quarkus OIDC login endpoint for web-app flows
    const returnTo = encodeURIComponent('/')
    window.location.assign(`/q/oidc/login?returnTo=${returnTo}`)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
      <div className="w-full max-w-md">
        <div className="bg-white shadow rounded-lg border border-gray-200">
          <div className="px-6 py-8">
            <h1 className="text-2xl font-semibold text-gray-900 mb-2">Redis Manager</h1>

            {!provider && (
              <p className="text-sm text-gray-600">Detecting authentication method…</p>
            )}

            {providerError && (
              <div className="mb-4 text-yellow-800 bg-yellow-50 border border-yellow-200 rounded p-2 text-sm">
                {providerError}
              </div>
            )}

            {provider === 'Basic' && (
              <>
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
                  <Button type="submit" disabled={loading} className="w-full text-black">
                    {loading ? 'Signing in…' : 'Sign in'}
                  </Button>
                </form>
              </>
            )}

            {provider === 'OIDC' && (
              <>
                <p className="text-sm text-gray-600 mb-6">You will be redirected to your Identity Provider to sign in.</p>
                <Button onClick={startOidcLogin} className="w-full text-black">Sign in with Single Sign-On</Button>
              </>
            )}

            {provider === 'EntraID' && (
              <>
                <p className="text-sm text-gray-600 mb-4">Microsoft Entra ID authentication is not implemented yet.</p>
                <p className="text-xs text-gray-500">Please contact your administrator.</p>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
