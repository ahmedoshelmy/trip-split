package dev.tripsplit.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/** Property-style checks of the remainder rule over random totals and weights. */
class AllocationProperties {

  @Property
  void partsAlwaysAddUpAndStayWithinOneUnitOfTheExactShare(
      @ForAll @LongRange(min = 0, max = 10_000_000_000L) long total,
      @ForAll @Size(min = 1, max = 12) List<@LongRange(min = 1, max = 1000) Long> weights) {
    var parts = Allocation.byWeights(total, weights);
    assertThat(Arrays.stream(parts).sum()).isEqualTo(total);
    var sum = weights.stream().mapToLong(Long::longValue).sum();
    for (int i = 0; i < parts.length; i++) {
      var exact = (double) total * weights.get(i) / sum;
      assertThat((double) parts[i]).isBetween(Math.floor(exact), Math.ceil(exact));
    }
  }
}
