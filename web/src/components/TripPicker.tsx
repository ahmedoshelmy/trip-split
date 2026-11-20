import { useState, type FormEvent } from 'react'
import type { TripSummary } from '../lib/api'
import { formatMinor } from '../lib/money'

interface Props {
  trips: TripSummary[]
  onOpen: (id: string) => void
  onCreate: (name: string, currency: string, members: string[]) => Promise<void>
}

export function TripPicker({ trips, onOpen, onCreate }: Props) {
  const [name, setName] = useState('')
  const [currency, setCurrency] = useState('EGP')
  const [members, setMembers] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const names = members
      .split(',')
      .map((m) => m.trim())
      .filter(Boolean)
    await onCreate(name, currency, names)
    setName('')
    setMembers('')
  }

  return (
    <div className="grid two">
      <section className="card">
        <h2>Your trips</h2>
        {trips.length === 0 && <p className="muted">No trips yet. Start one on the right.</p>}
        <ul className="list">
          {trips.map((t) => (
            <li key={t.id}>
              <button className="row-button" onClick={() => onOpen(t.id)}>
                <strong>{t.name}</strong>
                <span className="muted">
                  {t.memberCount} people · {formatMinor(t.totalSpentMinor, t.baseCurrency)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </section>
      <section className="card">
        <h2>New trip</h2>
        <form onSubmit={submit} className="stack">
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={60} />
          </label>
          <label>
            Currency everyone settles in
            <input
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              required
              minLength={3}
              maxLength={3}
            />
          </label>
          <label>
            People (comma separated)
            <input
              value={members}
              onChange={(e) => setMembers(e.target.value)}
              placeholder="Omar, Lina, Karim"
            />
          </label>
          <button type="submit" className="primary">
            Start trip
          </button>
        </form>
      </section>
    </div>
  )
}
