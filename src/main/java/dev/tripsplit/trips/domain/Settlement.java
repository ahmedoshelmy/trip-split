package dev.tripsplit.trips.domain;

import dev.tripsplit.money.Money;
import java.time.Instant;
import java.util.UUID;

/** A repayment between two members, in the trip's base currency. */
public record Settlement(
    UUID id, UUID from, UUID to, Money amount, Instant occurredAt, boolean voided) {

  Settlement markVoided() {
    return new Settlement(id, from, to, amount, occurredAt, true);
  }
}
