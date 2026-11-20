package dev.tripsplit.expenses.domain;

import dev.tripsplit.money.Allocation;
import dev.tripsplit.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Turns an expense and a split rule into base-currency shares that add up exactly.
 *
 * <p>The amount is converted once (half-even to the base minor unit), then the converted total is
 * allocated by weights: 1 each for {@link SplitRule.Equal}, the exact amounts for {@link
 * SplitRule.Exact} and the shares for {@link SplitRule.Shares}. Participants are ordered by trip
 * join order, which makes the remainder rule in {@link Allocation} deterministic.
 */
public final class ExpenseSplitter {
  private ExpenseSplitter() {}

  public static SplitResult split(
      Money amount, BigDecimal rate, String baseCurrency, SplitRule rule, List<UUID> joinOrder) {
    if (!amount.isPositive()) {
      throw new InvalidSplitException("Amount must be positive");
    }
    var participants = ordered(rule.participants(), joinOrder);
    var weights = weights(rule, participants, amount);
    var base = amount.convert(rate, baseCurrency);
    var parts = Allocation.byWeights(base.minor(), weights);
    var shares = new ArrayList<Share>(parts.length);
    for (int i = 0; i < parts.length; i++) {
      shares.add(new Share(participants.get(i), parts[i]));
    }
    return new SplitResult(base, shares);
  }

  private static List<UUID> ordered(List<UUID> participants, List<UUID> joinOrder) {
    if (participants.isEmpty()) {
      throw new InvalidSplitException("Pick at least one participant");
    }
    if (new HashSet<>(participants).size() != participants.size()) {
      throw new InvalidSplitException("A participant is listed twice");
    }
    for (var id : participants) {
      if (!joinOrder.contains(id)) {
        throw new InvalidSplitException("Not a member of this trip: " + id);
      }
    }
    return joinOrder.stream().filter(participants::contains).toList();
  }

  private static List<Long> weights(SplitRule rule, List<UUID> participants, Money amount) {
    return switch (rule) {
      case SplitRule.Equal ignored -> participants.stream().map(id -> 1L).toList();
      case SplitRule.Exact exact -> {
        var weights = participants.stream().map(exact.amounts()::get).toList();
        if (weights.stream().anyMatch(w -> w < 0)) {
          throw new InvalidSplitException("Exact amounts must not be negative");
        }
        var sum = weights.stream().mapToLong(Long::longValue).sum();
        if (sum != amount.minor()) {
          throw new InvalidSplitException(
              "Exact amounts add up to %s, not %s"
                  .formatted(Money.of(sum, amount.currency()), amount));
        }
        yield weights;
      }
      case SplitRule.Shares shares -> {
        var weights = participants.stream().map(id -> (long) shares.shares().get(id)).toList();
        if (weights.stream().anyMatch(w -> w <= 0)) {
          throw new InvalidSplitException("Shares must be whole numbers above zero");
        }
        yield weights;
      }
    };
  }
}
