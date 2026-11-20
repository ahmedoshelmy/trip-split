package dev.tripsplit.trips.adapter.persistence;

import dev.tripsplit.eventstore.EventStore;
import dev.tripsplit.eventstore.NewEvent;
import dev.tripsplit.eventstore.StoredEvent;
import dev.tripsplit.trips.application.TripStreams;
import dev.tripsplit.trips.domain.Trip;
import dev.tripsplit.trips.domain.TripEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Trips persisted as event streams in the shared event store. */
@Repository
class EventSourcedTripStreams implements TripStreams {

  static final String STREAM_TYPE = "trip";

  private final EventStore store;
  private final TripEventCodec codec;

  EventSourcedTripStreams(EventStore store, TripEventCodec codec) {
    this.store = store;
    this.codec = codec;
  }

  @Override
  public Optional<Trip> load(UUID tripId) {
    return rebuild(store.read(tripId));
  }

  @Override
  public Optional<Trip> loadAsOf(UUID tripId, Instant asOf) {
    return rebuild(store.readUntil(tripId, asOf));
  }

  @Override
  public List<RecordedTripEvent> history(UUID tripId) {
    return store.read(tripId).stream()
        .map(e -> new RecordedTripEvent(e.version(), e.type(), decode(e)))
        .toList();
  }

  @Override
  public long append(Trip trip) {
    var events =
        trip.uncommittedEvents().stream()
            .map(e -> new NewEvent(codec.typeOf(e), codec.serialize(e), e.occurredAt()))
            .toList();
    return store.append(trip.id(), STREAM_TYPE, trip.committedVersion(), events);
  }

  @Override
  public List<UUID> allTripIds() {
    return store.streamIds(STREAM_TYPE);
  }

  private Optional<Trip> rebuild(List<StoredEvent> events) {
    if (events.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(Trip.replay(events.stream().map(this::decode).toList()));
  }

  private TripEvent decode(StoredEvent event) {
    return codec.deserialize(event.type(), event.payloadJson());
  }
}
