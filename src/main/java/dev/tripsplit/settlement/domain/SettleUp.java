package dev.tripsplit.settlement.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Greedy min-cash-flow: repeatedly let the biggest debtor pay the biggest creditor as much as one
 * of them needs. Each transfer zeroes at least one person, so n people need at most n-1 transfers.
 * The true minimum is NP-hard (it hides subset-sum: groups that cancel out among themselves), and
 * for a holiday group the greedy answer is usually the same or one transfer longer.
 *
 * <p>Ties break by input order, so the same balances always give the same suggestion.
 */
public final class SettleUp {
  private SettleUp() {}

  /**
   * @param balances net position per person in minor units (positive = is owed), insertion order
   *     used for tie-breaks; must sum to zero
   */
  public static <K> List<Transfer<K>> suggest(Map<K, Long> balances) {
    var total = balances.values().stream().mapToLong(Long::longValue).sum();
    if (total != 0) {
      throw new IllegalArgumentException("Balances must net to zero, off by " + total);
    }
    var order = new LinkedHashMap<K, Integer>();
    balances.keySet().forEach(k -> order.put(k, order.size()));
    Comparator<Position<K>> biggestFirst =
        Comparator.comparingLong((Position<K> p) -> p.amount)
            .reversed()
            .thenComparingInt(p -> order.get(p.key));
    var creditors = new PriorityQueue<>(biggestFirst);
    var debtors = new PriorityQueue<>(biggestFirst);
    balances.forEach((key, balance) -> {
      if (balance > 0) {
        creditors.add(new Position<>(key, balance));
      } else if (balance < 0) {
        debtors.add(new Position<>(key, -balance));
      }
    });
    var transfers = new ArrayList<Transfer<K>>();
    while (!creditors.isEmpty() && !debtors.isEmpty()) {
      var creditor = creditors.poll();
      var debtor = debtors.poll();
      var amount = Math.min(creditor.amount, debtor.amount);
      transfers.add(new Transfer<>(debtor.key, creditor.key, amount));
      if (creditor.amount > amount) {
        creditors.add(new Position<>(creditor.key, creditor.amount - amount));
      }
      if (debtor.amount > amount) {
        debtors.add(new Position<>(debtor.key, debtor.amount - amount));
      }
    }
    return List.copyOf(transfers);
  }

  private record Position<K>(K key, long amount) {}
}
