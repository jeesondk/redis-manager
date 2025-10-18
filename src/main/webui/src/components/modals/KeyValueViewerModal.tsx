import { Button } from '../ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog'
import { Copy, Check } from 'lucide-react'

export type RedisValue = {
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

type KeyValueViewerModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  keyName: string
  value: RedisValue | null
  loading: boolean
  error: string | null
  copied: boolean
  onCopy: () => void
  onClose: () => void
}

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

export function KeyValueViewerModal({
  open,
  onOpenChange,
  keyName,
  value,
  loading,
  error,
  copied,
  onCopy,
  onClose,
}: KeyValueViewerModalProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="wide" className="w-[90vw] max-h-[90vh] flex flex-col">
        <DialogHeader className="shrink-0">
          <div className="flex items-center justify-between gap-4 pr-8">
            <div className="min-w-0 flex-1">
              <DialogTitle className="text-xs text-slate-500 font-normal mb-1">
                Key
              </DialogTitle>
              <div className="font-medium truncate" title={keyName}>
                {keyName}
              </div>
            </div>
            <button
              className="rounded hover:bg-slate-100 text-slate-700 shrink-0 p-2"
              aria-label={copied ? 'Copied' : 'Copy value'}
              title={copied ? 'Copied' : 'Copy value'}
              onClick={onCopy}
              disabled={!value || loading}
            >
              {copied ? (
                <Check className="h-4 w-4 shrink-0 text-green-600" />
              ) : (
                <Copy className="h-4 w-4" />
              )}
            </button>
          </div>
        </DialogHeader>
        <div className="space-y-2 flex-1 min-h-0 overflow-hidden">
          <div className="text-xs text-slate-500">Value</div>
          <div className="h-full overflow-auto">
            {loading && <div className="text-sm text-slate-500">Loading…</div>}
            {error && <div className="text-sm text-red-600">{error}</div>}
            {!loading && !error && value && (
              <pre className="whitespace-pre-wrap break-words text-sm bg-slate-50 rounded p-3 border max-w-full overflow-x-auto">
                {formatValueForDisplay(value)}
              </pre>
            )}
            {!loading && !error && !value && (
              <div className="text-sm text-slate-500">No value</div>
            )}
          </div>
        </div>
        <div className="flex justify-end shrink-0">
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
