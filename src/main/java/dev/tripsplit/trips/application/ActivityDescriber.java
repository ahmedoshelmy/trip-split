package dev.tripsplit.trips.application;

import dev.tripsplit.money.Money;
import dev.tripsplit.trips.TripActivity;
import dev.tripsplit.trips.TripActivity.Kind;
import dev.tripsplit.trips.domain.Trip;
import dev.tripsplit.trips.domain.TripEvent;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Turns a trip event into a one-line, human-readable timeline entry. */
@Component
public class ActivityDescriber {

  public TripActivity describe(Trip trip, long version, TripEvent event) {
    return switch (event) {
      case TripEvent.TripCreated e ->
          activity(
              trip,
              version,
              Kind.TRIP_CREATED,
              e.tripId(),
              "Trip \"%s\" started in %s".formatted(e.name(), e.baseCurrency()),
              e,
              null);
      case TripEvent.MemberJoined e ->
          activity(trip, version, Kind.MEMBER_JOINED, e.memberId(), e.name() + " joined", e, null);
      case TripEvent.ExpenseAdded e ->
          activity(trip, version, Kind.EXPENSE_ADDED, e.expenseId(), expenseLine(trip, e), e, null);
      case TripEvent.ExpenseVoided e ->
          activity(
              trip,
              version,
              Kind.EXPENSE_VOIDED,
              e.expenseId(),
              "Voided \"%s\" (%s)".formatted(trip.expense(e.expenseId()).description(), e.reason()),
              e,
              e.expenseId());
      case TripEvent.SettlementRecorded e ->
          activity(
              trip,
              version,
              Kind.SETTLEMENT_RECORDED,
              e.settlementId(),
              "%s paid %s %s"
                  .formatted(
                      name(trip, e.from()),
                      name(trip, e.to()),
                      Money.of(e.amountMinor(), trip.baseCurrency())),
              e,
              null);
      case TripEvent.SettlementVoided e -> {
        var s = trip.settlement(e.settlementId());
        yield activity(
            trip,
            version,
            Kind.SETTLEMENT_VOIDED,
            e.settlementId(),
            "Undid %s paying %s %s".formatted(name(trip, s.from()), name(trip, s.to()), s.amount()),
            e,
            e.settlementId());
      }
    };
  }

  private static String expenseLine(Trip trip, TripEvent.ExpenseAdded e) {
    var paid = Money.of(e.amountMinor(), e.currency());
    var converted =
        e.currency().equals(trip.baseCurrency())
            ? ""
            : " (= %s at %s)"
                .formatted(
                    Money.of(e.baseAmountMinor(), trip.baseCurrency()), e.rate().toPlainString());
    var how =
        switch (e.splitKind()) {
          case EQUAL -> "split equally";
          case EXACT -> "split by exact amounts";
          case SHARES -> "split by shares";
        };
    return "%s paid %s%s for %s, %s between %d"
        .formatted(
            name(trip, e.paidBy()), paid, converted, e.description(), how, e.shares().size());
  }

  private static String name(Trip trip, UUID memberId) {
    return trip.member(memberId).name();
  }

  private static TripActivity activity(
      Trip trip,
      long version,
      Kind kind,
      UUID refId,
      String summary,
      TripEvent event,
      UUID undoes) {
    return new TripActivity(trip.id(), version, kind, refId, summary, event.occurredAt(), undoes);
  }
}
