# 4. A modular monolith with Spring Modulith

Date: 2026-11-20 · Status: accepted

## Context

Trip Split has clear sub-domains (the trip and its events, split rules, settle-up, history, live updates) but one
team, one database and modest load. Microservices would buy independent deployment nobody needs and cost
network hops, distributed transactions and operational weight. A single package soup would let everything call
everything.

## Decision

One Spring Boot deployable, cut into application modules verified by Spring Modulith:

| Module | Owns | Talks to others through |
|---|---|---|
| `money` (shared kernel, open) | `Money`, `Allocation` | used by all |
| `eventstore` | append-only streams, optimistic concurrency | `EventStore` interface |
| `expenses` | split rules, conversion, remainder rule (pure) | named interface `split` |
| `trips` | trip aggregate, commands, inline projections, REST | `TripQueries`, events `TripChanged`, `TripActivity` |
| `settlement` | greedy min-cash-flow settle-up | reads `TripQueries` |
| `timeline` | async history projection | listens to `TripActivity`, publishes `TimelineUpdated` |
| `live` | STOMP WebSocket push | listens to `TripChanged`, `TimelineUpdated` |

- `ApplicationModules.verify()` runs in the test suite: no cycles, no access to another module's internal packages.
- Inside each module a hexagonal layout (`domain` → `application` → `adapter`) is enforced by ArchUnit; `domain`
  is plain Java with no Spring, Jackson or JDBC.
- **Module events go through the event publication registry** (Spring Modulith's transactional outbox, JDBC):
  events published inside the command's transaction are stored with it, `@ApplicationModuleListener`s run after
  commit on a virtual thread, incomplete publications are retried on restart. Listeners are idempotent
  (`timeline_entries` keyed by `(trip_id, version)`), so at-least-once delivery gives an exactly-once effect.

## Consequences

- Extracting a module later is mechanical: its API is already an interface plus events; the registry could be
  swapped for Kafka/RabbitMQ externalisation (`@Externalized`) without touching listeners.
- The timeline may lag the trip by milliseconds; the UI refreshes it when the `timeline` live message arrives.
- `spring.modulith.events.completion-mode=delete` keeps the publication table small; failed publications remain
  for inspection via the `modulith` actuator endpoint.
