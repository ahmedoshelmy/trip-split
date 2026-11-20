package dev.tripsplit.trips.application;

import java.util.UUID;

/** What a command changed: the trip's new stream version and the id it created, if any. */
public record CommandResult(UUID tripId, long version, UUID createdId) {}
