// Shared logout helpers for the web UI
// Usage:
//  - await logoutApi() to just invalidate the session cookie.
//  - await logoutAndRedirect('/') to logout and navigate away immediately.

export async function logoutApi(): Promise<void> {
  try {
    await fetch('/api/auth/logout', { method: 'POST' })
  } catch (_) {
    // ignore network errors: we still want to force client-side unauth state/redirect
  }
}

export async function logoutAndRedirect(to: string = '/'): Promise<void> {
  try {
    await logoutApi()
  } finally {
    // Force navigation regardless of API outcome
    window.location.assign(to)
  }
}
