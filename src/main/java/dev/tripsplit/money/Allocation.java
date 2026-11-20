package dev.tripsplit.money;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits an integer total in proportion to integer weights, so the parts always add up to the total
 * exactly (largest-remainder / Hamilton method).
 *
 * <p>Remainder rule: every part first gets {@code floor(total * weight / sumOfWeights)}; the
 * leftover minor units go one each to the parts with the largest fractional remainder, ties broken
 * by position (earlier first). The result is deterministic for a given input order.
 */
public final class Allocation {
  private Allocation() {}

  public static long[] byWeights(long total, List<Long> weights) {
    if (total < 0) {
      throw new IllegalArgumentException("Total must not be negative");
    }
    if (weights.isEmpty()) {
      throw new IllegalArgumentException("At least one weight is required");
    }
    if (weights.stream().anyMatch(w -> w == null || w < 0)) {
      throw new IllegalArgumentException("Weights must not be negative");
    }
    var sum = weights.stream().map(BigInteger::valueOf).reduce(BigInteger.ZERO, BigInteger::add);
    if (sum.signum() == 0) {
      throw new IllegalArgumentException("Weights must not all be zero");
    }
    var parts = new long[weights.size()];
    var remainders = new ArrayList<BigInteger>(weights.size());
    var bigTotal = BigInteger.valueOf(total);
    long given = 0;
    for (int i = 0; i < parts.length; i++) {
      var qr = bigTotal.multiply(BigInteger.valueOf(weights.get(i))).divideAndRemainder(sum);
      parts[i] = qr[0].longValueExact();
      remainders.add(qr[1]);
      given += parts[i];
    }
    var leftover = total - given;
    var order =
        IntStream.range(0, parts.length)
            .boxed()
            .sorted(
                Comparator.comparing((Integer i) -> remainders.get(i))
                    .reversed()
                    .thenComparing(i -> i))
            .toList();
    for (int k = 0; k < leftover; k++) {
      parts[order.get(k)]++;
    }
    return parts;
  }

  /** Equal split: the first {@code total % n} parts get one extra minor unit. */
  public static long[] equally(long total, int count) {
    return byWeights(total, java.util.Collections.nCopies(count, 1L));
  }
}
