package dev.tripsplit.expenses.domain;

import java.util.List;

/**
 * What {@link QuickEntryParser} understood. {@code currency} and {@code payer} are null when the
 * text did not say; {@code unknownNames} are names that match no member.
 */
public record QuickEntry(
    String description,
    String amount,
    String currency,
    String payer,
    List<String> participants,
    List<String> unknownNames) {

  public QuickEntry {
    participants = List.copyOf(participants);
    unknownNames = List.copyOf(unknownNames);
  }
}
