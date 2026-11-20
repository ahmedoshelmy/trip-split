package dev.tripsplit.trips.application;

import dev.tripsplit.expenses.domain.Share;
import dev.tripsplit.money.Money;
import dev.tripsplit.trips.domain.Expense;
import dev.tripsplit.trips.domain.Member;
import dev.tripsplit.trips.domain.Trip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The trip's expenses as RFC 4180 CSV for a spreadsheet: one row per expense, one share column per
 * member in the base currency. Voided expenses stay in, marked, so the file matches the history.
 * Cells that a spreadsheet would run as a formula ({@code = + - @}) are prefixed with an
 * apostrophe.
 */
public final class ExpenseCsv {

  private static final String EOL = "\r\n";

  private ExpenseCsv() {}

  public static String of(Trip trip) {
    var members = trip.members();
    var out = new StringBuilder();
    var header =
        new ArrayList<>(
            List.of(
                "date",
                "description",
                "paid_by",
                "amount",
                "currency",
                "rate",
                "amount_" + trip.baseCurrency(),
                "status"));
    members.forEach(m -> header.add("share_" + m.name()));
    appendRow(out, header);
    for (var expense : trip.expenses()) {
      appendRow(out, row(trip, members, expense));
    }
    return out.toString();
  }

  private static List<String> row(Trip trip, List<Member> members, Expense expense) {
    var shares =
        expense.shares().stream().collect(Collectors.toMap(Share::memberId, Share::baseMinor));
    var cells =
        new ArrayList<>(
            List.of(
                expense.occurredAt().toString(),
                expense.description(),
                trip.member(expense.paidBy()).name(),
                expense.amount().toDecimalString(),
                expense.amount().currency(),
                expense.rate().toPlainString(),
                expense.baseAmount().toDecimalString(),
                expense.voided() ? "voided" : "active"));
    members.forEach(m -> cells.add(share(shares, m.id(), trip.baseCurrency())));
    return cells;
  }

  private static String share(Map<UUID, Long> shares, UUID memberId, String currency) {
    var minor = shares.get(memberId);
    return minor == null ? "" : Money.of(minor, currency).toDecimalString();
  }

  private static void appendRow(StringBuilder out, List<String> cells) {
    out.append(cells.stream().map(ExpenseCsv::cell).collect(Collectors.joining(","))).append(EOL);
  }

  static String cell(String value) {
    var safe = value;
    if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0 && !isNumber(safe)) {
      safe = "'" + safe;
    }
    if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
      return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    return safe;
  }

  private static boolean isNumber(String value) {
    return value.matches("-?\\d+(\\.\\d+)?");
  }
}
