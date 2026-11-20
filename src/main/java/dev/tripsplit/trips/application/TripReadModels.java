package dev.tripsplit.trips.application;

import dev.tripsplit.trips.BalanceSheet;
import dev.tripsplit.trips.TripSummary;
import dev.tripsplit.trips.domain.Trip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port for the inline projections (trip list and current balances). */
public interface TripReadModels {

  /** Writes the trip's summary and balances; called in the same transaction as the append. */
  void project(Trip trip);

  Optional<BalanceSheet> balances(UUID tripId);

  List<TripSummary> summaries();
}
