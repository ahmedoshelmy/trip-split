package dev.tripsplit.eventstore;

import java.time.Instant;

/** An event to append: a versioned type name, its JSON payload and when it happened. */
public record NewEvent(String type, String payloadJson, Instant occurredAt) {}
