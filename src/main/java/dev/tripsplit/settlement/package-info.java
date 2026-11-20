/**
 * Settle-up suggestions: the fewest transfers (greedy min-cash-flow) that bring every balance of a
 * trip to zero. Reads balances through {@link dev.tripsplit.trips.TripQueries}.
 */
@ApplicationModule(displayName = "Settlement")
package dev.tripsplit.settlement;

import org.springframework.modulith.ApplicationModule;
