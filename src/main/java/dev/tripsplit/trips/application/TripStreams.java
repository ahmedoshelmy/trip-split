package dev.tripsplit.trips.application;

import dev.tripsplit.trips.domain.Trip;
import dev.tripsplit.trips.domain.TripEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port: loads trips by replay and appends their new events. */
public interface TripStreams {

  Optional<Trip> load(UUID tripId);

  Optional<Trip> loadAsOf(UUID tripId, Instant asOf);

  /** Raw history, for the event log view. */
  List<RecordedTripEvent> history(UUID tripId);

  /**
   * Appends the trip's uncommitted events after its committed version and returns the new version.
   */
  long append(Trip trip);

  List<UUID> allTripIds();

  /** A stored event with its stream version. */
  record RecordedTripEvent(long version, String type, TripEvent event) {}
}
