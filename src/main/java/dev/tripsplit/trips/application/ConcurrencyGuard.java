package dev.tripsplit.trips.application;

import dev.tripsplit.eventstore.ConcurrencyConflictException;
import dev.tripsplit.trips.domain.Trip;

/** Applies an HTTP {@code If-Match} precondition before any rule runs. */
final class ConcurrencyGuard {
  private ConcurrencyGuard() {}

  static void check(Trip trip, Long expectedVersion) {
    if (expectedVersion != null && expectedVersion != trip.version()) {
      throw new ConcurrencyConflictException(trip.id(), expectedVersion);
    }
  }
}
