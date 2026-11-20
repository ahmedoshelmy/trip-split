package dev.tripsplit.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tripsplit.settlement.domain.SettleUp;
import dev.tripsplit.settlement.domain.Transfer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettleUpTest {

  @Test
  void biggestDebtorPaysBiggestCreditorFirst() {
    var balances = new LinkedHashMap<String, Long>();
    balances.put("Omar", 9000L);
    balances.put("Lina", -2000L);
    balances.put("Karim", -4000L);
    balances.put("Nour", -3000L);
    assertThat(SettleUp.suggest(balances))
        .containsExactly(
            new Transfer<>("Karim", "Omar", 4000),
            new Transfer<>("Nour", "Omar", 3000),
            new Transfer<>("Lina", "Omar", 2000));
  }

  @Test
  void chainsCollapseIntoDirectTransfers() {
    // A owes B 10, B owes C 10 -> A pays C directly, one transfer instead of two
    var balances = new LinkedHashMap<String, Long>();
    balances.put("A", -1000L);
    balances.put("B", 0L);
    balances.put("C", 1000L);
    assertThat(SettleUp.suggest(balances)).containsExactly(new Transfer<>("A", "C", 1000));
  }

  @Test
  void settledTripNeedsNoTransfers() {
    assertThat(SettleUp.suggest(Map.of("A", 0L, "B", 0L))).isEmpty();
  }

  @Test
  void tiesBreakByInputOrder() {
    var balances = new LinkedHashMap<String, Long>();
    balances.put("A", 500L);
    balances.put("B", 500L);
    balances.put("C", -500L);
    balances.put("D", -500L);
    assertThat(SettleUp.suggest(balances))
        .containsExactly(new Transfer<>("C", "A", 500), new Transfer<>("D", "B", 500));
  }

  @Test
  void rejectsBalancesThatDoNotNetToZero() {
    assertThatThrownBy(() -> SettleUp.suggest(Map.of("A", 1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
