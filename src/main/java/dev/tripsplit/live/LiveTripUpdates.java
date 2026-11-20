package dev.tripsplit.live;

import dev.tripsplit.timeline.TimelineUpdated;
import dev.tripsplit.trips.BalanceSheet;
import dev.tripsplit.trips.TripChanged;
import dev.tripsplit.trips.TripQueries;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Fans committed trip changes out to subscribed browsers. */
@Component
class LiveTripUpdates {

  private final SimpMessagingTemplate broker;
  private final TripQueries trips;

  LiveTripUpdates(SimpMessagingTemplate broker, TripQueries trips) {
    this.broker = broker;
    this.trips = trips;
  }

  @ApplicationModuleListener
  void on(TripChanged changed) {
    trips
        .balances(changed.tripId())
        .ifPresent(
            sheet -> send(changed.tripId(), new LiveMessage("balances", sheet.version(), sheet)));
  }

  @ApplicationModuleListener
  void on(TimelineUpdated updated) {
    send(updated.tripId(), new LiveMessage("timeline", updated.version(), null));
  }

  private void send(UUID tripId, LiveMessage message) {
    broker.convertAndSend("/topic/trips/" + tripId, message);
  }

  /** {@code type} is "balances" (with the sheet) or "timeline" (refetch the timeline). */
  record LiveMessage(String type, long version, BalanceSheet balances) {}
}
