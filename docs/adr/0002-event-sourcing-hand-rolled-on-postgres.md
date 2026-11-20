# 2. Event sourcing, hand-rolled on Postgres

Date: 2026-11-20 · Status: accepted

## Context

Money shared among friends raises three questions a plain CRUD table answers badly:

- **"Who changed what?"** Somebody always disputes a number after the trip. An audit trail has to be complete
  by construction, not a best-effort log written next to the update.
- **"Undo that."** Deleting a row loses the fact that it ever existed; balances shown yesterday become
  unexplainable.
- **"What did we owe each other last Tuesday?"** Point-in-time answers need history.

Storing the events and deriving state from them answers all three. The options for the event store were
Axon Framework/Axon Server, EventStoreDB (KurrentDB), or a table in the Postgres the app already needs.

## Decision

Each trip is one stream in an append-only `events` table:
`(position, stream_id, stream_type, version, type, payload jsonb, occurred_at)` with
`UNIQUE (stream_id, version)`. A trigger forbids `UPDATE` and `DELETE`.

- **Optimistic concurrency** is the unique key: a writer that read version N appends N+1; if another writer got
  there first the insert fails and the API answers `412 Precondition Failed`. The same version is exposed as the
  HTTP `ETag` and checked against `If-Match` before any rule runs.
- **Aggregate by replay.** `Trip.replay(events)` rebuilds state; commands validate against it and raise new events.
  Streams are a few hundred events at most, so no snapshots.
- **Facts, not formulas.** `ExpenseAdded` stores the computed shares and the exchange rate used. A replay never
  depends on today's split code, so changing the remainder rule cannot rewrite history.
- **Versioned type names** (`trip.expense-added.v1`) are the storage contract, decoupled from Java class names.
  A breaking payload change gets a `.v2` name and an upcaster in `TripEventCodec`.
- **Projections.** Balances and the trip list are inline projections written in the command's transaction (never
  stale). The timeline is asynchronous (see ADR 4). Both are disposable: `TripCommands.rebuildReadModels()`
  replays every stream.
- **As-of queries** replay the stream up to an instant (`?asOf=2026-11-20T10:00:00Z`).
- **Undo** is a compensating event (`ExpenseVoided`, `SettlementVoided`); nothing is ever deleted.

## Why not Axon or EventStoreDB

Both are good tools, but here they would add a server (or a framework with its own programming model and an
Axon Server license question) to answer questions that one table and one unique index already answer. Postgres
gives transactions across the append and the inline projection, which a separate event database cannot. The
store is ~100 lines and every line is visible in an interview.

## Consequences

- Reading a trip costs a replay; fine at this size, and the read models cover the hot paths.
- Event schemas must evolve additively or through upcasters; there is no "just migrate the column".
- `asOf` relies on `occurred_at` coming from one clock (the API server). Events from several writers with skewed
  clocks would need a hybrid logical clock or the global `position` instead.
- Contrast with Qirsh (a sibling project): its ledger is CRUD plus an outbox, because bank SMS are already
  immutable facts from the outside world and nobody edits them collaboratively. Event sourcing earns its cost
  here because people edit the same shared state and argue about it.
