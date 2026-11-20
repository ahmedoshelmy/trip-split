import { useMemo, useState, type FormEvent } from 'react'
import type { Member } from '../lib/api'
import { formatMinor, parseToMinor } from '../lib/money'
import { buildSplit, previewEqual, type SplitKind, type SplitRow } from '../lib/split'

interface Props {
  members: Member[]
  baseCurrency: string
  onAdd: (body: unknown) => Promise<void>
}

const freshRows = (members: Member[]): SplitRow[] =>
  members.map((m) => ({ memberId: m.id, included: true, shares: '1', exact: '' }))

export function ExpenseForm({ members, baseCurrency, onAdd }: Props) {
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState(baseCurrency)
  const [rate, setRate] = useState('')
  const [paidBy, setPaidBy] = useState(members[0]?.id ?? '')
  const [kind, setKind] = useState<SplitKind>('EQUAL')
  const [rows, setRows] = useState<SplitRow[]>(() => freshRows(members))
  const [error, setError] = useState<string | null>(null)

  // Keep one row per member when people join.
  const allRows = useMemo(
    () =>
      members.map(
        (m) =>
          rows.find((r) => r.memberId === m.id) ?? {
            memberId: m.id,
            included: true,
            shares: '1',
            exact: '',
          },
      ),
    [members, rows],
  )

  const foreign = currency !== baseCurrency
  const minor = parseToMinor(amount, currency)
  const included = allRows.filter((r) => r.included)
  const preview = kind === 'EQUAL' && minor !== null ? previewEqual(minor, included.length) : []

  const update = (memberId: string, patch: Partial<SplitRow>) =>
    setRows(allRows.map((r) => (r.memberId === memberId ? { ...r, ...patch } : r)))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const check = buildSplit(kind, allRows, amount, currency)
    if (!check.ok) {
      setError(check.error)
      return
    }
    try {
      await onAdd({
        description,
        paidBy: paidBy || members[0]?.id,
        amount: amount.replace(/,/g, ''),
        currency,
        rate: foreign ? rate : undefined,
        split: check.split,
      })
      setDescription('')
      setAmount('')
      setRows(freshRows(members))
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <form className="stack" onSubmit={submit} aria-label="Add expense">
      <div className="grid three">
        <label className="span2">
          What
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Dinner by the sea"
            required
            maxLength={120}
          />
        </label>
        <label>
          Paid by
          <select value={paidBy} onChange={(e) => setPaidBy(e.target.value)}>
            {members.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="grid three">
        <label>
          Amount
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal"
            placeholder="600.00"
            required
          />
        </label>
        <label>
          Currency
          <input
            value={currency}
            onChange={(e) => setCurrency(e.target.value.toUpperCase())}
            minLength={3}
            maxLength={3}
            required
          />
        </label>
        {foreign && (
          <label>
            1 {currency} = ? {baseCurrency}
            <input
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              inputMode="decimal"
              placeholder="48.75"
              required
            />
          </label>
        )}
      </div>
      <fieldset>
        <legend>Split</legend>
        <div className="segmented" role="radiogroup">
          {(['EQUAL', 'SHARES', 'EXACT'] as const).map((k) => (
            <button
              type="button"
              key={k}
              role="radio"
              aria-checked={kind === k}
              className={kind === k ? 'active' : ''}
              onClick={() => setKind(k)}
            >
              {k === 'EQUAL' ? 'Equally' : k === 'SHARES' ? 'By shares' : 'Exact amounts'}
            </button>
          ))}
        </div>
        <ul className="split-rows">
          {members.map((m) => {
            const row = allRows.find((r) => r.memberId === m.id)!
            const index = included.findIndex((r) => r.memberId === m.id)
            return (
              <li key={m.id}>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={row.included}
                    onChange={(e) => update(m.id, { included: e.target.checked })}
                  />
                  {m.name}
                </label>
                {kind === 'SHARES' && row.included && (
                  <input
                    className="small"
                    aria-label={`${m.name} shares`}
                    value={row.shares}
                    onChange={(e) => update(m.id, { shares: e.target.value })}
                    inputMode="numeric"
                  />
                )}
                {kind === 'EXACT' && row.included && (
                  <input
                    className="small"
                    aria-label={`${m.name} amount`}
                    value={row.exact}
                    onChange={(e) => update(m.id, { exact: e.target.value })}
                    inputMode="decimal"
                  />
                )}
                {kind === 'EQUAL' && row.included && index >= 0 && preview[index] !== undefined && (
                  <span className="muted">{formatMinor(preview[index], currency)}</span>
                )}
              </li>
            )
          })}
        </ul>
      </fieldset>
      {error && <p className="error">{error}</p>}
      <button type="submit" className="primary">
        Add expense
      </button>
    </form>
  )
}
