import { parseToMinor } from './money'

export type SplitKind = 'EQUAL' | 'SHARES' | 'EXACT'

/** Per-member form state: included in the split, share count, exact amount text. */
export interface SplitRow {
  memberId: string
  included: boolean
  shares: string
  exact: string
}

export interface SplitPayload {
  kind: SplitKind
  participants?: string[]
  shares?: Record<string, number>
  amounts?: Record<string, string>
}

export type SplitCheck = { ok: true; split: SplitPayload } | { ok: false; error: string }

/**
 * Turns the split form into the API payload, catching what the server would reject
 * (no participants, bad share counts, exact amounts that do not add up).
 */
export function buildSplit(
  kind: SplitKind,
  rows: SplitRow[],
  amountText: string,
  currency: string,
): SplitCheck {
  const chosen = rows.filter((r) => r.included)
  if (chosen.length === 0) return { ok: false, error: 'Pick at least one person' }

  if (kind === 'EQUAL') {
    return { ok: true, split: { kind, participants: chosen.map((r) => r.memberId) } }
  }

  if (kind === 'SHARES') {
    const shares: Record<string, number> = {}
    for (const row of chosen) {
      const n = Number(row.shares)
      if (!Number.isInteger(n) || n <= 0) return { ok: false, error: 'Shares are whole numbers above 0' }
      shares[row.memberId] = n
    }
    return { ok: true, split: { kind, shares } }
  }

  const total = parseToMinor(amountText, currency)
  if (total === null) return { ok: false, error: 'Enter the amount first' }
  const amounts: Record<string, string> = {}
  let sum = 0
  for (const row of chosen) {
    const minor = parseToMinor(row.exact || '0', currency)
    if (minor === null) return { ok: false, error: 'Exact amounts must be valid numbers' }
    sum += minor
    amounts[row.memberId] = row.exact || '0'
  }
  if (sum !== total) {
    return { ok: false, error: `Exact amounts add up to ${sum} of ${total} minor units` }
  }
  return { ok: true, split: { kind, amounts } }
}

/** Preview of an equal split with the server's remainder rule: earliest joiners get the extra unit. */
export function previewEqual(totalMinor: number, count: number): number[] {
  if (count <= 0) return []
  const base = Math.floor(totalMinor / count)
  const extra = totalMinor - base * count
  return Array.from({ length: count }, (_, i) => base + (i < extra ? 1 : 0))
}
