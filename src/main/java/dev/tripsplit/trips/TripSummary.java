package dev.tripsplit.trips;

import java.time.Instant;
import java.util.UUID;

/** A row of the trip list read model. */
public record TripSummary(
    UUID id,
    String name,
    String baseCurrency,
    int memberCount,
    long totalSpentMinor,
    long version,
    Instant updatedAt) {}
