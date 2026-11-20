package dev.tripsplit.eventstore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only streams of serialised events with optimistic concurrency. */
public interface EventStore {

  /** All events of a stream in version order; empty when the stream does not exist. */
  List<StoredEvent> read(UUID streamId);

  /** Events of a stream that occurred at or before {@code asOf}, in version order. */
  List<StoredEvent> readUntil(UUID streamId, Instant asOf);

  /**
   * Appends events after {@code expectedVersion} (0 for a new stream) and returns the new stream
   * version.
   *
   * @throws ConcurrencyConflictException when another writer appended first
   */
  long append(UUID streamId, String streamType, long expectedVersion, List<NewEvent> events);

  /** Ids of every stream of a type, oldest first (used to rebuild projections). */
  List<UUID> streamIds(String streamType);
}
