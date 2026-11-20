package dev.tripsplit.expenses.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based quick entry: {@code "dinner 600 split with Omar and Lina"}, {@code "Lina paid 40 usd
 * for the boat"}, {@code "groceries 870.25 everyone"}. It only proposes a draft; the person
 * confirms it in the form. No AI involved.
 */
public final class QuickEntryParser {

  private static final Map<String, String> CURRENCY_WORDS =
      Map.ofEntries(
          Map.entry("le", "EGP"),
          Map.entry("egp", "EGP"),
          Map.entry("pounds", "EGP"),
          Map.entry("$", "USD"),
          Map.entry("usd", "USD"),
          Map.entry("dollars", "USD"),
          Map.entry("€", "EUR"),
          Map.entry("eur", "EUR"),
          Map.entry("euros", "EUR"),
          Map.entry("sar", "SAR"),
          Map.entry("aed", "AED"),
          Map.entry("gbp", "GBP"));

  private static final String CURRENCY = "(le|egp|pounds|usd|dollars|eur|euros|sar|aed|gbp|\\$|€)";
  private static final Pattern AMOUNT =
      Pattern.compile(
          "(?:(?<!\\p{L})"
              + CURRENCY
              + "\\s*)?(\\d+(?:[.,]\\d{1,3})?)(?:\\s*"
              + CURRENCY
              + "(?![a-z]))?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern PAID_BY =
      Pattern.compile("\\bpaid by\\s+(\\p{L}+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern PAYER_FIRST =
      Pattern.compile("^(\\p{L}+)\\s+(?:paid|spent)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPLIT =
      Pattern.compile(
          "\\b(?:split\\s+)?(?:with|between|among)\\s+(.+)$|\\b(?:split\\s+)?(everyone|everybody|all of us)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern NAME_SEPARATOR =
      Pattern.compile("\\s*(?:,|&|\\band\\b)\\s*", Pattern.CASE_INSENSITIVE);

  private QuickEntryParser() {}

  public static Optional<QuickEntry> parse(String text, List<String> memberNames) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    var rest = text.strip().replaceAll("\\s+", " ");
    var amount = AMOUNT.matcher(rest);
    if (!amount.find()) {
      return Optional.empty();
    }
    var value = amount.group(2).replace(',', '.');
    var currency = currency(amount.group(1), amount.group(3));
    rest = cut(rest, amount);

    String payer = null;
    var unknown = new ArrayList<String>();
    var first = PAYER_FIRST.matcher(rest);
    if (first.find() && resolve(first.group(1), memberNames).isPresent()) {
      payer = resolve(first.group(1), memberNames).get();
      rest = cut(rest, first);
    } else {
      var by = PAID_BY.matcher(rest);
      if (by.find()) {
        payer = resolve(by.group(1), memberNames).orElse(null);
        if (payer == null) {
          unknown.add(by.group(1));
        }
        rest = cut(rest, by);
      }
    }

    var participants = new LinkedHashSet<String>();
    var split = SPLIT.matcher(rest);
    if (split.find()) {
      if (split.group(2) != null) {
        participants.addAll(memberNames);
      } else {
        if (payer != null) {
          participants.add(payer);
        }
        for (var name : NAME_SEPARATOR.split(split.group(1).strip())) {
          if (name.isBlank()) {
            continue;
          }
          resolve(name, memberNames).ifPresentOrElse(participants::add, () -> unknown.add(name));
        }
      }
      rest = cut(rest, split);
    } else {
      participants.addAll(memberNames);
    }

    return Optional.of(
        new QuickEntry(
            description(rest), value, currency, payer, List.copyOf(participants), unknown));
  }

  private static String currency(String before, String after) {
    var word = after != null ? after : before;
    return word == null ? null : CURRENCY_WORDS.get(word.toLowerCase(Locale.ROOT));
  }

  private static Optional<String> resolve(String name, List<String> memberNames) {
    var wanted = name.strip();
    return memberNames.stream().filter(m -> m.equalsIgnoreCase(wanted)).findFirst();
  }

  private static String cut(String text, Matcher match) {
    return (text.substring(0, match.start()) + " " + text.substring(match.end()))
        .replaceAll("\\s+", " ")
        .strip();
  }

  private static String description(String rest) {
    var cleaned =
        rest.replaceAll("(?i)^(for|on|at)\\s+", "")
            .replaceAll("(?i)\\s+(for|on|at|split)$", "")
            .strip();
    if (cleaned.isEmpty()) {
      return "Expense";
    }
    return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
  }
}
