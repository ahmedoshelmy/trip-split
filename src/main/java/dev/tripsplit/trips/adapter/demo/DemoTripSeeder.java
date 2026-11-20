package dev.tripsplit.trips.adapter.demo;

import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import dev.tripsplit.trips.application.TripCommands;
import dev.tripsplit.trips.application.TripQueryService;
import dev.tripsplit.trips.domain.ExpenseDraft;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds one invented trip when {@code TRIPSPLIT_DEMO_DATA=true} and the database has no trips.
 * Names, places and amounts are made up.
 */
@Component
@ConditionalOnBooleanProperty("tripsplit.demo-data")
class DemoTripSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoTripSeeder.class);
  private static final BigDecimal USD_RATE = new BigDecimal("48.75");

  private final TripCommands commands;
  private final TripQueryService queries;

  DemoTripSeeder(TripCommands commands, TripQueryService queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @EventListener(ApplicationReadyEvent.class)
  void seed() {
    if (!queries.trips().isEmpty()) {
      return;
    }
    var tripId =
        commands
            .createTrip("Dahab long weekend", "EGP", List.of("Omar", "Lina", "Karim", "Nour"))
            .tripId();
    var ids = queries.trip(tripId).orElseThrow().members().stream().map(m -> m.id()).toList();
    var omar = ids.get(0);
    var lina = ids.get(1);
    var karim = ids.get(2);
    var nour = ids.get(3);
    var all = new SplitRule.Equal(ids);
    egp(tripId, "Bus tickets Cairo - Dahab", omar, "2400.00", all);
    egp(tripId, "Camp hut, 3 nights", lina, "5400.00", all);
    egp(tripId, "Fish dinner at the lagoon", karim, "1630.50", all);
    usd(tripId, "Snorkel gear rental", nour, "40.00", all);
    egp(
        tripId,
        "Groceries",
        omar,
        "870.25",
        new SplitRule.Shares(Map.of(omar, 1, lina, 1, karim, 2, nour, 1)));
    usd(tripId, "Blue Hole boat trip", lina, "120.00", all);
    egp(tripId, "Breakfast", nour, "460.00", new SplitRule.Equal(List.of(lina, nour)));
    egp(
        tripId,
        "Souvenirs",
        karim,
        "1200.00",
        new SplitRule.Exact(Map.of(karim, 70_000L, omar, 50_000L)));
    egp(tripId, "Fuel for the jeep", omar, "950.00", all);
    usd(tripId, "Canyon safari", karim, "60.00", new SplitRule.Equal(List.of(omar, karim, nour)));
    egp(tripId, "Juice bar", lina, "315.00", all);
    egp(tripId, "Pharmacy", nour, "185.75", new SplitRule.Equal(List.of(nour)));
    egp(tripId, "Late-night koshary", omar, "240.00", all);
    egp(
        tripId,
        "Taxi to the lighthouse",
        karim,
        "150.00",
        new SplitRule.Equal(List.of(karim, lina)));
    egp(tripId, "Bus snacks", lina, "199.99", all);
    log.info("Seeded demo trip {}", tripId);
  }

  private void egp(UUID tripId, String what, UUID paidBy, String amount, SplitRule split) {
    commands.addExpense(
        tripId, null, new ExpenseDraft(what, paidBy, Money.parse(amount, "EGP"), null, split));
  }

  private void usd(UUID tripId, String what, UUID paidBy, String amount, SplitRule split) {
    commands.addExpense(
        tripId, null, new ExpenseDraft(what, paidBy, Money.parse(amount, "USD"), USD_RATE, split));
  }
}
