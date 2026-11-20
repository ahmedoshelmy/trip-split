package dev.tripsplit.trips;

import java.time.Instant;
import java.util.UUID;

/**
 * One event of a trip, described for people. {@code refId} is the expense, settlement or member the
 * event is about; {@code undoes} is set when the event compensates an earlier one.
 */
public record TripActivity(
    UUID tripId,
    long version,
    Kind kind,
    UUID refId,
    String summary,
    Instant occurredAt,
    UUID undoes) {

  /** Event kinds the timeline knows about. */
  public enum Kind {
    TRIP_CREATED,
    MEMBER_JOINED,
    EXPENSE_ADDED,
    EXPENSE_VOIDED,
    SETTLEMENT_RECORDED,
    SETTLEMENT_VOIDED
  }
}
