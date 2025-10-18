import { useEffect, useMemo, useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Button } from "@/components/ui/button"
import { SquarePen, Trash2, ChevronDown, ChevronRight } from "lucide-react"

// Minimal local type to render connection names
type ConnectionConfig = {
  id: number
  name: string
  type: 'node' | 'sentinel' | 'cluster'
}

type Database = { index: number; keys?: number | null }
type ConnectResult = { success: boolean; message: string }

export function DesktopSidebar() {
  const [connections, setConnections] = useState<ConnectionConfig[]>([])
  const [loading, setLoading] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  // New state: which connection currently has DBs loaded (only one active at a time)
  const [connectedId, setConnectedId] = useState<number | null>(null)
  const [dbs, setDbs] = useState<Database[] | null>(null)
  const [status, setStatus] = useState<ConnectResult | null>(null)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [loadingDbs, setLoadingDbs] = useState<Record<number, boolean>>({})

  async function loadConnections() {
    setLoading(true)
    try {
      const res = await fetch('/api/redis/connections', { credentials: 'include' })
      if (res.ok) {
        const data = await res.json()
        setConnections(data || [])
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    // Load on mount
    loadConnections()

    // Listen for global connection changes (created/updated elsewhere)
    const onChanged = () => { loadConnections() }
    window.addEventListener('connections:changed', onChanged as EventListener)

    // Listen for toggle events from other components (e.g., RedisLanding)
    const onToggle = (e: Event) => {
      try {
        const ev = e as CustomEvent<{ id: number }>
        const id = ev.detail?.id
        if (typeof id === 'number') {
          // If currently connected to another instance, prompt to disconnect
          if (connectedId != null && connectedId !== id) {
            const ok = confirm('Disconnect from current instance?')
            if (!ok) return
            // clear existing
            setConnectedId(null)
            setDbs(null)
            setStatus({ success: true, message: 'Disconnected' })
          }
          // Toggle connect/disconnect for this id
          if (connectedId === id) {
            // disconnect
            setConnectedId(null)
            setDbs(null)
            setStatus({ success: true, message: 'Disconnected' })
            setExpandedId(null)
            // Also reset RedisLanding to the default page if this was the selected connection
            if (selectedId === id) {
              const sp = new URLSearchParams(location.search)
              sp.delete('conn')
              sp.delete('db')
              sp.delete('key')
              sp.delete('add')
              sp.delete('edit')
              navigate({ search: `?${sp.toString()}` })
            }
          } else {
            // connect
            fetchDatabasesFor(id)
            setExpandedId(id)
          }
        }
      } catch (_) {}
    }

    window.addEventListener('connection:toggle', onToggle as EventListener)

    return () => {
      window.removeEventListener('connections:changed', onChanged as EventListener)
      window.removeEventListener('connection:toggle', onToggle as EventListener)
    }
  }, [connectedId])

  function openAddModal() {
    const sp = new URLSearchParams(location.search)
    sp.delete('edit')
    sp.set('add', '1')
    navigate({ search: `?${sp.toString()}` })
  }

  function selectConnection(id: number) {
    // If there is an active connected instance and it's different, ask to disconnect
    if (connectedId != null && connectedId !== id) {
      const ok = confirm('Disconnect from current instance?')
      if (!ok) return
      // disconnect
      setConnectedId(null)
      setDbs(null)
      setStatus({ success: true, message: 'Disconnected' })
      setExpandedId(null)
    }

    const sp = new URLSearchParams(location.search)
    sp.set('conn', String(id))
    sp.delete('add')
    sp.delete('edit')
    navigate({ search: `?${sp.toString()}` })
  }

  function editConnection(id: number) {
    const sp = new URLSearchParams(location.search)
    sp.set('conn', String(id))
    sp.set('edit', '1')
    sp.delete('add')
    navigate({ search: `?${sp.toString()}` })
  }

  async function deleteConnection(id: number) {
    if (!confirm('Delete this connection?')) return
    const res = await fetch(`/api/redis/connections/${id}`, { method: 'DELETE', credentials: 'include' })
    if (res.ok) {
      // If the deleted connection was selected, clear the selection
      const sp = new URLSearchParams(location.search)
      if (sp.get('conn') === String(id)) {
        sp.delete('conn')
      }
      sp.delete('edit')
      sp.delete('add')
      navigate({ search: `?${sp.toString()}` })
      // If it was the connected one, clear dbs
      if (connectedId === id) {
        setConnectedId(null)
        setDbs(null)
        setStatus(null)
        setExpandedId(null)
      }
      await loadConnections()
    } else {
      alert('Failed to delete')
    }
  }

  const selectedId = useMemo(() => {
    const sp = new URLSearchParams(location.search)
    const v = sp.get('conn')
    return v ? Number(v) : null
  }, [location.search])

  async function fetchDatabasesFor(id: number) {
    setLoadingDbs(prev => ({ ...prev, [id]: true }))
    try {
      const r = await fetch(`/api/redis/instances/${id}/databases`, { credentials: 'include' })
      if (!r.ok) {
        const msg = `Failed to connect (HTTP ${r.status})`
        setStatus({ success: false, message: msg })
        return
      }
      const map = await r.json() as Record<string, number>
      const list: Database[] = Object.keys(map)
        .map(k => ({ index: Number(k), keys: map[k as any] as unknown as number }))
        .sort((a, b) => a.index - b.index)
      setDbs(list)
      setConnectedId(id)
      setStatus({ success: true, message: 'Connected' })
    } catch (e: any) {
      setStatus({ success: false, message: e?.message || 'Failed to connect' })
    } finally {
      setLoadingDbs(prev => {
        const { [id]: _, ...rest } = prev
        return rest
      })
    }
  }

  function toggleExpand(id: number) {
    if (expandedId === id) {
      // collapse — also disconnect if this connection is currently connected
      if (connectedId === id) {
        setConnectedId(null)
        setDbs(null)
        setStatus({ success: true, message: 'Disconnected' })
        // Also reset RedisLanding to the default page if this was the selected connection
        if (selectedId === id) {
          const sp = new URLSearchParams(location.search)
          sp.delete('conn')
          sp.delete('db')
          sp.delete('key')
          sp.delete('add')
          sp.delete('edit')
          navigate({ search: `?${sp.toString()}` })
        }
      }
      setExpandedId(null)
      return
    }
    // If expanding another and we have an active different connected instance, ask to disconnect
    if (connectedId != null && connectedId !== id) {
      const ok = confirm('Disconnect from current instance?')
      if (!ok) return
      setConnectedId(null)
      setDbs(null)
      setStatus({ success: true, message: 'Disconnected' })
    }
    // If this id is already connected, just expand
    if (connectedId === id && dbs) {
      setExpandedId(id)
      return
    }
    // Otherwise fetch databases and expand
    fetchDatabasesFor(id)
    setExpandedId(id)
  }

  function disconnectCurrent() {
    if (connectedId == null) return
    setConnectedId(null)
    setDbs(null)
    setStatus({ success: true, message: 'Disconnected' })
    setExpandedId(null)
    // Reset RedisLanding to the default page if the currently selected connection was disconnected
    if (selectedId === connectedId) {
      const sp = new URLSearchParams(location.search)
      sp.delete('conn')
      sp.delete('db')
      sp.delete('key')
      sp.delete('add')
      sp.delete('edit')
      navigate({ search: `?${sp.toString()}` })
    }
  }

  return (
    <aside className="hidden md:block w-1/4 shrink-0 border-r bg-white">
      <ScrollArea className="h-[calc(100vh-3.5rem)]">
        <div className="space-y-2">
          <div className="flex px-2 pt-2 items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700">Connections</h2>
            <Button size="sm" variant="ghost" onClick={openAddModal}>
              Add
            </Button>
          </div>
          <div className="space-y-1">
            {loading && (
              <div className="text-xs text-slate-500 px-3 py-1">Loading…</div>
            )}
            {!loading && connections.length === 0 && (
              <div className="text-xs text-slate-500 px-3 py-1">No connections</div>
            )}
            {connections.map((c) => (
              <div
                key={c.id}
                className={`group rounded-md border border-transparent hover:border-slate-200 ${
                  selectedId === c.id ? 'bg-slate-50' : ''
                }`}
              >
                <div className="w-full px-3 py-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => toggleExpand(c.id)}
                        className="h-6 w-6 inline-flex items-center justify-center rounded hover:bg-slate-100 text-slate-700 shrink-0"
                        aria-label={expandedId === c.id ? 'Collapse' : 'Expand'}
                      >
                        {expandedId === c.id ? (
                          <ChevronDown className="h-4 w-4 shrink-0" />
                        ) : (
                          <ChevronRight className="h-4 w-4 shrink-0" />
                        )}
                      </button>
                      <div
                        onClick={() => selectConnection(c.id)}
                        className="text-sm cursor-pointer truncate"
                        title={c.name}
                        role="button"
                        tabIndex={0}
                      >
                        {c.name}
                      </div>
                    </div>

                    <div className="flex items-center gap-1 ml-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        className="h-7 w-7 inline-flex items-center justify-center rounded hover:bg-slate-100 text-slate-700 shrink-0"
                        aria-label="Edit connection"
                        title="Edit"
                        onClick={(e) => {
                          e.stopPropagation()
                          editConnection(c.id)
                        }}
                      >
                        <SquarePen className="h-4 w-4 shrink-0" />
                      </button>
                      <button
                        className="h-7 w-7 inline-flex items-center justify-center rounded hover:bg-slate-100 text-red-600 shrink-0"
                        aria-label="Delete connection"
                        title="Delete"
                        onClick={(e) => {
                          e.stopPropagation()
                          deleteConnection(c.id)
                        }}
                      >
                        <Trash2 className="h-4 w-4 shrink-0" />
                      </button>
                    </div>
                  </div>

                  {/* Expanded DB tree */}
                  {expandedId === c.id && (
                    <div className="mt-2 pl-8">
                      {loadingDbs[c.id] && (
                        <div className="text-xs text-slate-500">Loading databases…</div>
                      )}
                      {!loadingDbs[c.id] && status && (
                        <div className={status.success ? 'text-green-700 text-xs' : 'text-red-700 text-xs'}>
                          {status.message}
                        </div>
                      )}
                      {!loadingDbs[c.id] && !dbs && (
                        <div className="text-xs text-slate-500">No databases loaded. Click the arrow to load.</div>
                      )}
                      {!loadingDbs[c.id] && dbs && (
                        <ul className="mt-2 pl-2 border-l">
                          {dbs.map(db => {
                            const sp = new URLSearchParams(location.search)
                            const selDbStr = sp.get('db')
                            const defaultDbIndex = (c as any)?.serverInfo && typeof (c as any).serverInfo.defaultDb === 'number' ? (c as any).serverInfo.defaultDb : 0
                            const selectedDb = selDbStr ? Number(selDbStr) : defaultDbIndex
                            const isActive = selectedId === c.id && selectedDb === db.index
                            return (
                              <li
                                key={db.index}
                                className={`py-1 px-2 rounded text-sm flex items-center justify-between cursor-pointer hover:bg-slate-100 ${isActive ? 'bg-blue-50 text-blue-700 font-medium' : ''}`}
                                onClick={() => {
                                  const sp2 = new URLSearchParams(location.search)
                                  sp2.set('conn', String(c.id))
                                  sp2.set('db', String(db.index))
                                  // Do not touch 'key' here; keys view modal controls it
                                  sp2.delete('add')
                                  sp2.delete('edit')
                                  navigate({ search: `?${sp2.toString()}` })
                                }}
                                role="button"
                                aria-selected={isActive}
                                title={`Switch to DB ${db.index}`}
                             >
                                <div>
                                  <span className="font-medium">DB {db.index}</span>
                                  <span className="text-gray-500"> — {db.keys ?? 0} keys</span>
                                </div>
                                <div className="text-xs text-slate-500">&nbsp;</div>
                              </li>
                            )
                          })}
                        </ul>
                      )}

                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </ScrollArea>
    </aside>
  )
}