import type { SettleUp } from '../lib/api'
import { formatMinor } from '../lib/money'

interface Props {
  settleUp: SettleUp
  readOnly: boolean
  onRecord: (from: string, to: string, amountMinor: number) => Promise<void>
}

export function SettleUpList({ settleUp, readOnly, onRecord }: Props) {
  if (settleUp.transfers.length === 0) {
    return <p className="muted">Everyone is square.</p>
  }
  return (
    <ol className="transfers">
      {settleUp.transfers.map((t) => (
        <li key={`${t.from}-${t.to}`}>
          <span>
            <strong>{t.fromName}</strong> pays <strong>{t.toName}</strong>{' '}
            {formatMinor(t.amountMinor, settleUp.currency)}
          </span>
          {!readOnly && (
            <button onClick={() => void onRecord(t.from, t.to, t.amountMinor)}>Mark paid</button>
          )}
        </li>
      ))}
    </ol>
  )
}
