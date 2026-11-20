package dev.tripsplit.eventstore;

import java.io.Serial;
import java.util.UUID;

/** The stream moved on since the caller read it; re-read and retry or report a conflict. */
public class ConcurrencyConflictException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  private final UUID streamId;
  private final long expectedVersion;

  public ConcurrencyConflictException(UUID streamId, long expectedVersion) {
    super("Stream %s is no longer at version %d".formatted(streamId, expectedVersion));
    this.streamId = streamId;
    this.expectedVersion = expectedVersion;
  }

  public UUID streamId() {
    return streamId;
  }

  public long expectedVersion() {
    return expectedVersion;
  }
}
