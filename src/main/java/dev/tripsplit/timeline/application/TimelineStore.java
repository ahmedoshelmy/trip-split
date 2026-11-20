package dev.tripsplit.timeline.application;

import dev.tripsplit.trips.TripActivity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Output port for the timeline table. */
public interface TimelineStore {

  /** Inserts the entry once; returns false when it was already there (a redelivery). */
  boolean insert(TripActivity activity);

  void markUndone(UUID tripId, UUID refId, Instant at);

  List<TimelineEntry> forTrip(UUID tripId);
}
