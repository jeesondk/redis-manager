import { useEffect, useMemo, useState } from 'react'
import { Button } from '../components/ui/button'

export type ConnectionType = 'node' | 'sentinel' | 'cluster'

type ConnectionConfig = {
  id?: number
  name: string
  type: ConnectionType
  url?: string
  urls?: string[]
  username?: string
  password?: string
  sentinelMasterId?: string
  database?: number
  timeoutMs?: number
}

type Database = { index: number; keys?: number | null }

type ConnectResult = { success: boolean; message: string }

export default function RedisLanding() {
  const [connections, setConnections] = useState<ConnectionConfig[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editing, setEditing] = useState<ConnectionConfig | null>(null)
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})
  const [dbs, setDbs] = useState<Record<number, Database[]>>({})
  const [status, setStatus] = useState<Record<number, ConnectResult>>({})

  async function loadConnections() {
    const res = await fetch('/api/redis/connections')
    if (res.ok) setConnections(await res.json())
  }
  useEffect(() => { loadConnections() }, [])

  function startAdd() {
    setSelectedId(null)
    setEditing({ name: '', type: 'node', url: '' })
  }
  function startEdit(c: ConnectionConfig) {
    setEditing({ ...c })
  }
  function cancelEdit() { setEditing(null) }

  async function saveEdit() {
    if (!editing) return
    if (!editing.name) return alert('Name is required')
    if (editing.id) {
      const res = await fetch(`/api/redis/connections/${editing.id}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(editing)
      })
      if (!res.ok) return alert('Failed to update')
    } else {
      const res = await fetch('/api/redis/connections', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(editing)
      })
      if (!res.ok) return alert('Failed to create')
      const created: ConnectionConfig = await res.json()
      setSelectedId(created.id!)
    }
    setEditing(null)
    await loadConnections()
  }

  async function remove(id: number) {
    if (!confirm('Delete this connection?')) return
    const res = await fetch(`/api/redis/connections/${id}`, { method: 'DELETE' })
    if (!res.ok) return alert('Failed to delete')
    if (selectedId === id) setSelectedId(null)
    const { [id]: _, ...restDbs } = dbs
    const { [id]: __, ...restStatus } = status
    setDbs(restDbs); setStatus(restStatus)
    await loadConnections()
  }

  async function connect(id: number) {
    const res = await fetch(`/api/redis/connections/${id}/connect`, { method: 'POST' })
    const data: ConnectResult = await res.json()
    setStatus(s => ({ ...s, [id]: data }))
    if (data.success) {
      const r = await fetch(`/api/redis/connections/${id}/databases`)
      if (r.ok) {
        const list: Database[] = await r.json()
        setDbs(prev => ({ ...prev, [id]: list }))
        setExpanded(prev => ({ ...prev, [id]: true }))
      }
    }
  }

  const selected = useMemo(() => connections.find(c => c.id === selectedId) || null, [connections, selectedId])

  return (
    <div className="min-h-screen flex">
      <aside className="w-72 border-r bg-gray-50 p-3">
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-semibold">Connections</h2>
          <Button size="sm" onClick={startAdd}>Add</Button>
        </div>
        <ul className="space-y-1">
          {connections.map(c => (
            <li key={c.id}>
              <div className={`flex items-center justify-between rounded px-2 py-1 cursor-pointer ${selectedId===c.id?'bg-white border':''}`}
                   onClick={() => setSelectedId(c.id!)}>
                <div className="truncate">
                  <div className="text-sm font-medium">{c.name}</div>
                  <div className="text-xs text-gray-500">{c.type}</div>
                </div>
                <div className="flex gap-1">
                  <Button size="sm" variant="outline" onClick={(e)=>{e.stopPropagation();startEdit(c)}}>Edit</Button>
                  <Button size="sm" variant="destructive" onClick={(e)=>{e.stopPropagation();remove(c.id!)}}>Del</Button>
                </div>
              </div>
              {/* Databases under connection when expanded */}
              {expanded[c.id!] && dbs[c.id!] && (
                <ul className="ml-3 mt-1 mb-2 text-sm text-gray-700">
                  {dbs[c.id!]!.map(d => (
                    <li key={d.index} className="flex items-center justify-between">
                      <span>DB {d.index}</span>
                      {typeof d.keys === 'number' ? <span className="text-xs text-gray-500">{d.keys} keys</span> : null}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      </aside>
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
                <Button onClick={()=>connect(selected.id!)}>Connect</Button>
                <Button variant="secondary" onClick={()=>setExpanded(e=>({ ...e, [selected.id!]: !e[selected.id!] }))}>
                  {expanded[selected.id!] ? 'Collapse' : 'Expand'}
                </Button>
              </div>
            </div>
            {status[selected.id!] && (
              <div className={status[selected.id!]!.success? 'text-green-700':'text-red-700'}>
                {status[selected.id!]!.message}
              </div>
            )}
            {!dbs[selected.id!] && (
              <div className="text-gray-500">No databases loaded. Click Connect to fetch databases.</div>
            )}
          </div>
        )}
      {editing && !editing.id && (
          <div className="max-w-2xl">
            <div className="bg-white rounded shadow border p-4">
              <h3 className="text-lg font-semibold mb-3">New connection</h3>
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
                  <label className="grid gap-1">
                    <span className="text-sm">URL</span>
                    <input className="border rounded px-2 py-1" placeholder="redis://host:6379" value={editing.url||''} onChange={e=>setEditing({...editing!, url:e.target.value})} />
                  </label>
                )}
                {editing.type !== 'node' && (
                  <label className="grid gap-1">
                    <span className="text-sm">Hosts</span>
                    <input className="border rounded px-2 py-1" placeholder="host1:26379,host2:26379" value={(editing.urls||[]).join(',')} onChange={e=>setEditing({...editing!, urls:e.target.value.split(',').map(s=>s.trim()).filter(Boolean)})} />
                  </label>
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
                <Button onClick={saveEdit}>Create</Button>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Edit dialog (existing) */}
      {editing && editing.id && (
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
                <label className="grid gap-1">
                  <span className="text-sm">URL</span>
                  <input className="border rounded px-2 py-1" placeholder="redis://host:6379" value={editing.url||''} onChange={e=>setEditing({...editing!, url:e.target.value})} />
                </label>
              )}
              {editing.type !== 'node' && (
                <label className="grid gap-1">
                  <span className="text-sm">Hosts</span>
                  <input className="border rounded px-2 py-1" placeholder="host1:26379,host2:26379" value={(editing.urls||[]).join(',')} onChange={e=>setEditing({...editing!, urls:e.target.value.split(',').map(s=>s.trim()).filter(Boolean)})} />
                </label>
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
              <Button onClick={saveEdit}>{editing.id ? 'Save' : 'Create'}</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
