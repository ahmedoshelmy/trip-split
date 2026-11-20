/** Typed client for the Trip Split API. Commands send If-Match with the trip version. */

export interface Member {
  id: string
  name: string
}

export interface Share {
  memberId: string
  amountMinor: number
}

export interface Expense {
  id: string
  description: string
  paidBy: string
  amountMinor: number
  currency: string
  rate: string
  baseAmountMinor: number
  splitKind: 'EQUAL' | 'SHARES' | 'EXACT'
  shares: Share[]
  occurredAt: string
  voided: boolean
}

export interface Settlement {
  id: string
  from: string
  to: string
  amountMinor: number
  occurredAt: string
  voided: boolean
}

export interface Trip {
  id: string
  name: string
  baseCurrency: string
  version: number
  createdAt: string
  asOf?: string
  totalSpentMinor: number
  members: Member[]
  expenses: Expense[]
  settlements: Settlement[]
}

export interface TripSummary {
  id: string
  name: string
  baseCurrency: string
  memberCount: number
  totalSpentMinor: number
  version: number
  updatedAt: string
}

export interface MemberBalance {
  memberId: string
  name: string
  balanceMinor: number
}

export interface BalanceSheet {
  tripId: string
  version: number
  currency: string
  asOf?: string
  balances: MemberBalance[]
}

export interface Transfer {
  from: string
  fromName: string
  to: string
  toName: string
  amountMinor: number
}

export interface SettleUp {
  tripId: string
  version: number
  currency: string
  transfers: Transfer[]
}

export interface TimelineEntry {
  version: number
  kind:
    | 'TRIP_CREATED'
    | 'MEMBER_JOINED'
    | 'EXPENSE_ADDED'
    | 'EXPENSE_VOIDED'
    | 'SETTLEMENT_RECORDED'
    | 'SETTLEMENT_VOIDED'
  refId: string
  summary: string
  occurredAt: string
  undoneAt?: string
  undoable: boolean
}

export interface QuickEntryDraft {
  description: string
  amount: string
  currency: string
  paidBy?: string
  participants: string[]
  unknownNames: string[]
}

export interface CommandResult {
  tripId: string
  version: number
  id?: string
}

/** The server answered 412: the trip moved on since we loaded it. */
export class StaleTripError extends Error {
  constructor() {
    super('Someone else changed this trip. It has been reloaded, please try again.')
  }
}

export class ApiError extends Error {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (response.status === 412) throw new StaleTripError()
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null
    throw new ApiError(problem?.detail ?? `Request failed (${response.status})`)
  }
  return (await response.json()) as T
}

function command<T>(path: string, version: number | null, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: version === null ? {} : { 'If-Match': `"${version}"` },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

const asOfQuery = (asOf?: string) => (asOf ? `?asOf=${encodeURIComponent(asOf)}` : '')

export const api = {
  trips: () => request<TripSummary[]>('/api/trips'),
  trip: (id: string, asOf?: string) => request<Trip>(`/api/trips/${id}${asOfQuery(asOf)}`),
  balances: (id: string, asOf?: string) =>
    request<BalanceSheet>(`/api/trips/${id}/balances${asOfQuery(asOf)}`),
  settleUp: (id: string, asOf?: string) =>
    request<SettleUp>(`/api/trips/${id}/settle-up${asOfQuery(asOf)}`),
  timeline: (id: string) => request<TimelineEntry[]>(`/api/trips/${id}/timeline`),

  quickEntry: (id: string, text: string) =>
    command<QuickEntryDraft>(`/api/trips/${id}/quick-entry`, null, { text }),

  createTrip: (name: string, baseCurrency: string, members: string[]) =>
    command<Trip>('/api/trips', null, { name, baseCurrency, members }),
  addMember: (id: string, version: number, name: string) =>
    command<CommandResult>(`/api/trips/${id}/members`, version, { name }),
  addExpense: (id: string, version: number, body: unknown) =>
    command<CommandResult>(`/api/trips/${id}/expenses`, version, body),
  voidExpense: (id: string, version: number, expenseId: string, reason?: string) =>
    command<CommandResult>(`/api/trips/${id}/expenses/${expenseId}/void`, version, { reason }),
  recordSettlement: (id: string, version: number, from: string, to: string, amount: string) =>
    command<CommandResult>(`/api/trips/${id}/settlements`, version, { from, to, amount }),
  voidSettlement: (id: string, version: number, settlementId: string) =>
    command<CommandResult>(`/api/trips/${id}/settlements/${settlementId}/void`, version),
}
