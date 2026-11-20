package dev.tripsplit.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tripsplit.expenses.domain.ExpenseSplitter;
import dev.tripsplit.expenses.domain.InvalidSplitException;
import dev.tripsplit.expenses.domain.Share;
import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseSplitterTest {

  private final UUID a = UUID.randomUUID();
  private final UUID b = UUID.randomUUID();
  private final UUID c = UUID.randomUUID();
  private final List<UUID> joinOrder = List.of(a, b, c);

  @Test
  void equalSplitOrdersByJoinOrderSoTheRemainderIsDeterministic() {
    // participants given out of order; the extra piaster still goes to the first joiner
    var result =
        ExpenseSplitter.split(
            Money.parse("100.00", "EGP"),
            BigDecimal.ONE,
            "EGP",
            new SplitRule.Equal(List.of(c, a, b)),
            joinOrder);
    assertThat(result.shares())
        .containsExactly(new Share(a, 3334), new Share(b, 3333), new Share(c, 3333));
  }

  @Test
  void sharesSplitIsProportional() {
    var result =
        ExpenseSplitter.split(
            Money.parse("900.00", "EGP"),
            BigDecimal.ONE,
            "EGP",
            new SplitRule.Shares(Map.of(a, 1, b, 2)),
            joinOrder);
    assertThat(result.shares()).containsExactly(new Share(a, 30000), new Share(b, 60000));
  }

  @Test
  void exactSplitMustAddUpToTheTotal() {
    var ok =
        ExpenseSplitter.split(
            Money.parse("10.00", "EGP"),
            BigDecimal.ONE,
            "EGP",
            new SplitRule.Exact(Map.of(a, 700L, c, 300L)),
            joinOrder);
    assertThat(ok.shares()).containsExactly(new Share(a, 700), new Share(c, 300));

    assertThatThrownBy(() ->
            ExpenseSplitter.split(
                Money.parse("10.00", "EGP"),
                BigDecimal.ONE,
                "EGP",
                new SplitRule.Exact(Map.of(a, 700L, c, 200L)),
                joinOrder))
        .isInstanceOf(InvalidSplitException.class)
        .hasMessageContaining("add up to EGP 9.00");
  }

  @Test
  void foreignCurrencyIsConvertedOnceThenAllocated() {
    // 10.00 USD at 48.75 = 487.50 EGP, three ways: 162.50 each
    var result =
        ExpenseSplitter.split(
            Money.parse("10.00", "USD"),
            new BigDecimal("48.75"),
            "EGP",
            new SplitRule.Equal(joinOrder),
            joinOrder);
    assertThat(result.baseAmount()).isEqualTo(Money.parse("487.50", "EGP"));
    assertThat(result.shares())
        .extracting(Share::baseMinor)
        .containsExactly(16250L, 16250L, 16250L);
  }

  @Test
  void exactForeignAmountsKeepTheirProportionAfterConversion() {
    // 3.00 USD as 1.00 + 2.00 at rate 33.333 = 100.00 EGP (99.999 rounded) -> 33.33 / 66.67
    var result =
        ExpenseSplitter.split(
            Money.parse("3.00", "USD"),
            new BigDecimal("33.333"),
            "EGP",
            new SplitRule.Exact(Map.of(a, 100L, b, 200L)),
            joinOrder);
    assertThat(result.baseAmount().minor()).isEqualTo(10000);
    assertThat(result.shares()).extracting(Share::baseMinor).containsExactly(3333L, 6667L);
  }

  @Test
  void rejectsStrangersDuplicatesAndEmptySplits() {
    var amount = Money.parse("10", "EGP");
    var stranger = UUID.randomUUID();
    assertThatThrownBy(() ->
            ExpenseSplitter.split(
                amount, BigDecimal.ONE, "EGP", new SplitRule.Equal(List.of(stranger)), joinOrder))
        .isInstanceOf(InvalidSplitException.class);
    assertThatThrownBy(() ->
            ExpenseSplitter.split(
                amount, BigDecimal.ONE, "EGP", new SplitRule.Equal(List.of(a, a)), joinOrder))
        .hasMessageContaining("twice");
    assertThatThrownBy(() ->
            ExpenseSplitter.split(
                amount, BigDecimal.ONE, "EGP", new SplitRule.Equal(List.of()), joinOrder))
        .hasMessageContaining("at least one");
    assertThatThrownBy(() ->
            ExpenseSplitter.split(
                amount, BigDecimal.ONE, "EGP", new SplitRule.Shares(Map.of(a, 0)), joinOrder))
        .hasMessageContaining("above zero");
  }
}
