package dev.tripsplit.trips.adapter.web;

import dev.tripsplit.trips.domain.Expense;
import dev.tripsplit.trips.domain.Settlement;
import dev.tripsplit.trips.domain.SplitKind;
import dev.tripsplit.trips.domain.Trip;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response bodies. Money is sent as integer minor units plus the currency code. */
final class TripResponses {
  private TripResponses() {}

  record TripView(
      UUID id,
      String name,
      String baseCurrency,
      long version,
      Instant createdAt,
      Instant asOf,
      long totalSpentMinor,
      List<MemberView> members,
      List<ExpenseView> expenses,
      List<SettlementView> settlements) {

    static TripView of(Trip trip, Instant asOf) {
      return new TripView(
          trip.id(),
          trip.name(),
          trip.baseCurrency(),
          trip.version(),
          trip.createdAt(),
          asOf,
          trip.totalSpent().minor(),
          trip.members().stream().map(m -> new MemberView(m.id(), m.name())).toList(),
          trip.expenses().stream().map(ExpenseView::of).toList(),
          trip.settlements().stream().map(SettlementView::of).toList());
    }
  }

  record MemberView(UUID id, String name) {}

  record ShareView(UUID memberId, long amountMinor) {}

  record ExpenseView(
      UUID id,
      String description,
      UUID paidBy,
      long amountMinor,
      String currency,
      String rate,
      long baseAmountMinor,
      SplitKind splitKind,
      List<ShareView> shares,
      Instant occurredAt,
      boolean voided) {

    static ExpenseView of(Expense e) {
      return new ExpenseView(
          e.id(),
          e.description(),
          e.paidBy(),
          e.amount().minor(),
          e.amount().currency(),
          e.rate().toPlainString(),
          e.baseAmount().minor(),
          e.splitKind(),
          e.shares().stream().map(s -> new ShareView(s.memberId(), s.baseMinor())).toList(),
          e.occurredAt(),
          e.voided());
    }
  }

  record SettlementView(
      UUID id, UUID from, UUID to, long amountMinor, Instant occurredAt, boolean voided) {

    static SettlementView of(Settlement s) {
      return new SettlementView(
          s.id(), s.from(), s.to(), s.amount().minor(), s.occurredAt(), s.voided());
    }
  }

  /** A proposed expense from quick-entry text; the client fills its form with it. */
  record QuickEntryDraft(
      String description,
      String amount,
      String currency,
      UUID paidBy,
      List<UUID> participants,
      List<String> unknownNames) {}

  record CommandView(UUID tripId, long version, UUID id) {}

  record EventView(long version, String type, Object data) {}
}
