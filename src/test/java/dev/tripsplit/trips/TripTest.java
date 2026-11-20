package dev.tripsplit.trips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import dev.tripsplit.trips.domain.ExpenseDraft;
import dev.tripsplit.trips.domain.NotFoundException;
import dev.tripsplit.trips.domain.Trip;
import dev.tripsplit.trips.domain.TripEvent;
import dev.tripsplit.trips.domain.TripRuleViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TripTest {

  private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

  private Trip trip;
  private UUID omar;
  private UUID lina;
  private UUID karim;

  @BeforeEach
  void setUp() {
    trip =
        Trip.create(
            UUID.randomUUID(),
            "  Siwa weekend ",
            "EGP",
            List.of("Omar", "Lina", "Karim"),
            UUID::randomUUID,
            T0);
    var members = trip.members();
    omar = members.get(0).id();
    lina = members.get(1).id();
    karim = members.get(2).id();
  }

  @Test
  void createRaisesTripCreatedThenOneMemberJoinedPerMember() {
    assertThat(trip.name()).isEqualTo("Siwa weekend");
    assertThat(trip.uncommittedEvents())
        .hasSize(4)
        .first()
        .isInstanceOf(TripEvent.TripCreated.class);
    assertThat(trip.version()).isEqualTo(4);
    assertThat(trip.committedVersion()).isZero();
  }

  @Test
  void balancesFollowExpensesAndSettlementsAndAlwaysNetToZero() {
    addEqual("Dinner", omar, "300.00");
    assertThat(trip.balances()).containsEntry(omar, 20000L).containsEntry(lina, -10000L);

    trip.recordSettlement(UUID.randomUUID(), lina, omar, Money.parse("100", "EGP"), T0);
    assertThat(trip.balances()).containsEntry(lina, 0L).containsEntry(omar, 10000L);
    assertThat(trip.balances().values().stream().mapToLong(Long::longValue).sum()).isZero();
  }

  @Test
  void voidingIsACompensatingEventNotADelete() {
    var id = addEqual("Taxi", karim, "90.00");
    trip.voidExpense(id, "typo", T0);

    assertThat(trip.expense(id).voided()).isTrue();
    assertThat(trip.balances().values()).allMatch(v -> v == 0);
    assertThat(trip.uncommittedEvents()).last().isInstanceOf(TripEvent.ExpenseVoided.class);
    assertThatThrownBy(() -> trip.voidExpense(id, null, T0)).isInstanceOf(TripRuleViolation.class);
  }

  @Test
  void replayingTheEventsRebuildsTheSameState() {
    addEqual("Fuel", omar, "500.00");
    var usd =
        new ExpenseDraft(
            "Desert tour",
            lina,
            Money.parse("30.00", "USD"),
            new BigDecimal("48.75"),
            new SplitRule.Shares(Map.of(omar, 1, lina, 2)));
    trip.addExpense(UUID.randomUUID(), usd, T0);
    trip.recordSettlement(UUID.randomUUID(), karim, omar, Money.parse("50", "EGP"), T0);

    var replayed = Trip.replay(trip.uncommittedEvents());

    assertThat(replayed.balances()).isEqualTo(trip.balances());
    assertThat(replayed.version()).isEqualTo(trip.version());
    assertThat(replayed.expenses()).isEqualTo(trip.expenses());
    assertThat(replayed.totalSpent()).isEqualTo(Money.parse("1962.50", "EGP"));
  }

  @Test
  void replayingAPrefixGivesTheEarlierState() {
    addEqual("Fuel", omar, "300.00");
    var events = new ArrayList<>(trip.uncommittedEvents());
    addEqual("Hotel", lina, "900.00");

    var earlier = Trip.replay(events);

    assertThat(earlier.balances()).containsEntry(omar, 20000L).containsEntry(lina, -10000L);
    assertThat(trip.balances()).containsEntry(lina, 50000L);
  }

  @Test
  void enforcesTripRules() {
    assertThatThrownBy(() -> trip.join(UUID.randomUUID(), "omar", T0))
        .isInstanceOf(TripRuleViolation.class)
        .hasMessageContaining("already");
    assertThatThrownBy(
            () -> trip.recordSettlement(UUID.randomUUID(), omar, omar, Money.parse("1", "EGP"), T0))
        .isInstanceOf(TripRuleViolation.class);
    assertThatThrownBy(
            () -> trip.recordSettlement(UUID.randomUUID(), omar, lina, Money.parse("1", "USD"), T0))
        .hasMessageContaining("trip currency");
    var noRate =
        new ExpenseDraft(
            "Gift", omar, Money.parse("5", "USD"), null, new SplitRule.Equal(List.of(omar)));
    assertThatThrownBy(() -> trip.addExpense(UUID.randomUUID(), noRate, T0))
        .hasMessageContaining("rate");
    var stranger =
        new ExpenseDraft(
            "Gift",
            UUID.randomUUID(),
            Money.parse("5", "EGP"),
            null,
            new SplitRule.Equal(List.of(omar)));
    assertThatThrownBy(() -> trip.addExpense(UUID.randomUUID(), stranger, T0))
        .hasMessageContaining("Not a member");
    assertThatThrownBy(() -> trip.voidExpense(UUID.randomUUID(), null, T0))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> Trip.create(UUID.randomUUID(), " ", "EGP", List.of(), UUID::randomUUID, T0))
        .hasMessageContaining("required");
    assertThatThrownBy(
            () -> Trip.create(UUID.randomUUID(), "x", "XXX", List.of(), UUID::randomUUID, T0))
        .hasMessageContaining("currency");
  }

  @Test
  void expenseEventStoresTheComputedSharesAsFacts() {
    addEqual("Water", omar, "10.00");
    var added = (TripEvent.ExpenseAdded) trip.uncommittedEvents().getLast();
    assertThat(added.shares()).extracting(s -> s.baseMinor()).containsExactly(334L, 333L, 333L);
    assertThat(added.baseAmountMinor()).isEqualTo(1000);
  }

  private UUID addEqual(String what, UUID paidBy, String amount) {
    var id = UUID.randomUUID();
    trip.addExpense(
        id,
        new ExpenseDraft(
            what,
            paidBy,
            Money.parse(amount, "EGP"),
            null,
            new SplitRule.Equal(List.of(omar, lina, karim))),
        T0);
    return id;
  }
}
