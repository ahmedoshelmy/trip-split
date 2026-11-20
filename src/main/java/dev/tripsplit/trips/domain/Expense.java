package dev.tripsplit.trips.domain;

import dev.tripsplit.expenses.domain.Share;
import dev.tripsplit.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An expense as currently known; {@code voided} expenses stay visible but count for nothing. */
public record Expense(
    UUID id,
    String description,
    UUID paidBy,
    Money amount,
    BigDecimal rate,
    Money baseAmount,
    SplitKind splitKind,
    List<Share> shares,
    Instant occurredAt,
    boolean voided) {

  Expense markVoided() {
    return new Expense(
        id, description, paidBy, amount, rate, baseAmount, splitKind, shares, occurredAt, true);
  }
}
