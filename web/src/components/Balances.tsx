import type { BalanceSheet } from '../lib/api'
import { formatMinor } from '../lib/money'

/** Diverging bars: right (teal) = the group owes them, left (coral) = they owe the group. */
export function Balances({ sheet }: { sheet: BalanceSheet }) {
  const max = Math.max(1, ...sheet.balances.map((b) => Math.abs(b.balanceMinor)))
  return (
    <ul className="balances">
      {sheet.balances.map((b) => {
        const width = `${(Math.abs(b.balanceMinor) / max) * 50}%`
        const positive = b.balanceMinor >= 0
        return (
          <li key={b.memberId}>
            <span className="who">{b.name}</span>
            <span className="bar-track" aria-hidden="true">
              <span
                className={positive ? 'bar plus' : 'bar minus'}
                style={positive ? { left: '50%', width } : { right: '50%', width }}
              />
            </span>
            <span className={positive ? 'amount plus' : 'amount minus'}>
              {b.balanceMinor === 0
                ? 'settled'
                : `${positive ? 'gets' : 'owes'} ${formatMinor(Math.abs(b.balanceMinor), sheet.currency)}`}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
