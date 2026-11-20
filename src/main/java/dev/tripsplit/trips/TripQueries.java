package dev.tripsplit.trips;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Read access to trips for other modules. */
public interface TripQueries {

  /** Current balances from the inline projection. */
  Optional<BalanceSheet> balances(UUID tripId);

  /** Balances as they were at {@code asOf}, by replaying the stream up to that instant. */
  Optional<BalanceSheet> balancesAsOf(UUID tripId, Instant asOf);
}
