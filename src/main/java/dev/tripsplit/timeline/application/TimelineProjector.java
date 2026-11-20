package dev.tripsplit.timeline.application;

import dev.tripsplit.timeline.TimelineUpdated;
import dev.tripsplit.trips.TripActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Builds the timeline after each trip transaction commits, on a separate thread. Delivery is
 * at-least-once (the publication registry retries incomplete publications), so writes are
 * idempotent on {@code (trip_id, version)}.
 */
@Service
public class TimelineProjector {

  private final TimelineStore store;
  private final ApplicationEventPublisher events;

  public TimelineProjector(TimelineStore store, ApplicationEventPublisher events) {
    this.store = store;
    this.events = events;
  }

  @ApplicationModuleListener
  public void on(TripActivity activity) {
    if (!store.insert(activity)) {
      return;
    }
    if (activity.undoes() != null) {
      store.markUndone(activity.tripId(), activity.undoes(), activity.occurredAt());
    }
    events.publishEvent(new TimelineUpdated(activity.tripId(), activity.version()));
  }

  public List<TimelineEntry> timeline(UUID tripId) {
    return store.forTrip(tripId);
  }
}
