package dev.tripsplit.trips;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import dev.tripsplit.trips.application.ExpenseCsv;
import dev.tripsplit.trips.domain.ExpenseDraft;
import dev.tripsplit.trips.domain.Trip;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseCsvTest {

  private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

  @Test
  void writesOneRowPerExpenseWithSharesInTheBaseCurrencyAndKeepsVoidedOnesMarked() {
    var trip =
        Trip.create(
            UUID.randomUUID(), "Siwa", "EGP", List.of("Omar", "Lina"), UUID::randomUUID, T0);
    var omar = trip.members().get(0).id();
    var lina = trip.members().get(1).id();
    var boat = UUID.randomUUID();
    trip.addExpense(
        boat,
        new ExpenseDraft(
            "Boat \"Nile Queen\"",
            omar,
            Money.parse("10.00", "USD"),
            new BigDecimal("50"),
            new SplitRule.Equal(List.of(omar, lina))),
        T0);
    trip.addExpense(
        UUID.randomUUID(),
        new ExpenseDraft(
            "=HYPERLINK(\"x\")",
            lina,
            Money.parse("90.00", "EGP"),
            null,
            new SplitRule.Equal(List.of(lina))),
        T0.plusSeconds(60));
    trip.voidExpense(boat, "paid twice", T0.plusSeconds(120));

    var lines = ExpenseCsv.of(trip).split("\r\n");

    assertThat(lines)
        .containsExactly(
            "date,description,paid_by,amount,currency,rate,amount_EGP,status,share_Omar,share_Lina",
            "2026-03-01T10:00:00Z,\"Boat \"\"Nile Queen\"\"\",Omar,10.00,USD,50,500.00,voided,250.00,250.00",
            "2026-03-01T10:01:00Z,\"'=HYPERLINK(\"\"x\"\")\",Lina,90.00,EGP,1,90.00,active,,90.00");
  }
}
