import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Balances } from './components/Balances'
import { ExpenseForm } from './components/ExpenseForm'
import { ExpenseList } from './components/ExpenseList'
import { SettleUpList } from './components/SettleUpList'
import { Timeline } from './components/Timeline'
import { TimeTravel } from './components/TimeTravel'
import { TripPicker } from './components/TripPicker'
import {
  api,
  StaleTripError,
  type BalanceSheet,
  type SettleUp,
  type TimelineEntry,
  type Trip,
  type TripSummary,
} from './lib/api'
import { useTripLive, type LiveMessage } from './lib/live'
import { formatMinor, minorToDecimal } from './lib/money'

interface TripState {
  trip: Trip
  balances: BalanceSheet
  settleUp: SettleUp
}

async function fetchTripState(tripId: string, asOf?: string): Promise<TripState> {
  const [trip, balances, settleUp] = await Promise.all([
    api.trip(tripId, asOf),
    api.balances(tripId, asOf),
    api.settleUp(tripId, asOf),
  ])
  return { trip, balances, settleUp }
}

const tripIdFromHash = () => window.location.hash.replace(/^#\/?/, '') || null

export default function App() {
  const [tripId, setTripId] = useState<string | null>(tripIdFromHash)
  const [trips, setTrips] = useState<TripSummary[]>([])
  const [state, setState] = useState<TripState | null>(null)
  const [timeline, setTimeline] = useState<TimelineEntry[]>([])
  const [asOfVersion, setAsOfVersion] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [newMember, setNewMember] = useState('')

  useEffect(() => {
    const onHash = () => setTripId(tripIdFromHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => {
    if (tripId === null) api.trips().then(setTrips, (e: Error) => setNotice(e.message))
  }, [tripId])

  const asOf = asOfVersion === null ? undefined : timeline.find((e) => e.version === asOfVersion)?.occurredAt

  const load = useCallback(async () => {
    if (!tripId) return
    try {
      setState(await fetchTripState(tripId, asOf))
    } catch (e) {
      setNotice((e as Error).message)
    }
  }, [tripId, asOf])

  const loadTimeline = useCallback(async () => {
    if (tripId) setTimeline(await api.timeline(tripId))
  }, [tripId])

  useEffect(() => {
    if (!tripId) return
    let cancelled = false
    fetchTripState(tripId, asOf).then(
      (next) => !cancelled && setState(next),
      (e: Error) => !cancelled && setNotice(e.message),
    )
    return () => {
      cancelled = true
    }
  }, [tripId, asOf])

  useEffect(() => {
    if (!tripId) return
    let cancelled = false
    api.timeline(tripId).then((entries) => !cancelled && setTimeline(entries), () => undefined)
    return () => {
      cancelled = true
    }
  }, [tripId])

  const onLive = useCallback(
    (message: LiveMessage) => {
      if (message.type === 'timeline') {
        void loadTimeline()
      } else if (asOfVersion === null && state && message.version > state.trip.version) {
        void load()
      }
    },
    [asOfVersion, state, load, loadTimeline],
  )
  useTripLive(tripId, onLive)

  /** Runs a command with the loaded version; on 412 reloads and tells the user. */
  const run = async (action: (version: number) => Promise<unknown>) => {
    if (!state) return
    try {
      await action(state.trip.version)
      setNotice(null)
      await load()
    } catch (e) {
      setNotice((e as Error).message)
      if (e instanceof StaleTripError) await load()
      throw e
    }
  }

  const open = (id: string | null) => {
    window.location.hash = id ? `/${id}` : ''
    setTripId(id)
    setState(null)
    setTimeline([])
    setAsOfVersion(null)
  }

  if (!tripId) {
    return (
      <Shell notice={notice}>
        <TripPicker
          trips={trips}
          onOpen={open}
          onCreate={async (name, currency, members) => {
            try {
              const trip = await api.createTrip(name, currency, members)
              open(trip.id)
            } catch (e) {
              setNotice((e as Error).message)
            }
          }}
        />
      </Shell>
    )
  }

  if (!state) return <Shell notice={notice}>Loading…</Shell>

  const { trip, balances, settleUp } = state
  const readOnly = asOfVersion !== null
  const ignore = () => undefined

  return (
    <Shell notice={notice}>
      <div className="trip-head">
        <button className="link" onClick={() => open(null)}>
          ← All trips
        </button>
        <h1>{trip.name}</h1>
        <p className="muted">
          {trip.members.length} people · spent {formatMinor(trip.totalSpentMinor, trip.baseCurrency)} ·
          version {trip.version}
        </p>
        <TimeTravel entries={timeline} value={asOfVersion} onChange={setAsOfVersion} />
      </div>

      <div className="grid two">
        <div className="stack">
          <section className="card">
            <h2>Balances {readOnly && <span className="badge">history</span>}</h2>
            <Balances sheet={balances} />
          </section>
          <section className="card">
            <h2>Settle up</h2>
            <SettleUpList
              settleUp={settleUp}
              readOnly={readOnly}
              onRecord={(from, to, minor) =>
                run((v) =>
                  api.recordSettlement(trip.id, v, from, to, minorToDecimal(minor, trip.baseCurrency)),
                ).catch(ignore)
              }
            />
          </section>
          <section className="card">
            <h2>History</h2>
            <Timeline
              entries={timeline}
              readOnly={readOnly}
              onUndo={(entry) =>
                run((v) =>
                  entry.kind === 'EXPENSE_ADDED'
                    ? api.voidExpense(trip.id, v, entry.refId, 'Undone from history')
                    : api.voidSettlement(trip.id, v, entry.refId),
                ).catch(ignore)
              }
            />
          </section>
        </div>

        <div className="stack">
          {!readOnly && (
            <section className="card">
              <h2>Add expense</h2>
              <ExpenseForm
                key={trip.members.length}
                members={trip.members}
                baseCurrency={trip.baseCurrency}
                onAdd={(body) => run((v) => api.addExpense(trip.id, v, body))}
                onParse={(text) => api.quickEntry(trip.id, text)}
              />
            </section>
          )}
          <section className="card">
            <h2>Expenses</h2>
            <ExpenseList trip={trip} />
            <p className="muted">
              <a href={`/api/trips/${trip.id}/expenses.csv${asOf ? `?asOf=${encodeURIComponent(asOf)}` : ''}`} download>
                Download as CSV
              </a>
            </p>
          </section>
          {!readOnly && (
            <section className="card">
              <h2>People</h2>
              <p>{trip.members.map((m) => m.name).join(', ')}</p>
              <form
                className="inline"
                onSubmit={(e) => {
                  e.preventDefault()
                  run((v) => api.addMember(trip.id, v, newMember))
                    .then(() => setNewMember(''))
                    .catch(ignore)
                }}
              >
                <input
                  aria-label="New member name"
                  value={newMember}
                  onChange={(e) => setNewMember(e.target.value)}
                  placeholder="Name"
                  required
                />
                <button type="submit">Add person</button>
              </form>
            </section>
          )}
        </div>
      </div>
    </Shell>
  )
}

function Shell({ notice, children }: { notice: string | null; children: ReactNode }) {
  return (
    <>
      <header className="top">
        <a href="#" className="brand">
          Trip Split
        </a>
        <span className="muted">every change is an event</span>
      </header>
      <main>
        {notice && (
          <p className="notice" role="status">
            {notice}
          </p>
        )}
        {children}
      </main>
    </>
  )
}
