package dev.tripsplit.trips.domain;

import java.io.Serial;

/** A command that would break a trip rule (duplicate member, settling with yourself...). */
public class TripRuleViolation extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public TripRuleViolation(String message) {
    super(message);
  }
}
