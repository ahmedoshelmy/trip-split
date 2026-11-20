package dev.tripsplit.trips.adapter.persistence;

import dev.tripsplit.trips.domain.TripEvent;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps trip events to stable, versioned type names and JSON. The names are the storage contract:
 * renaming a Java record must not change them. A breaking payload change gets a new name ({@code
 * .v2}) plus an upcaster here.
 */
@Component
class TripEventCodec {

  private static final Map<String, Class<? extends TripEvent>> TYPES =
      Map.of(
          "trip.created.v1", TripEvent.TripCreated.class,
          "trip.member-joined.v1", TripEvent.MemberJoined.class,
          "trip.expense-added.v1", TripEvent.ExpenseAdded.class,
          "trip.expense-voided.v1", TripEvent.ExpenseVoided.class,
          "trip.settlement-recorded.v1", TripEvent.SettlementRecorded.class,
          "trip.settlement-voided.v1", TripEvent.SettlementVoided.class);

  /** Own mapper: web-facing Jackson settings must never change the storage format. */
  private final JsonMapper json = JsonMapper.builder().build();

  String typeOf(TripEvent event) {
    return switch (event) {
      case TripEvent.TripCreated ignored -> "trip.created.v1";
      case TripEvent.MemberJoined ignored -> "trip.member-joined.v1";
      case TripEvent.ExpenseAdded ignored -> "trip.expense-added.v1";
      case TripEvent.ExpenseVoided ignored -> "trip.expense-voided.v1";
      case TripEvent.SettlementRecorded ignored -> "trip.settlement-recorded.v1";
      case TripEvent.SettlementVoided ignored -> "trip.settlement-voided.v1";
    };
  }

  String serialize(TripEvent event) {
    return json.writeValueAsString(event);
  }

  TripEvent deserialize(String type, String payload) {
    var target = TYPES.get(type);
    if (target == null) {
      throw new IllegalStateException("Unknown trip event type " + type);
    }
    return json.readValue(payload, target);
  }
}
