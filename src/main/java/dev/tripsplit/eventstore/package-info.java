/**
 * A small append-only event store on Postgres: one row per event, {@code UNIQUE(stream_id,
 * version)} gives optimistic concurrency. Callers own their event types and serialisation.
 */
@ApplicationModule(displayName = "Event store")
package dev.tripsplit.eventstore;

import org.springframework.modulith.ApplicationModule;
