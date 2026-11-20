package dev.tripsplit.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tripsplit.settlement.domain.SettleUp;
import java.util.LinkedHashMap;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/** Property-style checks over random balanced groups. */
class SettleUpProperties {

  @Property(tries = 500)
  void transfersSettleEveryoneWithAtMostNMinusOneTransfers(
      @ForAll @Size(min = 1, max = 15)
          List<@LongRange(min = -1_000_000, max = 1_000_000) Long> raw) {
    var balances = balanced(raw);

    var transfers = SettleUp.suggest(balances);

    assertThat(transfers.size()).isLessThanOrEqualTo(Math.max(0, balances.size() - 1));
    var after = new LinkedHashMap<>(balances);
    for (var t : transfers) {
      assertThat(t.amountMinor()).isPositive();
      assertThat(t.from()).isNotEqualTo(t.to());
      after.merge(t.from(), t.amountMinor(), Long::sum);
      after.merge(t.to(), -t.amountMinor(), Long::sum);
    }
    assertThat(after.values()).allMatch(v -> v == 0);
  }

  @Property(tries = 200)
  void sameInputGivesSameSuggestion(
      @ForAll @Size(min = 1, max = 10) List<@LongRange(min = -50_000, max = 50_000) Long> raw) {
    var balances = balanced(raw);
    assertThat(SettleUp.suggest(balances)).isEqualTo(SettleUp.suggest(balances));
  }

  /** Random positions plus one closing member so the group nets to zero. */
  private static LinkedHashMap<String, Long> balanced(List<Long> raw) {
    var balances = new LinkedHashMap<String, Long>();
    long sum = 0;
    for (int i = 0; i < raw.size(); i++) {
      balances.put("m" + i, raw.get(i));
      sum += raw.get(i);
    }
    balances.put("closer", -sum);
    return balances;
  }
}
