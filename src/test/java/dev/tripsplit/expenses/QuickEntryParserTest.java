package dev.tripsplit.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tripsplit.expenses.domain.QuickEntry;
import dev.tripsplit.expenses.domain.QuickEntryParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuickEntryParserTest {

  private static final List<String> MEMBERS = List.of("Omar", "Lina", "Karim", "Nour");

  private static QuickEntry parse(String text) {
    return QuickEntryParser.parse(text, MEMBERS).orElseThrow();
  }

  @Test
  void amountDescriptionAndNamedParticipants() {
    var entry = parse("dinner 600 split with omar and Lina");
    assertThat(entry.description()).isEqualTo("Dinner");
    assertThat(entry.amount()).isEqualTo("600");
    assertThat(entry.currency()).isNull();
    assertThat(entry.payer()).isNull();
    assertThat(entry.participants()).containsExactly("Omar", "Lina");
  }

  @Test
  void payerFirstWithCurrencyAfterTheAmount() {
    var entry = parse("Lina paid 40 usd for the boat trip");
    assertThat(entry.payer()).isEqualTo("Lina");
    assertThat(entry.amount()).isEqualTo("40");
    assertThat(entry.currency()).isEqualTo("USD");
    assertThat(entry.description()).isEqualTo("The boat trip");
    assertThat(entry.participants()).containsExactlyElementsOf(MEMBERS);
  }

  @Test
  void payerJoinsTheSplitWhenOthersAreNamed() {
    var entry = parse("taxi $12.50 paid by karim with Nour");
    assertThat(entry.currency()).isEqualTo("USD");
    assertThat(entry.amount()).isEqualTo("12.50");
    assertThat(entry.payer()).isEqualTo("Karim");
    assertThat(entry.participants()).containsExactly("Karim", "Nour");
    assertThat(entry.description()).isEqualTo("Taxi");
  }

  @Test
  void everyoneAndDecimalComma() {
    var entry = parse("groceries 870,25 LE everyone");
    assertThat(entry.amount()).isEqualTo("870.25");
    assertThat(entry.currency()).isEqualTo("EGP");
    assertThat(entry.participants()).hasSize(4);
    assertThat(entry.description()).isEqualTo("Groceries");
  }

  @Test
  void reportsNamesThatAreNotMembers() {
    var entry = parse("lunch 300 with Omar, Zed");
    assertThat(entry.participants()).containsExactly("Omar");
    assertThat(entry.unknownNames()).containsExactly("Zed");
  }

  @Test
  void nothingToParseWithoutAnAmount() {
    assertThat(QuickEntryParser.parse("dinner with Omar", MEMBERS)).isEmpty();
    assertThat(QuickEntryParser.parse("  ", MEMBERS)).isEmpty();
  }
}
