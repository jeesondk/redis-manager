import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../components/ui/button'
import { Copy, Check } from 'lucide-react'

export type ConnectionType = 'node' | 'sentinel' | 'cluster'

type ConnectionConfig = {
  id?: number
  name: string
  type: ConnectionType
  url?: string
  urls?: string[]
  port?: number
  username?: string
  password?: string
  sentinelMasterId?: string
  database?: number
  timeoutMs?: number
}

export default function RedisLanding() {
  const [connections, setConnections] = useState<ConnectionConfig[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editing, setEditing] = useState<ConnectionConfig | null>(null)
  // Database list and connection status moved to DesktopSidebar

  // Key value modal state
  type RedisValue = {
    key: string
    type: 'STRING' | 'LIST' | 'SET' | 'ZSET' | 'HASH' | 'NONE' | 'UNKNOWN'
    stringValue?: string
    listValue?: string[]
    setValue?: string[]
    zsetValue?: { score: number; value: string }[]
    hashValue?: Record<string, string>
    ttlSeconds: number
    pttlMillis: number
  }
  const [viewKey, setViewKey] = useState<{ db: number; key: string } | null>(null)
  const [valueData, setValueData] = useState<RedisValue | null>(null)
  const [valueLoading, setValueLoading] = useState(false)
  const [valueError, setValueError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  // Keys table state (default DB)
  type RedisKeyInfo = {
    key: string
    type: 'STRING' | 'LIST' | 'SET' | 'ZSET' | 'HASH' | 'NONE' | 'UNKNOWN'
    ttlSeconds: number
    pttlMillis: number
  }
  const [keys, setKeys] = useState<RedisKeyInfo[]>([])
  const [keysLoading, setKeysLoading] = useState(false)
  const [keysError, setKeysError] = useState<string | null>(null)

  // Determine default DB from selected connection's serverInfo (fallback 0)
  const defaultDb = useMemo(() => {
    const sel = connections.find(c => c.id === selectedId) as any
    const d = sel?.serverInfo?.defaultDb
    return typeof d === 'number' ? d : 0
  }, [connections, selectedId])

  async function loadConnections() {
    const res = await fetch('/api/redis/connections', { credentials: 'include' })
    if (res.ok) setConnections(await res.json())
  }
  useEffect(() => { loadConnections() }, [])

  const location = useLocation()
  const navigate = useNavigate()

  // Sync selection and editing state from URL params
  useEffect(() => {
    const sp = new URLSearchParams(location.search)
    const conn = sp.get('conn')
    const add = sp.get('add') === '1'
    const edit = sp.get('edit') === '1'

    setSelectedId(conn ? Number(conn) : null)

    if (add) {
      setEditing({ name: '', type: 'node', url: '', port: 6379 })
      return
    }
    if (edit && conn) {
      const c = connections.find(x => String(x.id) === conn)
      if (c) {
        setEditing({ ...c })
        return
      }
    }
    // If neither add nor edit, ensure modal is closed
    setEditing(prev => (add || edit ? prev : null))
  }, [location.search, connections])

  // Detect URL params for viewing a key value and open the modal
  useEffect(() => {
    const sp = new URLSearchParams(location.search)
    const db = sp.get('db')
    const k = sp.get('key')
    if (db && k && selectedId != null) {
      setViewKey({ db: Number(db), key: k })
    } else {
      setViewKey(null)
      setValueData(null)
      setValueError(null)
      setValueLoading(false)
    }
  }, [location.search, selectedId])

  // Load keys for the default DB when connection changes
  useEffect(() => {
    if (selectedId == null) {
      setKeys([])
      setKeysError(null)
      setKeysLoading(false)
      return
    }
    let cancelled = false
    setKeysLoading(true)
    setKeysError(null)
    ;(async () => {
      try {
        const res = await fetch(`/api/redis/instances/${selectedId}/${defaultDb}/keys?pattern=${encodeURIComponent('*')}&count=100`, { credentials: 'include' })
        if (!res.ok) throw new Error(`Failed to load keys (HTTP ${res.status})`)
        const data = await res.json()
        if (!cancelled) setKeys(Array.isArray(data) ? data : [])
      } catch (e: any) {
        if (!cancelled) setKeysError(e?.message || 'Failed to load keys')
      } finally {
        if (!cancelled) setKeysLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [selectedId, defaultDb])

  // Fetch the key value when modal is requested
  useEffect(() => {
    if (!viewKey || selectedId == null) return
    let cancelled = false
    setValueLoading(true)
    setValueError(null)
    ;(async () => {
      try {
        const res = await fetch(`/api/redis/instances/${selectedId}/${viewKey.db}/${encodeURIComponent(viewKey.key)}`, { credentials: 'include' })
        if (!res.ok) throw new Error(`Failed to load (HTTP ${res.status})`)
        const data = await res.json()
        if (!cancelled) setValueData(data)
      } catch (e: any) {
        if (!cancelled) setValueError(e?.message || 'Failed to load value')
      } finally {
        if (!cancelled) setValueLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [viewKey, selectedId])

  function formatValueForDisplay(v: RedisValue | null): string {
    if (!v) return ''
    switch (v.type) {
      case 'STRING':
        return v.stringValue ?? ''
      case 'LIST':
        return (v.listValue || []).join('\n')
      case 'SET':
        return Array.from(v.setValue || []).join('\n')
      case 'HASH':
        return JSON.stringify(v.hashValue || {}, null, 2)
      case 'ZSET':
        return JSON.stringify(v.zsetValue || [], null, 2)
      default:
        return ''
    }
  }

  function formatValueForCopy(v: RedisValue | null): string {
    return formatValueForDisplay(v)
  }

  async function copyValue() {
    const text = formatValueForCopy(valueData)
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch (_) {
      try {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'
        ta.style.opacity = '0'
        document.body.appendChild(ta)
        ta.focus()
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        setCopied(true)
        setTimeout(() => setCopied(false), 1500)
      } catch {
        alert('Failed to copy')
      }
    }
  }

  function viewKeyFromTable(k: string) {
    const sp = new URLSearchParams(location.search)
    sp.set('db', String(defaultDb))
    sp.set('key', k)
    navigate({ search: `?${sp.toString()}` })
  }

  function closeView() {
    setViewKey(null)
    setValueData(null)
    const sp = new URLSearchParams(location.search)
    sp.delete('db')
    sp.delete('key')
    navigate({ search: `?${sp.toString()}` }, { replace: true })
  }

  function cancelEdit() {
    setEditing(null)
    const sp = new URLSearchParams(location.search)
    sp.delete('add')
    sp.delete('edit')
    navigate({ search: `?${sp.toString()}` }, { replace: true })
  }

  async function saveEdit() {
    if (!editing) return
    if (!editing.name) return alert('Name is required')

    // Build CreateConnectionRequest payload according to new API model
    const mode = editing.type
    const hosts = (mode === 'node')
      ? [ (editing.url || '').trim() ].filter(Boolean)
      : (editing.urls || [])
    const port = editing.port ?? 6379
    const defaultDb = mode === 'node' ? (editing.database ?? undefined) : undefined
    const serverInfo: any = {
      hosts,
      port,
      mode,
      ...(defaultDb !== undefined ? { defaultDb } : {}),
      ...(editing.timeoutMs != null ? { timeoutMs: editing.timeoutMs } : {})
    }
    const credentials = (editing.username || editing.password)
      ? { username: editing.username, password: editing.password }
      : undefined
    const body: any = {
      name: editing.name,
      serverInfo,
      ...(credentials ? { credentials } : {}),
      ...(mode === 'sentinel' && editing.sentinelMasterId ? { sentinelMasterId: editing.sentinelMasterId } : {})
    }

    let newId: number | null = null
    if (editing.id) {
      const res = await fetch(`/api/redis/connections/${editing.id}`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
        credentials: 'include'
      })
      if (!res.ok) return alert('Failed to update')
      const updated = await res.json().catch(() => null)
      newId = (updated && updated.id) ? updated.id : editing.id
    } else {
      const res = await fetch('/api/redis/connections', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
        credentials: 'include'
      })
      if (!res.ok) return alert('Failed to create')
      const created: any = await res.json()
      newId = created.id!
    }
    setEditing(null)
    // Update URL: select the (new or updated) connection and clear flags
    const sp = new URLSearchParams(location.search)
    if (newId != null) {
      sp.set('conn', String(newId))
    }
    sp.delete('add')
    sp.delete('edit')
    navigate({ search: `?${sp.toString()}` }, { replace: true })
    await loadConnections()
    // Notify other components (e.g., DesktopSidebar) that the connections list has changed
    try {
      window.dispatchEvent(new CustomEvent('connections:changed'))
    } catch (_) {
      // no-op
    }
  }

  const selected = useMemo(() => connections.find(c => c.id === selectedId) || null, [connections, selectedId])

  return (
    <div className="min-h-screen flex">
      
      <main className="flex-1 p-6">
        {!selected && (
          <div className="text-gray-500">Select a connection to get started.</div>
        )}
        {selected && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-2xl font-semibold">{selected.name}</h1>
                <div className="text-sm text-gray-500">{selected.type}</div>
              </div>
              <div className="flex items-center gap-2">
                {/* Removed Toggle connection button per spec */}
              </div>
            </div>
            <div className="space-y-3">
              <div className="text-xs text-slate-500">Default database keys are shown below. Databases are shown in the sidebar for reference.</div>
              <div>
                <div className="text-sm text-slate-700 mb-2">Keys in DB {defaultDb}</div>
                {keysLoading && <div className="text-sm text-slate-500">Loading keys…</div>}
                {keysError && <div className="text-sm text-red-600">{keysError}</div>}
                {!keysLoading && !keysError && Array.isArray(keys) && keys.length === 0 && (
                  <div className="text-sm text-slate-500">No keys found.</div>
                )}
                {!keysLoading && !keysError && Array.isArray(keys) && keys.length > 0 && (
                  <div className="border rounded-md overflow-hidden">
                    <table className="w-full text-sm">
                      <thead className="bg-slate-50 border-b">
                        <tr>
                          <th className="text-left px-3 py-2 font-medium text-slate-700">Key</th>
                          <th className="text-left px-3 py-2 font-medium text-slate-700">Type</th>
                          <th className="text-left px-3 py-2 font-medium text-slate-700">TTL</th>
                          <th className="px-3 py-2" />
                        </tr>
                      </thead>
                      <tbody>
                        {keys.map((k) => (
                          <tr key={k.key} className="border-b last:border-b-0 hover:bg-slate-50">
                            <td className="px-3 py-2 truncate max-w-[40vw]" title={k.key}>{k.key}</td>
                            <td className="px-3 py-2">{k.type}</td>
                            <td className="px-3 py-2">{k.ttlSeconds}</td>
                            <td className="px-3 py-2 text-right">
                              <Button size="sm" variant="ghost" onClick={() => viewKeyFromTable(k.key)}>View</Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

      </main>

      {editing && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center">
          <div className="bg-white rounded shadow w-full max-w-lg p-4">
            <h3 className="text-lg font-semibold mb-3">{editing.id ? 'Edit connection' : 'New connection'}</h3>
            <div className="grid gap-3">
              <label className="grid gap-1">
                <span className="text-sm">Name</span>
                <input className="border rounded px-2 py-1" value={editing.name} onChange={e=>setEditing({...editing!, name:e.target.value})} />
              </label>
              <label className="grid gap-1">
                <span className="text-sm">Type</span>
                <select className="border rounded px-2 py-1" value={editing.type} onChange={e=>setEditing({...editing!, type:e.target.value as ConnectionType})}>
                  <option value="node">Standalone</option>
                  <option value="sentinel">Sentinel</option>
                  <option value="cluster">Cluster</option>
                </select>
              </label>
              {editing.type === 'node' && (
                <>
                  <label className="grid gap-1">
                    <span className="text-sm">Host</span>
                    <input className="border rounded px-2 py-1" placeholder="host" value={editing.url||''} onChange={e=>setEditing({...editing!, url:e.target.value})} />
                  </label>
                  <label className="grid gap-1">
                    <span className="text-sm">Port</span>
                    <input className="border rounded px-2 py-1" type="number" placeholder="6379" value={editing.port ?? 6379} onChange={e=>setEditing({...editing!, port:e.target.value?Number(e.target.value):undefined})} />
                  </label>
                </>
              )}
              {editing.type !== 'node' && (
                <>
                  <label className="grid gap-1">
                    <span className="text-sm">Hosts</span>
                    <input className="border rounded px-2 py-1" placeholder="host1,host2" value={(editing.urls||[]).join(',')} onChange={e=>setEditing({...editing!, urls:e.target.value.split(',').map(s=>s.trim()).filter(Boolean)})} />
                  </label>
                  <label className="grid gap-1">
                    <span className="text-sm">Port</span>
                    <input className="border rounded px-2 py-1" type="number" placeholder="26379 (Sentinel) / 6379 (Cluster)" value={editing.port ?? (editing.type==='sentinel'?26379:6379)} onChange={e=>setEditing({...editing!, port:e.target.value?Number(e.target.value):undefined})} />
                  </label>
                </>
              )}
              {editing.type === 'sentinel' && (
                <label className="grid gap-1">
                  <span className="text-sm">Master name</span>
                  <input className="border rounded px-2 py-1" placeholder="mymaster" value={editing.sentinelMasterId||''} onChange={e=>setEditing({...editing!, sentinelMasterId:e.target.value})} />
                </label>
              )}
              <div className="grid grid-cols-2 gap-3">
                <label className="grid gap-1">
                  <span className="text-sm">Username</span>
                  <input className="border rounded px-2 py-1" value={editing.username||''} onChange={e=>setEditing({...editing!, username:e.target.value})} />
                </label>
                <label className="grid gap-1">
                  <span className="text-sm">Password</span>
                  <input className="border rounded px-2 py-1" type="password" value={editing.password||''} onChange={e=>setEditing({...editing!, password:e.target.value})} />
                </label>
              </div>
              {editing.type === 'node' && (
                <label className="grid gap-1">
                  <span className="text-sm">Database index</span>
                  <input className="border rounded px-2 py-1" type="number" value={editing.database ?? ''} onChange={e=>setEditing({...editing!, database:e.target.value?Number(e.target.value):undefined})} />
                </label>
              )}
              <label className="grid gap-1">
                <span className="text-sm">Timeout (ms)</span>
                <input className="border rounded px-2 py-1" type="number" value={editing.timeoutMs ?? ''} onChange={e=>setEditing({...editing!, timeoutMs:e.target.value?Number(e.target.value):undefined})} />
              </label>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="ghost" onClick={cancelEdit}>Cancel</Button>
              <Button variant="ghost" onClick={saveEdit}>{editing.id ? 'Save' : 'Create'}</Button>
            </div>
          </div>
        </div>
      )}

      {viewKey && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div className="bg-white rounded shadow w-full max-w-3xl overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 border-b">
              <div className="min-w-0">
                <div className="text-xs text-slate-500">Key</div>
                <div className="font-medium truncate max-w-[60vw]" title={viewKey.key}>{viewKey.key}</div>
              </div>
              <div className="flex items-center gap-2">
                <button className={"rounded hover:bg-slate-100 text-slate-700 shrink-0"} size="icon" aria-label={copied ? 'Copied' : 'Copy value'} title={copied ? 'Copied' : 'Copy value'} onClick={copyValue} disabled={!valueData || valueLoading}>
                  {copied ? <Check className="h-4 w-4 shrink-0 text-green-600" /> : <Copy className="h-4 w-4" />}
                </button>
                <Button variant="ghost" onClick={closeView}>Close</Button>
              </div>
            </div>
              <div className="pt-4 px-4 text-xs text-slate-500">Value</div>
            <div className="px-4 py-4 pb-4 pt-0 max-h-[70vh] overflow-auto">
              {valueLoading && <div className="text-sm text-slate-500">Loading…</div>}
              {valueError && <div className="text-sm text-red-600">{valueError}</div>}
              {!valueLoading && !valueError && valueData && (
                <pre className="whitespace-pre-wrap break-words text-sm bg-slate-50 rounded p-3 border">{formatValueForDisplay(valueData)}</pre>
              )}
              {!valueLoading && !valueError && !valueData && (
                <div className="text-sm text-slate-500">No value</div>
              )}
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
