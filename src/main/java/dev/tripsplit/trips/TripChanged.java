package dev.tripsplit.trips;

import java.util.UUID;

/** A trip's stream reached {@code version}; balances and summary are already updated. */
public record TripChanged(UUID tripId, long version) {}
