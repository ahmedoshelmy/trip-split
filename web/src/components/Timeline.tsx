import type { TimelineEntry } from '../lib/api'

interface Props {
  entries: TimelineEntry[]
  readOnly: boolean
  onUndo: (entry: TimelineEntry) => Promise<void>
}

const time = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

export function Timeline({ entries, readOnly, onUndo }: Props) {
  return (
    <ol className="timeline">
      {entries.map((e) => (
        <li key={e.version} className={e.undoneAt ? 'undone' : ''}>
          <span className="version">v{e.version}</span>
          <span className="summary">
            {e.summary}
            <small className="muted"> · {time.format(new Date(e.occurredAt))}</small>
          </span>
          {!readOnly && e.undoable && (
            <button className="link" onClick={() => void onUndo(e)}>
              Undo
            </button>
          )}
        </li>
      ))}
    </ol>
  )
}
