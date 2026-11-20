package dev.tripsplit.expenses.domain;

import dev.tripsplit.money.Money;
import java.util.List;

/** The expense converted to the base currency and its exact division among participants. */
public record SplitResult(Money baseAmount, List<Share> shares) {
  public SplitResult {
    shares = List.copyOf(shares);
    var sum = shares.stream().mapToLong(Share::baseMinor).sum();
    if (sum != baseAmount.minor()) {
      throw new IllegalStateException("Shares do not add up to the total");
    }
  }
}
