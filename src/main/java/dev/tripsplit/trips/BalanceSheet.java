package dev.tripsplit.trips;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Net positions of every member in the trip's base currency. {@code asOf} is null for the current
 * sheet. Positive balance: the group owes the member.
 */
public record BalanceSheet(
    UUID tripId, long version, String currency, Instant asOf, List<MemberBalance> balances) {

  public BalanceSheet {
    balances = List.copyOf(balances);
  }

  /** One member's net position in minor units. */
  public record MemberBalance(UUID memberId, String name, long balanceMinor) {}
}
