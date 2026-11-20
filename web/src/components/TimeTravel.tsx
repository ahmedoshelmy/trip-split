import type { TimelineEntry } from '../lib/api'

interface Props {
  entries: TimelineEntry[]
  value: number | null
  onChange: (version: number | null) => void
}

/**
 * Slider over the stream versions. Picking an older version asks the server for the state
 * "as of" that event's time: the server replays the stream up to that instant.
 */
export function TimeTravel({ entries, value, onChange }: Props) {
  const versions = entries.map((e) => e.version).sort((a, b) => a - b)
  if (versions.length < 2) return null
  const latest = versions[versions.length - 1]
  const current = value ?? latest
  const entry = entries.find((e) => e.version === current)
  return (
    <div className="time-travel">
      <label htmlFor="asof">
        {value === null ? 'Now' : `As of v${current}: ${entry?.summary ?? ''}`}
      </label>
      <input
        id="asof"
        type="range"
        min={versions[0]}
        max={latest}
        value={current}
        onChange={(e) => {
          const v = Number(e.target.value)
          onChange(v === latest ? null : v)
        }}
      />
      {value !== null && (
        <button className="link" onClick={() => onChange(null)}>
          Back to now
        </button>
      )}
    </div>
  )
}
