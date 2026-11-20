import type { Trip } from '../lib/api'
import { formatMinor } from '../lib/money'

export function ExpenseList({ trip }: { trip: Trip }) {
  const names = new Map(trip.members.map((m) => [m.id, m.name]))
  const expenses = [...trip.expenses].reverse()
  if (expenses.length === 0) return <p className="muted">No expenses yet.</p>
  return (
    <table className="expenses">
      <thead>
        <tr>
          <th>What</th>
          <th>Paid by</th>
          <th className="num">Amount</th>
          <th className="num">In {trip.baseCurrency}</th>
        </tr>
      </thead>
      <tbody>
        {expenses.map((e) => (
          <tr key={e.id} className={e.voided ? 'voided' : ''}>
            <td>
              {e.description}
              <small className="muted">
                {' '}
                · {e.splitKind.toLowerCase()} between {e.shares.length}
              </small>
            </td>
            <td>{names.get(e.paidBy)}</td>
            <td className="num">{formatMinor(e.amountMinor, e.currency)}</td>
            <td className="num">{formatMinor(e.baseAmountMinor, trip.baseCurrency)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
