package dev.tripsplit.timeline.application;

import dev.tripsplit.trips.TripActivity.Kind;
import java.time.Instant;
import java.util.UUID;

/** One line of a trip's history; {@code undoable} entries can be compensated from the UI. */
public record TimelineEntry(
    long version,
    Kind kind,
    UUID refId,
    String summary,
    Instant occurredAt,
    Instant undoneAt,
    boolean undoable) {}
