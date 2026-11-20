package dev.tripsplit.eventstore;

import java.time.Instant;
import java.util.UUID;

/** An event as persisted: its position in the stream plus the original payload. */
public record StoredEvent(
    UUID streamId, long version, String type, String payloadJson, Instant occurredAt) {}
