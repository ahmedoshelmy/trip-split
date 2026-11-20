package dev.tripsplit.trips.domain;

import dev.tripsplit.expenses.domain.Share;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything that can happen to a trip, as past-tense facts. Events are append-only and versioned
 * by additive change; computed shares are stored so a replay never depends on today's rules.
 */
public sealed interface TripEvent {

  Instant occurredAt();

  record TripCreated(UUID tripId, String name, String baseCurrency, Instant occurredAt)
      implements TripEvent {}

  record MemberJoined(UUID memberId, String name, Instant occurredAt) implements TripEvent {}

  /**
   * An expense paid by one member. {@code amountMinor}/{@code currency} are what was paid, {@code
   * rate} the base units per currency unit used, {@code shares} the base-currency result.
   */
  record ExpenseAdded(
      UUID expenseId,
      String description,
      UUID paidBy,
      long amountMinor,
      String currency,
      BigDecimal rate,
      long baseAmountMinor,
      SplitKind splitKind,
      Map<UUID, Long> splitWeights,
      List<Share> shares,
      Instant occurredAt)
      implements TripEvent {
    public ExpenseAdded {
      splitWeights = Map.copyOf(splitWeights);
      shares = List.copyOf(shares);
    }
  }

  /** Compensating event: the expense stays in history but no longer counts. */
  record ExpenseVoided(UUID expenseId, String reason, Instant occurredAt) implements TripEvent {}

  /** {@code from} paid {@code to} back, in base currency minor units. */
  record SettlementRecorded(
      UUID settlementId, UUID from, UUID to, long amountMinor, Instant occurredAt)
      implements TripEvent {}

  record SettlementVoided(UUID settlementId, Instant occurredAt) implements TripEvent {}
}
