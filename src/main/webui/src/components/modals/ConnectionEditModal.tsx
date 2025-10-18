import { Button } from '../ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog'

export type ConnectionType = 'node' | 'sentinel' | 'cluster'

export type ConnectionConfig = {
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

type ConnectionEditModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  connection: ConnectionConfig | null
  onConnectionChange: (connection: ConnectionConfig) => void
  onSave: () => void
  onCancel: () => void
}

export function ConnectionEditModal({
  open,
  onOpenChange,
  connection,
  onConnectionChange,
  onSave,
  onCancel,
}: ConnectionEditModalProps) {
  if (!connection) return null

  const handleChange = (updates: Partial<ConnectionConfig>) => {
    onConnectionChange({ ...connection, ...updates })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {connection.id ? 'Edit connection' : 'New connection'}
          </DialogTitle>
        </DialogHeader>
        <div className="grid gap-3 py-4">
          <label className="grid gap-1">
            <span className="text-sm">Name</span>
            <input
              className="border rounded px-2 py-1"
              value={connection.name}
              onChange={(e) => handleChange({ name: e.target.value })}
            />
          </label>
          <label className="grid gap-1">
            <span className="text-sm">Type</span>
            <select
              className="border rounded px-2 py-1"
              value={connection.type}
              onChange={(e) =>
                handleChange({ type: e.target.value as ConnectionType })
              }
            >
              <option value="node">Standalone</option>
              <option value="sentinel">Sentinel</option>
              <option value="cluster">Cluster</option>
            </select>
          </label>
          {connection.type === 'node' && (
            <>
              <label className="grid gap-1">
                <span className="text-sm">Host</span>
                <input
                  className="border rounded px-2 py-1"
                  placeholder="host"
                  value={connection.url || ''}
                  onChange={(e) => handleChange({ url: e.target.value })}
                />
              </label>
              <label className="grid gap-1">
                <span className="text-sm">Port</span>
                <input
                  className="border rounded px-2 py-1"
                  type="number"
                  placeholder="6379"
                  value={connection.port ?? 6379}
                  onChange={(e) =>
                    handleChange({
                      port: e.target.value ? Number(e.target.value) : undefined,
                    })
                  }
                />
              </label>
            </>
          )}
          {connection.type !== 'node' && (
            <>
              <label className="grid gap-1">
                <span className="text-sm">Hosts</span>
                <input
                  className="border rounded px-2 py-1"
                  placeholder="host1,host2"
                  value={(connection.urls || []).join(',')}
                  onChange={(e) =>
                    handleChange({
                      urls: e.target.value
                        .split(',')
                        .map((s) => s.trim())
                        .filter(Boolean),
                    })
                  }
                />
              </label>
              <label className="grid gap-1">
                <span className="text-sm">Port</span>
                <input
                  className="border rounded px-2 py-1"
                  type="number"
                  placeholder="26379 (Sentinel) / 6379 (Cluster)"
                  value={
                    connection.port ??
                    (connection.type === 'sentinel' ? 26379 : 6379)
                  }
                  onChange={(e) =>
                    handleChange({
                      port: e.target.value ? Number(e.target.value) : undefined,
                    })
                  }
                />
              </label>
            </>
          )}
          {connection.type === 'sentinel' && (
            <label className="grid gap-1">
              <span className="text-sm">Master name</span>
              <input
                className="border rounded px-2 py-1"
                placeholder="mymaster"
                value={connection.sentinelMasterId || ''}
                onChange={(e) =>
                  handleChange({ sentinelMasterId: e.target.value })
                }
              />
            </label>
          )}
          <div className="grid grid-cols-2 gap-3">
            <label className="grid gap-1">
              <span className="text-sm">Username</span>
              <input
                className="border rounded px-2 py-1"
                value={connection.username || ''}
                onChange={(e) => handleChange({ username: e.target.value })}
              />
            </label>
            <label className="grid gap-1">
              <span className="text-sm">Password</span>
              <input
                className="border rounded px-2 py-1"
                type="password"
                value={connection.password || ''}
                onChange={(e) => handleChange({ password: e.target.value })}
              />
            </label>
          </div>
          {connection.type === 'node' && (
            <label className="grid gap-1">
              <span className="text-sm">Database index</span>
              <input
                className="border rounded px-2 py-1"
                type="number"
                value={connection.database ?? ''}
                onChange={(e) =>
                  handleChange({
                    database: e.target.value ? Number(e.target.value) : undefined,
                  })
                }
              />
            </label>
          )}
          <label className="grid gap-1">
            <span className="text-sm">Timeout (ms)</span>
            <input
              className="border rounded px-2 py-1"
              type="number"
              value={connection.timeoutMs ?? ''}
              onChange={(e) =>
                handleChange({
                  timeoutMs: e.target.value ? Number(e.target.value) : undefined,
                })
              }
            />
          </label>
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
          <Button variant="ghost" onClick={onSave}>
            {connection.id ? 'Save' : 'Create'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
