import { useEffect, useMemo, useRef, useState } from 'react'
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

export default function Content() {
  const [connections, setConnections] = useState<ConnectionConfig[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editing, setEditing] = useState<ConnectionConfig | null>(null)
  const location = useLocation()
  const navigate = useNavigate()
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
  // Infinite scroll: only render a window of keys
  const pageSize = 20
  const [visibleCount, setVisibleCount] = useState<number>(pageSize)
  // Search controls
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [searchIn, setSearchIn] = useState<'keys' | 'values'>('keys')
  // Type filter (multi-select), applied before search
  type RedisKeyType = RedisKeyInfo['type']
  const TYPE_OPTIONS: RedisKeyType[] = ['STRING','LIST','SET','ZSET','HASH','NONE','UNKNOWN']
  const [selectedTypes, setSelectedTypes] = useState<RedisKeyType[]>(TYPE_OPTIONS)
  // Cache for values when searching in values
  const [valueCache, setValueCache] = useState<Record<string, string>>({})
  const [valueSearchProgress, setValueSearchProgress] = useState<{ running: boolean; done: number; total: number }>({ running: false, done: 0, total: 0 })
  const valueSearchRunId = useRef(0)

  // Determine default DB from selected connection's serverInfo (fallback 0)
  const defaultDb = useMemo(() => {
    const sel = connections.find(c => c.id === selectedId) as any
    const d = sel?.serverInfo?.defaultDb
    return typeof d === 'number' ? d : 0
  }, [connections, selectedId])

  // Active DB is the one selected in the URL if present, otherwise the connection's default DB
  const activeDb = useMemo(() => {
    const sp = new URLSearchParams(location.search)
    const v = sp.get('db')
    const n = v ? Number(v) : null
    return Number.isFinite(n as any) && n != null ? (n as number) : defaultDb
  }, [location.search, defaultDb])

  async function loadConnections() {
    const res = await fetch('/api/redis/connections', { credentials: 'include' })
    if (res.ok) setConnections(await res.json())
  }
  useEffect(() => { loadConnections() }, [])

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

  // Keys refresh controls
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [refreshIntervalSec, setRefreshIntervalSec] = useState<number>(30)

  // Dedicated function to fetch keys (can be triggered manually or on interval)
  const fetchKeys = async () => {
    if (selectedId == null) {
      setKeys([])
      setKeysError(null)
      setKeysLoading(false)
      return
    }
    // Prevent starting a new load if one is already in progress
    if (keysLoading) return
    setKeysLoading(true)
    setKeysError(null)
    try {
      const res = await fetch(`/api/redis/instances/${selectedId}/${activeDb}/keys?pattern=${encodeURIComponent('*')}&count=100`, { credentials: 'include' })
      if (!res.ok) throw new Error(`Failed to load keys (HTTP ${res.status})`)
      const data = await res.json()
      setKeys(Array.isArray(data) ? data : [])
    } catch (e: any) {
      setKeysError(e?.message || 'Failed to load keys')
    } finally {
      setKeysLoading(false)
    }
  }

  // Load keys when connection or DB changes
  useEffect(() => {
    fetchKeys()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, activeDb])

  // Auto-refresh effect
  useEffect(() => {
    if (!autoRefresh) return
    const ms = Math.max(1, Number.isFinite(refreshIntervalSec) ? refreshIntervalSec : 30) * 1000
    const id = setInterval(() => {
      // Avoid overlapping loads
      if (!keysLoading) {
        fetchKeys()
      }
    }, ms)
    return () => clearInterval(id)
  }, [autoRefresh, refreshIntervalSec, selectedId, activeDb, keysLoading])

  // Reset infinite-scroll window when context or filters change
  useEffect(() => {
    setVisibleCount(pageSize)
  }, [selectedId, activeDb, keys.length, searchTerm, searchIn, selectedTypes])

  // Clear cached values and cancel any running value-search when context changes
  useEffect(() => {
    setValueCache({})
    setValueSearchProgress({ running: false, done: 0, total: 0 })
    valueSearchRunId.current++ // invalidate any ongoing search
  }, [selectedId, activeDb, keys.length])

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
  
  // Available key types found in current list (fallback to TYPE_OPTIONS if unknown)
  const availableTypes = useMemo<RedisKeyType[]>(() => {
    const set = new Set<RedisKeyType>()
    for (const k of keys) set.add(k.type as RedisKeyType)
    const arr = Array.from(set)
    return arr.length > 0 ? arr : TYPE_OPTIONS
  }, [keys])

  // Apply type filter first
  const typeFilteredKeys = useMemo(() => {
    if (!selectedTypes || selectedTypes.length === 0) return [] as typeof keys
    const allowed = new Set<RedisKeyType>(selectedTypes)
    return keys.filter(k => allowed.has(k.type as RedisKeyType))
  }, [keys, selectedTypes])

  // Then apply search on the type-filtered set (before slicing for infinite scroll)
  const filteredKeys = useMemo(() => {
    const term = (searchTerm || '').toLowerCase().trim()
    if (!term) return typeFilteredKeys
    if (searchIn === 'keys') {
      return typeFilteredKeys.filter(k => k.key.toLowerCase().includes(term))
    } else {
      return typeFilteredKeys.filter(k => {
        const v = valueCache[k.key]
        return typeof v === 'string' && v.toLowerCase().includes(term)
      })
    }
  }, [typeFilteredKeys, searchTerm, searchIn, valueCache])

  // When searching in values, prefetch values for all keys in the current type-filtered set
  useEffect(() => {
    if (searchIn !== 'values') return
    const term = (searchTerm || '').trim()
    if (!term || selectedId == null || typeFilteredKeys.length === 0) return

    const runId = ++valueSearchRunId.current
    let cancelled = false

    const total = typeFilteredKeys.length
    setValueSearchProgress({ running: true, done: 0, total })

    const concurrency = 5
    let nextIndex = 0
    let done = 0

    async function fetchValueFor(keyStr: string) {
      try {
        const res = await fetch(`/api/redis/instances/${selectedId}/${activeDb}/${encodeURIComponent(keyStr)}`, { credentials: 'include' })
        if (!res.ok) return
        const data = await res.json()
        const display = formatValueForDisplay(data as any)
        if (cancelled || valueSearchRunId.current !== runId) return
        setValueCache(prev => (prev[keyStr] !== undefined ? prev : { ...prev, [keyStr]: display }))
      } catch (_) {
        // ignore errors for individual keys
      }
    }

    async function worker() {
      while (!cancelled && valueSearchRunId.current === runId) {
        const i = nextIndex++
        if (i >= typeFilteredKeys.length) break
        const k = typeFilteredKeys[i].key
        if (valueCache[k] === undefined) {
          await fetchValueFor(k)
        }
        done++
        if (!cancelled && valueSearchRunId.current === runId) {
          setValueSearchProgress(prev => ({ ...prev, done }))
        }
      }
    }

    const workers = Array.from({ length: concurrency }, () => worker())
    Promise.all(workers).finally(() => {
      if (!cancelled && valueSearchRunId.current === runId) {
        setValueSearchProgress(prev => ({ ...prev, running: false }))
      }
    })

    return () => {
      cancelled = true
    }
  }, [searchIn, searchTerm, selectedId, activeDb, typeFilteredKeys])
  
  // Infinite scroll handler
  function handleKeysScroll(e: any) {
    const el = e.currentTarget as HTMLDivElement
    if (!el) return
    const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 24
    if (nearBottom) {
      setVisibleCount(v => Math.min(v + pageSize, filteredKeys.length))
    }
  }

  function viewKeyFromTable(k: string) {
    const sp = new URLSearchParams(location.search)
    sp.set('db', String(activeDb))
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
    <div className="flex">
      <div className="flex">
        {!selected && (
          <div className="text-gray-500">Select a connection to get started.</div>
        )}
        {selected && (
          <div className="flex-row gap-4 p-4">
            <div className="flex-row items-center justify-between">
                <h1 className="text-2xl font-semibold">{selected.name}</h1>
                <div className="text-sm text-gray-500">{selected.type}</div>
            </div>
            <div className="space-y-3">
              <div className="text-xs text-slate-500">Default database keys are shown below. Databases are shown in the sidebar for reference.</div>
              <div>
                <div className="flex-row items-center justify-between mb-2">
                  <div className="text-sm text-slate-700 py-2">Keys in DB {activeDb}</div>
                  <div className="flex items-center gap-3 flex-wrap">
                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="text"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Search"
                        className="w-48 border rounded px-2 py-1"
                        aria-label="Search"
                        title="Search"
                      />
                    </label>
                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <span>in</span>
                      <select
                        value={searchIn}
                        onChange={(e) => setSearchIn(e.target.value as 'keys' | 'values')}
                        className="border rounded px-2 py-1"
                        aria-label="Search in"
                        title="Search in"
                      >
                        <option value="keys">Keys</option>
                        <option value="values">Values</option>
                      </select>
                    </label>

                    {/* Type filter (applied before search) */}
                    <div className="flex items-center gap-2 text-sm text-slate-700">
                      <span>Types</span>
                      <div className="flex items-center gap-2">
                        <label className="inline-flex items-center gap-1" title="Select all types">
                          <input
                            type="checkbox"
                            className="h-4 w-4"
                            checked={selectedTypes.length >= availableTypes.length}
                            onChange={(e) => {
                              if (e.target.checked) setSelectedTypes(availableTypes.slice())
                              else setSelectedTypes([])
                            }}
                            aria-label="Select all types"
                          />
                          <span>All</span>
                        </label>
                        {availableTypes.map(t => (
                          <label key={t} className="inline-flex items-center gap-1">
                            <input
                              type="checkbox"
                              className="h-4 w-4"
                              checked={selectedTypes.includes(t)}
                              onChange={(e) => {
                                setSelectedTypes(prev => {
                                  if (e.target.checked) {
                                    if (prev.includes(t)) return prev
                                    return [...prev, t]
                                  } else {
                                    return prev.filter(x => x !== t)
                                  }
                                })
                              }}
                              aria-label={`Toggle type ${t}`}
                            />
                            <span>{t}</span>
                          </label>
                        ))}
                      </div>
                    </div>

                    {searchIn === 'values' && searchTerm.trim() && valueSearchProgress.running && (
                      <span className="text-xs text-slate-500">Searching values… {valueSearchProgress.done}/{valueSearchProgress.total}</span>
                    )}

                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        className="h-4 w-4"
                        checked={autoRefresh}
                        onChange={(e) => setAutoRefresh(e.target.checked)}
                        aria-label="Toggle auto-refresh"
                        title="Toggle auto-refresh"
                      />
                      Auto refresh
                    </label>
                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <span>Interval</span>
                      <input
                        type="number"
                        min={1}
                        value={refreshIntervalSec}
                        onChange={(e) => {
                          const v = Number(e.target.value)
                          if (Number.isNaN(v)) {
                            setRefreshIntervalSec(30)
                          } else {
                            setRefreshIntervalSec(Math.max(1, Math.floor(v)))
                          }
                        }}
                        className="w-20 border rounded px-2 py-1"
                        aria-label="Refresh interval in seconds"
                        title="Refresh interval in seconds"
                      />
                      <span>s</span>
                    </label>
                    <Button size="sm" variant="ghost" onClick={() => fetchKeys()} disabled={keysLoading} aria-label="Refresh now" title="Refresh now">
                      {keysLoading ? 'Refreshing…' : 'Refresh'}
                    </Button>
                  </div>
                </div>
                {keysLoading && <div className="text-sm text-slate-500">Loading keys…</div>}
                {keysError && <div className="text-sm text-red-600">{keysError}</div>}
                {!keysLoading && !keysError && Array.isArray(keys) && keys.length === 0 && (
                  <div className="text-sm text-slate-500">No keys found.</div>
                )}
                {!keysLoading && !keysError && Array.isArray(keys) && filteredKeys.length > 0 && (
                  <div className="border rounded-md overflow-auto max-h-[75vh]" onScroll={handleKeysScroll}>
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
                        {filteredKeys.slice(0, visibleCount).map((k) => (
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
                {!keysLoading && !keysError && Array.isArray(keys) && searchTerm.trim() && filteredKeys.length === 0 && (
                  <div className="text-sm text-slate-500">No matches.</div>
                )}
                {!keysLoading && !keysError && Array.isArray(keys) && !searchTerm.trim() && keys.length > 0 && filteredKeys.length === 0 && (
                  <div className="text-sm text-slate-500">No keys match the selected types.</div>
                )}
              </div>
            </div>
          </div>
        )}

      </div>

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
                <button className={"rounded hover:bg-slate-100 text-slate-700 shrink-0"} aria-label={copied ? 'Copied' : 'Copy value'} title={copied ? 'Copied' : 'Copy value'} onClick={copyValue} disabled={!valueData || valueLoading}>
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
