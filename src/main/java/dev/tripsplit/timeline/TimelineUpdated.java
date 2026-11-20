package dev.tripsplit.timeline;

import java.util.UUID;

/** The timeline of a trip caught up with {@code version}. */
public record TimelineUpdated(UUID tripId, long version) {}
