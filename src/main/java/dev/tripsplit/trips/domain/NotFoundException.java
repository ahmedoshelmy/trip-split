package dev.tripsplit.trips.domain;

import java.io.Serial;

/** The trip, member, expense or settlement does not exist. */
public class NotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }
}
